package dev.durahud.hud;

import dev.durahud.config.DuraHudConfig;
import net.minecraft.item.ItemStack;

/**
 * HUD 上的一个格子：要么属于某个固定原版槽位（盔甲/主手/副手），
 * 要么是一件「额外装备」（饰品等，见 {@link ExtraSlots}）。
 *
 * <p>条目只是<b>栈的视图</b>：{@link HudEntries} 每帧把玩家当前的物品塞进来，
 * 这里不缓存、也不判断玩家状态。</p>
 */
public final class DurabilityEntry {

    /**
     * 6 个固定原版槽位；枚举顺序决定固定槽位之间的先后：4 个盔甲格在前，主手/副手在最后。
     * 额外装备槽（饰品）插在两者之间，见 {@link HudEntries}。
     */
    public enum Kind {
        HELMET(true),
        CHESTPLATE(true),
        LEGGINGS(true),
        BOOTS(true),
        MAINHAND(false),
        OFFHAND(false);

        /** 盔甲格：空着时是否仍占位由 {@link DuraHudConfig#showEmptySlots} 决定（见 occupiesCell）。 */
        public final boolean armor;

        Kind(boolean armor) {
            this.armor = armor;
        }
    }

    /** null 表示这是额外装备（饰品），没有原版槽位身份。 */
    private final Kind kind;
    private final boolean armor;
    private ItemStack stack;
    /** 本帧要画的内容快照；内容消失后保留最后一份，作为淡出用的替身。 */
    private final CellContent content = new CellContent();
    /** 淡入淡出进度 0..1（1 = 完全不透明）。 */
    private float fade;
    /** 本帧的绘制位置（缩放后的屏幕坐标），平滑移动的中间量。 */
    private float drawX;
    private float drawY;
    /** 上一帧是否画过 / 这一帧是否画过；没画过的格子直接落位，不从旧位置飞过来。 */
    private boolean onScreen;
    private boolean onScreenNow;

    public DurabilityEntry(Kind kind, ItemStack stack) {
        this.kind = kind;
        this.armor = kind != null && kind.armor;
        this.stack = stack;
    }

    /** 额外装备槽（Trinkets 等）里的物品。 */
    public static DurabilityEntry extra(ItemStack stack) {
        return new DurabilityEntry(null, stack);
    }

    public Kind kind() {
        return kind;
    }

    public ItemStack stack() {
        return stack;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;
    }

    /** 这一格是不是盔甲格；盔甲格的边框不参与淡入淡出（见 HudRenderer）。 */
    public boolean armor() {
        return armor;
    }

    /** 本帧要画的内容快照；内容已经消失时，它是最后一份淡出替身。 */
    public CellContent content() {
        return content;
    }

    /** 淡入淡出进度 0..1。 */
    public float fade() {
        return fade;
    }

    /** 本帧要画的屏幕坐标（已含平滑移动的中间量）。 */
    public float drawX() {
        return drawX;
    }

    public float drawY() {
        return drawY;
    }

    /**
     * 每帧把绘制位置朝目标滑动。
     *
     * <p>第一次出现在视图里（或关掉动画）时直接落位：格子该在哪儿就在哪儿开始淡入，
     * 而不是从上一帧的旧位置飞过来 —— 视图重建、窗口尺寸变化时尤其明显。</p>
     */
    public void slideTo(float targetX, float targetY, float dtSeconds, DuraHudConfig cfg) {
        if (onScreen && cfg.animateCells) {
            drawX = FadeState.slide(drawX, targetX, dtSeconds, cfg.animationMillis);
            drawY = FadeState.slide(drawY, targetY, dtSeconds, cfg.animationMillis);
        } else {
            drawX = targetX;
            drawY = targetY;
        }
        onScreenNow = true;
    }

    /** 帧末提交：这一帧没被画过的格子，下次出现时直接落位。 */
    void commitPlacement() {
        onScreen = onScreenNow;
        onScreenNow = false;
    }

