package dev.durahud.hud;

import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketsApi;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Trinkets 3.x 的连接层。<b>只有 Trinkets 已加载时</b>才会被 {@link ExtraSlots} 调用。
 *
 * <p>方法签名里不出现任何 Trinkets 类型（参数与返回值都是原版或 Java 类型），
 * 并且用 {@code var} 接住 {@code getAllEquipped()} 的元素，因此 JVM 只有在真正执行本方法时
 * 才需要解析 Trinkets 的类。这是「可选依赖」在 Fabric 上不炸的最省事做法。</p>
 *
 * <p>编译期依赖写在 build.gradle 的 {@code modCompileOnly "dev.emi:trinkets:3.7.2"}；
 * 没有它就无法编译，但运行时缺失完全没问题。</p>
 */
final class TrinketsBridge {

    private TrinketsBridge() {
    }

    static List<ItemStack> collect(PlayerEntity player) {
        List<ItemStack> stacks = new ArrayList<>();
        TrinketComponent component = TrinketsApi.getTrinketComponent(player).orElse(null);
        if (component == null) {
            return stacks;
        }
        for (var equipped : component.getAllEquipped()) {
            ItemStack stack = equipped.getRight();
            if (stack != null && !stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }
}
