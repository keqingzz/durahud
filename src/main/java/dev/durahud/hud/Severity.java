package dev.durahud.hud;

import dev.durahud.config.DuraHudConfig;

/**
 * 耐久档位。阈值判定只在这里做一次，耐久条的颜色与文字颜色都由它给出
 * （旧实现里 bossColor / severityColor 各判了一遍同样的阈值）。
 *
 * <p>{@link #of} 每帧每格只应调用一次，结果沿参数传给图标层与仪表层（见 README 13.24）：
 * 同一份内容判两遍阈值，就会出现「条已经变红、图标还没高亮」这种半帧不一致。</p>
 */
public enum Severity {

    /** 耐久比例 ≥ {@code warnThreshold}（正常）。 */
    GOOD(Metrics.BAR_GREEN, 0xFF9EE689),
    /** 耐久比例 < {@code warnThreshold} 且 ≥ {@code critThreshold}（注意 20% 仍算 WARN）。 */
    WARN(Metrics.BAR_YELLOW, 0xFFEBD983),
    /** 耐久比例 < {@code critThreshold}：图标上的濒危高亮框也只在 CRIT 时出现。 */
    CRIT(Metrics.BAR_RED, 0xFFEF9B91);

    /** bars.png 里的色号（0..6）。 */
    public final int barColorIndex;
    /** 文字/纯色条的 ARGB。 */
    public final int textColor;

    Severity(int barColorIndex, int textColor) {
        this.barColorIndex = barColorIndex;
        this.textColor = textColor;
    }

    /** 百分比严格小于阈值才算下一档，因此 critThreshold=20 时 20% 仍算 WARN。 */
    public static Severity of(DuraHudConfig cfg, float ratio) {
        float percent = ratio * 100.0F;
        if (percent < (float) cfg.critThreshold) {
            return CRIT;
        }
        if (percent < (float) cfg.warnThreshold) {
            return WARN;
        }
        return GOOD;
    }
}
