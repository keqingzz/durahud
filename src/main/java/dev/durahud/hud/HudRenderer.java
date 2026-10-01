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
        if (!cfg.enabled || mc.player == null || mc.options.hudHidden) {
            return;
        }
        float dtSeconds = frameSeconds();
        ENTRIES.refresh(mc, cfg, dtSeconds);
        if (ENTRIES.count() == 0 && !cfg.editMode) {
            ENTRIES.commitPlacement();
            return;
        }
        int textWidth = Atlas.reserveTextWidth(mc.textRenderer, cfg.valueDisplay);
        HudLayout layout = HudLayout.geometry(cfg, ENTRIES.count(),
                mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), textWidth);

        // 逐格矩阵：每格在自己的变换里画，位置由条目给出（含平滑移动的中间量）。
        // 自动补位时原点与格子序号会一起变，只有逐格算位置才能让每格从旧位置滑到新位置。
        float scale = layout.scale();
        for (int i = 0; i < ENTRIES.count(); i++) {
            DurabilityEntry entry = ENTRIES.get(i);
            entry.slideTo(layout.originX() + layout.cellLocalX(i) * scale,
                    layout.originY() + layout.cellLocalY(i) * scale, dtSeconds, cfg);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(entry.drawX(), entry.drawY(), 0.0F);
            ctx.getMatrices().scale(scale, scale, 1.0F);
            drawEntry(ctx, mc, cfg, layout, entry);
            ctx.getMatrices().pop();
        }
        ENTRIES.commitPlacement();
        if (cfg.editMode) {
            // 外框围的是整块 HUD：整排一起滑完之后按当前几何画。
            ctx.getMatrices().push();
            ctx.getMatrices().translate((float) layout.originX(), (float) layout.originY(), 0.0F);
            ctx.getMatrices().scale(scale, scale, 1.0F);
            drawEditBorder(ctx, layout.localW(), layout.localH());
            ctx.getMatrices().pop();
        }
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
            // 盔甲格恒定实心：空盔甲格与「刚戴上头盔」之间不该闪一下边框。
            float frameAlpha = entry.armor() ? 1.0F : alpha;
            pushAlpha(ctx, frameAlpha);
            Atlas.cellFrame(ctx, 0, 0);
            popAlpha(ctx, frameAlpha);
        }
        if (!visible) {
            return;
        }
        pushAlpha(ctx, alpha);
        drawCellContent(ctx, mc, cfg, layout, entry.content());
        popAlpha(ctx, alpha);
    }

    /**
     * 一格的内容：图标 / 耐久条 / 选中框 / 数值。不透明度由调用者包在外面 ——
     * 早退之前一定要先 popAlpha，否则这一格的半透明会漏给后面所有格子。
     */
    private static void drawCellContent(DrawContext ctx, MinecraftClient mc, DuraHudConfig cfg,
                                        HudLayout layout, CellContent content) {
        if (cfg.showItemIcons) {
            ctx.drawItem(content.stack(), Metrics.ICON_X, Metrics.ICON_Y);
        }
        Severity severity = Severity.of(cfg, content.ratio());
        if (content.hasBar()) {
            drawBar(ctx, layout.barOffsetX(), layout.barOffsetY(),
                    layout.barLength(), content.ratio(), severity);
            if (cfg.highlightCritical && severity == Severity.CRIT) {
                Atlas.selectionBox(ctx, (Metrics.CELL_W - Metrics.SELECT_W) / 2,
                        (Metrics.CELL_H - Metrics.SELECT_H) / 2);
            }
        }
        if (!layout.hasText()) {
            return;
        }
        String label = label(cfg, content);
        if (!label.isEmpty()) {
            drawOutlinedText(ctx, mc.textRenderer, label,
                    layout.textOffsetX(mc.textRenderer.getWidth(label)),
                    layout.textOffsetY(), severity.textColor);
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

    /**
     * 文字内容取自内容快照：淡出期间显示的仍是内容消失前的那个百分比。
     *
     * <p>{@code VALUE} 优先显示「剩余/上限」；没有原版数值的物品（自定义耐久条）退化成百分比 ——
     * 这与旧版「showValue 优先、否则看 showPercent」的取值顺序一致。</p>
     */
    private static String label(DuraHudConfig cfg, CellContent content) {
        return switch (cfg.valueDisplay) {
            case VALUE -> content.hasNumeric()
                    ? content.remaining() + "/" + content.max()
                    : percent(content);
            case PERCENT -> percent(content);
            default -> "";
        };
    }

    private static String percent(CellContent content) {
        return content.hasBar() ? Math.round(content.ratio() * 100.0F) + "%" : "";
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
