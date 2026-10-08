package dev.durahud.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.durahud.config.DuraHudConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * 原版 GUI 图集的绘制入口。所有 UV 与尺寸都写在 {@link Metrics} 里，这里只负责画。
 * 任何替换这些图集的资源包（例如 Cozy UI）都会自动覆盖本 HUD 的外观。
 *
 * <pre>
 * widgets.png  (24,23,22,22)   副手槽位【仅可见部分】  -> 每个装备自己的格子
 * widgets.png  ( 0,22,24,24)   选中格高亮              -> 危险高亮
 * bars.png     0..69, 步长10   7 组 BOSS 血条          -> 耐久条（非对称三段切片）
 * </pre>
 *
 * <p><b>关于格子尺寸</b>：副手槽位精灵名义上是 29x24，但实测其可见像素只有
 * x 0..21 / y 1..22，即一个 22x22 的框，右侧 7 列与上下各 1 行完全透明
 * （原版与 Cozy UI 一致）。这里直接按可见区域取样，图标与耐久条才能正确居中。</p>
 *
 * <p><b>关于条形端帽</b>：BOSS 血条条带不是左右对称的 —— 实测绿色进度条
 * 左端过渡 2px、右端过渡 4px。所以三段切片必须用非对称端帽，否则右端看起来会比左端硬。</p>
 */
public final class Atlas {

    public static final Identifier WIDGETS = new Identifier("textures/gui/widgets.png");
    public static final Identifier BARS = new Identifier("textures/gui/bars.png");

    private Atlas() {
    }

    /**
     * 一个格子 = 原版「副手槽位」精灵。资源包替换 widgets.png 就<b>直接</b>覆盖它。
     *
     * <p><b>画之前必须把混合打开</b>：{@code DrawContext.fill} 结束时会把混合关掉，
     * 于是「紧跟在某条耐久条后面的那个格子」会被画成不透明。这就是早期版本里
     * 「有的格子透明、有的格子实心、而且随装备变化」的原因。</p>
     */
    public static void cellFrame(DrawContext ctx, int x, int y) {
        beginTranslucent();
        ctx.drawTexture(WIDGETS, x, y, Metrics.CELL_U, Metrics.CELL_V, Metrics.CELL_W, Metrics.CELL_H);
    }

    /** fill() 与物品渲染会关掉或改掉混合，画任何带透明度的贴图前先把它复位。 */
    public static void beginTranslucent() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    public static void selectionBox(DrawContext ctx, int x, int y) {
        beginTranslucent();
        ctx.drawTexture(WIDGETS, x, y, Metrics.SELECT_U, Metrics.SELECT_V, Metrics.SELECT_W, Metrics.SELECT_H);
    }

    /** 从 5px 高的条带里取垂直居中的 h 行，避免画细条时采到上下描边。 */
    private static int centeredV(int baseV, int h) {
        return baseV + Math.max(0, (Metrics.BAR_H - h) / 2);
    }

    /** 非对称三段切片：左端帽 + 中段 + 右端帽。原图中间是纯平段，取子区域等效于拉伸。 */
    private static void drawSliced(DrawContext ctx, Identifier texture, int x, int y,
                                   int w, int h, int u, int v, int fullW) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int cl = Math.min(Metrics.CAP_LEFT, w / 2);
        int cr = Math.min(Metrics.CAP_RIGHT, w - cl);
        int mid = w - cl - cr;
        if (mid <= 0) {
            ctx.drawTexture(texture, x, y, u, v, w, h);
            return;
        }
        ctx.drawTexture(texture, x, y, u, v, cl, h);
        ctx.drawTexture(texture, x + cl, y, u + cl, v, mid, h);
        ctx.drawTexture(texture, x + cl + mid, y, u + fullW - cr, v, cr, h);
    }

    /** @param colorIndex bars.png 的色号 0..6  @param progress true 取「有进度」的那 5px 行 */
    public static void bossBar(DrawContext ctx, int x, int y, int w, int h, int colorIndex, boolean progress) {
        beginTranslucent();
        int base = colorIndex * Metrics.BAR_STEP + (progress ? Metrics.BAR_H : 0);
        drawSliced(ctx, BARS, x, y, w, h, 0, centeredV(base, h), Metrics.BAR_FULL_W);
    }

    // ------------------------------------------------------------------
    // 文字预留宽度
    // ------------------------------------------------------------------

    private static TextRenderer cachedFont;
    private static int cachedWidth;

    /**
     * 数值槽位的预留宽度：取 {@link Metrics#TEXT_SAMPLES} 里最宽样本的宽度。
     *
     * <p>它<b>只是字体</b>的函数，与当前是 VALUE、PERCENT 还是关掉无关——预留宽度一旦跟着
     * 显示模式变，切一次模式就会改变格子的横向步距，把整排格子推走。显示模式只决定槽位里画
     * 几个字、画在哪，不决定槽位本身有多大（见 README 10 节）。</p>
     *
     * <p>这个方法是几何计算的输入（{@link HudLayout#geometry}），因此它必须留在需要
     * Minecraft 字体类的这一侧，让 HudLayout 保持纯净。</p>
     */
    public static int reserveTextWidth(TextRenderer font) {
        if (font == null) {
            return 0;
        }
        if (font == cachedFont) {
            return cachedWidth;
        }
        int widest = 0;
        for (String sample : Metrics.TEXT_SAMPLES) {
            widest = Math.max(widest, font.getWidth(sample));
        }
        cachedFont = font;
        cachedWidth = Math.min(Metrics.TEXT_MAX_W, widest);
        return cachedWidth;
    }
}
