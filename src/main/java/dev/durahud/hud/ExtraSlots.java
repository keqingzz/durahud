package dev.durahud.hud;

import java.util.Collections;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * 可选模组提供的「额外装备槽」（目前只有 Trinkets）的入口。
 *
 * <p>这里刻意只做两件事：判断模组是否加载、把调用转给 {@link TrinketsBridge}。
 * 真正 import Trinkets 类的代码全在 TrinketsBridge 里，所以 Trinkets 未安装时
 * 这个类不会触发任何 Trinkets 类的加载（否则会 NoClassDefFoundError / ClassNotFound）。</p>
 *
 * <p>新增模组时在 {@link #collect} 里加一个分支即可，注意同样要经过一个「只在模组存在时才加载」
 * 的桥接类。</p>
 */
final class ExtraSlots {

    private static final boolean TRINKETS = FabricLoader.getInstance().isModLoaded("trinkets");

    private ExtraSlots() {
    }

    /** 是否有任何受支持的额外槽模组在场。 */
    static boolean available() {
        return TRINKETS;
    }

    /** 当前装备在额外槽里的物品，顺序稳定；没有模组时永远是空列表。 */
    static List<ItemStack> collect(PlayerEntity player) {
        if (!TRINKETS || player == null) {
            return Collections.emptyList();
        }
        return TrinketsBridge.collect(player);
    }
}
