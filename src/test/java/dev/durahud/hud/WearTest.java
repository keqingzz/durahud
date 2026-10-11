package dev.durahud.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 耐久口径的纯函数真值表（{@link Wear}）：四个入参就是 {@code ItemStack} 上读得出的全部信息，
 * 所以不需要 Minecraft 运行时（与 FadeStateTest 同一思路）。
 *
 * <p>要钉住的核心不变量是「<b>满</b>」与「<b>比例 = 1</b>」同源（README 13.36）。2.1.7 之前
 * {@code isFull} 写的是「上限 ≤ 0 就算满」，只有物品自带小条的工具（上限 = 0）因此被判成满，
 * 开着 {@code hideFull} 时整格都不画，可小条上明明还剩一半 —— 失败就意味着这个回归回来了。</p>
 */
class WearTest {

    // 防回归：小条格数是原版 ItemStack.getItemBarStep() 的满格值，改动会让所有条-only 物品比例错位。
    @Test
    void hasWearCoversBothSourcesOfWear() {
        assertEquals(13, Wear.ITEM_BAR_STEPS);
        // 可损坏物品：有没有小条都算有耐久
        assertTrue(Wear.hasWear(250, false));
        assertTrue(Wear.hasWear(250, true));
        // 不可损坏但物品自带小条
        assertTrue(Wear.hasWear(0, true));
        // 两样都没有：南瓜、机械动力的护目镜
        assertFalse(Wear.hasWear(0, false));
    }

    // 防回归：有耐久上限时一切以真实耐久为准，自带小条的步数不许插一脚。
    @Test
    void ratioUsesDamageWhenThereIsAMax() {
        assertEquals(1.0F, Wear.ratio(100, 0, false, 0), 1.0E-6F);
        assertEquals(0.5F, Wear.ratio(100, 50, false, 0), 1.0E-6F);
        assertEquals(0.0F, Wear.ratio(100, 100, false, 0), 1.0E-6F);
        // 两者冲突时同样以真实耐久为准
        assertEquals(0.5F, Wear.ratio(100, 50, true, 13), 1.0E-6F);
        // 越界要夹住：条长、数字与配色都从比例来，跑出 0..1 会把条画到格子外
        assertEquals(1.0F, Wear.ratio(100, -5, false, 0), 1.0E-6F);
        assertEquals(0.0F, Wear.ratio(100, 120, false, 0), 1.0E-6F);
    }

    // 防回归：没有上限时退回物品自带小条（13 格），步数越界同样夹住。
    @Test
    void ratioFallsBackToTheItemBarWithoutAMax() {
        for (int step = 0; step <= Wear.ITEM_BAR_STEPS; step++) {
            assertEquals(step / 13.0F, Wear.ratio(0, 0, true, step), 1.0E-6F);
        }
        assertEquals(1.0F, Wear.ratio(0, 0, true, 99), 1.0E-6F);
        assertEquals(0.0F, Wear.ratio(0, 0, true, -3), 1.0E-6F);
        // 小条也不可见又没有上限：没有信息可读，退回「满」（调用方会先问 hasWear）
        assertEquals(1.0F, Wear.ratio(0, 0, false, 0), 1.0E-6F);
    }

    // 防回归：可损坏物品掉一点耐久就不算满；条-only 物品必须走满 13 格才叫满。
    @Test
    void isFullFollowsTheSameRule() {
        assertTrue(Wear.isFull(100, 0, false, 0));
        assertFalse(Wear.isFull(100, 1, false, 0));
        assertFalse(Wear.isFull(100, 100, false, 0));
        assertTrue(Wear.isFull(0, 0, true, 13));
        assertFalse(Wear.isFull(0, 0, true, 12));
        assertFalse(Wear.isFull(0, 0, true, 0));
        // 没有耐久信息 = 没有「满」的概念（正常走不到：调用方先过 hasWear）
        assertTrue(Wear.isFull(0, 0, false, 0));
    }

    // 核心不变量：满 ⟺ 比例 = 1。两个入口问的是同一个事实，任何一组输入都不许给出相反的答案。
    @Test
    void isFullAndRatioNeverDisagree() {
        int[] maxes = {0, 1, 100, 250};
        int[] damages = {0, 1, 50, 100, 250};
        boolean[] bars = {false, true};
        int[] steps = {0, 1, 6, 12, 13};
        int cases = 0;
        for (int max : maxes) {
            for (int damage : damages) {
                for (boolean bar : bars) {
                    for (int step : steps) {
                        assertEquals(Wear.ratio(max, damage, bar, step) >= 1.0F,
                                Wear.isFull(max, damage, bar, step),
                                "max=" + max + " damage=" + damage + " bar=" + bar + " step=" + step
                                        + "：满与比例必须同源（README 13.36）");
                        cases++;
                    }
                }
            }
        }
        assertEquals(200, cases, "组合数写死了：改了上面四张表就要连这里一起改");
    }

    // 2.1.7 修的回归本体：条-only 物品还剩一半时不许判满（判满就会被 hideFull 整格藏掉）。
    @Test
    void halfFullBarOnlyItemsAreNotFull() {
        assertFalse(Wear.isFull(0, 0, true, 6));
        assertFalse(Wear.isFull(0, 0, true, 1));
        assertEquals(6 / 13.0F, Wear.ratio(0, 0, true, 6), 1.0E-6F);
    }
}
