package dev.durahud.hud;

import dev.durahud.config.DuraHudConfig;
import dev.durahud.config.DuraHudConfig.Anchor;
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
    private int textBlockH = Metrics.TEXT_H;
    private int valueOffsetY;

    private HudLayout() {
    }

    /**
     * 算一帧的几何。
     *
     * @param cellCount 这一帧有几个格子（见 {@link HudEntries#count()}）
     * @param viewW     缩放后的窗口宽度（{@code mc.getWindow().getScaledWidth()}）
     * @param viewH     缩放后的窗口高度
     * @param textWidth 为数值槽位预留的宽度；0 表示没有槽位（见 {@link Atlas#reserveTextWidth}）。
     *                  它只由字体决定，与显示模式无关；槽位的<b>高度</b>也不用传，固定两行
     *                  （见 {@link #textHeight()}）
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
        // ① 输入：格子数、方向、缩放、条长、预留文本宽度。每一项都先夹到安全范围再参与运算 ——
        // 调用方里有直接改配置字段的路径（编辑界面的滑条、单测），不能假设已经 sanitize 过。
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
        // 槽位高度也是常量：永远按最宽的情形（VALUE 的「剩余」+「上限」两行）留。高度要是
        // 跟着显示模式变，贴着上下边的数值就会把整排格子推上推下——与「预留宽度是常量」
        // 同一条纪律。
        textBlockH = Metrics.TEXT_H * 2;
        // 数值的纵向微调：只挪数字，槽位、格子和整块尺寸都不参与。sanitize() 之外的路径
        // （拖动中的滑条、直接改字段的测试）也要安全，所以这里再夹一次。
        valueOffsetY = Math.max(-DuraHudConfig.VALUE_OFFSET_LIMIT,
                Math.min(DuraHudConfig.VALUE_OFFSET_LIMIT, cfg.valueOffsetY));

        // ② 四个方向的「附着深度」：同侧时先放条、再放文本，顺次排开（LEFT/RIGHT 的深度含条长）。
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
                case ABOVE -> above += Metrics.TEXT_GAP + textBlockH;
                case BELOW -> below += Metrics.TEXT_GAP + textBlockH;
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

        // ③ 格子步距与整块尺寸：步距 = 格子 + 两侧深度 + 格间距 + 溢出余量；局部尺寸再由它推出，
        // 屏幕尺寸只是局部尺寸乘上缩放后取整（此后所有夹取都在屏幕坐标里做）。
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

        // ④ 条与数值相对各自格子的偏移，最后把整块摆到屏幕上（只有这一步用到窗口尺寸）。
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
            case ABOVE -> textOffsetY = -textSkip - Metrics.TEXT_GAP - textBlockH;
            case BELOW -> textOffsetY = Metrics.CELL_H + textSkip + Metrics.TEXT_GAP;
            default -> textOffsetY = (Metrics.CELL_H - textBlockH + 1) / 2;
        }
    }

    /** 把整块 HUD 摆到屏幕上：先按锚点算基准位置，再做两段式夹取。 */
    private void computeOrigin(DuraHudConfig cfg, int sw, int sh) {
        // ① 锚点基准坐标（不含用户偏移）：整块的「意图位置」。
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
        // ② 两段式夹取，把 HUD 拉回屏幕内。偏移量必须和锚点位置【一起】夹 —— 分开夹的话，
        // 一个很大的 offsetX（手改配置、换过分辨率）照样能把 HUD 推到屏幕外，而且编辑框也
        // 画在屏外，连 H 编辑界面都救不回来。
        // 这一步对锚点本身也是必要的：HOTBAR_LEFT / HOTBAR_RIGHT 把 HUD 的顶边对齐热键栏顶边
        // （y = sh - 22），只要 HUD 比 22 高，它的底边就会掉到屏幕外 —— 默认「条与数值都在
        // 上方」的高度就是 48。
        //
        //   第一段只夹锚点自己（allow = 0）：锚点表达的是「贴边 / 居中 / 对齐热键栏」这种意图，
        //   装得下时保证完整可见，装不下时对齐到左上角（HOTBAR_LEFT 就是靠这一步把比热键栏
        //   更高的整块顶上来）。
        //   第二段才加上用户的偏移量，并且允许每边最多露出半个格子 —— 用户想把 HUD 稍微推过
        //   屏幕边缘是可以的（见 README 6.6），但再往外就拦住，否则编辑框跑到屏外救不回来。
        boolean custom = cfg.anchor == Anchor.CUSTOM;
        int baseX = custom ? 0 : clampOnScreen(x, screenW, sw, 0);
        int baseY = custom ? 0 : clampOnScreen(y, screenH, sh, 0);
        // 整块比窗口还大时不再给余量：这时「从屏幕内开始画」比「两面各切一点」更有用，
        // 也让装不下的轴保持 origin >= 0（HudLayoutTest 的不变量之一）。
        int allowX = screenW <= sw ? halfCell(Metrics.CELL_W) : 0;
        int allowY = screenH <= sh ? halfCell(Metrics.CELL_H) : 0;
        originX = clampOnScreen(baseX + cfg.offsetX, screenW, sw, allowX);
        originY = clampOnScreen(baseY + cfg.offsetY, screenH, sh, allowY);
    }

    /** 允许露在屏幕外的像素数：半个格子（按当前缩放换算）。 */
    private int halfCell(int cellSize) {
        return Math.round(cellSize * scale / 2.0F);
    }

    /**
     * 把一段长 size 的内容夹到屏幕附近：每边最多允许 {@code allow} 像素露在外面。
     *
     * <p>余量还会再被 {@code size / 2} 夹一次：块本身比两个余量还窄时（理论上只可能出现在
     * 单元测试的畸形输入里），至少保证一半还在屏幕内，不会整块飘出去。</p>
     *
     * <p>连「内容 + 余量」都放不进这个小窗口时退回 [0, limit - size]：从屏幕内开始画，
     * 超出的部分留在屏外 —— 这时候任何夹取都救不了，但至少能让人看见、点得到。</p>
     */
    private static int clampOnScreen(int value, int size, int limit, int allow) {
        int room = Math.max(0, Math.min(allow, size / 2));
        int low = -room;
        int high = limit - size + room;
        if (low > high) {
            low = 0;
            high = Math.max(0, limit - size);
        }
        return Math.max(low, Math.min(value, high));
    }

    // ------------------------------------------------------------------
    // 只读访问器
    // ------------------------------------------------------------------

    /** HUD 左上角的屏幕 X（已含锚点、用户偏移与屏幕内夹取；单位是缩放后的逻辑像素）。 */
    public int originX() {
        return originX;
    }

    /** 同 {@link #originX()}，纵向。 */
    public int originY() {
        return originY;
    }

    /** 缩放系数，已夹在 0.25..4；非有限值或 ≤ 0 一律按 1（见 {@link #rebuild} 的防御性夹取）。 */
    public float scale() {
        return scale;
    }

    /** 整块 HUD 的局部宽度（缩放前，含溢出余量）；对应屏幕宽度见 {@link #screenW()}。 */
    public int localW() {
        return localW;
    }

    /** 整块 HUD 的局部高度（缩放前）。 */
    public int localH() {
        return localH;
    }

    /** {@code localW * scale} 四舍五入后的屏幕宽度；夹取与 {@link #containsScreen} 都用它。 */
    public int screenW() {
        return screenW;
    }

    /** {@code localH * scale} 四舍五入后的屏幕高度。 */
    public int screenH() {
        return screenH;
    }

    /** 第 index 格左上角的局部 X：横向排列时按 {@link #pitchX()} 递增，纵向时所有格子相同。 */
    public int cellLocalX(int index) {
        return padLeft + (vertical ? 0 : index * pitchX);
    }

    /** 第 index 格左上角的局部 Y：纵向排列时按 {@link #pitchY()} 递增，横向时所有格子相同。 */
    public int cellLocalY(int index) {
        return padTop + (vertical ? index * pitchY : 0);
    }

    /** 耐久条长度（缩放前，由 {@link #resolveBarLength} 按配置解析并夹取）。 */
    public int barLength() {
        return barLength;
    }

    /** 条相对格子左上角的横向偏移；可为负（条比格子宽，或贴在格子左侧）。 */
    public int barOffsetX() {
        return barOffsetX;
    }

    /** 条相对格子左上角的纵向偏移；语义与单位同 {@link #barOffsetX()}。 */
    public int barOffsetY() {
        return barOffsetY;
    }

    /** 这一帧有没有数值槽位（预留宽度为 0 时为 false，几何里根本没有文本的位置）。 */
    public boolean hasText() {
        return textSide != null;
    }

    /** 数值槽位顶端相对格子左上角的局部 Y；第二行在它再加一个 {@link Metrics#TEXT_H} 的位置。 */
    public int textOffsetY() {
        return textOffsetY;
    }

    /**
     * 数值槽位的高度：永远是两行（VALUE 的「剩余」+「上限」）。
     *
     * <p>它是<b>常量</b>，也与显示模式无关：槽位高度按模式变，贴着上下边的数值就会把整排
     * 格子推上推下（与「预留宽度是常量」同一条纪律，见 README 10 节）。
     * 渲染侧按它决定第二行画在哪（{@code textOffsetY() + Metrics.TEXT_H}）。</p>
     */
    public int textHeight() {
        return textBlockH;
    }

    /**
     * 数值的纵向微调（缩放前的像素，正数向下）：渲染侧把它加到槽位里的行坐标上。
     *
     * <p>它<b>不进几何</b>：localW/localH、格子位置与整块尺寸都不变，槽位仍按两行预留 ——
     * 微调只是让数字在自己的槽位里挪一挪（见 README 7.4 / 10 节）。</p>
     */
    public int valueOffsetY() {
        return valueOffsetY;
    }

    /**
     * 只画一行时，这一行在<b>两行高</b>槽位里的纵向偏移：贴住自己那一侧（靠条/格子的一侧）。
     *
     * <p>槽位高度恒定是 2.1.3 定下的纪律，但那之后单行在槽位里居中，「百分比」的数字就比
     * 2.1.2 及以前离耐久条远了 4px。现在按数值所在的一侧贴边：ABOVE 贴槽位下沿（贴条）、
     * BELOW 贴上沿；左右两侧仍然纵向居中 —— 槽位偏移本来就是 (CELL_H - 18 + 1) / 2 = 2，
     * 加 5 得 7，与 2.1.2 的 (CELL_H - TEXT_H + 1) / 2 = 7 完全一致。</p>
     */
    public int singleLineOffset() {
        if (textSide == null) {
            return 0;
        }
        if (textSide == BarSide.ABOVE) {
            return textBlockH - Metrics.TEXT_H;
        }
        if (textSide == BarSide.BELOW) {
            return 0;
        }
        return (textBlockH - Metrics.TEXT_H + 1) / 2;
    }

    /**
     * 数值相对格子原点的横向偏移。
     *
     * <p>槽位宽度是固定的 {@link #textWidth()}，数字按自己的实际宽度在槽位里<b>居中</b>：
     * 左右两侧（LEFT / RIGHT）和上下两侧一样，都只在槽位内部挪，不会因为数字位数或显示模式
     * 变了就把格子推走——换模式时格子的位置应当一模一样。</p>
     */
    public int textOffsetX(int labelWidth) {
        if (textSide == null) {
            return 0;
        }
        return switch (textSide) {
            case ABOVE, BELOW -> (Metrics.CELL_W - labelWidth) / 2;
            case LEFT -> -textSkip - Metrics.TEXT_GAP - textWidth + (textWidth - labelWidth) / 2;
            default -> Metrics.CELL_W + textSkip + Metrics.TEXT_GAP + (textWidth - labelWidth) / 2;
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
