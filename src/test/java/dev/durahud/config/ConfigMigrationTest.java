package dev.durahud.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置升级链的离线验证：只碰 {@link ConfigFile#migrate}，不需要 Minecraft 运行时。
 * 旧文件里的废弃键要被清掉、改名的键要保值、已经是当前版本的文件不能被改动。
 *
 * <p>守住的不变量：迁移只做「必要的改名 / 换算 / 清理」，缺的键一律留给 {@link ConfigFile#read} 的
 * 「有才覆盖」补默认值 —— 不许凭空发明键，也不许让废弃键复活（§10.5）。失败意味着用户升级后设置
 * 回退默认，或文件被改写成另一种东西；{@code migrate} 的返回值同时是「要不要回写磁盘」的信号，
 * 报错就等于每次启动都白写一遍盘。</p>
 */
class ConfigMigrationTest {

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    // ---------- 逐版本升级链（v0 → v2）：清废弃键、改名的键要保值 ----------

    // 防回归：v0 文件里 barStyle / cellOpacity 必须消失，改名键 onlyDurableHand 的值要落到
    // onlyDurableItems 上，barLength 一字不动。
    @Test
    void v0FileDropsRemovedKeysAndKeepsRenamedValue() {
        JsonObject root = parse(
                "{\"barStyle\":\"FLAT\",\"cellOpacity\":0.5,\"onlyDurableHand\":false,\"barLength\":90}");
        ConfigFile.migrate(root);
        assertEquals(ConfigFile.CONFIG_VERSION, root.get("configVersion").getAsInt());
        assertFalse(root.has("barStyle"));
        assertFalse(root.has("cellOpacity"));
        assertFalse(root.has("onlyDurableHand"));
        assertFalse(root.get("onlyDurableItems").getAsBoolean());
        assertEquals(90, root.get("barLength").getAsInt());
    }

    // 防回归：只标了 configVersion 1 的文件也要完成改名 —— 升级链不许跳级漏段。
    @Test
    void v1FileRenamesOnlyDurableHand() {
        JsonObject root = parse("{\"configVersion\":1,\"onlyDurableHand\":false}");
        ConfigFile.migrate(root);
        assertEquals(ConfigFile.CONFIG_VERSION, root.get("configVersion").getAsInt());
        assertFalse(root.has("onlyDurableHand"));
        assertFalse(root.get("onlyDurableItems").getAsBoolean());
    }

    // 防回归：迁移不许发明新键（showWornNonDurable 留给 read 补默认），已有的值照旧保留。
    @Test
    void v2FileGainsNewOptionByDefault() {
        JsonObject root = parse("{\"configVersion\":2,\"onlyDurableItems\":true,\"barLength\":90}");
        ConfigFile.migrate(root);
        assertEquals(ConfigFile.CONFIG_VERSION, root.get("configVersion").getAsInt());
        // 迁移不发明新键：showWornNonDurable 由 read 的「有才覆盖」给默认值 false。
        assertFalse(root.has("showWornNonDurable"));
        assertTrue(root.get("onlyDurableItems").getAsBoolean());
        assertEquals(90, root.get("barLength").getAsInt());
    }

    // ---------- 幂等与冲突：文件里的值比迁移逻辑新时不许覆盖 ----------

    // 防回归：已经是当前版本 → migrate 原地不动，值一个都不许改写。
    @Test
    void currentFileIsLeftAlone() {
        JsonObject root = parse("{\"configVersion\":" + ConfigFile.CONFIG_VERSION
                + ",\"onlyDurableItems\":false,\"barLength\":77}");
        ConfigFile.migrate(root);
        assertEquals(ConfigFile.CONFIG_VERSION, root.get("configVersion").getAsInt());
        assertFalse(root.get("onlyDurableItems").getAsBoolean());
        assertEquals(77, root.get("barLength").getAsInt());
    }

    // 防回归：改名撞上「新键」已经存在时不许覆盖它 —— 旧值只能垫在没有新值的字段上。
    @Test
    void renameDoesNotOverwriteNewerValue() {
        JsonObject root = parse("{\"configVersion\":1,\"onlyDurableHand\":true,\"onlyDurableItems\":false}");
        ConfigFile.migrate(root);
        assertFalse(root.has("onlyDurableHand"));
        assertFalse(root.get("onlyDurableItems").getAsBoolean());
    }

    // ---------- 逐版本升级链（v3 → v4）：新选项一律交给 read 的默认值 ----------

    // 防回归：v3 文件升级时不许顺手写进 animateCells / animationMillis / valueDisplay。
    @Test
    void v3FileGainsAnimationOptionsByDefault() {
        JsonObject root = parse("{\"configVersion\":3,\"showItemIcons\":false,\"barLength\":90}");
        ConfigFile.migrate(root);
        assertEquals(ConfigFile.CONFIG_VERSION, root.get("configVersion").getAsInt());
        assertFalse(root.get("showItemIcons").getAsBoolean());
        assertEquals(90, root.get("barLength").getAsInt());
        // 迁移不发明新键：animateCells / animationMillis / valueDisplay 的缺省值由 read 的
        // 「有才覆盖」给出。
        assertFalse(root.has("animateCells"));
        assertFalse(root.has("animationMillis"));
        assertFalse(root.has("valueDisplay"));
    }

    // 防回归：v4 文件升级时不许发明 compactCells；showEmptySlots 的已有值要原样留着。
    @Test
    void v4FileGainsCompactionOptionByDefault() {
        JsonObject root = parse("{\"configVersion\":4,\"showEmptySlots\":false,\"barLength\":90}");
        ConfigFile.migrate(root);
        assertEquals(ConfigFile.CONFIG_VERSION, root.get("configVersion").getAsInt());
        assertFalse(root.get("showEmptySlots").getAsBoolean());
        assertEquals(90, root.get("barLength").getAsInt());
        // compactCells 在 v6 被合并进 showEmptySlots：没有这个键时不做任何改写。
        assertFalse(root.has("compactCells"));
        assertFalse(root.get("showEmptySlots").getAsBoolean());
    }

    // ---------- v5 换算：四档枚举 → 毫秒、两个布尔 → 三态 ----------

    // 防回归：SLOW→450、只开 showPercent→PERCENT、compactCells=false→showEmptySlots=true，
    // 换算完四个旧键必须全部消失。
    @Test
    void v5FileConvertsFadeSpeedAndValueToggles() {
        JsonObject root = parse("{\"configVersion\":5,\"cellFadeSpeed\":\"SLOW\",\"showPercent\":true,"
                + "\"showValue\":false,\"compactCells\":false,\"showEmptySlots\":false,\"barLength\":90}");
        ConfigFile.migrate(root);
        assertEquals(ConfigFile.CONFIG_VERSION, root.get("configVersion").getAsInt());
        // 四档枚举换算成毫秒：SLOW = 450
        assertEquals(450, root.get("animationMillis").getAsInt());
        // 两个布尔合并成三态：只开 showPercent = PERCENT
        assertEquals("PERCENT", root.get("valueDisplay").getAsString());
        // compactCells=false（空槽位恒定占位）等价于 showEmptySlots=true
        assertTrue(root.get("showEmptySlots").getAsBoolean());
        assertFalse(root.has("cellFadeSpeed"));
        assertFalse(root.has("showPercent"));
        assertFalse(root.has("showValue"));
        assertFalse(root.has("compactCells"));
        assertEquals(90, root.get("barLength").getAsInt());
    }

    // 防回归：两个布尔都开时取 VALUE（优先级写反就会把「剩余 / 上限」压成百分比）。
    @Test
    void v5FileWithBothValueTogglesPrefersValue() {
        JsonObject root = parse("{\"configVersion\":5,\"showPercent\":true,\"showValue\":true}");
        ConfigFile.migrate(root);
        assertEquals("VALUE", root.get("valueDisplay").getAsString());
    }

    // 防回归：两个布尔都关时不许写 valueDisplay —— 缺键代表「用默认」，不是「已经关掉」。
    @Test
    void v5FileWithoutValueTogglesInventsNothing() {
        JsonObject root = parse(
                "{\"configVersion\":5,\"showPercent\":false,\"showValue\":false,\"cellFadeSpeed\":\"NORMAL\"}");
        ConfigFile.migrate(root);
        assertEquals(200, root.get("animationMillis").getAsInt());
        assertFalse(root.has("valueDisplay"));
        assertFalse(root.has("cellFadeSpeed"));
    }

    // ---------- 不发明新键、也不让废键复活 ----------

    // 防回归（2.1.5）：缺 valueOffsetY 的老文件由 read 给 0（单行贴紧条），迁移只补版本号。
    @Test
    void v6FileGainsValueOffsetByDefaultWithoutInventingTheKey() {
        JsonObject root = parse("{\"configVersion\":6,\"valueDisplay\":\"PERCENT\",\"offsetY\":12}");
        ConfigFile.migrate(root);
        assertEquals(ConfigFile.CONFIG_VERSION, root.get("configVersion").getAsInt());
        // 迁移不发明新键：缺 valueOffsetY 的老文件由 read 的「有才覆盖」给出 0（单行贴紧条）。
        assertFalse(root.has("valueOffsetY"));
        assertEquals(0, ConfigFile.read(root).valueOffsetY);
        assertEquals(12, root.get("offsetY").getAsInt());
    }

    // 防回归：只删不改名的旧键（onlyDamageable）不许被任何改名规则顺手复活。
    @Test
    void removedKeyIsNotRevivedByRename() {
        JsonObject root = parse("{\"onlyDamageable\":true,\"onlyDurableItems\":true}");
        ConfigFile.migrate(root);
        assertFalse(root.has("onlyDamageable"));
        assertTrue(root.get("onlyDurableItems").getAsBoolean());
    }

    /**
     * 单个字段类型不符 / 是 {@code null} / 是对象时，只影响它自己：不许抛异常，
     * 也不许把整份配置冲成默认值（旧实现里一个 {@code "barLength":"90abc"} 就能做到这件事）。
     */
    // ---------- 坏值容错：只影响自己那一个字段 ----------

    // 防回归：类型不符 / null / 对象 / 数字当枚举，都只退回该字段的默认值，不许抛异常、不许整份回滚。
    @Test
    void brokenFieldTypesOnlyAffectThatField() {
        DuraHudConfig cfg = ConfigFile.read(ConfigFile.parse("""
                {
                  "configVersion": 6,
                  "barLength": "90abc",
                  "scale": null,
                  "enabled": 42,
                  "anchor": 7,
                  "showMainhand": {"x": 1},
                  "offsetX": 33,
                  "warnThreshold": 42
                }
                """));
        DuraHudConfig defaults = new DuraHudConfig();
        assertEquals(defaults.barLength, cfg.barLength, "坏数字不该覆盖默认值");
        assertEquals(defaults.scale, cfg.scale, 0.0F, "null 不该覆盖默认值");
        assertEquals(defaults.enabled, cfg.enabled, "数字不该当成布尔");
        assertEquals(defaults.anchor, cfg.anchor, "数字不该当成枚举名");
        assertEquals(defaults.showMainhand, cfg.showMainhand, "对象不该当成布尔");
        assertEquals(33, cfg.offsetX, "同一个文件里的正常字段必须照读");
        assertEquals(42, cfg.warnThreshold, "同一个文件里的正常字段必须照读");
    }

    /** 26 个字段写出去再读回来必须逐字段一致：read 与 write 的键名对不上时这条立刻炸。 */
    // ---------- 写读往返与废弃键 ----------

    // 防回归：read 与 toJson 的键名对不上时，写出去再读回来就会丢字段（这份手写清单是第二道网）。
    @Test
    void everyFieldSurvivesAWriteReadRoundTrip() {
        DuraHudConfig cfg = new DuraHudConfig();
        cfg.enabled = false;
        cfg.anchor = DuraHudConfig.Anchor.CUSTOM;
        cfg.orientation = DuraHudConfig.Orientation.VERTICAL;
        cfg.barPlacement = DuraHudConfig.BarSide.BELOW;
        cfg.valuePlacement = DuraHudConfig.ValuePlacement.LEFT;
        cfg.barLength = 77;
        cfg.offsetX = -42;
        cfg.offsetY = 42;
        cfg.scale = 1.5F;
        cfg.showMainhand = false;
        cfg.showOffhand = false;
        cfg.showArmor = false;
        cfg.showTrinkets = false;
        cfg.onlyDurableItems = false;
        cfg.showWornNonDurable = true;
        cfg.hideFull = true;
        cfg.showEmptySlots = true;
        cfg.valueDisplay = DuraHudConfig.ValueDisplay.VALUE;
        cfg.valueOffsetY = 9;
        cfg.showSlotFrames = false;
        cfg.showItemIcons = false;
        cfg.animateCells = false;
        cfg.animationMillis = 700;
        cfg.highlightCritical = false;
        cfg.warnThreshold = 33;
        cfg.critThreshold = 11;

        String written = ConfigFile.toJson(cfg).toString();
        DuraHudConfig back = ConfigFile.read(ConfigFile.parse(written));
        assertEquals(written, ConfigFile.toJson(back).toString(), "写出去再读回来必须逐字段一致");
    }

    /** 以前删掉的键（barStyle / compactCells / cellFadeSpeed 之类）留在文件里也不能让读盘失败。 */
    // 防回归：早先删掉的键留在文件里也不许让读盘失败（用户手动改过的老配置很常见）。
    @Test
    void removedKeysAreIgnored() {
        DuraHudConfig cfg = ConfigFile.read(ConfigFile.parse("""
                {"configVersion": 6, "barStyle": "FLAT", "cellFadeSpeed": "SLOW",
                 "compactCells": false, "showValue": true, "offsetY": 12}
                """));
        assertEquals(12, cfg.offsetY);
    }

    /** sanitize 刻意不夹 offsetX/offsetY（编辑模式写的是屏幕上的绝对坐标），其余字段照旧夹取。 */
    // ---------- sanitize 的取舍 ----------

    // 防回归（§13.18）：offsetX / offsetY 是编辑模式写下的屏幕绝对坐标，夹一次就会「打开设置就跳回原位」。
    @Test
    void sanitizeKeepsTheAbsoluteOffsetsTheEditorWrote() {
        DuraHudConfig cfg = new DuraHudConfig();
        cfg.anchor = DuraHudConfig.Anchor.CUSTOM;
        cfg.offsetX = 1600;
        cfg.offsetY = 900;
        cfg.scale = 99.0F;
        cfg.warnThreshold = 5000;
        cfg.critThreshold = -7;
        cfg.sanitize();
        // 编辑模式把 HUD 拖到哪，偏移就是那块屏幕上的绝对坐标（1920 宽的窗口里能到 1600+）。
        // 这里夹一次，就等于「打开设置界面 HUD 跳回初始位置」——越界值交给 HudLayout 在绘制时
        // 拉回屏幕内，读盘 / 拖动 / 磁盘三方因此永远一致。
        assertEquals(DuraHudConfig.Anchor.CUSTOM, cfg.anchor);
        assertEquals(1600, cfg.offsetX);
        assertEquals(900, cfg.offsetY);
        // 其余夹取照旧。
        assertEquals(4.0F, cfg.scale, 1.0E-4F);
        assertEquals(100, cfg.warnThreshold);
        assertEquals(0, cfg.critThreshold);
    }

    /** configVersion 缺失 / 写成字符串：一律当成版本 0，不抛异常，迁移后补回当前版本号。 */
    // ---------- 版本号本身的容错与幂等 ----------

    // 防回归：configVersion 缺失 / 写成字符串一律当 0，不许抛异常，迁移后要补回当前版本号。
    @Test
    void brokenVersionFieldIsTreatedAsVersionZero() {
        var missing = ConfigFile.parse("{\"offsetX\": 5}");
        assertTrue(ConfigFile.migrate(missing));
        assertEquals(ConfigFile.CONFIG_VERSION, missing.get("configVersion").getAsInt());

        var text = ConfigFile.parse("{\"configVersion\": \"six\"}");
        assertTrue(ConfigFile.migrate(text));
        assertEquals(ConfigFile.CONFIG_VERSION, text.get("configVersion").getAsInt());
    }

    /** 标着 v1..v5 的旧文件升级时也要清废弃键（旧实现只在 from < 1 时清，垃圾键会一路留着）。 */
    // 防回归：旧实现只在 from < 1 时清废弃键，标着 v1..v5 的文件会把垃圾键一路带进新版本。
    @Test
    void olderVersionedFilesAlsoDropRemovedKeys() {
        JsonObject root = parse("{\"configVersion\":5,\"barStyle\":\"FLAT\",\"cellOpacity\":0.5,\"offsetY\":7}");
        assertTrue(ConfigFile.migrate(root), "旧文件升级必须报 true，load 才会写回");
        assertFalse(root.has("barStyle"));
        assertFalse(root.has("cellOpacity"));
        assertEquals(7, root.get("offsetY").getAsInt());
    }

    /** 已经是当前版本：一个字都不改，返回值必须是 false（load 靠它决定回不回写磁盘）。 */
    // 防回归：已经是当前版本时 migrate 必须报 false —— 返回值就是「要不要回写磁盘」。
    @Test
    void currentVersionFileIsLeftCompletelyAlone() {
        JsonObject root = parse("{\"configVersion\":" + ConfigFile.CONFIG_VERSION
                + ",\"offsetY\":7,\"barStyle\":\"FLAT\"}");
        assertFalse(ConfigFile.migrate(root));
        assertEquals(7, root.get("offsetY").getAsInt());
        assertEquals("FLAT", root.get("barStyle").getAsString(), "没升级动作就不该碰文件内容");
    }
}

