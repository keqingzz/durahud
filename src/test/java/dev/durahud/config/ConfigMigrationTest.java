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
 */
class ConfigMigrationTest {

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void v0FileDropsRemovedKeysAndKeepsRenamedValue() {
        JsonObject root = parse("{\"barStyle\":\"FLAT\",\"cellOpacity\":0.5,\"onlyDurableHand\":false,\"barLength\":90}");
        ConfigFile.migrate(root);
        assertEquals(ConfigFile.CONFIG_VERSION, root.get("configVersion").getAsInt());
        assertFalse(root.has("barStyle"));
        assertFalse(root.has("cellOpacity"));
        assertFalse(root.has("onlyDurableHand"));
        assertFalse(root.get("onlyDurableItems").getAsBoolean());
        assertEquals(90, root.get("barLength").getAsInt());
    }

    @Test
    void v1FileRenamesOnlyDurableHand() {
        JsonObject root = parse("{\"configVersion\":1,\"onlyDurableHand\":false}");
        ConfigFile.migrate(root);
        assertEquals(ConfigFile.CONFIG_VERSION, root.get("configVersion").getAsInt());
        assertFalse(root.has("onlyDurableHand"));
        assertFalse(root.get("onlyDurableItems").getAsBoolean());
    }

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

    @Test
    void currentFileIsLeftAlone() {
        JsonObject root = parse("{\"configVersion\":" + ConfigFile.CONFIG_VERSION
                + ",\"onlyDurableItems\":false,\"barLength\":77}");
        ConfigFile.migrate(root);
        assertEquals(ConfigFile.CONFIG_VERSION, root.get("configVersion").getAsInt());
        assertFalse(root.get("onlyDurableItems").getAsBoolean());
        assertEquals(77, root.get("barLength").getAsInt());
    }

    @Test
    void renameDoesNotOverwriteNewerValue() {
        JsonObject root = parse("{\"configVersion\":1,\"onlyDurableHand\":true,\"onlyDurableItems\":false}");
        ConfigFile.migrate(root);
        assertFalse(root.has("onlyDurableHand"));
        assertFalse(root.get("onlyDurableItems").getAsBoolean());
    }

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

    @Test
    void v5FileWithBothValueTogglesPrefersValue() {
        JsonObject root = parse("{\"configVersion\":5,\"showPercent\":true,\"showValue\":true}");
        ConfigFile.migrate(root);
        assertEquals("VALUE", root.get("valueDisplay").getAsString());
    }

    @Test
    void v5FileWithoutValueTogglesInventsNothing() {
        JsonObject root = parse("{\"configVersion\":5,\"showPercent\":false,\"showValue\":false,\"cellFadeSpeed\":\"NORMAL\"}");
        ConfigFile.migrate(root);
        assertEquals(200, root.get("animationMillis").getAsInt());
        assertFalse(root.has("valueDisplay"));
        assertFalse(root.has("cellFadeSpeed"));
    }

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
    @Test
    void removedKeysAreIgnored() {
        DuraHudConfig cfg = ConfigFile.read(ConfigFile.parse("""
                {"configVersion": 6, "barStyle": "FLAT", "cellFadeSpeed": "SLOW",
                 "compactCells": false, "showValue": true, "offsetY": 12}
                """));
        assertEquals(12, cfg.offsetY);
    }

    /** sanitize 刻意不夹 offsetX/offsetY（编辑模式写的是屏幕上的绝对坐标），其余字段照旧夹取。 */
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
    @Test
    void olderVersionedFilesAlsoDropRemovedKeys() {
        JsonObject root = parse("{\"configVersion\":5,\"barStyle\":\"FLAT\",\"cellOpacity\":0.5,\"offsetY\":7}");
        assertTrue(ConfigFile.migrate(root), "旧文件升级必须报 true，load 才会写回");
        assertFalse(root.has("barStyle"));
        assertFalse(root.has("cellOpacity"));
        assertEquals(7, root.get("offsetY").getAsInt());
    }

    /** 已经是当前版本：一个字都不改，返回值必须是 false（load 靠它决定回不回写磁盘）。 */
    @Test
    void currentVersionFileIsLeftCompletelyAlone() {
        JsonObject root = parse("{\"configVersion\":" + ConfigFile.CONFIG_VERSION
                + ",\"offsetY\":7,\"barStyle\":\"FLAT\"}");
        assertFalse(ConfigFile.migrate(root));
        assertEquals(7, root.get("offsetY").getAsInt());
        assertEquals("FLAT", root.get("barStyle").getAsString(), "没升级动作就不该碰文件内容");
    }
}

