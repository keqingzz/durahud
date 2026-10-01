package dev.durahud.config;

import dev.durahud.DuraHudClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * 设置界面：选项按用途分成 6 页（顶部分页标签），每页最多 6 行。
 *
 * <p>所有位置与尺寸都来自 {@link ScreenLayout}（按当前窗口尺寸算），控件在造出来的时候就带上
 * 最终尺寸，所以 GUI 缩放调大、窗口变矮变窄时也不会把按钮挤出屏幕 —— 旧版把 17 行硬塞进
 * 一屏、行高压到 12 像素，只有界面尺寸 4 及以下才正常。</p>
 *
 * <p>每页的选项先登记成 {@link Option}（拿到位置与尺寸之后再造成控件），因此行数、列数、
 * 行高可以一次算准，不需要「先造再挪」。控件全部是原版控件（ButtonWidget / SliderWidget）。</p>
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

    private final Screen parent;
    private final DuraHudConfig cfg = DuraHudClient.config();
    /** 当前页的选项（登记顺序 = 布局顺序）。 */
    private final List<Option> options = new ArrayList<>();
    /** 造出来的控件，与 {@link #options} 一一对应。 */
    private final List<ClickableWidget> widgets = new ArrayList<>();
    private Tab tab = Tab.DISPLAY;
    private ScreenLayout layout;
    private Text status;
    /** 需要置灰的行下标（-1 = 本页没有）；置灰条件见 {@link #refreshDependencies()}。 */
    private int wornNonDurableRow = -1;
    private int animationRow = -1;

    public DuraHudConfigScreen(Screen parent) {
        super(Text.translatable("durahud.config.title").append(Text.literal(" v" + DuraHudClient.version())));
        this.parent = parent;
    }

    /** 一行选项：给最终位置与尺寸，造出控件。 */
    @FunctionalInterface
    private interface Option {
        ClickableWidget create(int x, int y, int width, int height);
    }

    @Override
    protected void init() {
        options.clear();
        widgets.clear();
        wornNonDurableRow = -1;
        animationRow = -1;
        buildPage();

        this.layout = ScreenLayout.of(this.width, this.height, options.size(), TABS.length);
        for (int i = 0; i < options.size(); i++) {
            ClickableWidget widget = options.get(i).create(
                    layout.columnX(layout.columnOf(i)), layout.rowY(i), layout.colW, layout.rowH);
            widgets.add(widget);
            addDrawableChild(widget);
        }
        addTabs();
        addBottomButtons();
        refreshDependencies();
    }

    // ------------------------------------------------------------------
    // 各页的选项
    // ------------------------------------------------------------------

    private void buildPage() {
        switch (tab) {
            case DISPLAY -> {
                toggle("durahud.option.enabled", cfg.enabled, v -> cfg.enabled = v, null);
                toggle("durahud.option.showMainhand", cfg.showMainhand, v -> cfg.showMainhand = v, null);
                toggle("durahud.option.showOffhand", cfg.showOffhand, v -> cfg.showOffhand = v, null);
                toggle("durahud.option.showArmor", cfg.showArmor, v -> cfg.showArmor = v, null);
                toggle("durahud.option.showTrinkets", cfg.showTrinkets, v -> cfg.showTrinkets = v, null);
            }
            case ITEMS -> {
                toggle("durahud.option.onlyDurableItems", cfg.onlyDurableItems, v -> cfg.onlyDurableItems = v,
                        "durahud.tip.onlyDurableItems");
                wornNonDurableRow = options.size();
                toggle("durahud.option.showWornNonDurable", cfg.showWornNonDurable, v -> cfg.showWornNonDurable = v,
                        "durahud.tip.showWornNonDurable");
                toggle("durahud.option.hideFull", cfg.hideFull, v -> cfg.hideFull = v, "durahud.tip.hideFull");
                toggle("durahud.option.showEmptySlots", cfg.showEmptySlots, v -> cfg.showEmptySlots = v,
                        "durahud.tip.showEmptySlots");
            }
            case LAYOUT -> {
                cycle("durahud.option.anchor", "durahud.anchor", DuraHudConfig.Anchor.values(), cfg.anchor,
                        v -> cfg.anchor = v);
                cycle("durahud.option.orientation", "durahud.option.orientation",
                        DuraHudConfig.Orientation.values(), cfg.orientation, v -> cfg.orientation = v);
                slider("durahud.option.scale", 0.5D, 3.0D, cfg.scale, false, false,
                        v -> cfg.scale = (float) v, null);
                slider("durahud.option.offsetX", -200.0D, 200.0D, cfg.offsetX, true, false,
                        v -> cfg.offsetX = (int) v, null);
                slider("durahud.option.offsetY", -200.0D, 200.0D, cfg.offsetY, true, false,
                        v -> cfg.offsetY = (int) v, null);
                slider("durahud.option.barLength", 0.0D, 182.0D, cfg.barLength, true, true,
                        v -> cfg.barLength = (int) v, null);
            }
            case LOOK -> {
                cycle("durahud.option.barPlacement", "durahud.option.barPlacement",
                        DuraHudConfig.BarSide.values(), cfg.barPlacement, v -> cfg.barPlacement = v);
                cycle("durahud.option.valuePlacement", "durahud.option.valuePlacement",
                        DuraHudConfig.ValuePlacement.values(), cfg.valuePlacement, v -> cfg.valuePlacement = v);
                cycle("durahud.option.valueDisplay", "durahud.valueDisplay", DuraHudConfig.ValueDisplay.values(),
                        cfg.valueDisplay, v -> cfg.valueDisplay = v);
                toggle("durahud.option.showSlotFrames", cfg.showSlotFrames, v -> cfg.showSlotFrames = v, null);
                toggle("durahud.option.showItemIcons", cfg.showItemIcons, v -> cfg.showItemIcons = v, null);
            }
            case WARNING -> {
                slider("durahud.option.warnThreshold", 1.0D, 100.0D, cfg.warnThreshold, true, false,
                        v -> cfg.warnThreshold = (int) v, null);
                slider("durahud.option.critThreshold", 0.0D, 99.0D, cfg.critThreshold, true, false,
                        v -> cfg.critThreshold = (int) v, null);
                toggle("durahud.option.highlightCritical", cfg.highlightCritical, v -> cfg.highlightCritical = v, null);
            }
            case ANIMATION -> {
                toggle("durahud.option.animateCells", cfg.animateCells, v -> cfg.animateCells = v, null);
                animationRow = options.size();
                slider("durahud.option.animationMillis", 50.0D, 1000.0D, cfg.animationMillis, true, false,
                        v -> cfg.animationMillis = (int) v, "durahud.tip.animationMillis");
            }
        }
    }

    private void toggle(String labelKey, boolean value, Consumer<Boolean> setter, String tipKey) {
        options.add((x, y, width, height) -> {
            boolean[] state = { value };
            ButtonWidget widget = ButtonWidget.builder(onOff(labelKey, state[0]), button -> {
                state[0] = !state[0];
                setter.accept(state[0]);
                cfg.save();
                button.setMessage(onOff(labelKey, state[0]));
                refreshDependencies();
            }).dimensions(x, y, width, height).build();
            applyTip(widget, tipKey);
            return widget;
        });
    }

    private <T extends Enum<T>> void cycle(String labelKey, String valuePrefix, T[] values, T current,
                                           Consumer<T> setter) {
        options.add((x, y, width, height) -> {
            int[] index = { current.ordinal() };
            ButtonWidget widget = ButtonWidget.builder(enumLabel(labelKey, valuePrefix, values[index[0]]), button -> {
                index[0] = (index[0] + 1) % values.length;
                T next = values[index[0]];
                setter.accept(next);
                cfg.save();
                button.setMessage(enumLabel(labelKey, valuePrefix, next));
            }).dimensions(x, y, width, height).build();
            return widget;
        });
    }

    private void slider(String labelKey, double min, double max, double value, boolean integer, boolean autoAtZero,
                        DoubleConsumer setter, String tipKey) {
        options.add((x, y, width, height) -> {
            NumberSliderWidget widget = new NumberSliderWidget(x, y, width, height, labelKey, min, max,
                    value, integer, autoAtZero, setter, cfg::save);
            applyTip(widget, tipKey);
            return widget;
        });
    }

    private static void applyTip(ClickableWidget widget, String tipKey) {
        if (tipKey != null) {
            widget.setTooltip(Tooltip.of(Text.translatable(tipKey)));
        }
    }

    // ------------------------------------------------------------------
    // 分页标签与底部按钮
    // ------------------------------------------------------------------

    /** 当前页的标签加粗，并在下面画一条高亮线（见 {@link #render}），比做成禁用态更清楚。 */
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
            }).dimensions(layout.tabX(i), layout.tabY(i), layout.tabW, layout.tabH).build());
        }
    }

    /** 底部一行四个按钮：宽度按选项块的总宽平分，所以窄窗口也不会溢出。 */
    private void addBottomButtons() {
        addDrawableChild(ButtonWidget
                .builder(Text.translatable("durahud.button.reset"), button -> {
                    cfg.resetToDefaults();
                    cfg.save();
                    this.clearAndInit();
                })
                .dimensions(layout.buttonX(0), layout.buttonY, layout.buttonW, ScreenLayout.BUTTON_H).build());
        addDrawableChild(ButtonWidget
                .builder(Text.translatable("durahud.button.export"), button -> this.status = Text.translatable(
                        ConfigFile.exportPreset(cfg) ? "durahud.preset.exported" : "durahud.preset.failed"))
                .dimensions(layout.buttonX(1), layout.buttonY, layout.buttonW, ScreenLayout.BUTTON_H).build());
        addDrawableChild(ButtonWidget
                .builder(Text.translatable("durahud.button.import"), button -> this.applyPreset())
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

    private void setActive(int index, boolean active) {
        if (index >= 0 && index < widgets.size()) {
            widgets.get(index).active = active;
        }
    }

    /** 导入预设：成功就整份覆盖当前配置并重开界面，失败只显示提示（不破坏现有配置）。 */
    private void applyPreset() {
        DuraHudConfig imported = ConfigFile.importPreset();
        if (imported == null) {
            this.status = Text.translatable("durahud.preset.failed");
            return;
        }
        cfg.copyFrom(imported);
        cfg.save();
        this.status = Text.translatable("durahud.preset.imported");
        this.clearAndInit();
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (layout == null) {
            return;
        }
        int active = tab.ordinal();
        ctx.fill(layout.tabX(active), layout.tabY(active) + layout.tabH - 2,
                layout.tabX(active) + layout.tabW, layout.tabY(active) + layout.tabH, 0xFF6FE3FF);
        if (this.status != null) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, this.status, this.width / 2,
                    layout.statusY, 0xFFF0F0F0);
        }
    }

    @Override
    public void close() {
        cfg.save();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------
    // 文本
    // ------------------------------------------------------------------

    private static Text onOff(String labelKey, boolean value) {
        return Text.translatable(labelKey).append(": ")
                .append(Text.translatable(value ? "options.on" : "options.off"));
    }

    private static Text enumLabel(String labelKey, String valuePrefix, Enum<?> value) {
        return Text.translatable(labelKey).append(": ").append(enumValue(valuePrefix, value));
    }

    /** 优先用 <前缀>.<camelCase> 的翻译键；没有翻译时退回枚举原名，避免显示成空白。 */
    private static Text enumValue(String valuePrefix, Enum<?> value) {
        String key = valuePrefix + "." + camel(value.name());
        Text translated = Text.translatable(key);
        return key.equals(translated.getString()) ? Text.literal(value.name()) : translated;
    }

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
