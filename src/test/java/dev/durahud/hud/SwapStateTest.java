package dev.durahud.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 换物过渡的数学（与 FadeStateTest 同一思路，不碰 Minecraft 运行时）。
 *
 * <p>要钉住的三件事：静止时只有一份内容、原地、完全不透明；过渡中两份内容始终相差一个
 * 步距（旧的从 0 推到 1、新的从 -1 推到 0）；走完那一刻正好回到静止态。</p>
 *
 * <p>失败意味着 §13.21 的手感回归：过渡被打断时不保留进度、改用 {@code begin(...)} 重播 ——
 * 已经推进到一半的进场内容会瞬间落回格心（位置与不透明度一起跳回起点），而且每换一次东西都要
 * 重新计一整段时长，动画时长一大就是「怎么切都跟不上手」的黏滞。</p>
 */
class SwapStateTest {

    private static final int MILLIS = 200;

    // 防回归：静止态是「只有当前那一份、原地、完全不透明」—— 两个 alpha 反了就会常驻重影。
    @Test
    void freshStateDrawsOnlyTheCurrentContentAtRest() {
        SwapState swap = new SwapState();
        assertFalse(swap.active());
        assertEquals(0.0F, swap.progress(), 1.0E-4F);
        assertEquals(0.0F, swap.shift(), 1.0E-4F);
        assertEquals(0.0F, swap.outgoingAlpha(), 1.0E-4F);
        assertEquals(1.0F, swap.incomingAlpha(), 1.0E-4F);
    }

    // 防回归（2.0.3 起）：换物是「整体推进一格」，旧内容在格心、新内容差一格，不是先退场再进场。
    @Test
    void beginPutsTheOldContentAtRestAndTheNewOneOneStepBefore() {
        SwapState swap = new SwapState();
        swap.begin(MILLIS);
        assertTrue(swap.active());
        assertEquals(0.0F, swap.outgoingShift(), 1.0E-4F);
        assertEquals(-1.0F, swap.incomingShift(), 1.0E-4F);
        assertEquals(1.0F, swap.outgoingAlpha(), 1.0E-4F);
        assertEquals(0.0F, swap.incomingAlpha(), 1.0E-4F);
    }

    // 防回归：半程时两份内容必须恒差一个步距，否则交叉淡化会叠出重影（2.0.2 的老方案）。
    @Test
    void halfwayKeepsBothContentsExactlyOneStepApart() {
        SwapState swap = new SwapState();
        swap.begin(MILLIS);
        // 半程：progress 0.5，smoothstep(0.5) 也是 0.5。
        assertFalse(swap.advance(0.1F));
        assertEquals(0.5F, swap.progress(), 1.0E-4F);
        assertEquals(0.5F, swap.outgoingShift(), 1.0E-4F);
        assertEquals(-0.5F, swap.incomingShift(), 1.0E-4F);
        assertEquals(0.5F, swap.outgoingAlpha(), 1.0E-4F);
        assertEquals(0.5F, swap.incomingAlpha(), 1.0E-4F);
        // 两份内容的间距恒为一个步距（这是「不重叠」的全部依据）。
        assertEquals(swap.outgoingShift() - 1.0F, swap.incomingShift(), 1.0E-4F);
    }

    // 防回归：advance 用返回值报告走完，但那一帧不许自动复位 —— 调用者还得把这一帧画完。
    @Test
    void advanceReportsCompletionAndTheFinalFrameIsAtRest() {
        SwapState swap = new SwapState();
        swap.begin(MILLIS);
        assertTrue(swap.advance(1.0F), "超过总时长应当报「走完」");
        assertEquals(1.0F, swap.shift(), 1.0E-4F);
        assertEquals(0.0F, swap.outgoingAlpha(), 1.0E-4F);
        assertEquals(1.0F, swap.incomingAlpha(), 1.0E-4F);
        assertEquals(0.0F, swap.incomingShift(), 1.0E-4F);
        // 走完并不自动复位（调用者要画完这一帧），复位由 clear 负责。
        assertTrue(swap.active());
        swap.clear();
        assertFalse(swap.active());
        assertEquals(0.0F, swap.shift(), 1.0E-4F);
    }

