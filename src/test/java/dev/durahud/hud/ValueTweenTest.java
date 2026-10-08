package dev.durahud.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 耐久数值与耐久条的加减动画（与 FadeStateTest 同一思路，不碰 Minecraft 运行时）。
 *
 * <p>要钉住的性质：第一帧直接落位、中间<b>有加速度</b>（起步与收尾都比匀速慢）、走完精确
 * 吸附、中途改目标从当前位置重新起步、时间只由时长与帧间隔决定（与帧率无关）。</p>
 */
class ValueTweenTest {

    private static final int MILLIS = 200;

    @Test
    void firstUpdateLandsOnTheTargetWithoutGrowingFromZero() {
        ValueTween tween = new ValueTween();
        // 刚出现（第一次拿到目标）时直接落位：不该看到条从 0 长出来。
        assertEquals(0.8F, tween.update(0.8F, 0.016F, MILLIS), 1.0E-4F);
    }

    @Test
    void startsSlowHasAnAcceleratingMiddleAndSettlesExactly() {
        ValueTween tween = new ValueTween();
        tween.snap(1.0F);
        // 200ms 的动画走 50ms：smoothstep(0.25) = 0.15625，只走了 15.6% —— 起步比匀速慢。
        float quarter = tween.update(0.0F, 0.05F, MILLIS);
        assertEquals(1.0F - FadeState.smooth(0.25F), quarter, 1.0E-4F);
        assertTrue(quarter > 0.75F, "前四分之一时间应当只走了一小段（有加速度，不是匀速）");
        // 再走 50ms（累计进度 0.5，中点）：smoothstep(0.5) = 0.5，正好一半。
        float half = tween.update(0.0F, 0.05F, MILLIS);
        assertEquals(0.5F, half, 1.0E-4F);
        // 加速度的直接证据：同样长的 50ms，中段走的距离远大于起步段。
        assertTrue(1.0F - half > 1.0F - quarter, "中段应当比起步段走得快（这就是加速度）");
        // 最后 100ms 走完，且到点就是精确的 0。
        assertEquals(0.0F, tween.update(0.0F, 0.1F, MILLIS), 1.0E-4F);
    }

    @Test
    void retargetingMidFlightRestartsFromTheCurrentValue() {
        ValueTween tween = new ValueTween();
        tween.snap(1.0F);
        tween.update(0.0F, 0.1F, MILLIS);          // 走到 0.5
        float mid = tween.value();
        assertEquals(0.5F, mid, 1.0E-4F);
        // 目标改成 0.6：从 0.5 起步往回升，不会先掉到 0 再折返。
        assertEquals(mid, tween.update(0.6F, 0.0F, MILLIS), 1.0E-4F);
        float next = tween.update(0.6F, 0.01F, MILLIS);
        assertTrue(next > mid && next <= 0.6F, "重新起步应当朝新目标单调推进");
        assertEquals(0.6F, tween.update(0.6F, 1.0F, MILLIS), 1.0E-4F);
    }

    @Test
    void zeroOrNegativeFrameTimeFreezesTheValue() {
        ValueTween tween = new ValueTween();
        tween.snap(1.0F);
        assertEquals(1.0F, tween.update(0.0F, 0.0F, MILLIS), 1.0E-4F);
        assertEquals(1.0F, tween.update(0.0F, -0.5F, MILLIS), 1.0E-4F);
    }

    @Test
    void zeroDurationJumpsStraightToTheTarget() {
        ValueTween tween = new ValueTween();
        tween.snap(1.0F);
        assertEquals(0.25F, tween.update(0.25F, 0.016F, 0), 1.0E-4F);
    }

    @Test
    void sameDurationCoversTheSameDistanceRegardlessOfFrameRate() {
        ValueTween fast = new ValueTween();
        ValueTween slow = new ValueTween();
        fast.snap(1.0F);
        slow.snap(1.0F);
        for (int i = 0; i < 20; i++) {
            fast.update(0.0F, 0.01F, MILLIS);      // 100fps
        }
        for (int i = 0; i < 5; i++) {
            slow.update(0.0F, 0.04F, MILLIS);      // 25fps
        }
        assertEquals(slow.value(), fast.value(), 1.0E-3F);
        assertEquals(0.0F, fast.value(), 1.0E-3F);
    }
}
