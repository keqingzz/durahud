package dev.durahud.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 连续切换时的过渡时长（与 FadeStateTest 同一思路，不碰 Minecraft 运行时）。
 *
 * <p>要钉住的性质：间隔够宽时就是配置时长（配置项是上限）；间隔正好等于时长时压缩到「上一件
 * 还能停够 80 毫秒」；再快也有下限托住；间隔未知（NaN / 负数）不缩短；间隔的浮点误差不会让
 * 停留少一毫秒。</p>
 */
class SwapPacingTest {

    private static final int MILLIS = 200;

    @Test
    void wideGapKeepsTheConfiguredDuration() {
        // 很久没换过（或刚开局给了正无穷）：单次换物，完整播放配置时长。
        assertEquals(MILLIS, SwapPacing.duration(MILLIS, 5.0F));
        assertEquals(MILLIS, SwapPacing.duration(MILLIS, Float.POSITIVE_INFINITY));
    }

    @Test
    void gapEqualToTheDurationStillLeavesTheDwell() {
        // 间隔正好 200ms：本次只滑 120ms，上一件在格心停 80ms。
        assertEquals(120, SwapPacing.duration(MILLIS, 0.2F));
    }

    @Test
    void theConfiguredDurationIsAnUpperBound() {
        // 间隔 250ms：还想停 80ms 就只能滑 170ms；间隔 300ms 就顶到上限 200ms。
        assertEquals(170, SwapPacing.duration(MILLIS, 0.25F));
        assertEquals(MILLIS, SwapPacing.duration(MILLIS, 0.3F));
    }

    @Test
    void veryShortGapsAreHeldUpByTheFloor() {
        // 间隔 100ms：预算只剩 20ms，被下限托到 60ms（这时停留 40ms，而不是负的）。
        assertEquals(SwapPacing.FLOOR_MILLIS, SwapPacing.duration(MILLIS, 0.1F));
        assertEquals(SwapPacing.FLOOR_MILLIS, SwapPacing.duration(MILLIS, 0.0F));
    }

    @Test
    void theFloorNeverExceedsTheConfiguredDuration() {
        // 配置本身就比下限短时，一切照配置来（区间收缩成一个点）。
        assertEquals(50, SwapPacing.duration(50, 0.01F));
        assertEquals(40, SwapPacing.duration(40, 0.2F));
    }

    @Test
    void unknownGapsNeverShorten() {
        assertEquals(MILLIS, SwapPacing.duration(MILLIS, Float.NaN));
        assertEquals(MILLIS, SwapPacing.duration(MILLIS, -1.0F));
    }

    @Test
    void roundingNeverEatsIntoTheDwell() {
        // 间隔 180.1ms、停留 80ms → 余量 100.1ms：按毫秒四舍五入成 180 → 滑 100ms，停留正好 80。
        assertEquals(100, SwapPacing.duration(MILLIS, 0.1801F));
        // 自定义停留与下限：间隔 300ms、停留 100ms → 滑 200ms；间隔 50ms 被下限 80ms 托住。
        assertEquals(200, SwapPacing.duration(500, 0.3F, 100, 80));
        assertEquals(80, SwapPacing.duration(500, 0.05F, 100, 80));
    }
}