    // 防回归：帧间隔为 0 / 负数 / NaN 时过渡原地不动，不许把 NaN 灌进进度里。
    @Test
    void zeroOrNegativeDeltaDoesNotMoveTheTransition() {
        SwapState swap = new SwapState();
        swap.begin(MILLIS);
        assertFalse(swap.advance(0.0F));
        assertFalse(swap.advance(-0.5F));
        assertFalse(swap.advance(Float.NaN));
        assertEquals(0.0F, swap.progress(), 1.0E-4F);
    }

    // 防回归：时长 0 / 1 毫秒也要一帧走完（不能卡在过渡里），没开始时推进则什么也不做。
    @Test
    void shortOrZeroAnimationStillCompletesInOneFrame() {
        SwapState zero = new SwapState();
        zero.begin(0);
        assertTrue(zero.advance(1.0F / 60.0F), "时长 0 也要一帧走完，不能卡住");
        SwapState one = new SwapState();
        one.begin(1);
        assertTrue(one.advance(1.0F / 60.0F));
        // 没开始时推进什么也不做。
        SwapState idle = new SwapState();
        assertFalse(idle.advance(1.0F));
    }

    // 防回归：clear() 要把四路读数一起归位；复位之后再推进不许有任何动静。
    @Test
    void clearStopsAnOngoingSwap() {
        SwapState swap = new SwapState();
        swap.begin(MILLIS);
        swap.advance(0.05F);
        swap.clear();
        assertFalse(swap.active());
        assertEquals(0.0F, swap.progress(), 1.0E-4F);
        assertEquals(0.0F, swap.shift(), 1.0E-4F);
        assertEquals(0.0F, swap.outgoingAlpha(), 1.0E-4F);
        assertEquals(1.0F, swap.incomingAlpha(), 1.0E-4F);
        assertFalse(swap.advance(1.0F), "复位之后再推进不应该有任何动静");
    }

    // ---------- 打断与限时收尾（2.0.5 起） ----------

    // 防回归：打断只缩短剩下那段，进度与位移当场不动 —— 重播就是用户抱怨的黏滞手感（§13.21）。
    @Test
    void interruptionKeepsTheProgressAndOnlyShortensWhatIsLeft() {
        SwapState swap = new SwapState();
        swap.begin(MILLIS);
        swap.advance(0.05F);          // 走了 50 毫秒 / 200 毫秒 = 进度 0.25
        assertEquals(0.25F, swap.progress(), 1.0E-4F);
        swap.hurryTo(200);            // 限时 200 毫秒走完剩下的 0.75 -> 速率变成 200 / 0.75 ≈ 267 毫秒一格
        // 位移与不透明度在打断这一刻必须原地不动（接手的是位置，不是状态）。
        assertEquals(0.25F, swap.progress(), 1.0E-4F);
        assertTrue(swap.advance(0.2F), "限时 200 毫秒后应当正好走完");
        swap.clear();
        assertFalse(swap.active());
    }

    // 防回归：hurryTo 只会变快绝不拖慢；没有过渡 / 疯参数（0、极大值）都不许崩。
    @Test
    void hurryNeverSlowsDownAndIgnoresTheIdleCase() {
        SwapState swap = new SwapState();
        swap.begin(MILLIS);
        swap.hurryTo(10_000);         // 比原来的 200 毫秒还宽松：速率不许被拖慢
        assertFalse(swap.advance(0.1F), "原速率下 100 毫秒应该只走一半");
        assertEquals(0.5F, swap.progress(), 1.0E-4F);
        SwapState idle = new SwapState();
        idle.hurryTo(50);             // 没有过渡时什么也不做
        assertFalse(idle.active());
        swap.advance(1.0F);
        swap.clear();
        swap.hurryTo(0);              // 疯参数不该让任何东西崩掉
        assertFalse(swap.active());
    }
}
