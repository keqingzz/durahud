package dev.durahud.hud;

import dev.durahud.config.DuraHudConfig;
import net.minecraft.class_1799;
import net.minecraft.class_327;

/**
 * 一个格子「本帧要画的东西」的快照：物品图标 + 耐久条 + 数值。
 *
 * <p>每个 {@link DurabilityEntry} 常驻一个实例，有实物内容时每帧就地刷新（不产生垃圾）；
 * 内容消失后<b>不再刷新</b>，最后一份快照自然就成了淡出用的替身 —— 淡出期间画的仍是
 * 原来那把镐、原来那条耐久条、原来那个百分比，而不是「空槽位」的样子。</p>
 *
 * <p>数值文字也在这里缓存：内容与显示模式不变时直接复用上一次拼好的字符串和量好的字宽
 * （用 {@link #fingerprint()} 判断），所以数字没变的那些帧没有任何字符串分配与字形查询。</p>
 */
public final class CellContent {

    private class_1799 stack = class_1799.field_8037;
    private boolean bar;
    private float ratio = 1.0F;
    private boolean numeric;
    private int remaining;
    private int max;

    /** 缓存的标签：文字、字宽，以及算它时用的指纹与显示模式。 */
    private String label = "";
    private int labelWidth;
    private int labelKey;
    private DuraHudConfig.ValueDisplay labelDisplay;
    private boolean labelValid;

    /** 就地刷新（包内可见，只由 {@link DurabilityEntry#advance} 调用）。 */
    void capture(DurabilityEntry entry) {
        this.stack = entry.stack();
        this.bar = entry.hasWear();
        this.ratio = entry.ratio();
        this.numeric = entry.hasNumeric();
        this.remaining = entry.remaining();
        this.max = entry.max();
    }

    public class_1799 stack() {
        return stack;
    }

    /** 这一份内容要不要画耐久条（替身保留它当时的答案）。 */
    public boolean hasBar() {
        return bar;
    }

    public float ratio() {
        return ratio;
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
     * 本帧要画的数值文字（不显示时是空串）。只有内容或显示模式变了才重新拼、重新量宽度。
     *
     * @param font 用来量字宽；即使缓存命中也不会用到它
     */
    public String label(DuraHudConfig cfg, class_327 font) {
        int key = fingerprint();
        if (!labelValid || key != labelKey || cfg.valueDisplay != labelDisplay) {
            labelDisplay = cfg.valueDisplay;
            labelKey = key;
            labelValid = true;
            label = CellLabel.text(cfg.valueDisplay, bar, numeric, remaining, max, percent());
            labelWidth = label.isEmpty() ? 0 : font.method_1727(label);
        }
        return label;
    }

    /** 上一次 {@link #label} 算出来的字宽（和它同一次缓存，不会不同步）。 */
    public int labelWidth() {
        return labelWidth;
    }

    /** 内容的指纹：任何会改变标签的字段变了，指纹就变了。 */
    private int fingerprint() {
        int key = remaining * 31 + max;
        key = key * 31 + percent();
        key = key * 31 + (bar ? 1 : 0);
        return key * 31 + (numeric ? 1 : 0);
    }
}
