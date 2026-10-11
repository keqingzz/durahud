package dev.durahud.hud;

/**
 * DuraHUD 用到的全部数字常量。
 *
 * <p>这个类刻意<b>不引用任何 Minecraft 类</b>：几何计算（{@link HudLayout}）、单元测试和离线预览工具
 * 都只需要这里的数字，不需要游戏运行时。数值全部来自原版 {@code textures/gui/widgets.png} 与
 * {@code textures/gui/bars.png} 的像素布局（见 README「视觉实现」一节），改动前请先确认实测结论。</p>
 *
 * <p>这里的数字一律是<b>缩放前的逻辑像素</b>：{@code cfg.scale} 之后才变成屏幕像素，换算只发生在
 * {@link HudLayout} 与各绘制调用里。单位混用是改这个工程最容易出的错（见 README 8.6）。</p>
 */
public final class Metrics {

    private Metrics() {
    }

    // ------------------------------------------------------------------
    // widgets.png：格子与高亮
    // ------------------------------------------------------------------

    /** 单个格子的取样起点 u：原版「副手槽位」精灵的可见区域（22x22）从 (24,23) 开始。 */
    public static final int CELL_U = 24;
    /** 取样起点 v，与 {@link #CELL_U} 一起定位可见区域的左上角。 */
    public static final int CELL_V = 23;
    /** 格子宽。名义精灵是 29x24，只有这块可见区域参与取样，见 {@link Atlas}。 */
    public static final int CELL_W = 22;
    /** 格子高，与 {@link #CELL_W} 相同（正方形）。 */
    public static final int CELL_H = 22;

    /** 物品图标 16x16，在 22x22 的格子里居中（左右各 3px，上下各 3px）。 */
    public static final int ICON_SIZE = 16;
    /** 图标的横向落点，由 {@link #CELL_W} 与 {@link #ICON_SIZE} 推出：不要再手写这个数字。 */
    public static final int ICON_X = (CELL_W - ICON_SIZE) / 2;
    /** 图标的纵向落点，同上。 */
    public static final int ICON_Y = (CELL_H - ICON_SIZE) / 2;

    /** 危险高亮框 = 原版「选中格」精灵，比格子大一圈；取样起点 u。 */
    public static final int SELECT_U = 0;
    /** 高亮框的取样起点 v（widgets.png 第二行的精灵）。 */
    public static final int SELECT_V = 22;
    /** 高亮框宽：24 > {@link #CELL_W}，所以画它时要从格子左上角向左上各让 1px。 */
    public static final int SELECT_W = 24;
    /** 高亮框高，与 {@link #SELECT_W} 相同。 */
    public static final int SELECT_H = 24;

    // ------------------------------------------------------------------
    // bars.png：耐久条
    // ------------------------------------------------------------------

    /** 7 组 BOSS 血条纵向排列，每组 10px（底 5px + 进度 5px）：色号 n 的起点是 {@code n * BAR_STEP}。 */
    public static final int BAR_STEP = 10;
    /** 单条高 5px：「底」与「进度」两层都是这个高度，画细条时靠垂直居中取样躲开描边。 */
    public static final int BAR_H = 5;
    /** 血条贴图的总宽，切片时用来定位右端帽（{@code u + BAR_FULL_W - CAP_RIGHT}）。 */
    public static final int BAR_FULL_W = 182;

    /** 左端帽宽度：实测绿色进度条左端只有 2px 过渡，拉伸时两端保持原样。 */
    public static final int CAP_LEFT = 2;
    /** 右端帽宽度 4px：<b>与左端不对称</b>，这正是必须用非对称切片的原因，见 {@link Atlas#bossBar}。 */
    public static final int CAP_RIGHT = 4;

    /** 耐久条用到的三种色号（bars.png 第一行的颜色索引）；其余色号没有使用者，不在这里留死常量。 */
    public static final int BAR_RED = 2;
    public static final int BAR_GREEN = 3;
    public static final int BAR_YELLOW = 4;

    // ------------------------------------------------------------------
    // 布局
    // ------------------------------------------------------------------

    /** 横向/纵向排列时两个格子之间的空隙。 */
    public static final int CELL_GAP = 3;
    /** 格子与耐久条之间的空隙。 */
    public static final int BAR_GAP = 2;
    /** 格子与文字之间的空隙。 */
    public static final int TEXT_GAP = 1;
    /** 原版字体一行的高度，用于给文字预留空间。 */
    public static final int TEXT_H = 9;
    /** 单条文字的最大预留宽度，防止超长文本把格子推得到处都是。 */
    public static final int TEXT_MAX_W = 64;
    /**
     * 数值槽位的宽度样本：VALUE 模式是四位数，PERCENT 模式是 "100%"。
     *
     * <p>预留宽度取所有样本里<b>最宽</b>的那一个，与当前显示模式无关——跟着模式变的预留宽度
     * 会改变格子间距、把整排格子推走（见 README 10 节）。</p>
     */
    public static final String[] TEXT_SAMPLES = {"9999", "100%"};
    /** 纵向排列时耐久条的默认长度（横向时用格子宽度）。 */
    public static final int AUTO_SIDE_BAR = 60;
    /** 耐久条的最小长度，再短就只剩两个端帽了。 */
    public static final int BAR_MIN = 8;
    /** 贴边锚点与屏幕边缘的距离，同时也是把 HUD 拉回屏幕内时的最小留白。 */
    public static final int SCREEN_MARGIN = 4;
}
