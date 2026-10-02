package dev.durahud.hud;

/**
 * 同一个格子里换了东西时的过渡状态：<b>旧内容先按老方案退场，新内容再淡入 + 滑入</b>。
 *
 * <p>没有它的时候，主手从剑换成镐是「一帧跳变」：{@link CellContent} 每帧就地刷新快照，
 * 格子的 {@code fade} 一直是 1，什么动画都不会发生。换物过渡把这一帧拆成两个阶段：</p>
 *
 * <ol>
 *   <li>{@link Phase#OUT}：快照冻住不再刷新（由 {@link DurabilityEntry} 负责），画出来的仍是
 *       旧物品，不透明度 1 → 0；</li>
 *   <li>{@link Phase#IN}：换上新快照。前 {@link #FADE_SHARE} 的进度里只做淡入
 *       （不透明度 0 → 1），位置保持在 {@link Metrics#SWAP_SLIDE} 像素外；之后才平滑滑到位。
 *       先渐入、再滑入，所以不会出现「先出现在原位、再跳回起点」的瞬移。</li>
 * </ol>
 *
 * <p>两段合起来约 {@code animationMillis * 1.5}：退场取一半时长（{@link #outMillis}），
 * 进场取整段时长。关掉「格子动画」时调用者直接 {@link #clear()}，行为回到旧版。</p>
 *
 * <p>和 {@link FadeState} 一样是纯数学，不引用任何 Minecraft 类型，可以直接单测
 * （见 SwapStateTest）。</p>
 */
public final class SwapState {

    /** 过渡阶段。 */
    public enum Phase {
        /** 没有过渡。 */
        NONE,
        /** 旧内容退场中（快照必须冻住）。 */
        OUT,
        /** 新内容进场中（快照跟着实物刷新）。 */
        IN
    }

    /** {@link #advance} 的结果：这一帧是不是正好发生了「交接」。 */
    public enum Step {
        /** 什么都没发生。 */
        IDLE,
        /** 退场结束、东西还在：调用者该换上新快照了。 */
        INCOMING,
        /** 退场结束、东西已经没了：调用者该把格子淡出直接归零。 */
        GONE
    }

    /** 退场时长占动画时长的比例，剩下的留给新内容进场。 */
    public static final float OUT_SHARE = 0.5F;
    /** 进场里「先渐入」占的比例：前 45% 只淡入，后 55% 才滑动。 */
    public static final float FADE_SHARE = 0.45F;

    private Phase phase = Phase.NONE;
    private float progress;

    public Phase phase() {
        return phase;
    }

    /** 当前阶段的进度 0..1。 */
    public float progress() {
        return progress;
    }

    public boolean active() {
        return phase != Phase.NONE;
    }

    /** 旧内容正在退场：这段时间里调用者<b>不能</b>刷新内容快照。 */
    public boolean outgoing() {
        return phase == Phase.OUT;
    }

    /** 新内容正在进场：这段时间里内容快照应当跟着实物刷新。 */
    public boolean incoming() {
        return phase == Phase.IN;
    }

    /** 开始退场：进度归零，快照原地淡出。 */
    public void begin() {
        phase = Phase.OUT;
        progress = 0.0F;
    }

    /** 取消过渡（关掉动画、或东西在滑入途中就消失时用）。 */
    public void clear() {
        phase = Phase.NONE;
        progress = 0.0F;
    }

    /**
     * 推进一帧。
     *
     * @param dtSeconds      距上一帧的秒数（由渲染器给出）
     * @param animationMillis 动画时长，与淡入淡出共用
     * @param filled         这一格现在还有没有内容
     * @return {@link Step#INCOMING} 表示就在这一帧交接（改换新快照）；
     *         {@link Step#GONE} 表示退场结束时东西已经没了（格子该直接归零）
     */
    public Step advance(float dtSeconds, int animationMillis, boolean filled) {
        if (phase == Phase.NONE) {
            return Step.IDLE;
        }
        int duration = phase == Phase.OUT ? outMillis(animationMillis) : Math.max(1, animationMillis);
        progress = FadeState.approach(progress, 1.0F, dtSeconds, duration);
        if (progress < 1.0F) {
            return Step.IDLE;
        }
        if (phase == Phase.OUT) {
            if (!filled) {
                clear();
                return Step.GONE;
            }
            phase = Phase.IN;
            progress = 0.0F;
            return Step.INCOMING;
        }
        clear();
        return Step.IDLE;
    }

    /** 内容的不透明度：退场 1 → 0；进场 0 → 1，在前 {@link #FADE_SHARE} 的进度里走完。 */
    public float alpha() {
        return switch (phase) {
            case NONE -> 1.0F;
            case OUT -> 1.0F - FadeState.smooth(progress);
            case IN -> FadeState.smooth(Math.min(1.0F, progress / FADE_SHARE));
        };
    }

    /**
     * 滑入的位移比例：1 = 还在起始位置，0 = 已经到位。
     *
     * <p>进场的前 {@link #FADE_SHARE} 恒为 1（这一段只淡入、不移动），之后才平滑滑到 0。</p>
     */
    public float slide() {
        if (phase != Phase.IN) {
            return 0.0F;
        }
        if (progress <= FADE_SHARE) {
            return 1.0F;
        }
        return 1.0F - FadeState.smooth((progress - FADE_SHARE) / (1.0F - FADE_SHARE));
    }

    /** 退场时长：动画时长的一半，至少 1 毫秒（免得 0 时长时一步也走不动）。 */
    public static int outMillis(int animationMillis) {
        return Math.max(1, Math.round(animationMillis * OUT_SHARE));
    }
}
