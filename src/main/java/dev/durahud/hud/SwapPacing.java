package dev.durahud.hud;

/**
 * 连续切换装备/工具时，这一次换物过渡该走多久。
 *
 * <p>换物过渡是「整排挪一格」：新内容从上一个格子的位置推进来，<b>到位的那一刻正好是这段过渡的
 * 终点</b>。所以一件东西在格心完全静止、完全不透明的停留时间 = 两次切换的间隔 − 本次过渡时长。
 * 间隔正好等于配置的动画时长时，停留是 0 —— 前一件到位的那一帧就被下一次切换顶走，看起来就是
 * 「刚到位就被替换」。</p>
 *
 * <p>这里把本次时长压到「间隔 − 停留目标」：换得越快，滑动越短，但每一件都一定在格心停够
 * {@link #DWELL_MILLIS} 毫秒（人眼认下一件东西大约需要 80~100 毫秒）。间隔够宽时返回配置时长
 * 本身，所以<b>配置项在这里是上限，不是固定值</b>；下限 {@link #FLOOR_MILLIS} 保证再快也看得见
 * 这一段滑动。单次换物不受影响，仍然走完整的配置时长。</p>
 *
 * <p>纯数学，零 Minecraft 引用（可离线单测，见 SwapPacingTest）。</p>
 */
public final class SwapPacing {

    /** 连续切换时，上一件东西在格心静止不动、完全不透明的目标停留时间（毫秒）。 */
    public static final int DWELL_MILLIS = 80;

    /** 自适应缩短的下限：再快也要让人看得见这一段滑动（毫秒；60 帧下约 4 帧）。 */
    public static final int FLOOR_MILLIS = 60;

    private SwapPacing() {
    }

    /**
     * 本次过渡时长。
     *
     * @param configuredMillis     配置里的「动画时长」，在这里是上限
     * @param sinceLastSwapSeconds 距上一次换物的秒数；很久没换过（或刚开局）就给一个很大的值
     */
    public static int duration(int configuredMillis, float sinceLastSwapSeconds) {
        return duration(configuredMillis, sinceLastSwapSeconds, DWELL_MILLIS, FLOOR_MILLIS);
    }

    /** 显式给停留目标与下限的版本（单测用；下限会被夹到不超过上限）。 */
    public static int duration(int configuredMillis, float sinceLastSwapSeconds, int dwellMillis, int floorMillis) {
        int configured = Math.max(1, configuredMillis);
        int floor = Math.max(1, Math.min(floorMillis, configured));
        if (Float.isNaN(sinceLastSwapSeconds) || sinceLastSwapSeconds < 0.0F) {
            return configured;   // 间隔未知（NaN / 负数）时不缩短，宁可慢慢滑
        }
        // 间隔按毫秒四舍五入：浮点误差不该让停留少个一毫秒（0.2F 其实是 0.20000000298）。
        long gapMillis = Math.round((double) sinceLastSwapSeconds * 1000.0);   // 正无穷 → Long.MAX_VALUE
        long budget = gapMillis - Math.max(0, dwellMillis);
        if (budget >= configured) {
            return configured;   // 间隔够宽：照配置走（单次换物就是这条路）
        }
        return (int) Math.max(floor, budget);   // budget < 上限，所以结果一定不超过 configured
    }
}
