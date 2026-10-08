package dev.durahud.config;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;

/**
 * 数值滑条：滑条位置是 0..1，显示值线性映射到 [min, max]；整数模式四舍五入，
 * 否则保留两位小数。autoAtZero 时值 <= 0 显示成"自动"（耐久条长度用）。
 */
final class NumberSliderWidget extends SliderWidget {

    private final String labelKey;
    private final double min;
    private final double max;
    private final boolean integer;
    private final boolean autoAtZero;
    private final DoubleConsumer setter;
    private final Runnable onCommit;

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

    private double actual() {
        double raw = min + (max - min) * this.value;
        return integer ? Math.round(raw) : Math.round(raw * 100.0D) / 100.0D;
    }

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
        setMessage(Text.translatable(this.labelKey).append(": " + (integer ? String.valueOf((long) current) : String.valueOf(current))));
    }

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
