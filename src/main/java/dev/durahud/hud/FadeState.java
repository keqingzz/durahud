package dev.durahud.hud;

/**
 * 淡入淡出与平滑移动共用的纯数学：进度推进 + 两种缓动。故意不引用任何 Minecraft 类型，
 * 于是可以像 {@link Metrics}、{@link HudLayout} 那样直接单测（见 FadeStateTest）。
 */
public final class FadeState {

    private FadeState() {
    }

    /**
     * 把进度朝目标推进一帧。{@code durationMillis} 是「从 0 走到 1 需要的毫秒数」，
     * 所以步长只与时间有关、与帧率无关（同样的时长，60fps 与 200fps 看起来一样快）。
     * 一步跨过目标时直接吸附到目标，不会过冲、也不会在目标附近来回抖。
     *
     * @param current        当前进度（超出 0..1 会被夹住）
     * @param target         目标：{@code > 0} 当作 1（淡入），否则当作 0（淡出）
     * @param dtSeconds      距上一帧的秒数；负数（时钟回拨）当作 0，即这一帧不动
     * @param durationMillis 完整走完 0..1 所需的毫秒数；{@code <= 0} 表示立即到位
     * @return 新的进度，范围 0..1
     */
    public static float approach(float current, float target, float dtSeconds, int durationMillis) {
        float goal = target > 0.0F ? 1.0F : 0.0F;
        float now = clamp(current);
        if (durationMillis <= 0) {
            return goal;
        }
        float step = clamp(dtSeconds) * 1000.0F / durationMillis;
        if (now < goal) {
            return Math.min(goal, now + step);
        }
        if (now > goal) {
            return Math.max(goal, now - step);
        }
        return goal;
    }

    /** 平滑缓动（smoothstep）：两端导数为 0，起步与收尾都比线性更软。 */
    public static float smooth(float progress) {
        float p = clamp(progress);
        return p * p * (3.0F - 2.0F * p);
    }

    /** 绘制用的不透明度：关掉动画时恒为 1，也就是旧版本「直接出现/直接消失」的样子。 */
    public static float alpha(float progress, boolean animate) {
        return animate ? smooth(progress) : 1.0F;
    }

    /**
     * 平滑移动：把当前坐标按指数缓动拉向目标，帧率无关。时间常数取 {@code durationMillis / 3}，
     * 也就是大约 durationMillis 之后走完 95% 的距离 —— 距离远时快、临近目标时慢，看着像滑过去。
     *
     * <p>这里必须自己吸附：指数缓动永远到不了目标。差 0.25 像素以内就直接落到目标上，
     * 格子停稳的那一帧因此是像素对齐的（否则会一直亚像素抖动，贴图发虚）。</p>
     *
     * @param current        当前坐标（缩放后的屏幕像素，可以是小数）
     * @param target         目标坐标
     * @param dtSeconds      距上一帧的秒数；负数（时钟回拨）当作 0，即这一帧不动
     * @param durationMillis 期望的滑动时长（毫秒）；{@code <= 0} 表示立即到位
     * @return 新坐标，落在 current 与 target 之间，不会过冲
     */
    public static float slide(float current, float target, float dtSeconds, int durationMillis) {
        if (durationMillis <= 0) {
            return target;
        }
        float dt = clamp(dtSeconds);
        if (dt <= 0.0F) {
            return current;
        }
        float tau = durationMillis / 3000.0F;
        float next = current + (target - current) * (1.0F - (float) Math.exp(-dt / tau));
        return Math.abs(target - next) < 0.25F ? target : next;
    }

    private static float clamp(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        return value > 1.0F ? 1.0F : value;
    }
}
