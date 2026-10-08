package dev.durahud.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设置界面布局的离线验证。两条核心诉求：<b>GUI 缩放拉到多大，控件都得留在屏幕里</b>
 * （旧版固定 17 行、行高被压到 12 像素，底部按钮就被挤出了屏幕，只有界面尺寸 4 及以下正常）；
 * <b>左侧竖排标签既不能压到选项块，也不能越出窗口</b>。
 */
class ScreenLayoutTest {

    private static final int TABS = 6;
    /** 最长的一页（布局页）有 6 行。 */
    private static final int ROWS = 6;

    /** 常见分辨率与 GUI 缩放的可用像素，含最苛刻的 1080p + 缩放 6（320x180）。 */
    private static final int[][] SIZES = {
        {320, 180}, {384, 216}, {427, 240}, {480, 270}, {640, 360},
        {854, 480}, {1280, 720}, {1920, 1080}, {2560, 1440}, {3840, 2160},
    };

    @Test
    void everyRealisticSizeKeepsControlsInsideTheScreen() {
        for (int[] size : SIZES) {
            int w = size[0];
            int h = size[1];
            ScreenLayout l = ScreenLayout.of(w, h, ROWS, TABS);
            String at = w + "x" + h;
            assertTrue(l.rowH >= 12, "行高太小: " + at + " -> " + l.rowH);
            assertTrue(l.rowsTop + l.rowsPerColumn * l.rowH <= l.contentBottom,
                    "选项行溢出到按钮区: " + at);
            assertTrue(l.rowsTop >= l.contentTop, "选项块顶到标题: " + at);
            assertTrue(l.buttonY + ScreenLayout.BUTTON_H <= h, "底部按钮出屏幕: " + at);
            assertTrue(l.buttonY > l.contentTop, "按钮区与选项区重叠: " + at);
            assertTrue(l.blockLeft >= 0 && l.blockLeft + l.blockWidth <= w, "选项块出屏幕: " + at);
            assertTrue(l.tabLeft >= 0 && l.tabX() + l.tabW <= w, "标签列出屏幕: " + at);
            assertTrue(l.tabX() + l.tabW + ScreenLayout.GAP <= l.blockLeft, "标签压到选项块: " + at);
            assertTrue(l.tabTop >= l.contentTop, "标签列顶到标题: " + at);
            assertTrue(l.tabY(TABS - 1) + l.tabH <= l.contentBottom, "标签列压到提示行: " + at);
            assertTrue(l.tabY(TABS - 1) + l.tabH <= l.buttonY, "标签列压到底部按钮: " + at);
            assertTrue(l.buttonX(ScreenLayout.BUTTON_COUNT - 1) + l.buttonW <= w, "按钮行出屏幕: " + at);
            assertTrue(l.statusY >= l.contentTop, "提示行压到选项区: " + at);
        }
    }

    @Test
    void extremeSmallWindowKeepsButtonsAndTabsReachable() {
        for (int[] size : new int[][] {{213, 120}, {240, 140}, {160, 120}, {320, 150}}) {
            int w = size[0];
            int h = size[1];
            ScreenLayout l = ScreenLayout.of(w, h, ROWS, TABS);
            String at = w + "x" + h;
            assertTrue(l.rowH >= ScreenLayout.ROW_H_MIN, "行高低于最小值: " + at);
            assertTrue(l.buttonY + ScreenLayout.BUTTON_H <= h, "底部按钮出屏幕: " + at);
            assertTrue(l.tabH >= ScreenLayout.TAB_H_MIN, "标签太扁: " + at);
            assertTrue(l.tabY(TABS - 1) + l.tabH <= l.buttonY, "标签列压到底部按钮: " + at);
            assertTrue(l.tabX() + l.tabW + ScreenLayout.GAP <= l.blockLeft, "标签压到选项块: " + at);
            assertTrue(l.tabX() + l.tabW <= w, "标签列出屏幕: " + at);
            assertTrue(l.columns >= 1, at);
        }
    }

    @Test
    void wideWindowUsesTwoColumnsAndNarrowOneUsesSingleColumn() {
        assertEquals(2, ScreenLayout.of(854, 480, ROWS, TABS).columns);
        assertEquals(2, ScreenLayout.of(640, 360, ROWS, TABS).columns);
        assertEquals(1, ScreenLayout.of(300, 240, ROWS, TABS).columns);
        // 320x180（1080p + 缩放 6）被标签列占去 60 多像素后只够一列
        assertEquals(1, ScreenLayout.of(320, 180, ROWS, TABS).columns);
    }

