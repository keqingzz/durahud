package dev.durahud.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 淡入淡出与平滑移动的数学：这里只碰纯计算，不需要 Minecraft 运行时（与 HudLayoutTest 同一思路）。
 * 关注两套缓动各自的性质：淡入淡出的步长只由「时长 + 帧间隔」决定、不会过冲、关掉动画恒不透明；
 * 平滑移动先快后慢、到得了目标、临近目标时吸附到像素上。
 *
 * <p>失败意味着：要么格子淡入淡出时「边框在动、内容硬跳」（§13.20 的 alpha 纪律被绕过，
 * 2.0.4 修的就是这条），要么平滑移动停在半路或卡在亚像素上 —— 两种都能在游戏里直接看出来。</p>
 */
class FadeStateTest {

    // 防回归：进度只由「时长 + 帧间隔」推进，改成一帧固定百分比的话高帧率下会快到看不见。
    @Test
    void approachMovesTowardsTargetAtADurationBasedSpeed() {
        // 时长 200ms：每帧 100ms 正好走一半，两帧走完，与帧率无关。
        assertEquals(0.5F, FadeState.approach(0.0F, 1.0F, 0.1F, 200), 1.0E-4F);
        assertEquals(1.0F, FadeState.approach(0.5F, 1.0F, 0.1F, 200), 1.0E-4F);
        // 淡出同理：从 1 往回走一半。
        assertEquals(0.5F, FadeState.approach(1.0F, 0.0F, 0.1F, 200), 1.0E-4F);
    }

    // 防回归：逼近目标时不许过冲，到点必须精确落位（alpha 越过 1 会被钳成硬边）。
    @Test
    void approachSnapsToTargetWithoutOvershoot() {
        assertEquals(1.0F, FadeState.approach(0.9F, 1.0F, 10.0F, 200), 1.0E-4F);
        assertEquals(0.0F, FadeState.approach(0.1F, 0.0F, 10.0F, 200), 1.0E-4F);
    }

    // 防回归：进度超范围 / 时钟回拨（负帧间隔）/ 时长为 0 三种退化输入都不许反向跳或除零。
    @Test
    void approachClampsInputsAndHandlesZeroDuration() {
        // 进度超范围先夹住：1 已经是目标，原地不动。
        assertEquals(1.0F, FadeState.approach(5.0F, 1.0F, 0.016F, 200), 1.0E-4F);
        // 负数帧间隔（时钟回拨）当作 0：这一帧不动，但也不许反向跳。
        assertEquals(0.5F, FadeState.approach(0.5F, 0.0F, -0.1F, 200), 1.0E-4F);
        // 时长为 0 表示立即到位。
        assertEquals(1.0F, FadeState.approach(0.0F, 1.0F, 0.016F, 0), 1.0E-4F);
        assertEquals(0.0F, FadeState.approach(1.0F, 0.0F, 0.016F, 0), 1.0E-4F);
    }

    // 防回归：缓动曲线必须单调不减（回头的曲线会让滑入的格子中途倒着走），两端钉死在 0 / 1。
    @Test
    void smoothIsMonotonicAndPinnedAtBothEnds() {
        assertEquals(0.0F, FadeState.smooth(0.0F), 1.0E-6F);
        assertEquals(1.0F, FadeState.smooth(1.0F), 1.0E-6F);
        assertEquals(0.5F, FadeState.smooth(0.5F), 1.0E-6F);
        float previous = -1.0F;
        for (int step = 0; step <= 20; step++) {
            float value = FadeState.smooth(step / 20.0F);
            assertTrue(value >= previous, "缓动曲线必须单调不减，第 " + step + " 点反了");
            previous = value;
        }
    }

    // 防回归：关掉动画时 alpha 恒为 1 —— 动画开关不能顺带把内容画成半透明。
    @Test
    void alphaIsAlwaysOpaqueWhenAnimationIsOff() {
        for (int step = 0; step <= 10; step++) {
            assertEquals(1.0F, FadeState.alpha(step / 10.0F, false), 1.0E-6F);
        }
        assertEquals(0.0F, FadeState.alpha(0.0F, true), 1.0E-6F);
        assertEquals(1.0F, FadeState.alpha(1.0F, true), 1.0E-6F);
    }

    // 防回归：指数缓动得「先快后慢」且一个时长内基本走完，不许退化成匀速或永远到不了目标。
    @Test
    void slideCoversMostOfTheDistanceWithinTheGivenDuration() {
        // 时长 200ms：时间常数取三分之一，所以 200ms 后走完约 95% 的距离。
        float after = FadeState.slide(0.0F, 100.0F, 0.2F, 200);
        assertTrue(after > 90.0F && after < 100.0F, "200ms 应该走完约 95%，实际 " + after);
        // 指数缓动：距离越远每帧走得越多。
        float first = FadeState.slide(0.0F, 100.0F, 0.016F, 200);
        float second = FadeState.slide(first, 100.0F, 0.016F, 200) - first;
        assertTrue(second < first, "越接近目标每帧位移越小：" + first + " -> " + second);
    }

    // 防回归：长帧也不许越过目标；差 0.25 像素以内必须吸附 —— 停稳那一帧的像素对齐靠它。
    @Test
    void slideNeverOvershootsAndSnapsWhenClose() {
        // 一帧跨 1 秒（最大跨度）也不许越过目标。
        assertEquals(100.0F, FadeState.slide(0.0F, 100.0F, 1.0F, 200), 1.0E-3F);
        // 差 0.25 像素以内直接吸附：停稳的那一帧必须像素对齐。
        assertEquals(10.0F, FadeState.slide(10.1F, 10.0F, 0.016F, 200), 1.0E-6F);
        assertEquals(10.0F, FadeState.slide(9.8F, 10.0F, 0.016F, 200), 1.0E-6F);
    }

    // 防回归：关掉动画（时长 0）/ 时钟回拨 / 已在目标上三种退化输入都要安全落位。
    @Test
    void slideHandlesDegenerateInputs() {
        // 时长为 0（关掉动画）立即到位。
        assertEquals(42.0F, FadeState.slide(0.0F, 42.0F, 0.016F, 0), 1.0E-6F);
        // 负数帧间隔（时钟回拨）当作 0：这一帧不动。
        assertEquals(7.5F, FadeState.slide(7.5F, 100.0F, -0.1F, 200), 1.0E-6F);
        // 已经在目标上就原地不动。
        assertEquals(12.0F, FadeState.slide(12.0F, 12.0F, 0.016F, 200), 1.0E-6F);
    }
}
