package dev.durahud.hud;

/**
 * 同一格里换了东西时的过渡：两份内容各推进一格，边走边交叉淡入淡出。
 *
 * <p>画面上的效果是「整排往前挪一格」：旧内容朝排列方向的下一个格子位置移动并淡出，
 * 新内容从上一个格子的位置移动进来并淡入。两份内容始终相差一个步距（由渲染器给出），
 * 所以中途不会重叠 —— 旧内容还没淡完，新内容已经在它该在的位置上出现了。</p>
 *
 * <p><b>可被打断</b>：过渡途中又换了东西时，调用者不再 {@link #begin(int)}（那会让画面从
 * 头重播一遍、而且正在进场的那份会突然落回格心），而是保留进度、把新物品就地换进「正在进场」
 * 的那一层，再用 {@link #hurryTo(int)} 把剩下的这段路限时收尾。这样一次换物过渡的总时长
 * 永远是配置的那一段，换几次都不会越拖越长；连续快速切换时后几次还会主动加速跟上。</p>
 *
 * <p>纯数学，零 Minecraft 引用（可离线单测，见 SwapStateTest）。渲染器按 {@link #shift()} 把
 * 两份内容各推一段距离、按 {@link #outgoingAlpha()} / {@link #incomingAlpha()} 设不透明度；
 * 没有过渡时 {@link #active()} 为 false，此时只有当前内容需要画：位移 0、完全不透明。</p>
 */
public final class SwapState {

    /** 0..1 的线性进度（未缓动）；只有 {@link #active()} 时才有意义。 */
    private float progress;
    /** 当前速率下走完一整格（进度 0 -> 1）所需的毫秒数；被打断收尾时只会调小、不会调大。 */
    private int millis = 1;
    private boolean active;

    /** 过渡进行中；false 时渲染器走「只有一份内容」的老路。 */
    public boolean active() {
        return active;
    }

    /** 线性进度：过渡中夹在 0..1，没有过渡时恒为 0。 */
    public float progress() {
        if (!active) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    /** 缓动后的推进量：0 = 旧内容还在原位（新内容在上一格），1 = 刚好推进一格。 */
    public float shift() {
        return active ? FadeState.smooth(progress) : 0.0F;
    }

    /** 离场内容的推进量（0 → 1 个步距）。 */
    public float outgoingShift() {
        return shift();
    }

    /** 进场内容的推进量（-1 → 0 个步距；-1 就是「上一个格子」的位置）。 */
    public float incomingShift() {
        return active ? shift() - 1.0F : 0.0F;
    }

    /** 离场内容的不透明度（1 → 0）；没有过渡时它根本不该被画，返回 0。 */
    public float outgoingAlpha() {
        return active ? 1.0F - shift() : 0.0F;
    }

    /** 进场内容的不透明度（0 → 1）；没有过渡时当前内容完全不透明。 */
    public float incomingAlpha() {
        return active ? shift() : 1.0F;
    }

    /**
     * 开始一段过渡，整格用时 {@code animationMillis}。
     *
     * <p>已经在过渡里时调用它等于从头重播一遍 —— 打断（半路换物）不要走这里，见 {@link #hurryTo(int)}。</p>
     */
    public void begin(int animationMillis) {
        active = true;
        progress = 0.0F;
        millis = Math.max(1, animationMillis);
    }

    /**
     * 打断后的限时收尾：进度、位移、离场那份全都不动，只把「剩下的这段路」压到
     * {@code capMillis} 毫秒以内走完（已经比它快就什么都不做，绝不反过来变慢）。
     *
     * <p>用途是「过渡途中又换了东西」：新物品直接接手正在进场的那一层（位置与不透明度都连续，
     * 不会跳），同时别让它用剩下的整段时间慢慢悠悠飘进来 —— 动画时长设得越大，这一下越明显。</p>
     */
    public void hurryTo(int capMillis) {
        float remain = 1.0F - progress;
        if (remain <= 0.0F || capMillis <= 0) {
            return;
        }
        int scaled = (int) Math.ceil(capMillis / remain);
        if (scaled < millis) {
            millis = Math.max(1, scaled);
        }
    }

    /** 立刻结束过渡：回到「只有当前内容、原地、完全不透明」的静止态。 */
    public void clear() {
        active = false;
        progress = 0.0F;
        millis = 1;
    }

    /**
     * 按当前速率推进一段。
     *
     * @param dtSeconds 距上一帧的秒数（停帧或长时间暂停后传 0，动画就停在原地）
     * @return 是否刚好在这一帧走完（调用者据此收尾，比如 {@link #clear()}）
     */
    public boolean advance(float dtSeconds) {
        if (!active || !(dtSeconds > 0.0F)) {
            return false;   // NaN 与负数都当「这一帧没动」
        }
        progress += dtSeconds * 1000.0F / millis;
        if (progress >= 1.0F) {
            progress = 1.0F;
            return true;
        }
        return false;
    }
}
