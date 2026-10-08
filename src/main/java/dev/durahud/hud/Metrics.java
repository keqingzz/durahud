package dev.durahud.hud;

/**
 * DuraHUD 用到的全部数字常量。
 *
 * <p>这个类刻意<b>不引用任何 Minecraft 类</b>：几何计算（{@link HudLayout}）、单元测试和离线预览工具
 * 都只需要这里的数字，不需要游戏运行时。数值全部来自原版 {@code textures/gui/widgets.png} 与
 * {@code textures/gui/bars.png} 的像素布局（见 README「视觉实现」一节），改动前请先确认实测结论。</p>
 */
public final class Metrics {

    private Metrics() {
    }

    // ------------------------------------------------------------------
    // widgets.png：格子与高亮
    // ------------------------------------------------------------------

    /** 单个格子的取样起点：原版「副手槽位」精灵的可见区域（22x22）从 (24,23) 开始。 */
    public static final int CELL_U = 24;
    public static final int CELL_V = 23;
    public static final int CELL_W = 22;
    public static final int CELL_H = 22;

    /** 物品图标 16x16，在 22x22 的格子里居中（左右各 3px，上下各 3px）。 */
    public static final int ICON_SIZE = 16;
    public static final int ICON_X = (CELL_W - ICON_SIZE) / 2;
    public static final int ICON_Y = (CELL_H - ICON_SIZE) / 2;

    /** 危险高亮框 = 原版「选中格」精灵，比格子大一圈。 */
    public static final int SELECT_U = 0;
    public static final int SELECT_V = 22;
    public static final int SELECT_W = 24;
    public static final int SELECT_H = 24;

    // ------------------------------------------------------------------
    // bars.png：耐久条
    // ------------------------------------------------------------------

    /** 7 组 BOSS 血条纵向排列，每组 10px（底 5px + 进度 5px）。 */
    public static final int BAR_STEP = 10;
    public static final int BAR_H = 5;
    public static final int BAR_FULL_W = 182;

    /** 条带端帽宽度：<b>左右不对称</b>（实测左端过渡 2px、右端过渡 4px），拉伸时两端保持原样。 */
    public static final int CAP_LEFT = 2;
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
