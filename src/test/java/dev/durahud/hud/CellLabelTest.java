package dev.durahud.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.durahud.config.DuraHudConfig.ValueDisplay;
import org.junit.jupiter.api.Test;

/**
 * 数值标签的拼装规则：三种显示模式 + VALUE 模式的第二行 + 有没有条 / 有没有耐久上限的组合。
 *
 * <p>守住的不变量：第一行与第二行的分工只由 {@link CellLabel} 决定 —— VALUE 的「剩余 / 上限」
 * 必须拆成两次调用，绝不能在渲染层拼成一个字符串。失败意味着 §13.27 被绕过：预留宽度会按九个
 * 字符算，横向排列的格子间距当场被撑宽，违反 §10.3「布局稳定优先于紧凑」。</p>
 */
class CellLabelTest {

    // 防回归：VALUE 模式的上下限是两行两个字符串（§13.27），合并回一行就撑宽格子间距。
    @Test
    void valueModeSplitsRemainingAndMaxIntoTWOLines() {
        assertEquals("240", CellLabel.text(ValueDisplay.VALUE, true, true, 240, 250, 96));
        assertEquals("250", CellLabel.secondLine(ValueDisplay.VALUE, true, 250));
    }

    // 防回归：没有耐久数字的物品（例如只有比例的自定义条）退回百分比，不许显示空白或 "null"。
    @Test
    void valueModeFallsBackToPercentWhenThereIsNoDamageValue() {
        assertEquals("96%", CellLabel.text(ValueDisplay.VALUE, true, false, 0, 0, 96));
        assertEquals("", CellLabel.secondLine(ValueDisplay.VALUE, false, 0));
    }

    // 防回归：关掉条 / 关掉数字时文字必须是空串，不许留下孤零零的残留文字。
    @Test
    void nothingIsShownWithoutABarOrWithoutNumbers() {
        assertEquals("", CellLabel.text(ValueDisplay.VALUE, false, false, 0, 0, 100));
        assertEquals("", CellLabel.secondLine(ValueDisplay.VALUE, false, 0));
        assertEquals("", CellLabel.text(ValueDisplay.PERCENT, false, true, 5, 10, 50));
    }

    // 防回归：PERCENT 只看条、只用真实百分比，第二行永远为空（第二行只属于 VALUE + 有数字）。
    @Test
    void percentModeOnlyNeedsABarAndNeverUsesASecondLine() {
        assertEquals("37%", CellLabel.text(ValueDisplay.PERCENT, true, true, 37, 100, 37));
        assertEquals("100%", CellLabel.text(ValueDisplay.PERCENT, true, false, 0, 0, 100));
        assertEquals("", CellLabel.secondLine(ValueDisplay.PERCENT, true, 100));
    }

    // 防回归：OFF 是「关掉文字」而不是「关掉槽位」—— 两行都得为空，槽位本身照旧预留（§13.29）。
    @Test
    void offIsAlwaysEmpty() {
        assertEquals("", CellLabel.text(ValueDisplay.OFF, true, true, 1, 2, 50));
        assertEquals("", CellLabel.text(ValueDisplay.OFF, false, false, 0, 0, 0));
        assertEquals("", CellLabel.secondLine(ValueDisplay.OFF, true, 2));
    }
}
