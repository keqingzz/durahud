package dev.durahud.config;

/**
 * 设置界面的布局计算：左侧竖排分页标签 + 选项列 + 底部按钮行，全部由当前窗口尺寸推出。
 *
 * <p>标签是一列竖排的按钮，标签列右边是选项块，两者都在「标题下沿到提示行上沿」这段高度里
 * 垂直居中，所以二级菜单不会挤在顶上，而是与左侧六个分页标签齐平。标签列 + 间隔 + 选项块
 * 作为一个整体在窗口里水平居中，所以标签总是落在屏幕中央偏左的位置。</p>
 *
 * <p>纯数学、零 Minecraft 引用，所以可以用单测覆盖「GUI 缩放拉到最大」这类极端尺寸
 * （见 {@code ScreenLayoutTest}）：只要窗口还进得去游戏，控件就必须留在屏幕里 —— 这正是
 * 旧版「只有界面尺寸 4 及以下才正常」那个问题的根因（控件行数固定、行高被压到 12 像素，
 * 底部按钮被挤出屏幕）。</p>
 *
 * <p>窗口小到排不下完整布局时（例如 100x60、40x30），策略是<b>逐级退让而不是溢出</b>：
 * 按钮行先贴住下沿，标题下沿与提示行上沿依次上移，标签高度 / 行高按「按钮行上方还剩多少」
 * 硬性夹取（必要时扁到 1 像素），最后标签列与选项块整体再夹进窗口 —— 任何尺寸下都有
 * {@code tabX()+tabW <= width}、{@code blockLeft+blockWidth <= width}、
 * {@code buttonX(3)+buttonW <= width}，且所有 y 都落在 {@code [0, height]} 里。</p>
 */
public final class ScreenLayout {

    public static final int MARGIN = 6;
    /** 顶部标题占的高度（原版在顶部居中画标题）。 */
    public static final int TITLE_H = 18;
    /** 标题与内容之间的空隙。 */
    public static final int TOP_GAP = 4;
    public static final int TAB_GAP = 3;
    /** 标签高度按窗口高度反推：窗口越矮标签越扁，正常窗口下不会低于这个值（否则点不中）；
     *  窗口矮到连它都放不下时（见构造函数末尾的兜底夹取）会继续变扁，优先保证不出屏。 */
    public static final int TAB_H_MIN = 6;
    public static final int TAB_H_MAX = 20;
    /** 标签列宽度：按窗口宽度的五分之一取，夹在这个区间里。 */
    public static final int TAB_W_MIN = 44;
    public static final int TAB_W_MAX = 72;
    /** 标签列与选项块之间的间隔。 */
    public static final int GAP = 8;
    public static final int BTN_GAP = 4;
    public static final int BUTTON_H = 20;
    public static final int BUTTON_COUNT = 4;
    public static final int BOTTOM_MARGIN = 6;
    /** 底部提示行（导出 / 导入的结果）的高度。 */
    public static final int STATUS_H = 11;
    public static final int STATUS_GAP = 3;
    /** 行高的正常下限；窗口矮到连它都放不下时同样会继续变扁（见构造函数末尾的兜底夹取）。 */
    public static final int ROW_H_MIN = 8;
    public static final int ROW_H_MAX = 20;
    /** 至少要这么宽才排成两列。 */
    public static final int COL_MIN_W = 150;
    public static final int COL_MAX_W = 220;

    public final int width;
    public final int height;
    public final int rows;
    public final int tabCount;
    public final int tabW;
    public final int tabH;
    /** 标签列的左上角 x。 */
    public final int tabLeft;
    /** 第一行标签的 y；整列已垂直居中。 */
    public final int tabTop;
    public final int contentTop;
    public final int contentBottom;
    public final int columns;
    public final int colW;
    public final int rowsPerColumn;
    public final int rowH;
    /** 选项块第一行的 y；整块已在这段高度里垂直居中，与标签列齐平。 */
    public final int rowsTop;
    public final int blockLeft;
    public final int blockWidth;
    public final int buttonY;
    public final int buttonW;
    /** 底部按钮行的左端 x：正常情况与选项块左对齐，极小窗口下改用窗口边距。 */
    private final int buttonLeft;
    public final int statusY;

    public static ScreenLayout of(int width, int height, int rows, int tabCount) {
        return new ScreenLayout(width, height, rows, tabCount);
    }

