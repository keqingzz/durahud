package dev.durahud.hud;

import dev.durahud.config.DuraHudConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * HUD 的渲染入口：每帧刷新物品、算一次几何、再把每个格子画出来。
 *
 * <p>这里只做与游戏状态相关的事（读玩家的槽位、读窗口尺寸、量文字宽度），
 * 所有位置都来自 {@link HudLayout}。</p>
 *
 * <p><b>逐格矩阵</b>：每个格子在自己的 {@code push/translate/scale/pop} 里画，位置取
 * {@link DurabilityEntry#drawX()} / {@link DurabilityEntry#drawY()}（缩放后的屏幕坐标）。
 * 自动补位时原点与格子序号会一起变，只有逐格算位置才能让每格从旧位置滑到新位置；
 * 格子内部因此都以左上角为原点。</p>
 *
 * <p><b>淡入淡出怎么画</b>：{@link DurabilityEntry} 给出 0..1 的动画进度，这里换算成
 * 着色器颜色里的 alpha（{@code DrawContext.setShaderColor}）再画。原版就是这么给 HUD
 * 做透明度的 —— {@code InGameHud.renderOverlay} 用同一个 alpha 画整屏贴图，末了再复位成
 * 1。物品图标与文字是<b>批处理</b>的，颜色要等 {@code ctx.draw()} 把它们刷出去那一刻才
 * 生效，所以设色前后各刷一次：设色前刷，是为了不让「已经排队的上一格」被染成这一格的颜色
 * （旧版 cellOpacity 就是栽在这里）。详见 README 7.6。</p>
 *
 * <p><b>换物过渡</b>：同一个格子里换了别的东西时，{@link SwapState} 让两份图标各推进一格 ——
 * 旧图标朝排列方向的下一个格子位置移动并淡出，新图标从上一个格子的位置移动进来并淡入
 * （濒危高亮框跟着图标走，它圈的是「这一件东西」）。<b>耐久条与数值不参与这段位移</b>：
 * 它们定在格子自己的位置上不动，只按 {@link CellContent#displayRatio()} 的变化改长度与数字
 * （见 README 7.8）—— 切工具时看到的是「图标滑过去、条和数字就地变了一下」，而不是整格内容平移。
 * 格子边框与格子位置同样不动：边框属于格子。</p>
 */
public final class HudRenderer {

    /** 编辑模式下的整体描边色（不依赖任何配置，用户一眼能看出「正在编辑」）。 */
    private static final int EDIT_BORDER = 0xFF6FE3FF;

    private static final HudEntries ENTRIES = new HudEntries();

    /** 上一帧的时间戳，用来算动画步长；0 表示还没画过第一帧。 */
    private static long lastFrameNanos;

    private HudRenderer() {
    }

    public static void render(DrawContext ctx, DuraHudConfig cfg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!shouldDraw(mc, cfg)) {
            return;
        }
        float dtSeconds = frameSeconds();
        ENTRIES.refresh(mc, cfg, dtSeconds);
        if (ENTRIES.count() == 0 && !cfg.editMode) {
            ENTRIES.commitPlacement();
            return;
        }
        HudLayout layout = HudLayout.geometry(cfg, ENTRIES.count(),
                mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(),
                Atlas.reserveTextWidth(mc.textRenderer));
        drawCells(ctx, mc, cfg, layout, dtSeconds);
        ENTRIES.commitPlacement();
        if (cfg.editMode) {
            drawEditOverlay(ctx, layout);
        }
    }

    /**
     * 编辑模式是例外：H 键界面只能在游戏里打开，但那时 HUD 可能被关掉或被 F1 隐藏。这种情况下
     * 仍然往下走一遍几何，否则编辑界面上根本没有外框，HudLayout.last() 还会停留在上一次的陈旧
     * 几何（换过窗口尺寸后拖动就对不齐）。{@code ENTRIES.refresh} 对 null 玩家是安全的。
     */
    private static boolean shouldDraw(MinecraftClient mc, DuraHudConfig cfg) {
        if (cfg.editMode) {
            return true;
        }
        return cfg.enabled && mc.player != null && !mc.options.hudHidden;
    }

    /**
     * 逐格矩阵：每格在自己的 {@code push/translate/scale/pop} 里画，位置由条目给出（含平滑移动的
     * 中间量）。自动补位时原点与格子序号会一起变，只有逐格算位置才能让每格从旧位置滑到新位置。
     */
    private static void drawCells(DrawContext ctx, MinecraftClient mc, DuraHudConfig cfg,
                                  HudLayout layout, float dtSeconds) {
        float scale = layout.scale();
        float originX = layout.originX();
        float originY = layout.originY();
        int count = ENTRIES.count();
        for (int i = 0; i < count; i++) {
            DurabilityEntry entry = ENTRIES.get(i);
            entry.slideTo(originX + layout.cellLocalX(i) * scale,
                    originY + layout.cellLocalY(i) * scale, dtSeconds, cfg);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(entry.drawX(), entry.drawY(), 0.0F);
            ctx.getMatrices().scale(scale, scale, 1.0F);
            drawEntry(ctx, mc, cfg, layout, entry);
            ctx.getMatrices().pop();
        }
    }

    /** 外框围的是整块 HUD：整排一起滑完之后按当前几何画。 */
    private static void drawEditOverlay(DrawContext ctx, HudLayout layout) {
        float scale = layout.scale();
        ctx.getMatrices().push();
        ctx.getMatrices().translate((float) layout.originX(), (float) layout.originY(), 0.0F);
        ctx.getMatrices().scale(scale, scale, 1.0F);
        drawEditBorder(ctx, layout.localW(), layout.localH());
        ctx.getMatrices().pop();
    }

    /**
     * 距上一帧的秒数。用 {@link System#nanoTime()} 而不是游戏的 tick 计时，动画因此与帧率无关；
     * 第一帧、或跨度太大（暂停、切维度、日志卡顿）时返回 0 —— 宁可这一帧不动，
     * 也不要让淡入淡出一步跳完。
     */
    private static float frameSeconds() {
        long now = System.nanoTime();
        long previous = lastFrameNanos;
        lastFrameNanos = now;
        if (previous == 0L) {
            return 0.0F;
        }
        float seconds = (now - previous) / 1_000_000_000.0F;
        return seconds < 0.0F || seconds > 1.0F ? 0.0F : seconds;
    }

    /**
     * 画一格：边框、图标、耐久条、数值。坐标以格子左上角为原点 —— 格子自己的
     * {@code push/translate/scale} 已经在 {@link #render} 里摆好，这里不用再管屏幕位置。
     */
    private static void drawEntry(DrawContext ctx, MinecraftClient mc, DuraHudConfig cfg,
                                  HudLayout layout, DurabilityEntry entry) {
        boolean visible = entry.visible(cfg);
        float alpha = FadeState.alpha(entry.fade(), cfg.animateCells);
        if (cfg.showSlotFrames && (visible || cfg.showEmptySlots)) {
            // 盔甲格边框：开着「空槽位边框」时，空盔甲格是一个常驻占位，边框就一直实心
            // （否则刚脱下头盔时边框会闪一下）；关掉它时空盔甲格会整格淡出，边框必须跟着
            // 内容一起淡 —— 不然脱下盔甲会在原地留一个空心方框（旧版恒定 1.0 就是这么来的）。
            float frameAlpha = entry.armor() && cfg.showEmptySlots ? 1.0F : alpha;
            pushAlpha(ctx, frameAlpha);
            Atlas.cellFrame(ctx, 0, 0);
            popAlpha(ctx, frameAlpha);
        }
        if (!visible) {
            return;
        }
        // 格子的淡入淡出 × 换物过渡：两层不透明度相乘（没有过渡时后一项恒为 1）。
        SwapState swap = entry.swap();
        if (swap.active()) {
            drawSwapping(ctx, mc, cfg, layout, entry, swap, alpha);
        } else {
            drawStill(ctx, mc, cfg, layout, entry.content(), alpha);
        }
    }

    /**
     * 换物过渡中的一格：两份内容各推一格、交叉淡入淡出（见 {@link SwapState}）。位移用几何层的
     * 步距（缩放前的像素），所以缩放多少就走多少。
     */
    private static void drawSwapping(DrawContext ctx, MinecraftClient mc, DuraHudConfig cfg, HudLayout layout,
                                     DurabilityEntry entry, SwapState swap, float alpha) {
        boolean horizontal = cfg.orientation == DuraHudConfig.Orientation.HORIZONTAL;
        int pitch = horizontal ? layout.pitchX() : layout.pitchY();
        CellContent incoming = entry.content();
        Severity incomingSeverity = Severity.of(cfg, incoming.ratio());
        // 只有<b>图标层</b>推一格、交叉淡入淡出：旧图标往下一格走、新图标从上一格进来。
        drawShiftedIcon(ctx, cfg, entry.outgoing(), swap.outgoingAlpha(), swap.outgoingShift(),
                horizontal, pitch, alpha, Severity.of(cfg, entry.outgoing().ratio()));
        drawShiftedIcon(ctx, cfg, incoming, swap.incomingAlpha(), swap.incomingShift(),
                horizontal, pitch, alpha, incomingSeverity);
        // 仪表层（耐久条 + 数值）不参与过渡：定在原地，也不跟着交叉淡化 ——
        // 切换工具时变的只是条的长度与数字（CellContent 的加减动画）。过渡期间也只画当前这一份：
        // 离场那份替身已经把显示值冻住了，两份都画就会在原地叠出两个数字、两条长度不同的条。
        pushAlpha(ctx, alpha);
        drawGaugeLayer(ctx, mc, cfg, layout, incoming, incomingSeverity);
        popAlpha(ctx, alpha);
    }

    /**
     * 没有过渡的一格：图标层与仪表层画在当前内容上，两层共用同一个耐久档位（同一份内容，
     * 没必要把阈值判两遍）。
     *
     * <p>本格的不透明度必须包在内容外面：只给边框设 alpha、内容直接画，就是 2.0.3 的回归
     * —— 空手 ↔ 工具来回切时图标、耐久条、数字都是硬跳出来的，只有边框在淡。</p>
     */
    private static void drawStill(DrawContext ctx, MinecraftClient mc, DuraHudConfig cfg, HudLayout layout,
                                  CellContent content, float alpha) {
        Severity severity = Severity.of(cfg, content.ratio());
        pushAlpha(ctx, alpha);
        drawIconLayer(ctx, cfg, content, 0, 0, severity);
        drawGaugeLayer(ctx, mc, cfg, layout, content, severity);
        popAlpha(ctx, alpha);
    }

    /**
     * 换物过渡里的<b>图标层</b>：按 {@code alpha * swapAlpha} 设不透明度、沿排列方向推
     * {@code shift} 个步距后画出来（{@code shift} 为 -1 时正好落在「上一个格子」的位置）。
     *
     * @param alpha 这一格的淡入淡出系数（两份内容共用的外层系数）
     * @param severity 这一份内容的耐久档位，由调用者按它自己的 {@code ratio()} 算好传来
     */
    private static void drawShiftedIcon(DrawContext ctx, DuraHudConfig cfg, CellContent content,
                                        float swapAlpha, float shift, boolean horizontal, int pitch,
                                        float alpha, Severity severity) {
        float a = alpha * swapAlpha;
        if (a <= 0.01F) {
            return;   // 完全透明的替身没必要画（也省掉一次物品渲染）
        }
        int offset = Math.round(shift * pitch);
        pushAlpha(ctx, a);
        drawIconLayer(ctx, cfg, content, horizontal ? offset : 0, horizontal ? 0 : offset, severity);
        popAlpha(ctx, a);
    }

    /**
     * 图标层：物品图标与濒危高亮框。这是换物过渡里<b>唯一</b>跟着位移走的部分 ——
     * 高亮框圈的是「这一件东西」，所以它跟图标一起滑。
     *
     * <p>调用者负责外层不透明度：早退之前一定要先 popAlpha，否则这一格的半透明会漏给
     * 后面所有格子（2.0.3 曾漏掉这一步，表现为只有边框在淡、内容硬跳）。</p>
     *
     * @param dx 换物滑入的横向位移（已缩放、已取整；没有过渡时是 0）
     * @param dy 换物滑入的纵向位移
     * @param severity 这一份内容的耐久档位（调用者算一次，图标层与仪表层共用）
     */
    private static void drawIconLayer(DrawContext ctx, DuraHudConfig cfg, CellContent content, int dx, int dy,
                                      Severity severity) {
        if (cfg.showItemIcons) {
            ctx.drawItem(content.stack(), Metrics.ICON_X + dx, Metrics.ICON_Y + dy);
        }
        if (content.hasBar() && cfg.highlightCritical && severity == Severity.CRIT) {
            Atlas.selectionBox(ctx, (Metrics.CELL_W - Metrics.SELECT_W) / 2 + dx,
                    (Metrics.CELL_H - Metrics.SELECT_H) / 2 + dy);
        }
    }

    /**
     * 仪表层：耐久条与数值。这一层<b>不参与换物过渡的位移与交叉淡化</b> —— 条与数字永远画在
     * 格子自己的位置上，同一个格子换东西时变的只是长度与数字（{@link CellContent#displayRatio()}
     * 的加减动画），不会像图标那样滑过去。
     *
     * <p>过渡期间也只画<b>当前</b>这一份（{@code entry.content()}）：离场那份替身已经把显示值
     * 冻住了，两份都画就会在原地叠出两个数字、两条长度不同的条。</p>
     *
     * @param severity 这一份内容的耐久档位（同一帧里图标层已经算过，这里不再重判阈值）
     */
    private static void drawGaugeLayer(DrawContext ctx, MinecraftClient mc, DuraHudConfig cfg,
                                       HudLayout layout, CellContent content, Severity severity) {
        if (content.hasBar()) {
            drawBar(ctx, layout.barOffsetX(), layout.barOffsetY(), layout.barLength(),
                    content.displayRatio(), severity);
        }
        if (!layout.hasText()) {
            return;
        }
        // 标签与字宽都由 CellContent 缓存：数字没变的帧既不拼字符串、也不量字形宽度。
        // VALUE 模式是两行（剩余 / 上限），两行在同一次 label(...) 里一起刷好，行距一行高。
        String label = content.label(cfg, mc.textRenderer);
        String second = content.secondLabel();
        // 槽位高度固定为两行：只有一行时贴着它那一侧（VALUE 是两行，第一行顶格）——
        // 切显示模式时格子不动，数字自己在槽位里换位置。valueOffsetY 是可调的纵向微调，
        // 两行一起挪，仍旧不改槽位与格子。
        int dy = layout.valueOffsetY();
        int firstLineY = layout.textOffsetY() + (second.isEmpty() ? layout.singleLineOffset() : 0) + dy;
        if (!label.isEmpty()) {
            drawOutlinedText(ctx, mc.textRenderer, label,
                    layout.textOffsetX(content.labelWidth()), firstLineY, severity.textColor);
        }
        if (!second.isEmpty()) {
            drawOutlinedText(ctx, mc.textRenderer, second,
                    layout.textOffsetX(content.secondLabelWidth()),
                    layout.textOffsetY() + Metrics.TEXT_H + dy, severity.textColor);
        }
    }

    /**
     * 让接下来的绘制带上不透明度。{@code alpha >= 1} 时什么都不做 —— 绝大多数帧都是这种情况，
     * 于是批处理行为与不开动画时完全一致，不额外增加一次 flush。
     *
     * <p>设色之前先 {@code ctx.draw()}：已经排队的物品/文字必须用<b>上一个</b>颜色刷出去，
     * 否则它们会被染成这一格的颜色。</p>
     */
    private static void pushAlpha(DrawContext ctx, float alpha) {
        if (alpha >= 1.0F) {
            return;
        }
        ctx.draw();
        ctx.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
    }

    /** 复位不透明度。复位之前再刷一次：本格排队的物品/文字要在颜色还生效时落到屏幕上。 */
    private static void popAlpha(DrawContext ctx, float alpha) {
        if (alpha >= 1.0F) {
            return;
        }
        ctx.draw();
        ctx.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 与原版经验条等级数字完全同一套画法：{@code InGameHud} 也是把同一个字符串画 5 次 ——
     * 上 / 下 / 左 / 右各偏移 1px 画黑色字身，最后在原位叠一层彩色字身，5 次都用
     * {@code shadow = false}（javap 实测，见 README 9.13）。这样数字自带一圈 1px 硬描边，
     * 压在耐久条或物品图标上也看得清，不会有投影那种糊边。
     *
     * <p>文字仍然交给 {@link TextRenderer} 去画，本类不自己拼字形，所以字体资源包
     * （{@code assets/&lt;namespace&gt;/font/*.json} 与字体贴图）照常覆盖它，和原版等级数字一样。</p>
     */
    private static void drawOutlinedText(DrawContext ctx, TextRenderer font, String text,
                                         int x, int y, int color) {
        ctx.drawText(font, text, x + 1, y, 0, false);
        ctx.drawText(font, text, x - 1, y, 0, false);
        ctx.drawText(font, text, x, y + 1, 0, false);
        ctx.drawText(font, text, x, y - 1, 0, false);
        ctx.drawText(font, text, x, y, color, false);
    }

    /** 先画底条再画进度条：原版 BOSS 血条就是「凹槽 + 进度」两层。 */
    private static void drawBar(DrawContext ctx, int x, int y, int w, float ratio, Severity severity) {
        int h = Metrics.BAR_H;
        int filled = Math.max(0, Math.min(w, Math.round(w * ratio)));
        Atlas.bossBar(ctx, x, y, w, h, severity.barColorIndex, false);
        Atlas.bossBar(ctx, x, y, filled, h, severity.barColorIndex, true);
    }

    /** 编辑模式下的虚线外框；fill() 会关掉混合，所以最后把混合恢复回去。 */
    private static void drawEditBorder(DrawContext ctx, int width, int height) {
        ctx.fill(-1, -1, width + 1, 0, EDIT_BORDER);
        ctx.fill(-1, height, width + 1, height + 1, EDIT_BORDER);
        ctx.fill(-1, 0, 0, height, EDIT_BORDER);
        ctx.fill(width, 0, width + 1, height, EDIT_BORDER);
        Atlas.beginTranslucent();
    }
}
