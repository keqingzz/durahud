package dev.durahud.hud;

import dev.durahud.config.DuraHudConfig;
import net.minecraft.class_1799;

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
    private class_1799 stack;
    /** 本帧要画的内容快照；内容消失后保留最后一份，作为淡出用的替身。 */
    private final CellContent content = new CellContent();
    /** 换物过渡里「正在离场」的那一份快照（只有过渡进行中才有意义，见 {@link SwapState}）。 */
    private final CellContent outgoing = new CellContent();
    /** 同一格里换了别的东西时的过渡：两份内容各推进一格，边走边交叉淡入淡出。 */
    private final SwapState swap = new SwapState();
    /** 淡入淡出进度 0..1（1 = 完全不透明）。 */
    private float fade;
    /** 本帧的绘制位置（缩放后的屏幕坐标），平滑移动的中间量。 */
    private float drawX;
    private float drawY;
    /** 上一帧是否画过 / 这一帧是否画过；没画过的格子直接落位，不从旧位置飞过来。 */
    private boolean onScreen;
    private boolean onScreenNow;

    public DurabilityEntry(Kind kind, class_1799 stack) {
        this.kind = kind;
        this.armor = kind != null && kind.armor;
        this.stack = stack;
    }

    /** 额外装备槽（Trinkets 等）里的物品。 */
    public static DurabilityEntry extra(class_1799 stack) {
        return new DurabilityEntry(null, stack);
    }

    public Kind kind() {
        return kind;
    }

    public class_1799 stack() {
        return stack;
    }

    public void setStack(class_1799 stack) {
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

    /** 换物过渡的状态；渲染器读它的推进量、不透明度与位移（见 {@link SwapState}）。 */
    public SwapState swap() {
        return swap;
    }

    /** 过渡里正在离场的那份旧内容；只有 {@code swap().active()} 时才该被画。 */
    public CellContent outgoing() {
        return outgoing;
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
     *
     * <p><b>编辑模式永远不平滑</b>：拖动时外框是跟着鼠标立刻走的，格子若还在用缓动追，
     * 就会看到「框走了、格子还在后面飘」。H 模式下的目标就是所见即所得，所以直接落位。</p>
     */
    public void slideTo(float targetX, float targetY, float dtSeconds, DuraHudConfig cfg) {
        if (onScreen && cfg.animateCells && !cfg.editMode) {
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
     * 每帧推进淡入淡出与换物过渡，并在有实物内容时刷新快照。
     *
     * <p>「有内容」时默认就地刷新快照（掉耐久、改显示模式都走这里）。只有当<b>快照里画的
     * 是另一种物品</b>——也就是同一个格子里换了东西——才启动 {@link SwapState}：现在这份内容
     * 留作离场的替身，新内容立刻接手、从上一格的位置推进来。</p>
     *
     * <p>过渡途中又换了一次（连续切换工具/装备）时<b>不重播</b>：进度、位移与离场的那份都
     * 原地不动，新物品接手进场层并限时收尾（见 {@link SwapState#hurryTo(int)}）。</p>
     *
     * @param dtSeconds 距上一帧的秒数（由渲染器给出；停帧或长时间暂停后传 0，动画就停在原地）
     */
    public void advance(float dtSeconds, DuraHudConfig cfg) {
        boolean filled = hasContents(cfg);

        if (!cfg.animateCells) {
            swap.clear();
            if (filled) {
                content.capture(this, cfg, dtSeconds);
            }
            fade = filled ? 1.0F : 0.0F;
            return;
        }

        if (!filled) {
            // 东西没了：快照不再刷新（最后一份自然成了淡出用的替身），但正在进行的换物过渡
            // 照常走完 —— 半路掐断会让已经推进到一半的进场内容突然弹回原位、不透明度跳回 1。
            if (swap.active() && swap.advance(dtSeconds)) {
                swap.clear();
            }
        } else if (!swap.active()) {
            // 格子还在淡入淡出的半路上时就别插一脚：那点时间本来就短，交给 fade 管更干净。
            if (sameContentAsCurrent() || fade < 1.0F) {
                content.capture(this, cfg, dtSeconds);
            } else {
                outgoing.copyFrom(content);   // 现在这份内容留作离场的替身
                swap.begin(cfg.animationMillis);
                content.capture(this, cfg, dtSeconds);   // 新内容立刻接手：它在上一格的位置
            }
        } else {
            if (!sameContentAsCurrent()) {
                // 过渡途中又换了一次：<b>不重播</b>。离场那份照旧接着走、进度与位移都原地不动，
                // 新物品直接接手「正在进场」的那一层（位置与不透明度连续，不跳），只是把剩下
                // 这段路限时收尾 —— 否则动画时长一大，每换一次都要从头飘一遍，手感就是「卡住」。
                swap.hurryTo(interruptMillis(cfg));
            }
            content.capture(this, cfg, dtSeconds);
            if (swap.advance(dtSeconds)) {
                swap.clear();   // 走完这一帧画出来就是静止态（位移归位、不透明度各就各位）
            }
        }

        fade = FadeState.approach(fade, filled ? 1.0F : 0.0F, dtSeconds, cfg.animationMillis);
    }

    /**
     * 被打断的那一段收尾时间上限：不超过配置时长的一半，最少 100 毫秒。
     *
     * <p>下限是为了让一小段位移仍然看得见（100 毫秒在 60 帧下也有 6 帧）；上限跟着配置走，
     * 于是「连续快速切换」每次都把剩下的路砍掉一半，几次之后就基本跟手了 —— 而单次换物
     * 仍然完整播放 {@code animationMillis}。</p>
     */
    private static int interruptMillis(DuraHudConfig cfg) {
        return Math.max(100, cfg.animationMillis / 2);
    }

    /**
     * 快照里画的还是不是手里这件东西。
     *
     * <p>比的是<b>物品身份</b>（{@code getItem()}）而不是整份栈：掉耐久、附魔变化、数量增减
     * 都不算「换物」，那些情况本来就地刷新就够了；换成另一种物品才走过渡。任一侧是空的时候
     * 也不算换物 —— 空槽位的进出由 {@code fade} 负责，别在这里插一脚。</p>
     */
    private boolean sameContentAsCurrent() {
        class_1799 drawn = content.stack();
        if (drawn.method_7960() || isEmpty()) {
            return true;
        }
        return drawn.method_7909() == stack.method_7909();
    }

    public boolean isEmpty() {
        return stack == null || stack.method_7960();
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
        return stack.method_7936() > 0 || stack.method_31578();
    }

    public int max() {
        return stack.method_7936();
    }

    public int remaining() {
        return Math.max(0, max() - stack.method_7919());
    }

    /** 是否能显示「剩余/上限」这种精确数值（没有耐久上限时只能显示百分比）。 */
    public boolean hasNumeric() {
        return max() > 0;
    }

    /** 0..1 的剩余比例；没有耐久上限时退回物品自带小条（13 步）。 */
    public float ratio() {
        int max = max();
        if (max > 0) {
            return clamp((float) (max - stack.method_7919()) / (float) max);
        }
        if (stack.method_31578()) {
            return clamp(stack.method_31579() / 13.0F);
        }
        return 1.0F;
    }

    public boolean isFull() {
        int max = max();
        return max <= 0 || stack.method_7919() <= 0;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
