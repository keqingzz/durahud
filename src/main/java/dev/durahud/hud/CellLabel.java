package dev.durahud.hud;

import dev.durahud.config.DuraHudConfig.ValueDisplay;

/**
 * 数值标签的拼装 —— 纯函数，零 Minecraft 引用，因此可以直接单测（见 CellLabelTest）。
 *
 * <p>单独成一个类是因为 {@link CellContent} 要按「指纹」缓存结果：数字不变的那些帧既不重新
 * 拼字符串、也不重新量字形宽度，而拼装规则本身需要能被测到。</p>
 */
final class CellLabel {

    private CellLabel() {
    }

    /**
     * 第一行文字（不显示时是空串）：VALUE 模式且有耐久上限时是「剩余」，否则是百分比或空串。
     * 第二行（上限）见 {@link #secondLine}。
     *
     * @param display   显示模式：OFF / PERCENT / VALUE
     * @param bar       这一份内容有没有耐久条（没有条就没有百分比可显示）
     * @param numeric   有耐久上限，能显示「剩余」/「上限」
     * @param remaining 剩余耐久
     * @param max       耐久上限
     * @param percent   耐久比例四舍五入成的整数百分比
     */
    static String text(ValueDisplay display, boolean bar, boolean numeric, int remaining, int max, int percent) {
        return switch (display) {
            case VALUE -> numeric ? Integer.toString(remaining) : percentText(bar, percent);
            case PERCENT -> percentText(bar, percent);
            default -> "";
        };
    }

    /**
     * 第二行文字：只有 VALUE 模式、且这件东西真有耐久上限时才是那个上限，其余情况是空串。
     *
     * <p>剩余与上限<b>上下叠放</b>，而不是拼成 {@code 剩余/上限} 一行。一行要按 9 个字符预留
     * 宽度，叠成两行只要按一个 4 位数预留 —— 横向排列时格子间距能窄掉一大截，
     * 而多出来的高度由 {@link HudLayout#textHeight} 预留（见 README 6.3 / 7.6）。</p>
     */
    static String secondLine(ValueDisplay display, boolean numeric, int max) {
        return display == ValueDisplay.VALUE && numeric ? Integer.toString(max) : "";
    }

    private static String percentText(boolean bar, int percent) {
        return bar ? percent + "%" : "";
    }
}
