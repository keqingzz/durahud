package dev.durahud.hud;

import dev.durahud.config.DuraHudConfig;
import dev.durahud.config.DuraHudConfig.BarSide;
import dev.durahud.config.DuraHudConfig.Orientation;
import dev.durahud.config.DuraHudConfig.ValuePlacement;

/**
 * 一帧的几何布局：把「算位置」与「画东西」彻底分开。
 *
 * <p><b>这个类不引用任何 Minecraft 类型</b>：窗口尺寸与文字宽度都由调用方传进来。
 * 好处是几何可以脱离游戏运行 —— 单元测试与离线预览工具（tools/preview.mjs）
 * 都直接调 {@link #geometry} 算坐标，不需要启动客户端。</p>
 *
 * <p>纯算术，不碰 DrawContext。整份布局填充到<b>单例</b>上，所以每帧零分配
 * （旧实现每帧要 new 一个 int[2] 来返回原点）。编辑界面用 {@link #last()} 读上一帧
 * 的位置做命中测试。</p>
 *
 * <p>屏幕坐标 = origin + 局部坐标 × scale，渲染时只需 push/translate/scale，
 * 之后全部按局部坐标绘制。</p>
 */
public final class HudLayout {

    private static final HudLayout INSTANCE = new HudLayout();

    private boolean valid;
    private int cells;
    private boolean vertical;
    private int barLength;
    private int textWidth;
    private BarSide barSide = BarSide.BELOW;
    private BarSide textSide;
    private int pitchX;
    private int pitchY;
    private int padLeft;
    private int padTop;
    private int localW;
    private int localH;
    private int screenW;
    private int screenH;
    private float scale = 1.0F;
    private int originX;
    private int originY;
    private int barOffsetX;
    private int barOffsetY;
    private int textOffsetY;
    private int textSkip;

    private HudLayout() {
    }

    /**
     * 算一帧的几何。
     *
     * @param cellCount 这一帧有几个格子（见 {@link HudEntries#count()}）
     * @param viewW     缩放后的窗口宽度（{@code mc.getWindow().getScaledWidth()}）
     * @param viewH     缩放后的窗口高度
     * @param textWidth 为文字预留的宽度；0 表示不显示文字（见 {@link Atlas#reserveTextWidth}）
     */
    public static HudLayout geometry(DuraHudConfig cfg, int cellCount, int viewW, int viewH, int textWidth) {
        INSTANCE.rebuild(cfg, cellCount, viewW, viewH, textWidth);
        INSTANCE.valid = true;
        return INSTANCE;
    }

    /** 上一帧的布局；从未绘制过时为 null。 */
    public static HudLayout last() {
        return INSTANCE.valid ? INSTANCE : null;
    }

    private void rebuild(DuraHudConfig cfg, int cellCount, int viewW, int viewH, int reservedText) {
        cells = Math.max(1, cellCount);
        vertical = cfg.orientation == Orientation.VERTICAL;
        barSide = cfg.barPlacement;
        // 防御性夹取：sanitize() 只在读盘 / 导入 / 重置时跑，直接改字段的路径（滑条、测试）也要安全。
        scale = Float.isFinite(cfg.scale) && cfg.scale > 0.0F
                ? Math.max(0.25F, Math.min(4.0F, cfg.scale))
                : 1.0F;
        barLength = resolveBarLength(cfg);
        textWidth = Math.max(0, reservedText);
        textSide = textWidth > 0
                ? (cfg.valuePlacement == ValuePlacement.AUTO ? opposite(barSide) : toSide(cfg.valuePlacement))
                : null;

        // 各方向的「附着深度」：同侧时先放条、再放文本，顺次排开
        int above = 0;
        int below = 0;
        int left = 0;
        int right = 0;
        switch (barSide) {
            case ABOVE -> above += Metrics.BAR_GAP + Metrics.BAR_H;
            case BELOW -> below += Metrics.BAR_GAP + Metrics.BAR_H;
            case LEFT -> left += Metrics.BAR_GAP + barLength;
            case RIGHT -> right += Metrics.BAR_GAP + barLength;
        }
        if (textSide != null) {
            switch (textSide) {
                case ABOVE -> above += Metrics.TEXT_GAP + Metrics.TEXT_H;
                case BELOW -> below += Metrics.TEXT_GAP + Metrics.TEXT_H;
                case LEFT -> left += Metrics.TEXT_GAP + textWidth;
                case RIGHT -> right += Metrics.TEXT_GAP + textWidth;
            }
        }

        // 吸附在上下方的条/数值比格子宽时会向左右溢出，间距必须留出这段
        int overhang = 0;
        if (barSide == BarSide.ABOVE || barSide == BarSide.BELOW) {
            overhang = Math.max(overhang, (barLength - Metrics.CELL_W + 1) / 2);
        }
        if (textSide == BarSide.ABOVE || textSide == BarSide.BELOW) {
            overhang = Math.max(overhang, (textWidth - Metrics.CELL_W + 1) / 2);
        }
        overhang = Math.max(0, overhang);

        pitchX = Metrics.CELL_W + left + right + Metrics.CELL_GAP + overhang * 2;
        pitchY = Metrics.CELL_H + above + below + Metrics.CELL_GAP;
        padLeft = Math.max(left, overhang);
        padTop = above;

        int spanX = vertical ? Metrics.CELL_W : (cells - 1) * pitchX + Metrics.CELL_W;
        int spanY = vertical ? (cells - 1) * pitchY + Metrics.CELL_H : Metrics.CELL_H;
        localW = padLeft + spanX + Math.max(right, overhang);
        localH = padTop + spanY + below;
        screenW = Math.round(localW * scale);
        screenH = Math.round(localH * scale);

        computeOffsets();
        computeOrigin(cfg, viewW, viewH);
    }

