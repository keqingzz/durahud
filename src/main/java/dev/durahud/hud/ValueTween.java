package dev.durahud.hud;

/**
 * 耐久数值与耐久条共用的「加减动画」：把显示值缓动地推向目标值。
 *
 * <p>掉一格耐久不是硬跳，而是从<b>当前显示值</b>出发、用时 {@code durationMillis} 走完这段
 * 距离；中间用 {@link FadeState#smooth(float)} 缓动，所以起步慢、中段快、收尾又慢 —— 加减
 * 有自己的<b>加速度</b>，既不是匀速滑动，也不是一瞬间跳过去。</p>
 *
 * <p>目标在动画途中又变了（连续挖方块、被连续打、连续换工具）时，从<b>当前位置</b>重新
 * 起步：不会先跑到旧目标再折返，也不会因为插值起点没更新而抖一下；走完直接吸附到目标，
 * 不会过冲、也不会永远逼近。</p>
 *
 * <p>静止态是<b>精确</b>值（不是逼近值）：条长与数字最终一定会落在真实耐久上，
 * 这也是「掉到 0 就是 0」的依据。</p>
 *
 * <p>纯数学，零 Minecraft 引用，可直接单测（见 ValueTweenTest）。</p>
 */
public final class ValueTween {

    /** 当前的显示值。 */
    private float value;
    /** 本次动画的起点、终点，以及 0..1 的线性进度（未缓动）；1 表示已经停在目标上。 */
    private float start;
    private float target;
    private float progress = 1.0F;
    /** 有没有拿到过目标：第一帧直接落位，不会从 0 开始长出来或掉下去。 */
    private boolean started;

    /** 当前显示值；还没有过目标时是 0。 */
    public float value() {
        return value;
    }

    /** 立刻停到目标上（第一次出现、关掉动画、被复制成离场替身时用）。 */
    public void snap(float target) {
        this.value = target;
        this.start = target;
        this.target = target;
        this.progress = 1.0F;
        this.started = true;
    }

    /**
     * 推进一帧。
     *
     * @param target         这一帧的真实值（耐久比例）
     * @param dtSeconds      距上一帧的秒数；0 或负数（停帧、时钟回拨）时这一帧不动
     * @param durationMillis 走完这段距离所需的毫秒数；{@code <= 0} 表示立即到位
     * @return 推进后的显示值
     */
    public float update(float target, float dtSeconds, int durationMillis) {
        if (!started || durationMillis <= 0) {
            snap(target);
            return value;
        }
        if (target != this.target) {
            this.start = value;        // 从当前位置重新起步：不跳变、也不折返
            this.target = target;
            this.progress = 0.0F;
        }
        if (progress >= 1.0F) {
            value = this.target;       // 已经静止：目标没变就地保持精确值
            return value;
        }
        if (dtSeconds > 0.0F) {
            progress += dtSeconds * 1000.0F / durationMillis;
        }
        if (progress >= 1.0F) {
            progress = 1.0F;
            value = this.target;
        } else {
            value = start + (this.target - start) * FadeState.smooth(progress);
        }
        return value;
    }
}
