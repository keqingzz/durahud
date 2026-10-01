package dev.durahud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    public static final int CONFIG_VERSION = 6;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "durahud.json";
    private static final String PRESET_NAME = "durahud-preset.json";
    private static final String VERSION_KEY = "configVersion";

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

    /** 预设路径：config/durahud-preset.json（导出与导入都读写它）。 */
    public static Path presetPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(PRESET_NAME);
    }

    public static DuraHudConfig load() {
        Path path = path();
        if (!Files.exists(path)) {
            return new DuraHudConfig();
        }
        try {
            JsonObject root = parse(Files.readString(path, StandardCharsets.UTF_8));
            migrate(root);
            DuraHudConfig cfg = read(root);
            cfg.sanitize();
            return cfg;
        } catch (Exception e) {
            DuraHudClient.LOGGER.warn("[DuraHUD] 配置文件读取失败，改用默认值：{}", e.toString());
            return new DuraHudConfig();
        }
    }

    public static void save(DuraHudConfig cfg) {
        write(path(), cfg, "配置");
    }

    /** 把当前配置导出成预设文件；返回是否成功（界面据此显示提示）。 */
    public static boolean exportPreset(DuraHudConfig cfg) {
        return write(presetPath(), cfg, "预设");
    }

    /** 读回预设；文件不存在或格式有问题时返回 null（界面据此显示提示）。 */
    public static DuraHudConfig importPreset() {
        Path path = presetPath();
        if (!Files.exists(path)) {
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
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(toJson(cfg)), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            DuraHudClient.LOGGER.warn("[DuraHUD] {} 写入失败：{}", what, e.toString());
            return false;
        }
    }

    private static JsonObject parse(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static JsonObject toJson(DuraHudConfig cfg) {
        JsonObject root = GSON.toJsonTree(cfg).getAsJsonObject();
        root.addProperty(VERSION_KEY, CONFIG_VERSION);
        return root;
    }

    /**
     * 按版本号逐级升级。每加一个版本就补一个 if 分支，旧文件永远能被读进来。
     * 迁移只改键的集合，字段本身的缺省由 {@link #read} 的「有才覆盖」负责。
     */
    static void migrate(JsonObject root) {
        int from = root.has(VERSION_KEY) ? root.get(VERSION_KEY).getAsInt() : 0;
        if (from >= CONFIG_VERSION) {
            return;
        }
        if (from < 1) {
            for (String key : REMOVED_KEYS) {
                root.remove(key);
            }
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
            // v5 → v6：三处合并。
            // 1) cellFadeSpeed（四档枚举）→ animationMillis（毫秒，界面改成滑条）。
            if (root.has("cellFadeSpeed")) {
                root.addProperty("animationMillis", fadeSpeedToMillis(root.get("cellFadeSpeed").getAsString()));
            }
            // 2) showValue / showPercent（两个布尔）→ valueDisplay（OFF / PERCENT / VALUE）：
            //    有「剩余-上限」就优先它，否则看百分比 —— 与旧 label() 的取值顺序一致。
            boolean showValue = root.has("showValue") && root.get("showValue").getAsBoolean();
            boolean showPercent = root.has("showPercent") && root.get("showPercent").getAsBoolean();
            if (showValue) {
                root.addProperty("valueDisplay", "VALUE");
            } else if (showPercent) {
                root.addProperty("valueDisplay", "PERCENT");
            }
            // 3) compactCells → 合并进 showEmptySlots：旧的 compactCells=false 表示「空槽位恒定占位」，
            //    正是新语义里 showEmptySlots=true 的样子。
            if (root.has("compactCells") && !root.get("compactCells").getAsBoolean()) {
                root.addProperty("showEmptySlots", true);
            }
            root.remove("cellFadeSpeed");
            root.remove("showValue");
            root.remove("showPercent");
            root.remove("compactCells");
        }
        root.addProperty(VERSION_KEY, CONFIG_VERSION);
        DuraHudClient.LOGGER.info("[DuraHUD] 配置已从版本 {} 升级到 {}。", from, CONFIG_VERSION);
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
    private static DuraHudConfig read(JsonObject root) {
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
        cfg.showSlotFrames = json.b("showSlotFrames", cfg.showSlotFrames);
        cfg.showItemIcons = json.b("showItemIcons", cfg.showItemIcons);
        cfg.animateCells = json.b("animateCells", cfg.animateCells);
        cfg.animationMillis = json.i("animationMillis", cfg.animationMillis);
        cfg.highlightCritical = json.b("highlightCritical", cfg.highlightCritical);
        cfg.warnThreshold = json.i("warnThreshold", cfg.warnThreshold);
        cfg.critThreshold = json.i("critThreshold", cfg.critThreshold);
        return cfg;
    }

    /** 只读取值封装：缺字段就返回当前值，「有才覆盖」这件事因此只写一遍。 */
    private static final class Json {

        private final JsonObject object;

        Json(JsonObject object) {
            this.object = object;
        }

        boolean has(String key) {
            return object.has(key);
        }

        boolean b(String key, boolean current) {
            return object.has(key) ? object.get(key).getAsBoolean() : current;
        }

        int i(String key, int current) {
            return object.has(key) ? object.get(key).getAsInt() : current;
        }

        float f(String key, float current) {
            return object.has(key) ? object.get(key).getAsFloat() : current;
        }

        <T extends Enum<T>> T e(String key, Class<T> type, T current) {
            if (!object.has(key)) {
                return current;
            }
            String name = object.get(key).getAsString();
            for (T candidate : type.getEnumConstants()) {
                if (candidate.name().equalsIgnoreCase(name)) {
                    return candidate;
                }
            }
            return current;
        }
    }
}
