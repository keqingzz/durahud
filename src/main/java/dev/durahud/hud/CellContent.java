package dev.durahud.hud;

import dev.durahud.config.DuraHudConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.item.ItemStack;

/**
 * 一个格子「本帧要画的东西」的快照：物品图标 + 耐久条 + 数值。
 *
 * <p>每个 {@link DurabilityEntry} 常驻一个实例，有实物内容时每帧就地刷新（不产生垃圾）；
 * 内容消失后<b>不再刷新</b>，最后一份快照自然就成了淡出用的替身 —— 淡出期间画的仍是
 * 原来那把镐、原来那条耐久条、原来那个百分比，而不是「空槽位」的样子。</p>
 *
 * <p><b>真实值与显示值是两件事</b>：{@link #ratio()} / {@link #percent()} 是这一帧的真实
 * 耐久（颜色、濒危判定用它，必须立刻是准的），{@link #displayRatio()} 是加减动画当前的
 * 显示值（<b>条长与数字用它</b>）。两者之间是一个 {@link ValueTween}：掉一格耐久时条会
 * 短一截、数字会滚一下，而且带加减速度，不是硬跳（见 README 7.9）。</p>
 *
 * <p>数值文字也在这里缓存：内容、显示模式与<b>显示值</b>都不变时直接复用上一次拼好的字符串
 * 和量好的字宽（用 {@link #fingerprint()} 判断），所以数字没变的那些帧没有任何字符串分配
 * 与字形查询；动画期间显示值每帧都在变，但指纹取的是<b>四舍五入后的整数</b>，
 * 只有数字真的翻一位时才重拼一次。</p>
 *
 * <p>VALUE 模式的「剩余 / 上限」是<b>两行</b>：两行在同一次刷新里一起拼好、一起量宽
 * （{@link #label} / {@link #secondLabel}）。只有第一行参与加减动画 —— 上限是个常量。</p>
 */
public final class CellContent {

    private ItemStack stack = ItemStack.EMPTY;
    private boolean bar;
    private float ratio = 1.0F;
    private boolean numeric;
    private int remaining;
    private int max;

    /** 条长与数字共用的加减动画：真实比例 -> 显示比例。 */
    private final ValueTween tween = new ValueTween();

    /** 缓存的第一行标签：文字、字宽，以及算它时用的指纹与显示模式。 */
    private String label = "";
    private int labelWidth;
    /** 缓存的第二行标签（VALUE 模式的上限）；与第一行同一次刷新。 */
    private String secondLabel = "";
    private int secondLabelWidth;
    private int labelKey;
    private DuraHudConfig.ValueDisplay labelDisplay;
    private boolean labelValid;

    /**
     * 就地刷新（包内可见，只由 {@link DurabilityEntry#advance} 调用）。
     *
     * <p>真实值照抄，显示值交给加减动画推进；关掉 {@link DuraHudConfig#animateCells}
     * 时显示值直接等于真实值，也就是旧版本「数字与条一帧到位」的样子。</p>
     */
    void capture(DurabilityEntry entry, DuraHudConfig cfg, float dtSeconds) {
        this.stack = entry.stack();
        this.bar = entry.hasWear();
        this.ratio = entry.ratio();
        this.numeric = entry.hasNumeric();
        this.remaining = entry.remaining();
        this.max = entry.max();
        if (cfg.animateCells) {
            tween.update(this.ratio, dtSeconds, cfg.animationMillis);
        } else {
            tween.snap(this.ratio);
        }
    }

    /**
     * 把另一份快照整个复制过来（换物过渡开始时，把「当前内容」留作离场的替身）。
     *
     * <p>物品栈是复制一份而不是共用引用：手持物被消耗掉时原栈会就地变空，替身要留着当时的
     * 样子 —— 淡出期间画的仍是换成别的东西之前的那一件。标签缓存刻意不复制，指纹重算；
     * 加减动画则<b>就地冻住</b>：离场那条条/那个数字保持它离开时的长度，不再跟着新物品变。</p>
     */
    void copyFrom(CellContent other) {
        this.stack = other.stack.copy();
        this.bar = other.bar;
        this.ratio = other.ratio;
        this.numeric = other.numeric;
        this.remaining = other.remaining;
        this.max = other.max;
        this.tween.snap(other.displayRatio());
        this.labelValid = false;
    }