    private void computeOffsets() {
        switch (barSide) {
            case ABOVE -> {
                barOffsetX = (Metrics.CELL_W - barLength) / 2;
                barOffsetY = -Metrics.BAR_GAP - Metrics.BAR_H;
            }
            case BELOW -> {
                barOffsetX = (Metrics.CELL_W - barLength) / 2;
                barOffsetY = Metrics.CELL_H + Metrics.BAR_GAP;
            }
            case LEFT -> {
                barOffsetX = -Metrics.BAR_GAP - barLength;
                barOffsetY = (Metrics.CELL_H - Metrics.BAR_H) / 2;
            }
            default -> {
                barOffsetX = Metrics.CELL_W + Metrics.BAR_GAP;
                barOffsetY = (Metrics.CELL_H - Metrics.BAR_H) / 2;
            }
        }
        boolean sameSide = textSide != null && textSide == barSide;
        textSkip = sameSide
                ? (textSide == BarSide.LEFT || textSide == BarSide.RIGHT
                        ? Metrics.BAR_GAP + barLength
                        : Metrics.BAR_GAP + Metrics.BAR_H)
                : 0;
        if (textSide == null) {
            textOffsetY = 0;
            return;
        }
        switch (textSide) {
            case ABOVE -> textOffsetY = -textSkip - Metrics.TEXT_GAP - Metrics.TEXT_H;
            case BELOW -> textOffsetY = Metrics.CELL_H + textSkip + Metrics.TEXT_GAP;
            default -> textOffsetY = (Metrics.CELL_H - Metrics.TEXT_H + 1) / 2;
        }
    }

    private void computeOrigin(DuraHudConfig cfg, int sw, int sh) {
        int x;
        int y;
        switch (cfg.anchor) {
            case HOTBAR_LEFT -> {
                x = sw / 2 - 91 - 4 - screenW;
                y = sh - 22;
            }
            case HOTBAR_RIGHT -> {
                x = sw / 2 + 91 + 4;
                y = sh - 22;
            }
            case TOP_LEFT -> {
                x = Metrics.SCREEN_MARGIN;
                y = Metrics.SCREEN_MARGIN;
            }
            case TOP_RIGHT -> {
                x = sw - Metrics.SCREEN_MARGIN - screenW;
                y = Metrics.SCREEN_MARGIN;
            }
            case BOTTOM_LEFT -> {
                x = Metrics.SCREEN_MARGIN;
                y = sh - Metrics.SCREEN_MARGIN - screenH;
            }
            case BOTTOM_RIGHT -> {
                x = sw - Metrics.SCREEN_MARGIN - screenW;
                y = sh - Metrics.SCREEN_MARGIN - screenH;
            }
            case CUSTOM -> {
                // 自定义位置：锚点就是屏幕原点，「偏移量」本身就是绝对坐标 —— 下面统一再加一次
                // 偏移就得到该坐标（这里不能再写一遍 offset，否则加两次，HUD 会跑到两倍远）。
                x = 0;
                y = 0;
            }
            default -> {
                x = sw / 2 - screenW / 2;
                y = sh - 42 - screenH;
            }
        }
        // 收尾：把 HUD 拉回屏幕内。偏移量必须和锚点位置<b>一起</b>夹取 —— 分开夹的话，
        // 一个很大的 offsetX（手改配置、换过分辨率）照样能把 HUD 推到屏幕外，而且编辑框
        // 也画在屏外，连 H 编辑界面都救不回来。
        // 这一步对锚点也是必要的：HOTBAR_LEFT / HOTBAR_RIGHT 把 HUD 的顶边对齐热键栏顶边
        // （y = sh - 22），只要 HUD 比 22 高，底边就会掉到屏幕外 —— 默认「条在格子下方」
        // 的高度就是 29。装得下时保证完整可见，装不下时对齐到左上角，绝不整块落到屏幕外。
        originX = clampOnScreen(x + cfg.offsetX, screenW, sw);
        originY = clampOnScreen(y + cfg.offsetY, screenH, sh);
    }

