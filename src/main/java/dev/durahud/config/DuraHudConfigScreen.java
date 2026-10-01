package dev.durahud.config;

import dev.durahud.DuraHudClient;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import net.minecraft.class_124;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_339;
import net.minecraft.class_4185;
import net.minecraft.class_437;
import net.minecraft.class_5250;
import net.minecraft.class_7919;

/**
 * 设置界面：选项按用途分成 6 页（分页标签竖排在窗口左侧、垂直居中），每页最多 6 行。
 *
 * <p>所有位置与尺寸都来自 {@link ScreenLayout}（按当前窗口尺寸算），控件在造出来的时候就带上
 * 最终尺寸，所以 GUI 缩放调大、窗口变矮变窄时也不会把按钮挤出屏幕 —— 旧版把 17 行硬塞进
 * 一屏、行高压到 12 像素，只有界面尺寸 4 及以下才正常。</p>
 *
 * <p>每页的选项先登记成 {@link Option}（拿到位置与尺寸之后再造成控件），因此行数、列数、
 * 行高可以一次算准，不需要「先造再挪」。控件全部是原版控件（ButtonWidget / SliderWidget）。</p>
 */
public class DuraHudConfigScreen extends class_437 {

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

        class_5250 label() {
            return class_2561.method_43471("durahud.tab." + key);
        }
    }

    private static final Tab[] TABS = Tab.values();

    private final class_437 parent;
    private final DuraHudConfig cfg = DuraHudClient.config();
    /** 当前页的选项（登记顺序 = 布局顺序）。 */
    private final List<Option> options = new ArrayList<>();
    /** 造出来的控件，与 {@link #options} 一一对应。 */
    private final List<class_339> widgets = new ArrayList<>();
    private Tab tab = Tab.DISPLAY;
    private ScreenLayout layout;
    private class_2561 status;
    /** 需要置灰的行下标（-1 = 本页没有）；置灰条件见 {@link #refreshDependencies()}。 */
    private int wornNonDurableRow = -1;
    private int animationRow = -1;

    public DuraHudConfigScreen(class_437 parent) {
        super(class_2561.method_43471("durahud.config.title").method_10852(class_2561.method_43470(" v" + DuraHudClient.version())));
        this.parent = parent;
    }

    /** 一行选项：给最终位置与尺寸，造出控件。 */
    @FunctionalInterface
    private interface Option {
        class_339 create(int x, int y, int width, int height);
    }

    @Override
    protected void method_25426() {
        options.clear();
        widgets.clear();
        wornNonDurableRow = -1;
        animationRow = -1;
        buildPage();

        this.layout = ScreenLayout.of(this.field_22789, this.field_22790, options.size(), TABS.length);
        for (int i = 0; i < options.size(); i++) {
            class_339 widget = options.get(i).create(
                    layout.columnX(layout.columnOf(i)), layout.rowY(i), layout.colW, layout.rowH);
            widgets.add(widget);
            method_37063(widget);
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
            class_4185 widget = class_4185.method_46430(onOff(labelKey, state[0]), button -> {
                state[0] = !state[0];
                setter.accept(state[0]);
                cfg.save();
                button.method_25355(onOff(labelKey, state[0]));
                refreshDependencies();
            }).method_46434(x, y, width, height).method_46431();
            applyTip(widget, tipKey);
            return widget;
        });
    }

    private <T extends Enum<T>> void cycle(String labelKey, String valuePrefix, T[] values, T current,
                                           Consumer<T> setter) {
        options.add((x, y, width, height) -> {
            int[] index = { current.ordinal() };
            class_4185 widget = class_4185.method_46430(enumLabel(labelKey, valuePrefix, values[index[0]]), button -> {
                index[0] = (index[0] + 1) % values.length;
                T next = values[index[0]];
                setter.accept(next);
                cfg.save();
                button.method_25355(enumLabel(labelKey, valuePrefix, next));
            }).method_46434(x, y, width, height).method_46431();
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

    private static void applyTip(class_339 widget, String tipKey) {
        if (tipKey != null) {
            widget.method_47400(class_7919.method_47407(class_2561.method_43471(tipKey)));
        }
    }

    // ------------------------------------------------------------------
    // 分页标签与底部按钮
    // ------------------------------------------------------------------

    /** 当前页的标签加粗，并在下面画一条高亮线（见 {@link #method_25394}），比做成禁用态更清楚。 */
    private void addTabs() {
        for (int i = 0; i < TABS.length; i++) {
            Tab target = TABS[i];
            class_5250 label = target.label();
            if (target == tab) {
                label.method_27692(class_124.field_1067);
            }
            method_37063(class_4185.method_46430(label, button -> {
                if (this.tab != target) {
                    this.tab = target;
                    this.status = null;
                    this.method_41843();
                }
            }).method_46434(layout.tabX(), layout.tabY(i), layout.tabW, layout.tabH).method_46431());
        }
    }

    /** 底部一行四个按钮：宽度按选项块的总宽平分，所以窄窗口也不会溢出。 */
    private void addBottomButtons() {
        method_37063(class_4185
                .method_46430(class_2561.method_43471("durahud.button.reset"), button -> {
                    cfg.resetToDefaults();
                    cfg.save();
                    this.method_41843();
                })
                .method_46434(layout.buttonX(0), layout.buttonY, layout.buttonW, ScreenLayout.BUTTON_H).method_46431());
        method_37063(class_4185
                .method_46430(class_2561.method_43471("durahud.button.export"), button -> this.status = class_2561.method_43471(
                        ConfigFile.exportPreset(cfg) ? "durahud.preset.exported" : "durahud.preset.failed"))
                .method_46434(layout.buttonX(1), layout.buttonY, layout.buttonW, ScreenLayout.BUTTON_H).method_46431());
        method_37063(class_4185
                .method_46430(class_2561.method_43471("durahud.button.import"), button -> this.applyPreset())
                .method_46434(layout.buttonX(2), layout.buttonY, layout.buttonW, ScreenLayout.BUTTON_H).method_46431());
        method_37063(class_4185
                .method_46430(class_2561.method_43471("durahud.button.done"), button -> this.method_25419())
                .method_46434(layout.buttonX(3), layout.buttonY, layout.buttonW, ScreenLayout.BUTTON_H).method_46431());
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
            widgets.get(index).field_22763 = active;
        }
    }

    /** 导入预设：成功就整份覆盖当前配置并重开界面，失败只显示提示（不破坏现有配置）。 */
    private void applyPreset() {
        DuraHudConfig imported = ConfigFile.importPreset();
        if (imported == null) {
            this.status = class_2561.method_43471("durahud.preset.failed");
            return;
        }
        cfg.copyFrom(imported);
        cfg.save();
        this.status = class_2561.method_43471("durahud.preset.imported");
        this.method_41843();
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    @Override
    public void method_25394(class_332 ctx, int mouseX, int mouseY, float delta) {
        // 原版每个界面都在自己的 render 开头调这句：Screen.render 只把 drawables 画一遍，
        // 不画背景（javap 实测，见 README 9.14）。少了它，从 Mod Menu / 标题界面进来时
        // 背后是透的，文字压在游戏画面上根本看不清。
        this.method_25420(ctx);
        super.method_25394(ctx, mouseX, mouseY, delta);
        if (layout == null) {
            return;
        }
        int active = tab.ordinal();
        // 竖排标签用左侧的一条高亮竖线标出当前页（标签文字本身再加粗，见 addTabs）。
        ctx.method_25294(layout.tabX() - 2, layout.tabY(active), layout.tabX(),
                layout.tabY(active) + layout.tabH, 0xFF6FE3FF);
        if (this.status != null) {
            ctx.method_27534(this.field_22793, this.status, this.field_22789 / 2,
                    layout.statusY, 0xFFF0F0F0);
        }
    }

    @Override
    public void method_25419() {
        cfg.save();
        if (this.field_22787 != null) {
            this.field_22787.method_1507(this.parent);
        }
    }

    @Override
    public boolean method_25421() {
        return false;
    }

    // ------------------------------------------------------------------
    // 文本
    // ------------------------------------------------------------------

    private static class_2561 onOff(String labelKey, boolean value) {
        return class_2561.method_43471(labelKey).method_27693(": ")
                .method_10852(class_2561.method_43471(value ? "options.on" : "options.off"));
    }

    private static class_2561 enumLabel(String labelKey, String valuePrefix, Enum<?> value) {
        return class_2561.method_43471(labelKey).method_27693(": ").method_10852(enumValue(valuePrefix, value));
    }

    /** 优先用 <前缀>.<camelCase> 的翻译键；没有翻译时退回枚举原名，避免显示成空白。 */
    private static class_2561 enumValue(String valuePrefix, Enum<?> value) {
        String key = valuePrefix + "." + camel(value.name());
        class_2561 translated = class_2561.method_43471(key);
        return key.equals(translated.getString()) ? class_2561.method_43470(value.name()) : translated;
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
