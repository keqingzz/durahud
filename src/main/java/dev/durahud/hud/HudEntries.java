package dev.durahud.hud;

import dev.durahud.config.DuraHudConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

/**
 * 当前要画的条目列表：先摆 4 个盔甲格，再摆额外装备槽（饰品），最后才是主手/副手 ——
 * 工具永远排在最后，戴在身上的东西不会被挤到手持物品后面去。
 *
 * <p><b>视图每帧重建</b>（最多 25 个引用，代价可以忽略）：旧版靠一个「签名」判断要不要重建，
 * 签名漏掉的字段就会让视图过期；每帧重建之后这一类 bug 不复存在。</p>
 *
 * <p><b>额外装备槽按物品身份复用格子</b>：取下一件饰品时，淡出的是那一件自己的格子，
 * 其余饰品保留各自的淡入淡出与滑动状态 —— 旧版按序号复用实例，表现为「下一个饰品原地
 * 替换」（README 里记着的那条已知限制）。正在淡出的格子依旧占位
 * （{@link DurabilityEntry#occupiesCell}），所以格数与顺序在淡出期间是稳的。</p>
 */
public final class HudEntries {

    private static final DurabilityEntry.Kind[] ORDER = DurabilityEntry.Kind.values();

    /** 额外装备槽的画面上限，防止某个模组把上百个槽位塞进 HUD。 */
    private static final int MAX_EXTRA = 12;

    private final DurabilityEntry[] all = new DurabilityEntry[ORDER.length];
    private final DurabilityEntry[] extraPool = new DurabilityEntry[MAX_EXTRA];
    /** 每一格当前占第几个额外槽位；-1 = 空闲（新物品可以直接用）。 */
    private final int[] extraSlot = new int[MAX_EXTRA];
    /** 本帧重新认领过的格子；没被认领的说明物品已经不在，要开始淡出。 */
    private final boolean[] extraClaimed = new boolean[MAX_EXTRA];
    /**
     * 池子是否「全空闲」：没有任何一格占着位、也没有换物过渡没走完。没有额外装备模组的玩家
     * （大多数）因此每帧不用碰这 12 个格子 —— 跳过 {@code advance}/{@code occupiesCell}，
     * 视图里也不会出现饰品格。一旦有格子认领或被占住，这个标记就在同一次刷新里变回 false。
     */
    private boolean extrasIdle = true;

    /** 本帧的额外槽物品（每 tick 至多向 Trinkets 要一次，槽位内容只会在 tick 里变）。 */
    private List<ItemStack> extraStacks = Collections.emptyList();
    private long extraStacksTick = Long.MIN_VALUE;

    private final DurabilityEntry[] view = new DurabilityEntry[ORDER.length + MAX_EXTRA];
    private int count;

    public HudEntries() {
        for (int i = 0; i < ORDER.length; i++) {
            all[i] = new DurabilityEntry(ORDER[i], ItemStack.EMPTY);
        }
        for (int i = 0; i < MAX_EXTRA; i++) {
            extraPool[i] = DurabilityEntry.extra(ItemStack.EMPTY);
            extraSlot[i] = -1;
        }
    }

    public int count() {
        return count;
    }

    public DurabilityEntry get(int index) {
        return view[index];
    }

    /**
     * 帧末提交：把「这一帧画过」记成「上一帧画过」。没被画过的格子（被筛掉、刚让位）
     * 下次出现时直接落位，不需要滑动。
     */
    public void commitPlacement() {
        for (DurabilityEntry entry : all) {
            entry.commitPlacement();
        }
        for (DurabilityEntry entry : extraPool) {
            entry.commitPlacement();
        }
    }

    /**
     * 每帧刷新物品、额外装备与动画进度。
     *
     * @param dtSeconds 距上一帧的秒数，交给条目推进淡入淡出
     */
    public void refresh(MinecraftClient mc, DuraHudConfig cfg, float dtSeconds) {
        PlayerEntity player = mc.player;
        PlayerInventory inventory = player == null ? null : player.getInventory();
        for (DurabilityEntry entry : all) {
            entry.setStack(inventory == null ? ItemStack.EMPTY : stackOf(player, inventory, entry.kind()));
            entry.advance(dtSeconds, cfg);
        }
        refreshExtra(player, cfg, dtSeconds);
        rebuildView(cfg);
    }