    /** 让一段长 size 的内容尽量完整地落在 [margin, limit - margin] 内。 */
    private static int clampOnScreen(int value, int size, int limit) {
        int margin = Metrics.SCREEN_MARGIN;
        if (size + margin * 2 > limit) {
            // 连两条边距都放不下：宁可不要边距，也要让右 / 下边不出屏（limit - size 可能小于 margin，
            // 甚至为负 —— 那时候只能从 0 开始画，超出的部分本来就没救）。
            return Math.max(0, Math.min(margin, limit - size));
        }
        return Math.max(margin, Math.min(value, limit - margin - size));
    }

    // ------------------------------------------------------------------
    // 只读访问器
    // ------------------------------------------------------------------

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }

    public float scale() {
        return scale;
    }

    public int localW() {
        return localW;
    }

    public int localH() {
        return localH;
    }

    public int screenW() {
        return screenW;
    }

    public int screenH() {
        return screenH;
    }

    public int cellLocalX(int index) {
        return padLeft + (vertical ? 0 : index * pitchX);
    }

    public int cellLocalY(int index) {
        return padTop + (vertical ? index * pitchY : 0);
    }

    public int barLength() {
        return barLength;
    }

    public int barOffsetX() {
        return barOffsetX;
    }

    public int barOffsetY() {
        return barOffsetY;
    }

    public boolean hasText() {
        return textSide != null;
    }

    public int textOffsetY() {
        return textOffsetY;
    }

    /** 数值相对格子原点的横向偏移；LEFT 用<b>预留宽度</b>而不是实际字宽，数字长短不会让文字抖动。 */
    public int textOffsetX(int labelWidth) {
        if (textSide == null) {
            return 0;
        }
        return switch (textSide) {
            case ABOVE, BELOW -> (Metrics.CELL_W - labelWidth) / 2;
            case LEFT -> -textSkip - Metrics.TEXT_GAP - textWidth;
            default -> Metrics.CELL_W + textSkip + Metrics.TEXT_GAP;
        };
    }

    /** 光标是否落在 HUD 上（H 编辑界面用它判断「点住的是不是格子」）。 */
    public boolean containsScreen(double mx, double my) {
        // 半像素容差：screenW / screenH 是取整后的整数，而真正画出来的是浮点变换，
        // 边界上最多差 1 像素 —— 正好点在最右一列边缘时不该判成「没点到」。
        return screenW > 0 && screenH > 0
                && mx >= originX - 0.5 && mx < originX + screenW + 0.5
                && my >= originY - 0.5 && my < originY + screenH + 0.5;
    }

    // ------------------------------------------------------------------
    // 供单元测试与离线预览使用的内部量
    // ------------------------------------------------------------------

    int cells() {
        return cells;
    }

    boolean vertical() {
        return vertical;
    }

    int pitchX() {
        return pitchX;
    }

    int pitchY() {
        return pitchY;
    }

    int textWidth() {
        return textWidth;
    }

    int textSkip() {
        return textSkip;
    }

    int padLeft() {
        return padLeft;
    }

    int padTop() {
        return padTop;
    }

    BarSide barSide() {
        return barSide;
    }

    BarSide textSide() {
        return textSide;
    }

    // ------------------------------------------------------------------
    // 计算
    // ------------------------------------------------------------------

    static int resolveBarLength(DuraHudConfig cfg) {
        boolean horizontal = cfg.barPlacement == BarSide.ABOVE || cfg.barPlacement == BarSide.BELOW;
        int raw = cfg.barLength > 0 ? cfg.barLength : (horizontal ? Metrics.CELL_W : Metrics.AUTO_SIDE_BAR);
        return Math.max(Metrics.BAR_MIN, Math.min(Metrics.BAR_FULL_W, raw));
    }

    static BarSide opposite(BarSide side) {
        return switch (side) {
            case ABOVE -> BarSide.BELOW;
            case BELOW -> BarSide.ABOVE;
            case LEFT -> BarSide.RIGHT;
            case RIGHT -> BarSide.LEFT;
        };
    }

    static BarSide toSide(ValuePlacement placement) {
        return switch (placement) {
            case ABOVE -> BarSide.ABOVE;
            case BELOW -> BarSide.BELOW;
            case LEFT -> BarSide.LEFT;
            default -> BarSide.RIGHT;
        };
    }
}
