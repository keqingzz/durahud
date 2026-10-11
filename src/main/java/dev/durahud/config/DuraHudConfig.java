package dev.durahud.config;

/**
 * 配置数据模型 + 取值校验：纯数据、无 IO，字段的声明顺序就是 JSON 里出现的顺序。
 *
 * <p>所有字段都是 public 的普通类型，方便直接赋值（省掉 getter/setter 那层噪声）；
 * 持久化与容错读取全在 {@link ConfigFile} 里，本类只负责「内存里的值合不合法」。</p>
 *
 * <p><b>加字段要改的地方</b>：本类 + {@code ConfigFile.read} / {@code copyFrom} /
 * {@code resetToDefaults} 三处都要补上（漏一处的表现是「存得进去，导入预设后却丢值」），
 * 另外别忘 {@code ConfigFile.CONFIG_VERSION} +1 与 {@code migrate} 里的空分支 ——
 * {@code ConfigFieldCoverageTest} 逐字段盯着这几件事，见 README 13.26。</p>
 */
public class DuraHudConfig {

    /**
     * 整块 HUD 贴在哪。{@code HOTBAR_*} 三个跟着热键栏走（热键栏位置本身会随窗口尺寸变），
     * 四个角是固定的屏幕角落，{@code CUSTOM} 表示由 {@code offsetX} / {@code offsetY}
     * 直接给绝对坐标 —— 编辑模式拖动、方向键微调之后就是这一档。
     */
    public enum Anchor {
        HOTBAR_ABOVE, HOTBAR_LEFT, HOTBAR_RIGHT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CUSTOM
    }

    /** 格子排列方向：HORIZONTAL = 横向一排，VERTICAL = 纵向一列。 */
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

    /**
     * 默认锚点：热键栏右侧。字段初始值、{@link #sanitize()} 的兜底与编辑模式里的 R（恢复默认位置）
     * 都引用这一个常量 —— 三处各写一遍迟早走样。
     */
    public static final Anchor DEFAULT_ANCHOR = Anchor.HOTBAR_RIGHT;

    /**
     * 数值纵向微调的上下限（缩放前的像素）：一整行文字高。再多就会盖到旁边的元素上，
     * 也会让「槽位高度恒定」这条纪律失去意义（见 README 7.4 节）。
     */
    public static final int VALUE_OFFSET_LIMIT = 9;

    /** 总开关；关掉后 HUD 完全不画，只有编辑模式例外（否则再也改不回来）。 */
    public boolean enabled = true;
    /** 停靠位置；选 {@code CUSTOM} 时位置由 {@code offsetX} / {@code offsetY} 给出。 */
    public Anchor anchor = DEFAULT_ANCHOR;
    /** 格子排列方向。 */
    public Orientation orientation = Orientation.HORIZONTAL;
    /** 条在格子的哪一侧。默认格子上方。 */
    public BarSide barPlacement = BarSide.ABOVE;
    /** 数值在条的哪一侧；AUTO = 自动放到条的对侧。默认条的上方（也就是整块最上面一行）。 */
    public ValuePlacement valuePlacement = ValuePlacement.ABOVE;
    /** 0 = 自动（上下时同格子宽，左右时 60）。 */
    public int barLength = 0;
    /** CUSTOM 锚点下 HUD 左上角的屏幕 x（缩放后的逻辑像素）。<b>刻意不夹取</b>，见 README 13.18。 */
    public int offsetX = 0;
    /** CUSTOM 锚点下 HUD 左上角的屏幕 y（缩放后的逻辑像素）。<b>刻意不夹取</b>，见 README 13.18。 */
    public int offsetY = 0;
    /** 整体缩放；sanitize 后落在 0.25 - 4，非正 / 非有限值按 1 处理。 */
    public float scale = 1.0F;
    /** 主手槽位显隐。 */
    public boolean showMainhand = true;
    /** 副手槽位显隐。 */
    public boolean showOffhand = true;
    /** 四个盔甲槽位显隐；空槽位的边框与占位另受 {@code showEmptySlots} 影响。 */
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
    /** 耐久数值显示什么：不显示 / 百分比 / 剩余-上限。默认显示「上剩余、下上限」两行。 */
    public ValueDisplay valueDisplay = ValueDisplay.VALUE;
    /**
     * 数值相对槽位的纵向微调（缩放前的像素，正数向下）：单行数值（百分比）默认已经贴着
     * 耐久条，这里只是允许再挪一点点。它<b>只挪数字</b> —— 槽位、格子与整块 HUD 的尺寸都不变。
     */
    public int valueOffsetY = 0;
    /** 画格子边框（空盔甲格的边框也归它管）。 */
    public boolean showSlotFrames = true;
    /** 画物品图标；关掉后只剩边框与条 / 数字。 */
    public boolean showItemIcons = true;
    /** 格子出现/消失时淡入淡出；关掉就是旧的「直接出现、直接消失」。 */
    public boolean animateCells = true;
    /** 淡入淡出与平滑移动的时长（毫秒）；只在 {@link #animateCells} 开着时有用。 */
    public int animationMillis = 200;
    /** 濒危高亮：比例低到 {@code critThreshold} 的格子加一圈警示框。 */
    public boolean highlightCritical = true;
    /** 泛黄的耐久百分比阈值，夹在 1 - 100。 */
    public int warnThreshold = 50;
    /** 泛红的耐久百分比阈值，夹在 0 - ({@code warnThreshold} - 1)，所以永远比 warn 至少小 1。 */
    public int critThreshold = 20;