    private ScreenLayout(int width, int height, int rows, int tabCount) {
        this.width = width;
        this.height = height;
        this.rows = Math.max(0, rows);
        this.tabCount = Math.max(0, tabCount);

        // 水平：usableW 不设下限 —— 窗口只有 40 宽时「至少 80」会让整块跑到屏幕外。
        // 标签列宽度也允许压到 TAB_W_MIN 以下（最多给选项块留出 GAP），于是
        // 「标签列 + 间隔 + 选项块」的整体右边界恒不超过 width - MARGIN。
        int usableW = Math.max(0, this.width - MARGIN * 2);

        // 纵向：按钮行贴着窗口下沿，标题下沿与提示行上沿在空间不够时依次退让。
        // 窗口太矮时这几段会互相重叠（那时本来也排不下），但坐标都留在窗口里。
        this.buttonY = clamp(height - BOTTOM_MARGIN - BUTTON_H, 0, Math.max(0, height - BUTTON_H));
        int above = Math.max(0, height - MARGIN - BUTTON_H);
        this.contentTop = Math.min(TITLE_H + TOP_GAP, above);
        this.statusY = clamp(buttonY - STATUS_H, 0, above);
        this.contentBottom = Math.max(contentTop, Math.min(buttonY, statusY - STATUS_GAP));

        // 内容带：标题下沿到提示行上沿。标签列与选项块都在这段高度里垂直居中，
        // 于是二级菜单（选项行）与左侧六大标签在视觉上齐平。
        int band = Math.max(0, contentBottom - contentTop);

        // 标签列：宽度按窗口宽度的五分之一取，高度由内容带均分；窄 / 矮窗口里两者都会缩水。
        // tabHCap 是「按钮行上方还放得下多高」的硬上限，极小窗口里它比 TAB_H_MIN 还小，
        // 于是标签会一路扁到 1 像素 —— 40x30 那种窗口本来就没有可用的格局，优先不出屏。
        int tabWMax = Math.min(TAB_W_MAX, Math.max(0, usableW - GAP));
        this.tabW = clamp(usableW / 5, Math.min(TAB_W_MIN, tabWMax), tabWMax);
        int tabHCap = Math.max(1,
                (buttonY - contentTop - Math.max(0, tabCount - 1) * TAB_GAP) / Math.max(1, tabCount));
        this.tabH = tabCount <= 0 ? 0
                : clamp((band - (tabCount - 1) * TAB_GAP) / tabCount, 1, Math.min(TAB_H_MAX, tabHCap));
        int tabsH = tabCount * tabH + Math.max(0, tabCount - 1) * TAB_GAP;
        this.tabTop = clamp(contentTop + Math.max(0, (band - tabsH) / 2), 0,
                Math.max(0, Math.min(buttonY, height - MARGIN) - tabsH));

        // 选项块：标签列右边剩下的宽度，够两列就排两列；行高按内容带均分，整块同样居中。
        // rowHCap 同理：按钮行上方放不下最小行高时，行高会被压到 1 像素。
        int contentW = Math.max(0, usableW - tabW - GAP);
        this.columns = contentW >= COL_MIN_W * 2 + GAP ? 2 : 1;
        this.rowsPerColumn = Math.max(1, (this.rows + columns - 1) / columns);
        int rowHCap = Math.max(1, (buttonY - contentTop) / rowsPerColumn);
        this.rowH = clamp(Math.max(ROW_H_MIN, band / rowsPerColumn), 1, Math.min(ROW_H_MAX, rowHCap));
        int rowsH = rowsPerColumn * rowH;
        this.rowsTop = clamp(contentTop + Math.max(0, (band - rowsH) / 2), 0,
                Math.max(0, Math.min(buttonY, height - MARGIN) - rowsH));
        this.colW = Math.min(COL_MAX_W, Math.max(0, (contentW - (columns - 1) * GAP) / columns));
        this.blockWidth = columns * colW + (columns - 1) * GAP;

        // 标签列 + 选项块整体水平居中。
        int group = tabW + GAP + blockWidth;
        this.tabLeft = Math.max(MARGIN, (this.width - group) / 2);
        this.blockLeft = tabLeft + tabW + GAP;

        // 按钮行：默认与选项块同宽（从左端排满四个按钮）；选项块窄到装不下四个按钮时，
        // 整行改用窗口可用宽度，仍然不出屏。
        int buttonSpace = Math.min(blockWidth, Math.max(0, this.width - MARGIN - blockLeft));
        if ((buttonSpace - (BUTTON_COUNT - 1) * BTN_GAP) / BUTTON_COUNT < 1) {
            buttonSpace = usableW;
            this.buttonLeft = MARGIN;
        } else {
            this.buttonLeft = blockLeft;
        }
        this.buttonW = Math.max(0, (buttonSpace - (BUTTON_COUNT - 1) * BTN_GAP) / BUTTON_COUNT);
    }

    /** 分页标签的左上角 x（所有标签共用同一列）。 */
    public int tabX() {
        return tabLeft;
    }

    /** 第 index 个分页标签的左上角 y。 */
    public int tabY(int index) {
        return tabTop + index * (tabH + TAB_GAP);
    }

    /** 第 column 列选项的左上角 x。 */
    public int columnX(int column) {
        return blockLeft + column * (colW + GAP);
    }

    /** 第 column 列放几行；行数除不尽时，前面的列多一行。 */
    public int rowsInColumn(int column) {
        int base = rows / columns;
        return Math.max(0, base + (column < rows % columns ? 1 : 0));
    }

    /** 第 index 个选项落在第几列（按列顺序连续填充）。 */
    public int columnOf(int index) {
        int acc = 0;
        for (int c = 0; c < columns; c++) {
            int n = rowsInColumn(c);
            if (index < acc + n) {
                return c;
            }
            acc += n;
        }
        return columns - 1;
    }

    /** 第 index 个选项在本列里的第几行。 */
    public int rowOf(int index) {
        int acc = 0;
        for (int c = 0; c < columns; c++) {
            int n = rowsInColumn(c);
            if (index < acc + n) {
                return index - acc;
            }
            acc += n;
        }
        return 0;
    }

    /** 第 index 个选项的左上角 y。 */
    public int rowY(int index) {
        return rowsTop + rowOf(index) * rowH;
    }

    /** 底部第 index 个按钮的左上角 x。 */
    public int buttonX(int index) {
        return buttonLeft + index * (buttonW + BTN_GAP);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}