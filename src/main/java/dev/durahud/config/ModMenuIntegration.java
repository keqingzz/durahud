package dev.durahud.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * 可选的 ModMenu 集成。ModMenu 未安装时这个类不会被加载，
 * 因为 Fabric 只会在存在 "modmenu" 入口点类型时才去解析它。
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return DuraHudConfigScreen::new;
    }
}
