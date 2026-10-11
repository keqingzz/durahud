package dev.durahud.hud;

/**
 * 耐久读数的纯函数部分：<b>不引用任何 Minecraft 类</b>，所以能脱离游戏直接跑单测。
 *
 * <p>{@link DurabilityEntry} 只负责把 {@code ItemStack} 上的四个数读出来，判定与换算全落在这里。
 * 「有没有耐久」「剩余比例」「算不算满」以前各写一套，口径一旦分叉就会出现自相矛盾的状态：
 * 只有物品自带小条的工具（{@code maxDamage = 0}）被 {@code isFull} 判成满的，于是开着
 * {@code hideFull} 时它连格子都不画，可条上明明还有一半 —— 这就是 2.1.7 修掉的回归。</p>
 *
 * <p>四个入参就是判定需要的全部信息：耐久上限、已损值、小条是否可见、小条当前步数。
 * 越界不用在这里防：{@code ItemStack} 保证 {@code damage ∈ [0, maxDamage]}，步数由原版夹在
 * {@code 0..}{@link #ITEM_BAR_STEPS}；真要手改配置也只会改到物品上，读出来仍然合规。</p>
 */
final class Wear {

    /** 原版物品自带小条的格数（{@code ItemStack.getItemBarStep()} 的满格值）。 */
    static final int ITEM_BAR_STEPS = 13;

    private Wear() {
    }

    /**
     * 有没有耐久信息：可损坏（{@code maxDamage > 0}）或物品自带小条。
     *
     * <p>两样都没有的物品（南瓜、机械动力的护目镜）没有「比例」与「满不满」可言，调用方
     * （{@code hasContents}）会先问这里，再决定走哪条显隐分支。</p>
     */
    static boolean hasWear(int maxDamage, boolean barVisible) {
        return maxDamage > 0 || barVisible;
    }

    /**
     * 0..1 的剩余比例；没有耐久上限时退回物品自带小条（{@link #ITEM_BAR_STEPS} 步）。
     *
     * <p>两端都夹住：条长、数值与配色都从这里取，越界会把条画到格子外面去。</p>
     */
    static float ratio(int maxDamage, int damage, boolean barVisible, int barStep) {
        if (maxDamage > 0) {
            return clamp((float) (maxDamage - damage) / (float) maxDamage);
        }
        if (barVisible) {
            return clamp(barStep / (float) ITEM_BAR_STEPS);
        }
        return 1.0F;
    }

    /**
     * 算不算「满」——<b>必须与 {@link #ratio()} 同一套口径</b>：比例到 1 才叫满。
     *
     * <p>只有小条的物品（{@code maxDamage = 0}）看小条步数，不再一律当成满的；两个入口问的是
     * 同一个事实，答案就不可能相反（见 README 13.36）。</p>
     */
    static boolean isFull(int maxDamage, int damage, boolean barVisible, int barStep) {
        if (maxDamage > 0) {
            return damage <= 0;
        }
        if (!barVisible) {
            // 没有耐久信息 = 没有「满」的概念。正常走不到这里：调用方先过 hasWear()。
            return true;
        }
        return barStep >= ITEM_BAR_STEPS;
    }

    /** 夹到 0..1。 */
    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
