package dev.durahud.config;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;

/**
 * 数值滑条：滑条位置是 0..1，显示值线性映射到 [min, max]；整数模式四舍五入，
 * 否则保留两位小数。autoAtZero 时值 <= 0 显示成"自动"（耐久条长度用）。
 *
 * <p>包内可见，使用者只有 {@link DuraHudConfigScreen}。位置与尺寸必须构造时一次给对：
 * {@code ClickableWidget} 没有 {@code setHeight}，造出来的控件高度改不了（见 README 13.11），
 * 所以尺寸一律由 {@link ScreenLayout} 先算好再传进来。</p>
 *
 * <p>回调纪律：{@code setter} 只改配置对象、{@code onCommit} 只置 dirty，两者都不写盘 ——
 * 拖一次滑条会触发几十上百次回调（见 README 13.15）。</p>
 */
final class NumberSliderWidget extends SliderWidget {

    /** 语言键；{@code null} 表示不写文字，只画滑条。 */
    private final String labelKey;
    /** 显示值下限（滑块最左端）。 */
    private final double min;
    /** 显示值上限（滑块最右端）。 */
    private final double max;
    /** true = 取整显示（阈值 / 像素 / 毫秒），false = 保留两位小数（缩放）。 */
    private final boolean integer;
    /** true = 值 <= 0 时把数字换成「自动」文案（耐久条长度 0 = 自动）。 */
    private final boolean autoAtZero;
    /** 值变化回调：只写配置字段。 */
    private final DoubleConsumer setter;
    /** 提交回调：只置 dirty。 */
    private final Runnable onCommit;

    /** 位置与尺寸取自 {@link ScreenLayout}，构造后不再改动（见 README 13.11）。 */
    NumberSliderWidget(int x, int y, int width, int height, String labelKey, double min, double max,
                       double value, boolean integer, boolean autoAtZero,
                       DoubleConsumer setter, Runnable onCommit) {
        super(x, y, width, height, Text.empty(), fraction(min, max, value));
        this.labelKey = labelKey;
        this.min = min;
        this.max = max;
        this.integer = integer;
        this.autoAtZero = autoAtZero;
        this.setter = setter;
        this.onCommit = onCommit;
        updateMessage();
    }

    /** 值 -> 0..1 的滑条位置；NaN、Infinity、越界值全部夹到端点。 */
    private static double fraction(double min, double max, double value) {
        if (!(max > min) || !Double.isFinite(value)) {
            return 0.0D;
        }
        return (Math.max(min, Math.min(max, value)) - min) / (max - min);
    }

    /**
     * 从外部改写配置后同步滑条显示（例如 warn 变小、crit 被一起夹小）。
     * 位置与显示一起更新，避免「配置里是 9、滑条上还写着 50」。
     */
    void showValue(double value) {
        this.value = fraction(this.min, this.max, value);
        updateMessage();
    }

    /** 滑块位置换算回显示值：整数模式四舍五入，否则保留两位小数。 */
    private double actual() {
        double raw = min + (max - min) * this.value;
        return integer ? Math.round(raw) : Math.round(raw * 100.0D) / 100.0D;
    }

    /** 重建控件上的文字；{@code labelKey} 为 null 时什么都不做（原版每次拖动都会调到这里）。 */
    @Override
    protected void updateMessage() {
        if (this.labelKey == null) {
            return;
        }
        double current = actual();
        if (autoAtZero && current <= 0.0D) {
            setMessage(Text.translatable(this.labelKey).append(": ").append(Text.translatable("durahud.value.auto")));
            return;
        }
        setMessage(Text.translatable(this.labelKey).append(": "
                + (integer ? String.valueOf((long) current) : String.valueOf(current))));
    }

    /** 原版回调：滑块动了就通知 setter 与 onCommit（都只改内存，落盘留到关屏时一次做完）。 */
    @Override
    protected void applyValue() {
        if (this.setter != null) {
            this.setter.accept(actual());
        }
        if (this.onCommit != null) {
            this.onCommit.run();
        }
    }
}