    /** 本帧还有东西可画：有实物内容，或还留着一份正在淡出的替身。 */
    public boolean visible(DuraHudConfig cfg) {
        return hasContents(cfg) || fade > 0.0F;
    }

    /**
     * 每帧推进淡入淡出，并在有实物内容时刷新快照。
     *
     * @param dtSeconds 距上一帧的秒数（由渲染器给出；停帧或长时间暂停后传 0，动画就停在原地）
     */
    public void advance(float dtSeconds, DuraHudConfig cfg) {
        boolean filled = hasContents(cfg);
        if (filled) {
            content.capture(this);
        }
        fade = cfg.animateCells
                ? FadeState.approach(fade, filled ? 1.0F : 0.0F, dtSeconds, cfg.animationMillis)
                : (filled ? 1.0F : 0.0F);
    }

    public boolean isEmpty() {
        return stack == null || stack.isEmpty();
    }

    /** 是否要在这个格子里画内容（物品图标/耐久条/文字）。 */
    public boolean hasContents(DuraHudConfig cfg) {
        if (isEmpty()) {
            return false;
        }
        if (!hasWear()) {
            // 没有耐久信息：只看「有没有被放行」，不受 hideFull 影响（它们没有「满」的概念）。
            return showsWithoutWear(cfg);
        }
        return !cfg.hideFull || !isFull();
    }

    /**
     * 是否要占用一个格子（决定 HUD 上到底有几格）。
     *
     * <p>有内容就占位；内容刚消失但还没淡完（{@code fade > 0}）时也占位，这样格子数与顺序
     * 不会在淡出期间跳一下 —— 淡完才真正让位，后面的格子这时才顶上来。</p>
     *
     * <p>空槽位看 {@link DuraHudConfig#showEmptySlots}：打开时空着的盔甲格照常占位（本来就画着
     * 边框），关掉时它不再占位、后面的格子自动补位。旧版那个单独的「自动补位」开关在 v5 → v6
     * 合并进了这个开关 —— 空槽位既然画着边框，它是否占位本来就等于「有没有自动补位」。</p>
     */
    public boolean occupiesCell(DuraHudConfig cfg) {
        if (hasContents(cfg) || fade > 0.0F) {
            return true;
        }
        return armor && cfg.showEmptySlots;
    }

    /**
     * 没有耐久信息的物品（机械动力的护目镜、南瓜、无耐久饰品）要不要显示：
     * 关掉「只显示有耐久的物品」时全都显示；否则还要开着「穿戴的无耐久装备例外」、
     * 而且这一格属于穿戴类（盔甲格或额外装备槽）。所以手里的胡萝卜、木板始终被挡在外面。
     */
    private boolean showsWithoutWear(DuraHudConfig cfg) {
        return !cfg.onlyDurableItems || (cfg.showWornNonDurable && isWorn());
    }

    /** 穿戴类格子：4 个盔甲格与额外装备槽（饰品）。手持槽不算。 */
    private boolean isWorn() {
        return armor || kind == null;
    }

    /** 有耐久度（可损坏）或物品自带小耐久条。 */
    public boolean hasWear() {
        if (isEmpty()) {
            return false;
        }
        return stack.getMaxDamage() > 0 || stack.isItemBarVisible();
    }

    public int max() {
        return stack.getMaxDamage();
    }

    public int remaining() {
        return Math.max(0, max() - stack.getDamage());
    }

    /** 是否能显示「剩余/上限」这种精确数值（没有耐久上限时只能显示百分比）。 */
    public boolean hasNumeric() {
        return max() > 0;
    }

    /** 0..1 的剩余比例；没有耐久上限时退回物品自带小条（13 步）。 */
    public float ratio() {
        int max = max();
        if (max > 0) {
            return clamp((float) (max - stack.getDamage()) / (float) max);
        }
        if (stack.isItemBarVisible()) {
            return clamp(stack.getItemBarStep() / 13.0F);
        }
        return 1.0F;
    }

    public boolean isFull() {
        int max = max();
        return max <= 0 || stack.getDamage() <= 0;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
