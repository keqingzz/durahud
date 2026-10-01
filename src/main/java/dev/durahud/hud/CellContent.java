package dev.durahud.hud;

import net.minecraft.item.ItemStack;

/**
 * 一个格子「本帧要画的东西」的快照：物品图标 + 耐久条 + 数值。
 *
 * <p>每个 {@link DurabilityEntry} 常驻一个实例，有实物内容时每帧就地刷新（不产生垃圾）；
 * 内容消失后<b>不再刷新</b>，最后一份快照自然就成了淡出用的替身 —— 淡出期间画的仍是
 * 原来那把镐、原来那条耐久条、原来那个百分比，而不是「空槽位」的样子。</p>
 */
public final class CellContent {

    private ItemStack stack = ItemStack.EMPTY;
    private boolean bar;
    private float ratio = 1.0F;
    private boolean numeric;
    private int remaining;
    private int max;

    /** 就地刷新（包内可见，只由 {@link DurabilityEntry#advance} 调用）。 */
    void capture(DurabilityEntry entry) {
        this.stack = entry.stack();
        this.bar = entry.hasWear();
        this.ratio = entry.ratio();
        this.numeric = entry.hasNumeric();
        this.remaining = entry.remaining();
        this.max = entry.max();
    }

    public ItemStack stack() {
        return stack;
    }

    /** 这一份内容要不要画耐久条（替身保留它当时的答案）。 */
    public boolean hasBar() {
        return bar;
    }

    public float ratio() {
        return ratio;
    }

    /** 能不能显示「剩余/上限」这种精确数值。 */
    public boolean hasNumeric() {
        return numeric;
    }

    public int remaining() {
        return remaining;
    }

    public int max() {
        return max;
    }
}