    /** 运行期状态，不写入文件。 */
    public transient boolean editMode = false;

    /** 读配置文件，等价于 {@link ConfigFile#load()}；调用方不需要知道路径与格式。 */
    public static DuraHudConfig load() {
        return ConfigFile.load();
    }

    /**
     * 把值夹到合法区间。任何来源（手改 JSON、拖滑条、导入预设、旧版本文件）都过这一关，
     * 唯一的例外是 {@code offsetX} / {@code offsetY}（原因见方法内注释与 README 13.18）。
     *
     * <p>规则一览：枚举为 null 时回默认值；{@code animationMillis} 夹 50 - 1000；{@code scale}
     * 非正或非有限值先回 1.0 再夹 0.25 - 4；{@code barLength} 夹 0 - 182；{@code valueOffsetY}
     * 夹 ±{@link #VALUE_OFFSET_LIMIT}；{@code warnThreshold} 夹 1 - 100；{@code critThreshold}
     * 夹 0 - ({@code warn} - 1)。越界的绝对值不报错，直接拉到边界。</p>
     */
    public void sanitize() {
        // 枚举兜底：null 会在下游的 switch / 比较里直接 NPE，宁可悄悄回默认值。
        if (anchor == null) {
            anchor = DEFAULT_ANCHOR;
        }
        if (orientation == null) {
            orientation = Orientation.HORIZONTAL;
        }
        if (barPlacement == null) {
            barPlacement = BarSide.ABOVE;
        }
        if (valuePlacement == null) {
            valuePlacement = ValuePlacement.ABOVE;
        }
        if (valueDisplay == null) {
            valueDisplay = ValueDisplay.VALUE;
        }
        // 数值夹取：一律拉到最近的边界，不报错也不保留越界值。
        animationMillis = Math.max(50, Math.min(1000, animationMillis));
        if (!(scale > 0.0F)) {
            scale = 1.0F;
        }
        scale = Math.max(0.25F, Math.min(4.0F, scale));
        barLength = Math.max(0, Math.min(182, barLength));
        valueOffsetY = Math.max(-VALUE_OFFSET_LIMIT, Math.min(VALUE_OFFSET_LIMIT, valueOffsetY));
        // 偏移量刻意<b>不</b>夹：编辑模式把 HUD 拖到哪，offsetX/offsetY 就是那块屏幕上的绝对
        // 坐标（1920 宽的窗口里能到 1600+），而界面滑条只有 ±200。早期版本在这里夹一次，于是
        // 「打开设置界面 HUD 就跳回初始位置」；越界值交给 HudLayout 在绘制时拉回屏幕内即可，
        // 读盘、拖动与磁盘上的值因此永远一致。
        warnThreshold = Math.max(1, Math.min(100, warnThreshold));
        critThreshold = Math.max(0, Math.min(warnThreshold - 1, critThreshold));
    }

    /** 把当前值写回 config/durahud.json；失败只记一条日志，不抛给调用方。 */
    public void save() {
        ConfigFile.save(this);
    }

    /**
     * 逐字段拷贝（导入预设、恢复默认值共用）。editMode 属于运行期状态，刻意不拷贝。
     *
     * <p>这里是「加字段要改的三处」之一：新字段漏在这里，导入预设时那一项会保持旧值
     * （见 README 13.26）。</p>
     */
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
        this.valueOffsetY = other.valueOffsetY;
        this.showSlotFrames = other.showSlotFrames;
        this.showItemIcons = other.showItemIcons;
        this.animateCells = other.animateCells;
        this.animationMillis = other.animationMillis;
        this.highlightCritical = other.highlightCritical;
        this.warnThreshold = other.warnThreshold;
        this.critThreshold = other.critThreshold;
    }

    /**
     * 恢复出厂默认值：先照 {@code new DuraHudConfig()} 的字段初始值整份拷过来，再夹一次。
     *
     * <p>拷贝而不是 {@code cfg = new ...}，是因为界面与入口都拿着同一个实例的引用；
     * 这也意味着新字段必须进 {@link #copyFrom(DuraHudConfig)}，否则「重置」会漏掉它。</p>
     */
    public void resetToDefaults() {
        copyFrom(new DuraHudConfig());
        sanitize();
    }
}
