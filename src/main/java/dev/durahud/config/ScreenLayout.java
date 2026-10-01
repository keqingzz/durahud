package dev.durahud.config;

/**
 * 设置界面的布局计算：分页标签栏 + 选项列 + 底部按钮行，全部由当前窗口尺寸推出。
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
    public static final int TAB_GAP = 3;
    public static final int TAB_H_MIN = 16;
    public static final int TAB_H_MAX = 20;
    /** 一个分页标签至少这么宽（低于它就把标签排成两行）。 */
    public static final int TAB_MIN_W = 30;
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
    public final int tabRows;
    public final int tabsPerRow;
    public final int tabW;
    public final int tabH;
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
        int oneRowNeed = this.tabCount * TAB_MIN_W + Math.max(0, this.tabCount - 1) * TAB_GAP;
        this.tabRows = this.tabCount <= 0 || usableW >= oneRowNeed ? 1 : 2;
        this.tabsPerRow = Math.max(1, (this.tabCount + tabRows - 1) / tabRows);
        this.tabH = clamp(height / 12, TAB_H_MIN, TAB_H_MAX);
        this.tabTop = TITLE_H;
        int tabsBottom = tabTop + tabRows * tabH + (tabRows - 1) * TAB_GAP;
        this.contentTop = tabsBottom + 6;

        this.buttonY = height - BOTTOM_MARGIN - BUTTON_H;
        this.statusY = buttonY - STATUS_H;
        this.contentBottom = statusY - STATUS_GAP;

        this.columns = usableW >= COL_MIN_W * 2 + GAP ? 2 : 1;
        this.rowsPerColumn = Math.max(1, (this.rows + columns - 1) / columns);
        int available = Math.max(ROW_H_MIN * rowsPerColumn, contentBottom - contentTop);
        this.rowH = clamp(available / rowsPerColumn, ROW_H_MIN, ROW_H_MAX);
        this.colW = Math.min(COL_MAX_W, (usableW - (columns - 1) * GAP) / columns);
        this.blockWidth = columns * colW + (columns - 1) * GAP;
        this.blockLeft = Math.max(MARGIN, (width - blockWidth) / 2);
        this.tabW = Math.max(20, (usableW - (tabsPerRow - 1) * TAB_GAP) / tabsPerRow);
        this.buttonW = Math.max(20, (blockWidth - (BUTTON_COUNT - 1) * BTN_GAP) / BUTTON_COUNT);
    }

    /** 分页标签（第 index 个）的左上角 x，自动换行。 */
    public int tabX(int index) {
        return MARGIN + (index % tabsPerRow) * (tabW + TAB_GAP);
    }

    /** 分页标签（第 index 个）的左上角 y，自动换行。 */
    public int tabY(int index) {
        return tabTop + (index / tabsPerRow) * (tabH + TAB_GAP);
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