    @Test
    void tabsShrinkWithTheWindowAndStayCentered() {
        ScreenLayout tall = ScreenLayout.of(854, 480, ROWS, TABS);
        assertEquals(ScreenLayout.TAB_H_MAX, tall.tabH);
        ScreenLayout small = ScreenLayout.of(213, 120, ROWS, TABS);
        assertTrue(small.tabH >= ScreenLayout.TAB_H_MIN, "标签太扁: " + small.tabH);
        assertTrue(small.tabH <= tall.tabH, "矮窗口的标签反而更高");
        // 标签列在内容带里垂直居中：上下留白相差不超过 1 像素
        int above = tall.tabTop - tall.contentTop;
        int below = tall.contentBottom - (tall.tabY(TABS - 1) + tall.tabH);
        assertTrue(Math.abs(above - below) <= 1, "标签列没有垂直居中: " + above + " / " + below);
    }

    /** 二级菜单（选项行）与左侧六大标签齐平：两者在同一段高度里居中，中心相差不超过 1 像素。 */
    @Test
    void rowsSitLevelWithTheTabs() {
        for (int[] size : SIZES) {
            ScreenLayout l = ScreenLayout.of(size[0], size[1], ROWS, TABS);
            String at = size[0] + "x" + size[1];
            int rowsCentre = l.rowsTop + l.rowsPerColumn * l.rowH / 2;
            int tabsCentre = l.tabTop + (TABS * l.tabH + (TABS - 1) * ScreenLayout.TAB_GAP) / 2;
            assertTrue(Math.abs(rowsCentre - tabsCentre) <= 1,
                    "选项块与标签列没齐平: " + at + " -> " + rowsCentre + " / " + tabsCentre);
            assertTrue(l.rowY(0) == l.rowsTop, "第一行不在 rowsTop: " + at);
        }
    }

    @Test
    void rowsAreDistributedOverColumnsWithoutGaps() {
        for (int rows = 0; rows <= ROWS; rows++) {
            ScreenLayout l = ScreenLayout.of(854, 480, rows, TABS);
            int sum = 0;
            for (int c = 0; c < l.columns; c++) {
                sum += l.rowsInColumn(c);
            }
            assertEquals(rows, sum, "列分配丢行: rows=" + rows);
            for (int i = 0; i < rows; i++) {
                assertTrue(l.rowOf(i) < l.rowsInColumn(l.columnOf(i)), "行号越出所在列: i=" + i);
                assertTrue(l.rowY(i) >= l.contentTop);
            }
        }
    }

    /**
     * 窗口小到排不下完整布局时（100x60、40x30 这类），只要求「所有控件都还在窗口里」
     * 且几何不自相矛盾 —— 这时标签与选项行会被压到 1 像素高，谈不上好看，但绝不能溢出。
     */
    @Test
    void tinyWindowsKeepEveryControlInsideTheWindow() {
        for (int[] size : new int[][] {{150, 110}, {140, 100}, {120, 80}, {100, 60}, {40, 30}}) {
            int w = size[0];
            int h = size[1];
            ScreenLayout l = ScreenLayout.of(w, h, ROWS, TABS);
            String at = w + "x" + h;
            assertTrue(l.rowH >= 1 && l.tabH >= 1, "行高 / 标签高被压到 0: " + at);
            assertTrue(l.contentTop >= 0 && l.contentTop <= l.contentBottom, "内容带为负: " + at);
            assertTrue(l.tabLeft >= 0 && l.tabX() + l.tabW <= w, "标签列出屏幕: " + at);
            assertTrue(l.tabX() + l.tabW + ScreenLayout.GAP <= l.blockLeft, "标签压到选项块: " + at);
            assertTrue(l.blockLeft >= 0 && l.blockLeft + l.blockWidth <= w, "选项块出屏幕: " + at);
            assertTrue(l.buttonX(ScreenLayout.BUTTON_COUNT - 1) + l.buttonW <= w, "按钮行出屏幕: " + at);
            assertTrue(l.buttonY >= 0 && l.buttonY + ScreenLayout.BUTTON_H <= h, "底部按钮出屏幕: " + at);
            assertTrue(l.statusY >= 0 && l.statusY <= l.buttonY, "提示行出屏幕: " + at);
            assertTrue(l.rowsTop >= 0 && l.rowsTop + l.rowsPerColumn * l.rowH <= h, "选项行出屏幕: " + at);
            assertTrue(l.tabTop >= 0 && l.tabY(TABS - 1) + l.tabH <= h, "标签列出屏幕: " + at);
        }
    }
}