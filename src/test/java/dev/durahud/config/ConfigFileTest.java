package dev.durahud.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置读写的离线测试（{@link ConfigFile}）：{@code path()} 依赖 FabricLoader，离线取不到路径，
 * 所以全部走包内可见的 {@link ConfigFile#loadFrom(Path)} / {@link ConfigFile#saveTo} 与临时目录。
 *
 * <p>守住两条护栏（README 13.38）：① 磁盘上的配置读不出来时，内存里的默认值绝不许盖上去，
 * 那份坏文件是用户手改过的唯一记录；② 写盘先写 {@code .tmp} 再原子替换，覆盖主配置前留一份
 * {@code .bak}，任何一条路径都不该剩下半截临时文件。</p>
 *
 * <p>失败的表现都很直接：文件被默认值悄悄改写、或者崩一次留下半截 JSON 让下次启动整份退回默认。</p>
 */
class ConfigFileTest {

    private static final String CONFIG = "durahud.json";

    @TempDir
    Path dir;

    private Path config() {
        return this.dir.resolve(CONFIG);
    }

    private Path sibling(String suffix) {
        return this.dir.resolve(CONFIG + suffix);
    }

    // 首次运行：文件不存在时给默认值，并且要求回写一次（否则用户没有可改的文件）。
    @Test
    void missingFileGivesDefaultsAndAsksForOneWriteBack() {
        DuraHudConfig cfg = ConfigFile.loadFrom(config());
        assertNotNull(cfg);
        assertFalse(Files.exists(config()), "读配置不该顺手写盘");
        assertTrue(ConfigFile.shouldWriteBack(), "首次运行要回写一次，用户才有东西可改");
        assertFalse(ConfigFile.loadFailed());
    }

    // 护栏 ①：坏文件一个字节都不许动，普通保存要被拒绝；只有「明确要求」才照写，并留一份 .bak。
    @Test
    void brokenFileSurvivesUntilTheUserAsksForARewrite() throws IOException {
        byte[] broken = "这不是 JSON {{{".getBytes(StandardCharsets.UTF_8);
        Files.write(config(), broken);

        DuraHudConfig cfg = ConfigFile.loadFrom(config());
        assertNotNull(cfg);
        assertTrue(ConfigFile.loadFailed(), "读坏了要让界面能提示用户");
        assertFalse(ConfigFile.shouldWriteBack(), "读坏时绝不能回写：默认值会把用户手改的文件永久盖掉");
        assertArrayEquals(broken, Files.readAllBytes(config()));

        assertFalse(ConfigFile.saveTo(config(), cfg, false), "读失败后普通保存必须被拒绝");
        assertArrayEquals(broken, Files.readAllBytes(config()), "拒绝之后原文件要一个字节都没动");
        assertFalse(Files.exists(sibling(".bak")), "被拒绝的写入不该留下备份");
        assertFalse(Files.exists(sibling(".tmp")), "被拒绝的写入不该留下临时文件");

        // 「恢复默认」/「导入预设」是用户明确要求重写这份文件，此时才照写
        assertTrue(ConfigFile.saveTo(config(), cfg, true));
        assertFalse(ConfigFile.loadFailed(), "重写成功后就不该再报读失败");
        assertFalse(ConfigFile.shouldWriteBack());
        assertArrayEquals(broken, Files.readAllBytes(sibling(".bak")), "备份里要留着上一份内容，人还能捞回去看");
        assertFalse(Files.exists(sibling(".tmp")));
        assertFalse(ConfigFile.loadFailed(), "再读一次刚刚写好的文件应当是干净的");
        ConfigFile.loadFrom(config());
        assertFalse(ConfigFile.loadFailed());
    }

    // 护栏 ②：写盘走 .tmp + 原子替换，覆盖前留一份 .bak，写完不留临时文件。
    @Test
    void saveIsAtomicAndKeepsOneBackup() throws IOException {
        DuraHudConfig first = new DuraHudConfig();
        first.offsetX = 12;
        first.scale = 1.5F;
        assertTrue(ConfigFile.saveTo(config(), first, true));
        String firstText = Files.readString(config(), StandardCharsets.UTF_8);
        assertFalse(Files.exists(sibling(".tmp")), "写完不该留下临时文件");
        assertFalse(Files.exists(sibling(".bak")), "第一次写没有旧文件可备份");

        DuraHudConfig second = new DuraHudConfig();
        second.offsetX = -34;
        assertTrue(ConfigFile.saveTo(config(), second, true));
        assertEquals(firstText, Files.readString(sibling(".bak"), StandardCharsets.UTF_8),
                "覆盖前要先把上一份存成 .bak");
        assertFalse(Files.exists(sibling(".tmp")));

        DuraHudConfig reloaded = ConfigFile.loadFrom(config());
        assertEquals(-34, reloaded.offsetX);
        assertFalse(ConfigFile.loadFailed());
    }

    // 导出再导入必须一字不差：预设是用户用来搬配置的，丢字段等于悄悄改他的设置。
    @Test
    void presetRoundTripKeepsEveryField() throws IOException {
        Path preset = this.dir.resolve("my-preset.txt");
        DuraHudConfig cfg = new DuraHudConfig();
        cfg.anchor = DuraHudConfig.Anchor.CUSTOM;
        cfg.orientation = DuraHudConfig.Orientation.VERTICAL;
        cfg.barPlacement = DuraHudConfig.BarSide.LEFT;
        cfg.valuePlacement = DuraHudConfig.ValuePlacement.BELOW;
        cfg.valueDisplay = DuraHudConfig.ValueDisplay.PERCENT;
        cfg.offsetX = 321;
        cfg.offsetY = -57;
        cfg.scale = 0.75F;
        cfg.barLength = 91;
        cfg.hideFull = true;
        cfg.showArmor = false;
        cfg.animationMillis = 350;
        cfg.warnThreshold = 44;
        cfg.critThreshold = 11;
        cfg.valueOffsetY = -3;
        cfg.sanitize();   // 先按配置自己的规矩夹一遍，再比对才只测「搬得干不干净」

        assertTrue(ConfigFile.exportTo(preset, cfg));
        assertFalse(Files.exists(this.dir.resolve("my-preset.txt.bak")), "预设不写备份：那是用户自己挑的文件");
        assertFalse(Files.exists(this.dir.resolve("my-preset.txt.tmp")));

        DuraHudConfig imported = ConfigFile.importFrom(preset);
        assertNotNull(imported);
        assertEquals(ConfigFile.toJson(cfg).toString(), ConfigFile.toJson(imported).toString(),
                "导出再导入必须一字不差");
        assertEquals(321, imported.offsetX);
        assertEquals(DuraHudConfig.Anchor.CUSTOM, imported.anchor);
    }

    // 导入坏文件/不存在的文件只返回 null，不该连累主配置的状态（否则界面会误报「配置读取失败」）。
    @Test
    void importFromReturnsNullAndLeavesMainConfigAlone() throws IOException {
        ConfigFile.loadFrom(this.dir.resolve("absent.json"));   // 先把静态状态摆成干净的一份
        assertFalse(ConfigFile.loadFailed());

        assertNull(ConfigFile.importFrom(null));
        assertNull(ConfigFile.importFrom(this.dir.resolve("nope.txt")));
        Path bad = this.dir.resolve("bad.txt");
        Files.writeString(bad, "not json at all", StandardCharsets.UTF_8);
        assertNull(ConfigFile.importFrom(bad));
        assertFalse(ConfigFile.loadFailed(), "导入预设失败不该让主配置背上「读失败」");
    }
}
