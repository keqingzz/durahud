package dev.durahud.hud;

import dev.durahud.config.DuraHudConfig;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

/**
 * 当前要画的条目列表：先摆 4 个盔甲格，再摆额外装备槽（饰品），最后才是主手/副手 ——
 * 工具永远排在最后，戴在身上的东西不会被挤到手持物品后面去。
 *
 * <p>视图只在「签名变化」或「额外装备数量变化」时重建，所以每帧只做几次 setStack，
 * 没有对象分配。签名把「有几格盔甲」也算进去，于是自动补位（空槽位不再占位）能立刻反映到
 * 格数上；正在淡出的格子依旧占位（{@link DurabilityEntry#occupiesCell}），所以淡出期间
 * 格数与顺序稳定，淡完才让位、后面的格子才顶上来。</p>
 */
public final class HudEntries {

    private static final DurabilityEntry.Kind[] ORDER = DurabilityEntry.Kind.values();
    private static final int BIT_MAINHAND = 1;
    private static final int BIT_OFFHAND = 2;
    /** 盔甲格数量放在第 2 位起（0..4），这样「盔甲格少了一格」也能触发视图重建。 */
    private static final int ARMOR_SHIFT = 2;

    /** 额外装备槽的画面上限，防止某个模组把上百个槽位塞进 HUD。 */
    private static final int MAX_EXTRA = 12;

    private final DurabilityEntry[] all = new DurabilityEntry[ORDER.length];
    private final DurabilityEntry[] extraEntries = new DurabilityEntry[MAX_EXTRA];
    private final DurabilityEntry[] view = new DurabilityEntry[ORDER.length + MAX_EXTRA];
    private int extraCount;
    private int count;
    private int mask = -1;

    public HudEntries() {
        for (int i = 0; i < ORDER.length; i++) {
            all[i] = new DurabilityEntry(ORDER[i], ItemStack.EMPTY);
        }
        for (int i = 0; i < MAX_EXTRA; i++) {
            extraEntries[i] = DurabilityEntry.extra(ItemStack.EMPTY);
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
        for (DurabilityEntry entry : extraEntries) {
            entry.commitPlacement();
        }
    }

    /**
     * 每帧刷新物品与动画进度。
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
        int previousExtra = extraCount;
        extraCount = 0;
        if (player != null && cfg.showTrinkets) {
            List<ItemStack> extra = ExtraSlots.collect(player);
            for (ItemStack stack : extra) {
                if (extraCount >= MAX_EXTRA) {
                    break;
                }
                DurabilityEntry candidate = extraEntries[extraCount];
                candidate.setStack(stack == null ? ItemStack.EMPTY : stack);
                candidate.advance(dtSeconds, cfg);
                if (candidate.occupiesCell(cfg)) {
                    extraCount++;
                }
            }
        }
        int signature = signatureOf(cfg);
        if (signature != mask || extraCount != previousExtra) {
            rebuildView(cfg, signature);
        }
    }

    private void rebuildView(DuraHudConfig cfg, int signature) {
        mask = signature;
        count = 0;
        // 顺序：盔甲 -> 额外装备（饰品）-> 手持。手持永远在最后（工具不与穿戴物混排）。
        for (DurabilityEntry entry : all) {
            if (entry.kind().armor && cfg.showArmor && entry.occupiesCell(cfg)) {
                view[count++] = entry;
            }
        }
        for (int i = 0; i < extraCount; i++) {
            view[count++] = extraEntries[i];
        }
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
     * 视图签名：只要它变了就重建视图。第 0/1 位是主手/副手，第 {@link #ARMOR_SHIFT} 位起是
     * 盔甲格数量（0..4）—— 把数量放进来是因为自动补位会让盔甲格随装备增减，光看开关位发现不了。
     */
    private int signatureOf(DuraHudConfig cfg) {
        return (armorCellCount(cfg) << ARMOR_SHIFT) | handBits(cfg);
    }

    private int armorCellCount(DuraHudConfig cfg) {
        if (!cfg.showArmor) {
            return 0;
        }
        int cells = 0;
        for (DurabilityEntry entry : all) {
            if (entry.kind().armor && entry.occupiesCell(cfg)) {
                cells++;
            }
        }
        return cells;
    }

    private int handBits(DuraHudConfig cfg) {
        int bits = 0;
        if (cfg.showMainhand && all[DurabilityEntry.Kind.MAINHAND.ordinal()].occupiesCell(cfg)) {
            bits |= BIT_MAINHAND;
        }
        if (cfg.showOffhand && all[DurabilityEntry.Kind.OFFHAND.ordinal()].occupiesCell(cfg)) {
            bits |= BIT_OFFHAND;
        }
        return bits;
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
