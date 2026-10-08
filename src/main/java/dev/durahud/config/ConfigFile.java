package dev.durahud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.durahud.DuraHudClient;
import dev.durahud.config.DuraHudConfig.Anchor;
import dev.durahud.config.DuraHudConfig.BarSide;
import dev.durahud.config.DuraHudConfig.ValueDisplay;
import dev.durahud.config.DuraHudConfig.Orientation;
import dev.durahud.config.DuraHudConfig.ValuePlacement;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * config/durahud.json 的读写。字段清单只有这里一处，模型类保持纯数据。
 *
 * <p>读的时候逐个字段「有才覆盖」：缺字段保留默认值；枚举容错解析，所以删掉/改名的旧值
 * （例如旧版 barStyle=XP、onlyDamageable）只会退回默认值，不会让整份配置失效。</p>
 *
 * <p>文件里带一个 {@code configVersion}：升级后靠它把废弃键清掉、并在将来做字段改名时
 * 有据可依（见 {@link #migrate}）。预设文件用同一套读写，因此导出的预设一定能被导入。</p>
 */
public final class ConfigFile {

    /** 当前配置格式版本。字段增删都要 +1，并在 migrate 里补一条升级路径。 */
    public static final int CONFIG_VERSION = 7;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "durahud.json";
    /** 默认预设文件名。用 .txt：导出的是给人看、可手工编辑的 JSON 文本（见 {@link #exportTo}）。 */
    private static final String PRESET_NAME = "durahud-preset.txt";
    private static final String VERSION_KEY = "configVersion";

    /**
     * 上一次 {@link #load()} 之后要不要写回文件。首次运行（文件还不存在）与升级过格式时为
     * true；读失败时<b>必须</b>是 false —— 否则一份坏掉的配置会被默认值直接覆盖掉。
     */
    private static boolean writeBack;

    /** 上一次 load() 是否需要把内存里的配置写回磁盘（见 {@link #writeBack}）。 */
    public static boolean shouldWriteBack() {
        return writeBack;
    }

    /** 已废弃的键。升级时顺手删掉，旧文件不会一直带着垃圾。 */
    private static final String[] REMOVED_KEYS = {
        "onlyDamageable", // 旧版：只显示可损坏的手持物，会把模组装备一起过滤掉
        "barStyle",       // 旧版：实验性的条样式（整组功能已删除）
        "cellStyle",      // 旧版：实验性的格子样式（整组功能已删除）
        "cellOpacity",    // 旧版：格子透明度（alpha 乘法在物品渲染后失效）
    };

    private ConfigFile() {
    }

    /** 主配置路径：config/durahud.json。 */
    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    /** 默认预设路径：config/durahud-preset.txt（原生文件框的初始目录与文件名）。 */
    public static Path presetPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(PRESET_NAME);
    }

    public static DuraHudConfig load() {
        Path path = path();
        if (!Files.exists(path)) {
            writeBack = true;   // 首次运行：把默认值落成文件，用户才有东西可改
            return new DuraHudConfig();
        }
        try {
            JsonObject root = parse(Files.readString(path, StandardCharsets.UTF_8));
            boolean upgraded = migrate(root);
            DuraHudConfig cfg = read(root);
            cfg.sanitize();
            writeBack = upgraded;   // 只有真的升级过才回写；平时读一次不碰磁盘
            return cfg;
        } catch (Exception e) {
            // 文件连 JSON 都不是（语法坏了、顶层不是对象）。刻意<b>不</b>回写：默认值覆盖上去
            // 就把用户手改过的设置永久毁了 —— 保留原文件，让人能自己看一眼、修一修或删掉。
            writeBack = false;
            DuraHudClient.LOGGER.warn("[DuraHUD] 配置文件读取失败，本次先用默认值，原文件保持不动：{}", e.toString());
            return new DuraHudConfig();
        }
    }

    public static void save(DuraHudConfig cfg) {
        write(path(), cfg, "配置");
    }

    /** 默认预设文件名（界面拿它当原生「另存为」框里的初始名字）。 */
    public static String defaultPresetName() {
        return PRESET_NAME;
    }

    /** 导出到 config 目录下的默认预设文件；返回是否成功（无图形环境时的回退路径）。 */
    public static boolean exportPreset(DuraHudConfig cfg) {
        return exportTo(presetPath(), cfg);
    }

    /** 读回 config 目录下的默认预设文件；不存在或格式有问题时返回 null。 */
    public static DuraHudConfig importPreset() {
        return importFrom(presetPath());
    }

    /**
     * 把当前配置导出到指定文件（原生「另存为」框选出来的路径）；返回是否成功。
     *
     * <p>写的是与主配置同一套 JSON，只是扩展名用 .txt：内容对人可读、能手改，
     * 也不至于被误当成模组自己的配置文件。</p>
     */
    public static boolean exportTo(Path path, DuraHudConfig cfg) {
        return path != null && write(path, cfg, "预设");
    }

    /** 从指定文件读一份配置（原生「打开」框选出来的路径）；不存在或格式有问题时返回 null。 */
    public static DuraHudConfig importFrom(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            JsonObject root = parse(Files.readString(path, StandardCharsets.UTF_8));
            migrate(root);
            DuraHudConfig cfg = read(root);
            cfg.sanitize();
            return cfg;
        } catch (Exception e) {
            DuraHudClient.LOGGER.warn("[DuraHUD] 预设导入失败：{}", e.toString());
            return null;
        }
    }

    private static boolean write(Path path, DuraHudConfig cfg, String what) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, GSON.toJson(toJson(cfg)), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            DuraHudClient.LOGGER.warn("[DuraHUD] {} 写入失败：{}", what, e.toString());
            return false;
        }
    }

    /** 包内可见：让单测直接覆盖读写路径（{@link #path()} 依赖 FabricLoader，离线跑不了）。 */
    static JsonObject parse(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    /** 包内可见，理由同 {@link #parse(String)}。 */
    static JsonObject toJson(DuraHudConfig cfg) {
        JsonObject root = GSON.toJsonTree(cfg).getAsJsonObject();
        root.addProperty(VERSION_KEY, CONFIG_VERSION);
        return root;
    }

    /**
     * 按版本号逐级升级。每加一个版本就补一个 if 分支，旧文件永远能被读进来。
     * 迁移只改键的集合，字段本身的缺省由 {@link #read} 的「有才覆盖」负责。
     */
    static boolean migrate(JsonObject root) {
        int from = versionOf(root);
        if (from >= CONFIG_VERSION) {
            return false;
        }
        // 废弃键对<b>任何</b>旧文件都清掉。这些键在 v0 时期就存在，只会出现在旧文件里；以前只在
        // from < 1 时清，于是标着 v1..v5 的文件升级后仍会一直带着 barStyle / cellOpacity 这些垃圾键。
        for (String key : REMOVED_KEYS) {
            root.remove(key);
        }
        if (from < 2) {
            // v1 → v2：onlyDurableHand 改名 onlyDurableItems，含义也从「只看手持」扩到所有槽位，
            // 所以旧值原样搬过去即可（true 依旧是「只显示有耐久信息的物品」）。
            if (root.has("onlyDurableHand") && !root.has("onlyDurableItems")) {
                root.add("onlyDurableItems", root.get("onlyDurableHand"));
            }
            root.remove("onlyDurableHand");
        }
        if (from < 3) {
            // v2 → v3：新增 showWornNonDurable（缺省 false）。没有键要搬，所以这一步是空操作 ——
            // 保留分支是为了让「一个版本一个 if」的读法成立，新字段的缺省由 read 的「有才覆盖」给出。
        }
        if (from < 4) {
            // v3 → v4：新增 animateCells（缺省 true）与 cellFadeSpeed（缺省 NORMAL）。
            // 同样没有键要搬，理由见上一条。
        }
        if (from < 5) {
            // v4 → v5：新增 compactCells（缺省 true）。同样没有键要搬。该字段又在 v6 被合并掉，
            // 所以这一步现在只剩「老文件也走过一遍版本号」的意义。
        }
        if (from < 6) {
            // v5 → v6：三处合并。每条都只在「旧键有、新键还没有」时才写 —— 手改过的文件里
            // 已经存在的 animationMillis / valueDisplay / showEmptySlots 不能被这次迁移盖掉。
            // 1) cellFadeSpeed（四档枚举）→ animationMillis（毫秒，界面改成滑条）。
            if (!root.has("animationMillis") && isString(root, "cellFadeSpeed")) {
                root.addProperty("animationMillis", fadeSpeedToMillis(root.get("cellFadeSpeed").getAsString()));
            }
            // 2) showValue / showPercent（两个布尔）→ valueDisplay（OFF / PERCENT / VALUE）：
            //    有「剩余-上限」就优先它，否则看百分比 —— 与旧 label() 的取值顺序一致。
            //    两个都是 false 时不发明这个键：v5 里「两个都关」的写法就是键不在，缺省自然落到 OFF。
            if (!root.has("valueDisplay") && (isTrue(root, "showValue") || isTrue(root, "showPercent"))) {
                root.addProperty("valueDisplay", isTrue(root, "showValue") ? "VALUE" : "PERCENT");
            }
            // 3) compactCells → 合并进 showEmptySlots：旧的 compactCells=false 表示「空槽位恒定占位」，
            //    正是新语义里 showEmptySlots=true 的样子。这一条必须让旧开关说了算 —— v5 的 showEmptySlots
            //    只管「画不画空槽位边框」，正是被并掉的那一半，所以不能因为它已经存在就跳过换算。
            if (root.has("compactCells") && !isTrue(root, "compactCells")) {
                root.addProperty("showEmptySlots", true);
            }
            root.remove("cellFadeSpeed");
            root.remove("showValue");
            root.remove("showPercent");
            root.remove("compactCells");
        }
        if (from < 7) {
            // v6 → v7：新增 valueOffsetY（缺省 0 = 单行数值贴紧耐久条）。没有键要搬 ——
            // 老文件读出来就是 0，正是不想改动任何人观感的默认值。
        }
        root.addProperty(VERSION_KEY, CONFIG_VERSION);
        DuraHudClient.LOGGER.info("[DuraHUD] 配置已从版本 {} 升级到 {}。", from, CONFIG_VERSION);
        return true;
    }

    /** 读 configVersion：缺失或不是数字都当作 0（最老的格式），坏值不会让异常冒到 load 之外。 */
    private static int versionOf(JsonObject root) {
        JsonElement element = root.get(VERSION_KEY);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        return element.getAsInt();
    }

    /** 迁移里读旧布尔：只有真的是 boolean true 才算 true（手改成字符串 / null 一律当 false）。 */
    private static boolean isTrue(JsonObject root, String key) {
        JsonElement element = root.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }

    private static boolean isString(JsonObject root, String key) {
        JsonElement element = root.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }

    /** v5 的 cellFadeSpeed 枚举名换算成毫秒；认不出来的值退回默认 200。 */
    private static int fadeSpeedToMillis(String name) {
        if (name == null) {
            return 200;
        }
        return switch (name.toUpperCase(java.util.Locale.ROOT)) {
            case "FAST" -> 100;
            case "SLOW" -> 450;
            case "SLOWEST" -> 900;
            default -> 200;
        };
    }

    /** 把 JSON 读成配置；缺字段保留默认值（「有才覆盖」只在这里写一遍）。 */
    /** 包内可见，理由同 {@link #parse(String)}。 */
    static DuraHudConfig read(JsonObject root) {
        DuraHudConfig cfg = new DuraHudConfig();
        Json json = new Json(root);
        cfg.enabled = json.b("enabled", cfg.enabled);
        cfg.anchor = json.e("anchor", Anchor.class, cfg.anchor);
        cfg.orientation = json.e("orientation", Orientation.class, cfg.orientation);
        cfg.barPlacement = json.e("barPlacement", BarSide.class, cfg.barPlacement);
        cfg.valuePlacement = json.e("valuePlacement", ValuePlacement.class, cfg.valuePlacement);
        cfg.barLength = json.i("barLength", cfg.barLength);
        cfg.offsetX = json.i("offsetX", cfg.offsetX);
        cfg.offsetY = json.i("offsetY", cfg.offsetY);
        cfg.scale = json.f("scale", cfg.scale);
        cfg.showMainhand = json.b("showMainhand", cfg.showMainhand);
        cfg.showOffhand = json.b("showOffhand", cfg.showOffhand);
        cfg.showArmor = json.b("showArmor", cfg.showArmor);
        cfg.showTrinkets = json.b("showTrinkets", cfg.showTrinkets);
        // 旧字段 onlyDamageable 已废弃（它会把模组装备一起过滤掉），刻意不读，直接用新默认值。
        cfg.onlyDurableItems = json.b("onlyDurableItems", cfg.onlyDurableItems);
        cfg.showWornNonDurable = json.b("showWornNonDurable", cfg.showWornNonDurable);
        cfg.hideFull = json.b("hideFull", cfg.hideFull);
        cfg.showEmptySlots = json.b("showEmptySlots", cfg.showEmptySlots);
        cfg.valueDisplay = json.e("valueDisplay", ValueDisplay.class, cfg.valueDisplay);
        // 刻意不在这里夹取：夹取属于 sanitize()。read 的职责是「有才覆盖」，逐字段往返才可验证。
        cfg.valueOffsetY = json.i("valueOffsetY", cfg.valueOffsetY);
        cfg.showSlotFrames = json.b("showSlotFrames", cfg.showSlotFrames);
        cfg.showItemIcons = json.b("showItemIcons", cfg.showItemIcons);
        cfg.animateCells = json.b("animateCells", cfg.animateCells);
        cfg.animationMillis = json.i("animationMillis", cfg.animationMillis);
        cfg.highlightCritical = json.b("highlightCritical", cfg.highlightCritical);
        cfg.warnThreshold = json.i("warnThreshold", cfg.warnThreshold);
        cfg.critThreshold = json.i("critThreshold", cfg.critThreshold);
        return cfg;
    }

    /**
     * 只读取值封装：缺字段、{@code null}、类型不符都返回当前值 ——「有才覆盖」与「一个坏字段
     * 只影响它自己」这两件事因此只写一遍。
     *
     * <p>类型检查不是洁癖：Gson 的 {@code getAsBoolean()} 在 {@code null} / 对象 / 数组上会抛
     * {@code UnsupportedOperationException}，{@code getAsInt()} 在 {@code "90abc"} 上会抛
     * {@code NumberFormatException} —— 以前这些异常会冒到 {@link #load()} 的兜底分支，
     * 让<b>整份</b>配置退回默认值。（更糟的是客户端随后还会把它写回磁盘。）</p>
     */
    private static final class Json {

        private final JsonObject object;

        Json(JsonObject object) {
            this.object = object;
        }

        boolean b(String key, boolean current) {
            JsonPrimitive value = primitive(key);
            if (value == null) {
                return current;
            }
            if (value.isBoolean()) {
                return value.getAsBoolean();
            }
            return reject(key, "布尔值", current);
        }

        int i(String key, int current) {
            JsonPrimitive value = primitive(key);
            if (value == null) {
                return current;
            }
            if (value.isNumber()) {
                double raw = value.getAsDouble();   // 绕开 int / long 的字符串解析限制
                if (Double.isFinite(raw)) {
                    return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.rint(raw)));
                }
            }
            return reject(key, "整数", current);
        }

        float f(String key, float current) {
            JsonPrimitive value = primitive(key);
            if (value == null) {
                return current;
            }
            if (value.isNumber()) {
                double raw = value.getAsDouble();
                if (Double.isFinite(raw)) {
                    return (float) raw;
                }
            }
            return reject(key, "数字", current);
        }

        <T extends Enum<T>> T e(String key, Class<T> type, T current) {
            JsonPrimitive value = primitive(key);
            if (value == null || !value.isString()) {
                return reject(key, type.getSimpleName(), current);
            }
            String name = value.getAsString();
            for (T candidate : type.getEnumConstants()) {
                if (candidate.name().equalsIgnoreCase(name)) {
                    return candidate;
                }
            }
            return current;   // 认不出来的值（旧版本删掉的选项）静默退回默认值
        }

        /** 取基本类型值；缺键、null、对象、数组一律返回 null（=「这个字段没写」）。 */
        private JsonPrimitive primitive(String key) {
            JsonElement element = object.get(key);
            if (element == null || !element.isJsonPrimitive()) {
                return null;
            }
            return element.getAsJsonPrimitive();
        }

        private static <T> T reject(String key, String expected, T current) {
            DuraHudClient.LOGGER.warn("[DuraHUD] 配置字段 {} 不是{}，已忽略它（其余字段照常读取）。", key, expected);
            return current;
        }
    }
}
