package dev.durahud;

import dev.durahud.config.ConfigFile;
import dev.durahud.config.DuraHudConfig;
import dev.durahud.config.DuraHudConfigScreen;
import dev.durahud.hud.DuraHudEditScreen;
import dev.durahud.hud.HudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuraHudClient implements ClientModInitializer {

    public static final String MOD_ID = "durahud";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static DuraHudConfig config;

    public static DuraHudConfig config() {
        return config;
    }

    /**
     * 当前版本号，取自 {@code fabric.mod.json}（构建时由 {@code gradle.properties} 的
     * {@code mod_version} 展开）。开发环境或元数据缺失时返回 {@code "?"}。
     * 每次改动都要先递增它，见 README 2.4。
     */
    public static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    @Override
    public void onInitializeClient() {
        config = DuraHudConfig.load();
        if (ConfigFile.shouldWriteBack()) {
            config.save();   // 只有首次运行或升级过格式才落盘，平时启动不碰配置文件
        }

        KeyBinding editKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.durahud.edit", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.durahud"));
        KeyBinding configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.durahud.config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.durahud"));

        HudRenderCallback.EVENT.register((context, tickDelta) -> HudRenderer.render(context, config));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (configKey.wasPressed()) {
                client.setScreen(new DuraHudConfigScreen(client.currentScreen));
            }
            // 注意 currentScreen == null 这个条件：本 HUD 的编辑模式现在是一个真实的界面
            // （光标必须被释放才能拖动），而原版只在没有界面时才更新按键状态。
            while (editKey.wasPressed() && client.currentScreen == null) {
                client.setScreen(new DuraHudEditScreen());
            }
        });

        LOGGER.info("[DuraHUD] 已初始化 v{}（纯客户端，零自定义贴图，复用原版 GUI 图集）", version());
    }
}
