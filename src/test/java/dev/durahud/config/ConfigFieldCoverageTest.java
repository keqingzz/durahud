package dev.durahud.config;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置字段的「全覆盖」守卫：不写字段清单，而是反射数一遍 {@link DuraHudConfig} 的持久化字段，
 * 逐字段确认三件事都照顾到了它 ——
 * ① {@link ConfigFile#toJson} 写得出去、{@link ConfigFile#read} 读得回来（加了字段却忘了补 read，
 * 值会悄悄退回默认，这里就会红）；② {@link DuraHudConfig#copyFrom} 拷得到它；
 * ③ {@link DuraHudConfig#resetToDefaults} 归得了位。
 *
 * <p>{@code ConfigMigrationTest} 里那条手写 26 字段清单的往返用例只能证明「写下来的都还在」，
 * 加一个字段它不会响；这里反过来：字段一多，自动就多测一条。</p>
 */
class ConfigFieldCoverageTest {

    /** 要写进配置文件的字段：非静态、非 transient（editMode 是会话内的临时状态，见最后一条用例）。 */
    private static List<Field> persistentFields() {
        List<Field> fields = new ArrayList<>();
        for (Field field : DuraHudConfig.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }
            field.setAccessible(true);
            fields.add(field);
        }
        return fields;
    }

    /**
     * 给每个字段换一个与默认值不同的值：布尔取反、整数/小数按字段序号取偏移量（避开 0 与 1）、
     * 枚举取下一个常量。出现没见过的类型时直接抛异常 —— 这是「新字段要在这里补规则」的提醒。
     */
    private static void mutateEveryField(DuraHudConfig cfg) throws IllegalAccessException {
        List<Field> fields = persistentFields();
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            Object current = field.get(cfg);
            Object next;
            if (field.getType() == boolean.class) {
                next = !((Boolean) current);
            } else if (field.getType() == int.class) {
                next = 11 + i * 7;
            } else if (field.getType() == float.class) {
                next = 0.75F + i;   // 二进制可精确表示，往返不会被精度咬到
            } else if (field.getType().isEnum()) {
                Object[] values = field.getType().getEnumConstants();
                next = values[(((Enum<?>) current).ordinal() + 1) % values.length];
            } else {
                throw new IllegalStateException("字段 " + field.getName() + " 的类型 " + field.getType()
                        + " 还没有测试值规则，请在上面的分支里补一条");
            }
            field.set(cfg, next);
        }
    }

    private static void assertSameValues(DuraHudConfig expected, DuraHudConfig actual, String what)
            throws IllegalAccessException {
        for (Field field : persistentFields()) {
            assertEquals(field.get(expected), field.get(actual),
                    what + " 之后字段 " + field.getName() + " 的值不一致");
        }
    }

    @Test
    void everyFieldSurvivesWriteAndRead() throws Exception {
        DuraHudConfig written = new DuraHudConfig();
        mutateEveryField(written);
        JsonObject json = ConfigFile.toJson(written);
        for (Field field : persistentFields()) {
            assertTrue(json.has(field.getName()),
                    "ConfigFile.toJson 没写出字段 " + field.getName() + "：是不是被封成 transient 了？");
        }
        assertSameValues(written, ConfigFile.read(json), "写盘再读回");
    }

    @Test
    void everyFieldIsCopiedByCopyFrom() throws Exception {
        DuraHudConfig source = new DuraHudConfig();
        mutateEveryField(source);
        DuraHudConfig target = new DuraHudConfig();
        target.copyFrom(source);
        assertSameValues(source, target, "copyFrom");
    }

    @Test
    void everyFieldIsRestoredByResetToDefaults() throws Exception {
        DuraHudConfig defaults = new DuraHudConfig();
        DuraHudConfig cfg = new DuraHudConfig();
        mutateEveryField(cfg);
        cfg.resetToDefaults();
        assertSameValues(defaults, cfg, "resetToDefaults");
    }

    @Test
    void editModeIsTemporaryStateAndNeverWritten() throws Exception {
        assertEquals(false, new DuraHudConfig().editMode, "editMode 的默认值应当是 false");

        DuraHudConfig cfg = new DuraHudConfig();
        cfg.editMode = true;
        assertFalse(ConfigFile.toJson(cfg).has("editMode"),
                "editMode 是会话里的临时状态，写进文件等于下次启动直接进编辑模式");
        DuraHudConfig target = new DuraHudConfig();
        target.copyFrom(cfg);
        assertFalse(target.editMode, "copyFrom 不该把临时状态一起拷过去");
    }
}