    /**
     * 额外装备槽：按物品身份认领池子里的格子。
     *
     * <p>认领顺序是「同一个栈对象 -> 同一槽位上的同类物品 -> 空闲格 -> 最接近淡完的那一格」，
     * 所以饰品集合变化时，只有真正变动的那几格会淡入 / 淡出，其余格子保持自己的状态。</p>
     */
    private void refreshExtra(PlayerEntity player, DuraHudConfig cfg, float dtSeconds) {
        if (player != null && cfg.showTrinkets) {
            if (player.age != extraStacksTick) {
                extraStacksTick = player.age;
                extraStacks = ExtraSlots.collect(player);
            }
        } else if (!extraStacks.isEmpty()) {
            extraStacks = Collections.emptyList();
            extraStacksTick = Long.MIN_VALUE;
        }
        Arrays.fill(extraClaimed, false);
        if (extraStacks.isEmpty() && extrasIdle) {
            return;   // 快路径：没东西要认领、池子也全空闲，这一帧不必碰池子（见 extrasIdle）
        }
        for (int slot = 0; slot < extraStacks.size() && slot < MAX_EXTRA; slot++) {
            ItemStack stack = extraStacks.get(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int index = claim(stack, slot);
            if (index < 0) {
                break;   // 池子满了（理论上不会：槽位上限与池子一样大）
            }
            extraPool[index].setStack(stack);
            extraSlot[index] = slot;
            extraClaimed[index] = true;
        }
        boolean busy = false;
        for (int i = 0; i < MAX_EXTRA; i++) {
            if (!extraClaimed[i]) {
                extraPool[i].setStack(ItemStack.EMPTY);   // 物品没了：开始淡出，槽位号先留着
            }
            extraPool[i].advance(dtSeconds, cfg);
            // 过渡也算「忙」：淡出结束得比过渡早时，这一格还得继续把过渡推进完（见 advanceEmpty）。
            if (extraPool[i].occupiesCell(cfg) || extraPool[i].swap().active()) {
                busy = true;
            } else {
                extraSlot[i] = -1;                        // 彻底离场，下次从头开始
            }
        }
        extrasIdle = !busy;
    }

    /** 给一件饰品挑一格；挑不到（池子满了）返回 -1。 */
    private int claim(ItemStack stack, int slot) {
        for (int i = 0; i < MAX_EXTRA; i++) {
            if (!extraClaimed[i] && extraPool[i].stack() == stack) {
                return i;                                 // 还是那个栈对象：绝大多数帧都走这里
            }
        }
        for (int i = 0; i < MAX_EXTRA; i++) {
            if (!extraClaimed[i] && extraSlot[i] == slot && ItemStack.areEqual(extraPool[i].stack(), stack)) {
                return i;                                 // 同一槽位上的同一件物品（栈对象被换过）
            }
        }
        for (int i = 0; i < MAX_EXTRA; i++) {
            if (!extraClaimed[i] && extraSlot[i] < 0) {
                return i;                                 // 空闲格
            }
        }
        int fallback = -1;
        for (int i = 0; i < MAX_EXTRA; i++) {
            if (!extraClaimed[i] && (fallback < 0 || extraPool[i].fade() < extraPool[fallback].fade())) {
                fallback = i;                             // 都在用：抢最接近淡完的那一格
            }
        }
        return fallback;
    }

    private void rebuildView(DuraHudConfig cfg) {
        count = 0;
        // 顺序：盔甲 -> 额外装备（饰品）-> 手持。手持永远在最后（工具不与穿戴物混排）。
        for (DurabilityEntry entry : all) {
            if (entry.kind().armor && cfg.showArmor && entry.occupiesCell(cfg)) {
                view[count++] = entry;
            }
        }
        appendExtras(cfg);
        DurabilityEntry mainhand = all[DurabilityEntry.Kind.MAINHAND.ordinal()];
        if (cfg.showMainhand && mainhand.occupiesCell(cfg)) {
            view[count++] = mainhand;
        }
        DurabilityEntry offhand = all[DurabilityEntry.Kind.OFFHAND.ordinal()];
        if (cfg.showOffhand && offhand.occupiesCell(cfg)) {
            view[count++] = offhand;
        }
    }

    /**
     * 额外装备按槽位号排进视图。
     *
     * <p>分两趟：先在场上（按槽位号）的，再正在淡出的。淡出的那一格排在<b>最后</b>而不是
     * 留在原位 —— 它原来的槽位可能已经被新上场的饰品占了，留在原位两格会叠在一起。</p>
     */
    private void appendExtras(DuraHudConfig cfg) {
        if (extrasIdle) {
            return;   // 池子里没有占位的格子（刚刷新过，见 extrasIdle）：视图里不会出现饰品格
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int slot = 0; slot < MAX_EXTRA; slot++) {
                for (int i = 0; i < MAX_EXTRA; i++) {
                    boolean live = !extraPool[i].stack().isEmpty();
                    if ((pass == 0) == live && extraSlot[i] == slot && extraPool[i].occupiesCell(cfg)) {
                        view[count++] = extraPool[i];
                    }
                }
            }
        }
    }

    private static ItemStack stackOf(PlayerEntity player, PlayerInventory inventory, DurabilityEntry.Kind kind) {
        return switch (kind) {
            case MAINHAND -> player.getMainHandStack();
            case OFFHAND -> player.getOffHandStack();
            case HELMET -> inventory.getArmorStack(3);
            case CHESTPLATE -> inventory.getArmorStack(2);
            case LEGGINGS -> inventory.getArmorStack(1);
            case BOOTS -> inventory.getArmorStack(0);
        };
    }
}
