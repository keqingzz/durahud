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
     * @param display   显示模式：OFF / PERCENT / VALUE
     * @param bar       这一份内容有没有耐久条（没有条就没有百分比可显示）
     * @param numeric   有耐久上限，能显示「剩余/上限」
     * @param remaining 剩余耐久
     * @param max       耐久上限
     * @param percent   耐久比例四舍五入成的整数百分比
     */
    static String text(ValueDisplay display, boolean bar, boolean numeric, int remaining, int max, int percent) {
        return switch (display) {
            case VALUE -> numeric ? remaining + "/" + max : percentText(bar, percent);
            case PERCENT -> percentText(bar, percent);
            default -> "";
        };
    }

    private static String percentText(boolean bar, int percent) {
        return bar ? percent + "%" : "";
    }
}
