package dev.durahud.config;

/**
 * 配置数据 + 取值校验。字段顺序即配置文件顺序；读写见 {@link ConfigFile}。
 * 所有字段都是 public 的普通类型，方便直接赋值（不需要 getter/setter 那层噪声）。
 */
public class DuraHudConfig {

    public enum Anchor {
        HOTBAR_ABOVE, HOTBAR_LEFT, HOTBAR_RIGHT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CUSTOM
    }

    public enum Orientation {
        HORIZONTAL, VERTICAL
    }

    /** 附着方向，耐久条与耐久数值都用它。 */
    public enum BarSide {
        ABOVE, BELOW, LEFT, RIGHT
    }

    /** 耐久数值的位置；AUTO = 自动放到耐久条的对侧，避免冲突。 */
    public enum ValuePlacement {
        AUTO, ABOVE, BELOW, LEFT, RIGHT
    }

    /**
     * 耐久数值显示什么。旧版是两个布尔（showValue / showPercent），v5 → v6 合并成三态：
     * 「两个都开」与「只开 showValue」本来就是一回事（有数字就显示数字，否则退化成百分比）。
     */
    public enum ValueDisplay {
        OFF, PERCENT, VALUE
    }

    public boolean enabled = true;
    public Anchor anchor = Anchor.HOTBAR_ABOVE;
    public Orientation orientation = Orientation.HORIZONTAL;
    public BarSide barPlacement = BarSide.BELOW;
    public ValuePlacement valuePlacement = ValuePlacement.AUTO;
    /** 0 = 自动（上下时同格子宽，左右时 60）。 */
    public int barLength = 0;
    public int offsetX = 0;
    public int offsetY = 0;
    public float scale = 1.0F;
    public boolean showMainhand = true;
    public boolean showOffhand = true;
    public boolean showArmor = true;
    /** 额外装备槽（Trinkets 等模组）里的物品也显示在 HUD 上；没有对应模组时无效果。 */
    public boolean showTrinkets = true;
    /**
     * 只显示带耐久信息的物品：有损耗量或有原版物品条。胡萝卜、木板、机械动力的护目镜
     * 这类物品不再占格内容（盔甲格保留空位，手持与饰品格直接不占位）。
     * 穿戴类格子可以用 {@link #showWornNonDurable} 单独放行。
     */
    public boolean onlyDurableItems = true;
    /**
     * 例外：穿戴类格子（4 个盔甲格与额外装备槽）里的物品即使没有耐久信息也显示，
     * 例如机械动力的护目镜、南瓜、没有耐久的饰品。只在 {@link #onlyDurableItems} 开着时
     * 有意义；手持槽不受它影响，手里的胡萝卜、木板依旧被挡在外面。
     */
    public boolean showWornNonDurable = false;
    /** 满耐久时隐藏内容（格子仍然占位，保证布局不移动）。 */
    public boolean hideFull = false;
    /**
     * 空槽位是否显示并占位。打开（默认）时空着的盔甲格照常画边框、照常占位；关掉时空槽位
     * 完全透明且不再占位，后面的格子自动补位（配合平滑移动的过渡动画）。旧版那个单独的
     * 「自动补位」开关在 v5 → v6 合并到这里 —— 两者本来就是同一件事。
     */
    public boolean showEmptySlots = true;
    /** 耐久数值显示什么：不显示 / 百分比 / 剩余-上限。 */
    public ValueDisplay valueDisplay = ValueDisplay.OFF;
    public boolean showSlotFrames = true;
    public boolean showItemIcons = true;
    /** 格子出现/消失时淡入淡出；关掉就是旧的「直接出现、直接消失」。 */
    public boolean animateCells = true;
    /** 淡入淡出与平滑移动的时长（毫秒）；只在 {@link #animateCells} 开着时有用。 */
    public int animationMillis = 200;
    public boolean highlightCritical = true;
    public int warnThreshold = 50;
    public int critThreshold = 20;

    /** 运行期状态，不写入文件。 */
    public transient boolean editMode = false;

    public static DuraHudConfig load() {
        return ConfigFile.load();
    }

    /** 把读到的值夹到合法区间；任何来源（手改 JSON、滑条、旧版本文件）都过这一关。 */
    public void sanitize() {
        if (anchor == null) {
            anchor = Anchor.HOTBAR_ABOVE;
        }
        if (orientation == null) {
            orientation = Orientation.HORIZONTAL;
        }
        if (barPlacement == null) {
            barPlacement = BarSide.BELOW;
        }
        if (valuePlacement == null) {
            valuePlacement = ValuePlacement.AUTO;
        }
        if (valueDisplay == null) {
            valueDisplay = ValueDisplay.OFF;
        }
        animationMillis = Math.max(50, Math.min(1000, animationMillis));
        if (!(scale > 0.0F)) {
            scale = 1.0F;
        }
        scale = Math.max(0.25F, Math.min(4.0F, scale));
        barLength = Math.max(0, Math.min(182, barLength));
        // 偏移量刻意<b>不</b>夹：编辑模式把 HUD 拖到哪，offsetX/offsetY 就是那块屏幕上的绝对
        // 坐标（1920 宽的窗口里能到 1600+），而界面滑条只有 ±200。早期版本在这里夹一次，于是
        // 「打开设置界面 HUD 就跳回初始位置」；越界值交给 HudLayout 在绘制时拉回屏幕内即可，
        // 读盘、拖动与磁盘上的值因此永远一致。
        warnThreshold = Math.max(1, Math.min(100, warnThreshold));
        critThreshold = Math.max(0, Math.min(warnThreshold - 1, critThreshold));
    }

    public void save() {
        ConfigFile.save(this);
    }

    /** 逐字段拷贝（导入预设、恢复默认值共用）。editMode 属于运行期状态，刻意不拷贝。 */
    public void copyFrom(DuraHudConfig other) {
        this.enabled = other.enabled;
        this.anchor = other.anchor;
        this.orientation = other.orientation;
        this.barPlacement = other.barPlacement;
        this.valuePlacement = other.valuePlacement;
        this.barLength = other.barLength;
        this.offsetX = other.offsetX;
        this.offsetY = other.offsetY;
        this.scale = other.scale;
        this.showMainhand = other.showMainhand;
        this.showOffhand = other.showOffhand;
        this.showArmor = other.showArmor;
        this.showTrinkets = other.showTrinkets;
        this.onlyDurableItems = other.onlyDurableItems;
        this.showWornNonDurable = other.showWornNonDurable;
        this.hideFull = other.hideFull;
        this.showEmptySlots = other.showEmptySlots;
        this.valueDisplay = other.valueDisplay;
        this.showSlotFrames = other.showSlotFrames;
        this.showItemIcons = other.showItemIcons;
        this.animateCells = other.animateCells;
        this.animationMillis = other.animationMillis;
        this.highlightCritical = other.highlightCritical;
        this.warnThreshold = other.warnThreshold;
        this.critThreshold = other.critThreshold;
    }

    public void resetToDefaults() {
        copyFrom(new DuraHudConfig());
        sanitize();
    }
}