    public ItemStack stack() {
        return stack;
    }

    /** 这一份内容要不要画耐久条（替身保留它当时的答案）。 */
    public boolean hasBar() {
        return bar;
    }

    /** 这一帧的<b>真实</b>耐久比例，用于配色与濒危判定；条长请看 {@link #displayRatio()}。 */
    public float ratio() {
        return ratio;
    }

    /** 加减动画当前的显示比例 0..1：耐久条的长度按它画。 */
    public float displayRatio() {
        float value = tween.value();
        return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
    }

    /** 耐久比例四舍五入成的整数百分比；既是显示用的数，也是缓存指纹的一部分。 */
    public int percent() {
        return Math.round(ratio * 100.0F);
    }

    /** 能不能显示「剩余/上限」这种精确数值。 */
    public boolean hasNumeric() {
        return numeric;
    }

    public int remaining() {
        return remaining;
    }

    public int max() {
        return max;
    }

    /**
     * 本帧要画的数值文字（不显示时是空串）。只有内容、显示模式或显示值变了才重新拼、
     * 重新量宽度 —— 动画期间数字每翻一位才算「变了」。
     *
     * @param font 用来量字宽；即使缓存命中也不会用到它
     */
    public String label(DuraHudConfig cfg, TextRenderer font) {
        int key = fingerprint();
        if (!labelValid || key != labelKey || cfg.valueDisplay != labelDisplay) {
            labelDisplay = cfg.valueDisplay;
            labelKey = key;
            labelValid = true;
            label = CellLabel.text(cfg.valueDisplay, bar, numeric, displayRemaining(), max, displayPercent());
            labelWidth = label.isEmpty() ? 0 : font.getWidth(label);
            secondLabel = CellLabel.secondLine(cfg.valueDisplay, numeric, max);
            secondLabelWidth = secondLabel.isEmpty() ? 0 : font.getWidth(secondLabel);
        }
        return label;
    }

    /** 上一次 {@link #label} 算出来的字宽（和它同一次缓存，不会不同步）。 */
    public int labelWidth() {
        return labelWidth;
    }

    /**
     * 第二行文字（VALUE 模式的上限，其余模式是空串），画在 {@link #label} 的下一行。
     *
     * <p>与 {@link #label} <b>同一次</b>刷新：画之前必须先调 {@code label(cfg, font)}，
     * 否则拿到的可能是上一帧的那一份（绘制路径本来就是先第一行、再第二行，见
     * {@code HudRenderer.drawGaugeLayer}）。</p>
     */
    public String secondLabel() {
        return secondLabel;
    }

    /** 第二行的字宽，同样与 {@link #label} 同步缓存；第二行不存在时是 0。 */
    public int secondLabelWidth() {
        return secondLabelWidth;
    }

    /** 显示值下的剩余耐久：动画没走完时它是中间的整数（VALUE 模式显示这个）。 */
    private int displayRemaining() {
        if (numeric && max > 0) {
            int value = Math.round(displayRatio() * max);
            return Math.max(0, Math.min(max, value));
        }
        return remaining;
    }

    /** 显示值下的百分比（PERCENT 模式显示这个）。 */
    private int displayPercent() {
        return Math.round(displayRatio() * 100.0F);
    }

    /** 内容的指纹：任何会改变标签的字段变了，指纹就变了。 */
    private int fingerprint() {
        int key = displayRemaining() * 31 + max;
        key = key * 31 + displayPercent();
        key = key * 31 + (bar ? 1 : 0);
        return key * 31 + (numeric ? 1 : 0);
    }
}
