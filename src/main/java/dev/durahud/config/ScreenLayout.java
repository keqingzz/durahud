package dev.durahud.config;

/**
 * 设置界面的布局计算：左侧竖排分页标签 + 选项列 + 底部按钮行，全部由当前窗口尺寸推出。
 *
 * <p>标签是一列竖排的按钮，整列在标题与底部按钮之间垂直居中；标签列右边是选项块，
 * 标签列 + 间隔 + 选项块作为一个整体在窗口里水平居中，所以标签总是落在屏幕中央偏左的位置。</p>
 *
 * <p>纯数学、零 Minecraft 引用，所以可以用单测覆盖「GUI 缩放拉到最大」这类极端尺寸
 * （见 {@code ScreenLayoutTest}）：只要窗口还进得去游戏，控件就必须留在屏幕里 —— 这正是
 * 旧版「只有界面尺寸 4 及以下才正常」那个问题的根因（控件行数固定、行高被压到 12 像素，
 * 底部按钮被挤出屏幕）。</p>
 */
public final class ScreenLayout {

    public static final int MARGIN = 6;
    /** 顶部标题占的高度（原版在顶部居中画标题）。 */
    public static final int TITLE_H = 18;
    /** 标题与内容之间的空隙。 */
    public static final int TOP_GAP = 4;
    public static final int TAB_GAP = 3;
    /** 标签高度按窗口高度反推：窗口越矮标签越扁，但不会低于这个值（否则点不中）。 */
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
    public final int blockLeft;
    public final int blockWidth;
    public final int buttonY;
    public final int buttonW;
    public final int statusY;

    public static ScreenLayout of(int width, int height, int rows, int tabCount) {
        return new ScreenLayout(width, height, rows, tabCount);
    }

    private ScreenLayout(int width, int height, int rows, int tabCount) {
        this.width = width;
        this.height = height;
        this.rows = Math.max(0, rows);
        this.tabCount = Math.max(0, tabCount);

        int usableW = Math.max(80, width - MARGIN * 2);
        this.contentTop = TITLE_H + TOP_GAP;
        this.buttonY = height - BOTTOM_MARGIN - BUTTON_H;
        this.statusY = buttonY - STATUS_H;
        this.contentBottom = statusY - STATUS_GAP;

        // 标签列：宽度按窗口取档，高度均分可用高度，整列在标题与按钮行之间垂直居中。
        this.tabW = clamp(usableW / 5, TAB_W_MIN, TAB_W_MAX);
        int bandTop = contentTop;
        int bandBottom = Math.max(bandTop, buttonY - TOP_GAP);
        int band = bandBottom - bandTop;
        this.tabH = tabCount <= 0 ? 0
                : clamp((band - (tabCount - 1) * TAB_GAP) / tabCount, TAB_H_MIN, TAB_H_MAX);
        int tabsH = tabCount * tabH + Math.max(0, tabCount - 1) * TAB_GAP;
        this.tabTop = bandTop + Math.max(0, (band - tabsH) / 2);

        // 选项块：标签列右边剩下的宽度，够两列就排两列。
        int contentW = Math.max(40, usableW - tabW - GAP);
        this.columns = contentW >= COL_MIN_W * 2 + GAP ? 2 : 1;
        this.rowsPerColumn = Math.max(1, (this.rows + columns - 1) / columns);
        int available = Math.max(ROW_H_MIN * rowsPerColumn, contentBottom - contentTop);
        this.rowH = clamp(available / rowsPerColumn, ROW_H_MIN, ROW_H_MAX);
        this.colW = Math.min(COL_MAX_W, (contentW - (columns - 1) * GAP) / columns);
        this.blockWidth = columns * colW + (columns - 1) * GAP;

        // 标签列 + 选项块整体水平居中。
        int group = tabW + GAP + blockWidth;
        this.tabLeft = Math.max(MARGIN, (width - group) / 2);
        this.blockLeft = tabLeft + tabW + GAP;
        this.buttonW = Math.max(20, (blockWidth - (BUTTON_COUNT - 1) * BTN_GAP) / BUTTON_COUNT);
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
        return contentTop + rowOf(index) * rowH;
    }

    /** 底部第 index 个按钮的左上角 x。 */
    public int buttonX(int index) {
        return blockLeft + index * (buttonW + BTN_GAP);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}