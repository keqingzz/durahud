package dev.durahud.config;

import dev.durahud.DuraHudClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * 设置界面：选项按用途分成 6 页（分页标签竖排在窗口左侧、垂直居中），每页最多 6 行。
 *
 * <p>所有位置与尺寸都来自 {@link ScreenLayout}（按当前窗口尺寸算），控件在造出来的时候就带上
 * 最终尺寸，所以 GUI 缩放调大、窗口变矮变窄时也不会把按钮挤出屏幕 —— 旧版把 17 行硬塞进
 * 一屏、行高压到 12 像素，只有界面尺寸 4 及以下才正常。</p>
 *
 * <p>每页的选项先登记成 {@link Option}（拿到位置与尺寸之后再造成控件），因此行数、列数、
 * 行高可以一次算准，不需要「先造再挪」。控件全部是原版控件（ButtonWidget / SliderWidget）。</p>
 *
 * <p>为什么必须「先登记后造控件」：{@code ClickableWidget} 没有 {@code setHeight}，控件高度
 * 只能在构造时定死（见 README 13.11），所以布局必须先于控件存在。同理，这个类里没有一处
 * 硬编码的「第 N 行」（见 README 13.12），位置与尺寸一律由 {@link ScreenLayout} 给出。</p>
 *
 * <p>三条贯穿全类的纪律：控件回调只置 {@link #dirty}（拖一次滑条会触发上百次回调，
 * 见 README 13.15），夹取与落盘留到关屏时做一次；配置值一律经 {@code ConfigFile} 的
 * 「先判类型再取值」读入（见 README 13.14 / 9.15）；换页会重建全部控件，所以行号字段必须在
 * {@link #init()} 开头复位、取控件时必须判类型（见 README 13.28）。</p>
 */
public class DuraHudConfigScreen extends Screen {

    /** 分页；顺序即标签顺序，语言键为 {@code durahud.tab.<key>}。 */
    private enum Tab {
        DISPLAY("display"),
        ITEMS("items"),
        LAYOUT("layout"),
        LOOK("look"),
        WARNING("warning"),
        ANIMATION("animation");

        private final String key;

        Tab(String key) {
            this.key = key;
        }

        MutableText label() {
            return Text.translatable("durahud.tab." + key);
        }
    }

    private static final Tab[] TABS = Tab.values();

    /** 关掉本界面后回到的界面（ModMenu / 标题界面进来时由调用方给出）。 */
    private final Screen parent;
    /** 当前配置：在 {@link #init()} 里取，避免在任何界面打开之前构造本界面时拿到 null。 */
    private DuraHudConfig cfg;
    /** 当前页的选项（登记顺序 = 布局顺序）。 */
    private final List<Option> options = new ArrayList<>();
    /** 造出来的控件，与 {@link #options} 一一对应。 */
    private final List<ClickableWidget> widgets = new ArrayList<>();
    /** 当前页；换页会重建全部控件，所以行号字段只在同一次 {@link #init()} 里有效（见 README 13.28）。 */
    private Tab tab = Tab.DISPLAY;
    /** 本次 {@link #init()} 算出的布局；{@link #render} 用它画标签高亮与底部提示行。 */
    private ScreenLayout layout;
    /** 底部提示行的文字（导出 / 导入的结果）；null = 不画。 */
    private Text status;
    /** 「显示无耐久穿戴物」所在的行（-1 = 本页没有）；跟着 onlyDurableItems 置灰。 */
    private int wornNonDurableRow = -1;
    /** 「动画时长」所在的行（-1 = 本页没有）；跟着 animateCells 置灰。 */
    private int animationRow = -1;
    /** 警示页的 crit 阈值所在的行（-1 = 本页没有）。 */
    private int critRow = -1;
    /** 警示页的 crit 滑条：warn 变小把它一起夹小时，要同步它的显示。 */
    private NumberSliderWidget critSlider;
    /** 界面里改过东西：关屏时统一落盘（滑条拖一次会触发上百次值变化）。 */
    private boolean dirty;

    /**
     * 标题带当前版本号（{@link DuraHudClient#version()}），进游戏后一眼能确认装的是哪一版。
     * 配置实例刻意留到 {@link #init()} 再取：早于模组初始化构造本界面时它还是 null。
     *
     * @param parent 关掉后回到的界面
     */
    public DuraHudConfigScreen(Screen parent) {
        super(Text.translatable("durahud.config.title").append(Text.literal(" v" + DuraHudClient.version())));
        this.parent = parent;
    }

    /** 一行选项：给最终位置与尺寸，造出控件。 */
    @FunctionalInterface
    private interface Option {
        ClickableWidget create(int x, int y, int width, int height);
    }

        // ------------------------------------------------------------------
        // ------------------------------------------------------------------
    // 生命周期：建界面 / 复位行号 / 统一落盘
        // ------------------------------------------------------------------
        // ------------------------------------------------------------------
    /**
     * 重建界面，顺序不能变：取配置 → 复位行号字段 → 登记当页选项 → 算布局 →
     * 带着最终尺寸造控件 → 造分页标签与底部按钮 → 刷新从属项。
     *
     * <p>布局要等选项登记完才知道行数（{@code options.size()}），而控件必须一次就带着
     * 最终尺寸造出来（见 README 13.11）。顶部的行号复位是硬性要求：换页与「恢复默认」
     * 都会重进这里，上一次的行号会落到别的控件上（见 README 13.28）。</p>
     */
    @Override
    protected void init() {
        cfg = DuraHudClient.config();
        cfg.sanitize();   // 手改过的越界值先夹回来，滑条才不会显示 200 而配置里是 100000
        options.clear();
        widgets.clear();
        wornNonDurableRow = -1;
        animationRow = -1;
        critRow = -1;
        critSlider = null;
        buildPage();

        this.layout = ScreenLayout.of(this.width, this.height, options.size(), TABS.length);
        for (int i = 0; i < options.size(); i++) {
            ClickableWidget widget = options.get(i).create(
                    layout.columnX(layout.columnOf(i)), layout.rowY(i), layout.colW, layout.rowH);
            widgets.add(widget);
            addDrawableChild(widget);
        }
        critSlider = sliderAt(critRow);
        addTabs();
        addBottomButtons();
        refreshDependencies();
    }

    /**
     * 按下标取一行控件，并确认它真是滑条。
     *
     * <p>行号只在同一次 {@link #buildPage()} 里有效：换页之后同一个下标可能落在按钮上，
     * 直接强转会抛 {@code ClassCastException} 把游戏崩掉，2.1.2 修的就是这个。
     * 这里既查范围也判类型，最坏情况只是拿不到滑条、crit 的显示不同步（见 README 13.28）。</p>
     */
    private NumberSliderWidget sliderAt(int row) {
        if (row < 0 || row >= widgets.size()) {
            return null;
        }
        ClickableWidget widget = widgets.get(row);
        return widget instanceof NumberSliderWidget slider ? slider : null;
    }

    /** 改动先只标脏：{@link #removed()} 里统一夹取并落盘。 */
    private void markDirty() {
        dirty = true;
    }

    /**
     * 关屏（换界面 / ESC / 点「完成」）时统一收尾：确实改过东西才夹取 + 落盘一次。
     * 回调里不写盘的理由见 {@link #markDirty()} 与 README 13.15。
     */
    @Override
    public void removed() {
        super.removed();
        flush();
    }

    /**
     * 统一落盘：用户真改过（{@link #dirty}）或这次读盘本来就欠一次回写
     * （{@link ConfigFile#shouldWriteBack()}：首次运行、刚升级过格式）才写。
     *
     * <p>{@link #removed()}（切界面 / ESC）与 {@link #close()}（点「完成」）共用它。以前
     * {@code close()} 里是无条件 {@code cfg.save()}，等于没动过也把文件重写一遍；更要紧的是配置
     * 读坏时会把内存里的默认值盖到用户手改的文件上 —— 现在 {@link ConfigFile#save} 自己会拒绝，
     * 界面另在 {@link #render} 里常驻一条红字说明（2.1.7）。</p>
     */
    private void flush() {
        cfg.sanitize();
        if (!dirty && !ConfigFile.shouldWriteBack()) {
            return;   // 没动过、也不欠回写：磁盘上就是这份内容，别去碰它
        }
        cfg.save();
        dirty = false;
    }

    // ------------------------------------------------------------------
    // 各页的选项
    // ------------------------------------------------------------------

    /** 按当前页分派到对应的 {@code buildXxxPage}；每个页面方法只管往 {@link #options} 里登记。 */
    private void buildPage() {
        switch (tab) {
            case DISPLAY -> buildDisplayPage();
            case ITEMS -> buildItemsPage();
            case LAYOUT -> buildLayoutPage();
            case LOOK -> buildLookPage();
            case WARNING -> buildWarningPage();
            case ANIMATION -> buildAnimationPage();
        }
    }

    /** 显示页：整块 HUD 的总开关，以及四类装备各自的显隐。 */
    private void buildDisplayPage() {
        toggle("durahud.option.enabled", cfg.enabled, v -> cfg.enabled = v, null);
        toggle("durahud.option.showMainhand", cfg.showMainhand, v -> cfg.showMainhand = v, null);
        toggle("durahud.option.showOffhand", cfg.showOffhand, v -> cfg.showOffhand = v, null);
        toggle("durahud.option.showArmor", cfg.showArmor, v -> cfg.showArmor = v, null);
        toggle("durahud.option.showTrinkets", cfg.showTrinkets, v -> cfg.showTrinkets = v, null);
    }

    /**
     * 物品页：哪些东西才占格。{@code wornNonDurableRow} 记下第二行，供
     * {@link #refreshDependencies()} 在「只显示有耐久的物品」开启时置灰。
     */
    private void buildItemsPage() {
        toggle("durahud.option.onlyDurableItems", cfg.onlyDurableItems, v -> cfg.onlyDurableItems = v,
                "durahud.tip.onlyDurableItems");
        wornNonDurableRow = options.size();
        toggle("durahud.option.showWornNonDurable", cfg.showWornNonDurable, v -> cfg.showWornNonDurable = v,
                "durahud.tip.showWornNonDurable");
        toggle("durahud.option.hideFull", cfg.hideFull, v -> cfg.hideFull = v, "durahud.tip.hideFull");
        toggle("durahud.option.showEmptySlots", cfg.showEmptySlots, v -> cfg.showEmptySlots = v,
                "durahud.tip.showEmptySlots");
    }

    /** 非 {@code CUSTOM} 锚点时位置滑条的固定半程：±200 像素，够推出手感范围又不牺牲精度。 */
    private static final double OFFSET_SPAN = 200.0D;

    /**
     * 布局页：停靠方向、排列方向、缩放、位置与条长。
     *
     * <p>后两个滑条的含义<b>取决于锚点</b>：非 {@code CUSTOM} 时它们是「相对锚点的偏移」，
     * {@code CUSTOM} 时 {@code offsetX/offsetY} 本身就是屏幕绝对坐标（见
     * {@code HudLayout#computeOrigin}）。所以 CUSTOM 下换一套标签与提示，量程也跟着屏幕走 ——
     * 固定 ±200 的老写法让「滑条显示 200、配置里却存着 521」这种状态出现，只要碰一下滑条，
     * 拖出来的位置就被夹回 200（README 13.37）。换锚点后重建整页见上面的 {@code cycle} 重载。</p>
     */
    private void buildLayoutPage() {
        cycle("durahud.option.anchor", "durahud.anchor", DuraHudConfig.Anchor.values(), cfg.anchor,
                v -> cfg.anchor = v, this::clearAndInit);
        cycle("durahud.option.orientation", "durahud.option.orientation",
                DuraHudConfig.Orientation.values(), cfg.orientation, v -> cfg.orientation = v);
        slider("durahud.option.scale", 0.5D, 3.0D, cfg.scale, false, false,
                v -> cfg.scale = (float) v, null);
        boolean custom = cfg.anchor == DuraHudConfig.Anchor.CUSTOM;
        slider(custom ? "durahud.option.positionX" : "durahud.option.offsetX",
                positionMin(custom, cfg.offsetX), positionMax(custom, cfg.offsetX, this.width),
                cfg.offsetX, true, false, v -> cfg.offsetX = (int) v,
                custom ? "durahud.tip.positionX" : null);
        slider(custom ? "durahud.option.positionY" : "durahud.option.offsetY",
                positionMin(custom, cfg.offsetY), positionMax(custom, cfg.offsetY, this.height),
                cfg.offsetY, true, false, v -> cfg.offsetY = (int) v,
                custom ? "durahud.tip.positionY" : null);
        slider("durahud.option.barLength", 0.0D, 182.0D, cfg.barLength, true, true,
                v -> cfg.barLength = (int) v, null);
    }

    /**
     * 位置滑条的下界。
     *
     * <p>常规锚点给 {@link #OFFSET_SPAN} 的手感量程，{@code CUSTOM} 时屏幕坐标从 0 起算；两种
     * 情况都必须<b>包住当前值</b> —— 手改过配置的大值得先能显示出来，滑条才不会一碰就把它夹掉。</p>
     */
    private static double positionMin(boolean custom, int value) {
        return custom ? Math.min(0.0D, value) : Math.min(-OFFSET_SPAN, value);
    }

    /** 位置滑条的上界，理由同 {@link #positionMin}：{@code CUSTOM} 时的上界是屏幕边长。 */
    private static double positionMax(boolean custom, int value, int screen) {
        return custom ? Math.max(screen, value) : Math.max(OFFSET_SPAN, value);
    }

    /** 外观页：条与数值放哪、数值显示成什么、格子与图标的装饰。 */
    private void buildLookPage() {
        cycle("durahud.option.barPlacement", "durahud.option.barPlacement",
                DuraHudConfig.BarSide.values(), cfg.barPlacement, v -> cfg.barPlacement = v);
        cycle("durahud.option.valuePlacement", "durahud.option.valuePlacement",
                DuraHudConfig.ValuePlacement.values(), cfg.valuePlacement, v -> cfg.valuePlacement = v);
        cycle("durahud.option.valueDisplay", "durahud.valueDisplay", DuraHudConfig.ValueDisplay.values(),
                cfg.valueDisplay, v -> cfg.valueDisplay = v);
        slider("durahud.option.valueOffsetY", -(double) DuraHudConfig.VALUE_OFFSET_LIMIT,
                (double) DuraHudConfig.VALUE_OFFSET_LIMIT, cfg.valueOffsetY, true, false,
                v -> cfg.valueOffsetY = (int) v, "durahud.tip.valueOffsetY");
        toggle("durahud.option.showSlotFrames", cfg.showSlotFrames, v -> cfg.showSlotFrames = v, null);
        toggle("durahud.option.showItemIcons", cfg.showItemIcons, v -> cfg.showItemIcons = v, null);
    }

    /**
     * 警示页：两个阈值与濒危高亮。{@code critRow} 记下第二行，{@link #init()} 据此抓出 crit
     * 滑条（{@link #setWarn(int)} 改 warn 时要同步它的显示）。
     */
    private void buildWarningPage() {
        slider("durahud.option.warnThreshold", 1.0D, 100.0D, cfg.warnThreshold, true, false,
                v -> setWarn((int) v), null);
        critRow = options.size();
        slider("durahud.option.critThreshold", 0.0D, 99.0D, cfg.critThreshold, true, false,
                v -> setCrit((int) v), null);
        toggle("durahud.option.highlightCritical", cfg.highlightCritical, v -> cfg.highlightCritical = v, null);
    }

    /** 动画页：淡入淡出开关与时长。{@code animationRow} 记下时长那一行，供置灰用。 */
    private void buildAnimationPage() {
        toggle("durahud.option.animateCells", cfg.animateCells, v -> cfg.animateCells = v, null);
        animationRow = options.size();
        slider("durahud.option.animationMillis", 50.0D, 1000.0D, cfg.animationMillis, true, false,
                v -> cfg.animationMillis = (int) v, "durahud.tip.animationMillis");
    }

    /**
     * 警示阈值：{@code crit <= warn - 1} 是 {@code sanitize()} 的规则，界面里就夹好 ——
     * 否则本次会话按 crit=50 / warn=10 判定（几乎全是红色），重启后 crit 被悄悄改成 9，
     * 同一个配置文件在两处表现出两种行为。
     */
    private void setWarn(int value) {
        cfg.warnThreshold = value;
        setCrit(cfg.critThreshold);
    }

    /** 夹好 crit（0 - warn - 1，规则与 {@code sanitize()} 一致）并同步滑条显示；只置 dirty。 */
    private void setCrit(int value) {
        cfg.critThreshold = Math.max(0, Math.min(cfg.warnThreshold - 1, value));
        if (critSlider != null) {
            critSlider.showValue(cfg.critThreshold);
        }
        markDirty();
    }

    /** 登记一个开关行：文字是「<label>: 开 / 关」，点一下翻转并刷新从属项的置灰状态。 */
    private void toggle(String labelKey, boolean value, Consumer<Boolean> setter, String tipKey) {
        options.add((x, y, width, height) -> {
            boolean[] state = { value };
            ButtonWidget widget = ButtonWidget.builder(onOff(labelKey, state[0]), button -> {
                state[0] = !state[0];
                setter.accept(state[0]);
                markDirty();
                button.setMessage(onOff(labelKey, state[0]));
                refreshDependencies();
            }).dimensions(x, y, width, height).build();
            applyTip(widget, tipKey);
            return widget;
        });
    }

    /** 登记一个枚举循环行：点一下切到下一个取值（到头绕回第一个），文字走 {@link #enumLabel}。 */
    private <T extends Enum<T>> void cycle(String labelKey, String valuePrefix, T[] values, T current,
                                           Consumer<T> setter) {
        cycle(labelKey, valuePrefix, values, current, setter, null);
    }

    /**
     * 同上，另外支持「取值一变、这一页的别的控件就得重建」的行：{@code afterChange} 在取值换好、
     * 标脏之后跑。
     *
     * <p>目前只有停靠位置（anchor）用它：换成 {@code CUSTOM} 前后，下面两个位置滑条的标签、量程
     * 与提示各不相同（见 {@link #buildLayoutPage()}），必须整页重建才配得上。控件回调里重建界面
     * 在本工程是既有做法（见 {@link #addTabs()}）。</p>
     */
    private <T extends Enum<T>> void cycle(String labelKey, String valuePrefix, T[] values, T current,
                                           Consumer<T> setter, Runnable afterChange) {
        options.add((x, y, width, height) -> {
            int[] index = { current.ordinal() };
            ButtonWidget widget = ButtonWidget.builder(enumLabel(labelKey, valuePrefix, values[index[0]]), button -> {
                index[0] = (index[0] + 1) % values.length;
                T next = values[index[0]];
                setter.accept(next);
                markDirty();
                button.setMessage(enumLabel(labelKey, valuePrefix, next));
                if (afterChange != null) {
                    afterChange.run();
                }
            }).dimensions(x, y, width, height).build();
            return widget;
        });
    }

    /** 登记一个滑条行；onCommit 只接 {@link #markDirty()}，拖动期间它会被调几十上百次（README 13.15）。 */
    private void slider(String labelKey, double min, double max, double value, boolean integer, boolean autoAtZero,
                        DoubleConsumer setter, String tipKey) {
        options.add((x, y, width, height) -> {
            NumberSliderWidget widget = new NumberSliderWidget(x, y, width, height, labelKey, min, max,
                    value, integer, autoAtZero, setter, this::markDirty);
            // 注意：拖动期间 onCommit 会被调几十上百次，所以这里只能标脏，不能写盘。
            applyTip(widget, tipKey);
            return widget;
        });
    }

    /** 给了提示键才挂悬停提示（只有含义不直观的选项才带）。 */
    private static void applyTip(ClickableWidget widget, String tipKey) {
        if (tipKey != null) {
            widget.setTooltip(Tooltip.of(Text.translatable(tipKey)));
        }
    }

    // ------------------------------------------------------------------
    // 分页标签与底部按钮
    // ------------------------------------------------------------------

    /**
     * 当前页的标签加粗，并在标签列左侧画一条高亮竖线（见 {@link #render}），比做成禁用态更清楚。
     *
     * <p>点标签只改 {@link #tab} 再 {@code clearAndInit()}：换页会重建全部控件，所以
     * {@link #init()} 开头必须把行号字段复位（见 README 13.28）。</p>
     */
    private void addTabs() {
        for (int i = 0; i < TABS.length; i++) {
            Tab target = TABS[i];
            MutableText label = target.label();
            if (target == tab) {
                label.formatted(Formatting.BOLD);
            }
            addDrawableChild(ButtonWidget.builder(label, button -> {
                if (this.tab != target) {
                    this.tab = target;
                    this.status = null;
                    this.clearAndInit();
                }
            }).dimensions(layout.tabX(), layout.tabY(i), layout.tabW, layout.tabH).build());
        }
    }

    /** 底部一行四个按钮：宽度按选项块的总宽平分，所以窄窗口也不会溢出。 */
    private void addBottomButtons() {
        addDrawableChild(ButtonWidget
                .builder(Text.translatable("durahud.button.reset"), button -> {
                    cfg.resetToDefaults();
                    // 用户明确要求「恢复默认」：配置读坏时也要照写（这是唯一的重置出口），
                    // 写成功才算落盘，失败时状态行留着原来的内容让人再试一次。
                    if (ConfigFile.saveForced(cfg)) {
                        dirty = false;
                    }
                    this.clearAndInit();
                })
                .dimensions(layout.buttonX(0), layout.buttonY, layout.buttonW, ScreenLayout.BUTTON_H).build());
        addDrawableChild(ButtonWidget
                .builder(Text.translatable("durahud.button.export"), button -> this.exportPreset())
                .dimensions(layout.buttonX(1), layout.buttonY, layout.buttonW, ScreenLayout.BUTTON_H).build());
        addDrawableChild(ButtonWidget
                .builder(Text.translatable("durahud.button.import"), button -> this.importPreset())
                .dimensions(layout.buttonX(2), layout.buttonY, layout.buttonW, ScreenLayout.BUTTON_H).build());
        addDrawableChild(ButtonWidget
                .builder(Text.translatable("durahud.button.done"), button -> this.close())
                .dimensions(layout.buttonX(3), layout.buttonY, layout.buttonW, ScreenLayout.BUTTON_H).build());
    }

    /**
     * 从属选项置灰：无耐久装备的例外只在「只显示有耐久的物品」开着时有意义，
     * 动画时长只在「格子动画」开着时有意义。改任何选项后都重算一遍。
     */
    private void refreshDependencies() {
        setActive(wornNonDurableRow, cfg.onlyDurableItems);
        setActive(animationRow, cfg.animateCells);
    }

    /** 按下标置灰一行；下标为 -1（本页没有这一项）时什么都不做。 */
    private void setActive(int index, boolean active) {
        if (index >= 0 && index < widgets.size()) {
            widgets.get(index).active = active;
        }
    }

    /**
     * 导出预设：先弹系统的「另存为」框选路径，选完再写盘。
     *
     * <p>弹框会阻塞，所以「选路径 + 写盘」整段放进工作线程，完成后回主线程改提示文字。
     * 无图形环境时退回 config 目录下的默认文件名，与旧版行为一致。</p>
     */
    private void exportPreset() {
        // 快照一份：工作线程只读它，不受之后在界面上继续改设置的影响。
        DuraHudConfig snapshot = new DuraHudConfig();
        snapshot.copyFrom(cfg);
        if (!PresetDialogs.available()) {
            this.status = Text.translatable(ConfigFile.exportPreset(snapshot)
                    ? "durahud.preset.exported" : "durahud.preset.failed", ConfigFile.defaultPresetName());
            return;
        }
        this.status = Text.translatable("durahud.preset.choosing");
        File directory = dialogDirectory();
        String defaultName = ConfigFile.defaultPresetName();
        String title = Text.translatable("durahud.button.export").getString();
        Thread worker = new Thread(() -> {
            Path chosen = PresetDialogs.chooseSave(directory, defaultName, title);
            if (chosen == null) {
                runOnClient(() -> this.status = Text.translatable("durahud.preset.cancelled"));
                return;
            }
            boolean ok = ConfigFile.exportTo(chosen, snapshot);
            String name = chosen.getFileName().toString();
            runOnClient(() -> this.status = Text.translatable(
                    ok ? "durahud.preset.exported" : "durahud.preset.failed", name));
        }, "DuraHUD-preset-export");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 导入预设：先弹系统的「打开」框选文件，选完再读盘并整份覆盖当前配置。
     *
     * <p>与导出同样在工作线程里弹框；读回来的配置如果不能用（文件坏了），只显示提示，
     * 现有配置不动。</p>
     */
    private void importPreset() {
        if (!PresetDialogs.available()) {
            applyImported(ConfigFile.importPreset(), ConfigFile.defaultPresetName());
            return;
        }
        this.status = Text.translatable("durahud.preset.choosing");
        File directory = dialogDirectory();
        String defaultName = ConfigFile.defaultPresetName();
        String title = Text.translatable("durahud.button.import").getString();
        Thread worker = new Thread(() -> {
            Path chosen = PresetDialogs.chooseOpen(directory, defaultName, title);
            if (chosen == null) {
                runOnClient(() -> this.status = Text.translatable("durahud.preset.cancelled"));
                return;
            }
            DuraHudConfig imported = ConfigFile.importFrom(chosen);
            String name = chosen.getFileName().toString();
            runOnClient(() -> this.applyImported(imported, name));
        }, "DuraHUD-preset-import");
        worker.setDaemon(true);
        worker.start();
    }

    /** 原生框的初始目录：config 目录（拿不到就用系统默认）。 */
    private static File dialogDirectory() {
        Path parent = ConfigFile.presetPath().getParent();
        return parent == null ? null : parent.toFile();
    }

    /** 工作线程收尾统一回渲染线程：界面状态只在渲染线程上改。 */
    private static void runOnClient(Runnable action) {
        MinecraftClient.getInstance().execute(action);
    }

    /** 把导入的配置整份覆盖上去并重开界面；失败只显示提示（不破坏现有配置）。 */
    private void applyImported(DuraHudConfig imported, String name) {
        if (imported == null) {
            this.status = Text.translatable("durahud.preset.failed", name);
            return;
        }
        cfg.copyFrom(imported);
        // 导入是用户明确要求「就用这份配置」，所以走 saveForced：配置读坏时它同样是自救出口。
        // 写不进去（磁盘满 / 无权限）就如实报失败，别只显示「已导入」。
        if (!ConfigFile.saveForced(cfg)) {
            this.status = Text.translatable("durahud.preset.failed", name);
            return;
        }
        dirty = false;
        this.status = Text.translatable("durahud.preset.imported", name);
        if (MinecraftClient.getInstance().currentScreen == this) {
            this.clearAndInit();
        }
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    /**
     * 画背景、控件、当前页的高亮竖线与底部提示行。
     *
     * <p>开头必须先 {@code renderBackground}：{@code Screen.render} 只把 drawables 画一遍、
     * 不画背景（javap 实测，见 README 9.14），少了它从 ModMenu / 标题界面进来时背后是透的。</p>
     */
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // 原版每个界面都在自己的 render 开头调这句：Screen.render 只把 drawables 画一遍，
        // 不画背景（javap 实测，见 README 9.14）。少了它，从 Mod Menu / 标题界面进来时
        // 背后是透的，文字压在游戏画面上根本看不清。
        this.renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        if (layout == null) {
            return;
        }
        int active = tab.ordinal();
        // 竖排标签用左侧的一条高亮竖线标出当前页（标签文字本身再加粗，见 addTabs）。
        ctx.fill(layout.tabX() - 2, layout.tabY(active), layout.tabX(),
                layout.tabY(active) + layout.tabH, 0xFF6FE3FF);
        if (ConfigFile.loadFailed()) {
            // 常驻警告：磁盘上那份配置读不了、内存里是默认值 —— 也就是「这次的改动不会保存」。
            // 画在状态行正上方（12px 正好落在内容区与状态行之间的空档里，见 ScreenLayout）。
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("durahud.status.configBroken"), this.width / 2,
                    layout.statusY - 12, 0xFFFF7070);
        }
        if (this.status != null) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, this.status, this.width / 2,
                    layout.statusY, 0xFFF0F0F0);
        }
    }

    /** 关屏：先落盘再切回父界面（与 {@link #removed()} 共用 {@link #flush()}，重复调用也没事）。 */
    @Override
    public void close() {
        flush();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    /** 不暂停游戏：改设置时后面的世界继续跑，方便对着真实画面调位置。 */
    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------
    // 文本
    // ------------------------------------------------------------------

    /** 「<label>: 开 / 关」文本（复用原版 {@code options.on} / {@code options.off}，自动跟随语言）。 */
    private static Text onOff(String labelKey, boolean value) {
        return Text.translatable(labelKey).append(": ")
                .append(Text.translatable(value ? "options.on" : "options.off"));
    }

    /** 「<label>: <枚举值>」文本；枚举值那一半交给 {@link #enumValue(String, Enum)}。 */
    private static Text enumLabel(String labelKey, String valuePrefix, Enum<?> value) {
        return Text.translatable(labelKey).append(": ").append(enumValue(valuePrefix, value));
    }

    /** 优先用 <前缀>.<camelCase> 的翻译键；没有翻译时退回枚举原名，避免显示成空白。 */
    private static Text enumValue(String valuePrefix, Enum<?> value) {
        String key = valuePrefix + "." + camel(value.name());
        Text translated = Text.translatable(key);
        return key.equals(translated.getString()) ? Text.literal(value.name()) : translated;
    }

    /** 枚举名转小驼峰，拼出 {@code <prefix>.<camel>} 形式的翻译键（如 HOTBAR_ABOVE -> hotbarAbove）。 */
    private static String camel(String upper) {
        StringBuilder out = new StringBuilder(upper.length());
        boolean nextUpper = false;
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else if (out.length() == 0) {
                out.append(Character.toLowerCase(c));
            } else {
                out.append(nextUpper ? Character.toUpperCase(c) : Character.toLowerCase(c));
                nextUpper = false;
            }
        }
        return out.toString();
    }
}
