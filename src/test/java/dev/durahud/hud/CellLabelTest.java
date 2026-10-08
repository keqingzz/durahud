package dev.durahud.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.durahud.config.DuraHudConfig.ValueDisplay;
import org.junit.jupiter.api.Test;

/** 数值标签的拼装规则：三种显示模式 + VALUE 模式的第二行 + 有没有条 / 有没有耐久上限的组合。 */
class CellLabelTest {

    @Test
    void valueModeSplitsRemainingAndMaxIntoTWOLines() {
        assertEquals("240", CellLabel.text(ValueDisplay.VALUE, true, true, 240, 250, 96));
        assertEquals("250", CellLabel.secondLine(ValueDisplay.VALUE, true, 250));
    }

    @Test
    void valueModeFallsBackToPercentWhenThereIsNoDamageValue() {
        assertEquals("96%", CellLabel.text(ValueDisplay.VALUE, true, false, 0, 0, 96));
        assertEquals("", CellLabel.secondLine(ValueDisplay.VALUE, false, 0));
    }

    @Test
    void nothingIsShownWithoutABarOrWithoutNumbers() {
        assertEquals("", CellLabel.text(ValueDisplay.VALUE, false, false, 0, 0, 100));
        assertEquals("", CellLabel.secondLine(ValueDisplay.VALUE, false, 0));
        assertEquals("", CellLabel.text(ValueDisplay.PERCENT, false, true, 5, 10, 50));
    }

    @Test
    void percentModeOnlyNeedsABarAndNeverUsesASecondLine() {
        assertEquals("37%", CellLabel.text(ValueDisplay.PERCENT, true, true, 37, 100, 37));
        assertEquals("100%", CellLabel.text(ValueDisplay.PERCENT, true, false, 0, 0, 100));
        assertEquals("", CellLabel.secondLine(ValueDisplay.PERCENT, true, 100));
    }

    @Test
    void offIsAlwaysEmpty() {
        assertEquals("", CellLabel.text(ValueDisplay.OFF, true, true, 1, 2, 50));
        assertEquals("", CellLabel.text(ValueDisplay.OFF, false, false, 0, 0, 0));
        assertEquals("", CellLabel.secondLine(ValueDisplay.OFF, true, 2));
    }
}
