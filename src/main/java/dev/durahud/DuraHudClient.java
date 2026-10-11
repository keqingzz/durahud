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

/**
 * 模组入口（{@code fabric.mod.json} 里的 {@code client} 入口点），纯客户端、无服务端逻辑。
 *
 * <p>只做四件事：读一次配置并持有全局实例（{@link #config()}）、注册两个按键
 * （H = 编辑模式、K = 设置界面）、挂 {@code HudRenderCallback}（每帧转交 {@link HudRenderer}）、
 * 挂 {@code END_CLIENT_TICK}（轮询按键）。几何与绘制全在 hud 包里，这里只负责接线。</p>
 */
public class DuraHudClient implements ClientModInitializer {

    /** 模组 id：同时是语言键前缀、资源包命名空间与日志器名字。 */
    public static final String MOD_ID = "durahud";
    /** 全局日志器；各失败分支都往这里写一条 warn，排查时在 latest.log 里搜 {@code [DuraHUD]}。 */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 全局配置实例，由 {@link #onInitializeClient()} 填好；在此之前为 {@code null}。 */
    private static DuraHudConfig config;

    /** 当前配置；设置界面在自己的 {@code init()} 里取它，避免界面被提前构造时拿到 null。 */
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

    /**
     * 模组初始化：先读配置（必要时回写一次），再注册按键、渲染回调与 tick 回调。
     *
     * <p>按键有两条不能动的细节：设置界面把 {@code client.currentScreen} 当父界面传进去，
     * 关掉后回到原处；编辑模式必须等 {@code currentScreen == null} 才开 —— 它是真实界面
     * （拖动要先释放光标），而原版只在没有界面时才更新按键的按下状态。</p>
     */
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

        // 每帧回调里现读静态字段 config，所以设置界面改完立刻就反映到画面。
        HudRenderCallback.EVENT.register((context, tickDelta) -> HudRenderer.render(context, config));

        // 按键轮询：两个 while 是原版约定写法（一次 tick 可能补发多次「刚按下」）。
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
