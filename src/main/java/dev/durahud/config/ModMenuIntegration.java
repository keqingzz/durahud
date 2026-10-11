package dev.durahud.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * 可选的 ModMenu 集成：在模组列表里给 DuraHUD 加一个「配置」按钮。
 *
 * <p>ModMenu 未安装时这个类不会被加载，因为 Fabric 只会在存在 {@code "modmenu"} 入口点
 * 类型时才去解析它 —— 所以这里可以放心地引用 ModMenu 的 API，不必加依赖守卫。</p>
 */
public class ModMenuIntegration implements ModMenuApi {

    /**
     * ModMenu 点「配置」时打开的界面：直接复用本模组自己的 {@link DuraHudConfigScreen}，
     * 构造参数是父界面（由 ModMenu 传入），返回后回到模组列表。刻意不引 Cloth Config
     * （见 README 10.6），设置界面只用原版控件。
     */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return DuraHudConfigScreen::new;
    }
}
