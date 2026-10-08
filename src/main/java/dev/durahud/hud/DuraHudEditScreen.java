package dev.durahud.hud;

import dev.durahud.DuraHudClient;
import dev.durahud.config.DuraHudConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * 编辑位置界面（热键 H 打开，ESC 或 H 退出）。
 *
 * <p><b>为什么必须开一个界面</b>：游戏内鼠标是<b>锁定</b>的 —— 光标被固定在窗口中心，
 * Mouse.getX()/getY() 不再跟随鼠标移动。所以"不打开任何界面、直接拖动 HUD"这条路走不通，
 * 这也正是编辑模式一度"看起来失效"的根本原因。</p>
 *
 * <p>开一个全透明 Screen 之后：光标被释放，mouseDragged 给出的坐标才是真实位置；
 * 本界面不画背景，世界和 HUD 依旧可见（HUD 走 HudRenderCallback，绘制发生在本界面之前）；
 * shouldPause() = false，单人游戏不会因此暂停。</p>
 *
 * <p>操作：左键拖动 / 滚轮缩放 / 方向键微调（Shift ×10）/ +- 缩放 / R 恢复默认。</p>
 */
public class DuraHudEditScreen extends Screen {

    private final DuraHudConfig cfg = DuraHudClient.config();

    private boolean dragging;
    private boolean dirty;
    private double grabDX;
    private double grabDY;

    public DuraHudEditScreen() {
        super(Text.empty());
    }

    @Override
    protected void init() {
        cfg.editMode = true;
    }

    @Override
    public void removed() {
        cfg.editMode = false;
        dragging = false;
        flush();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** 全透明：只用 HUD 自身的青色边框提示，不画任何背景。 */
    @Override
    public void renderBackground(DrawContext context) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        HudLayout layout = HudLayout.last();
        if (button == 0 && layout != null && layout.containsScreen(mouseX, mouseY)) {
            dragging = true;
            grabDX = mouseX - layout.originX();
            grabDY = mouseY - layout.originY();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            moveTo(mouseX - grabDX, mouseY - grabDY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            flush();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scale((float) amount * 0.05F);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> nudge(-step, 0);
            case GLFW.GLFW_KEY_RIGHT -> nudge(step, 0);
            case GLFW.GLFW_KEY_UP -> nudge(0, -step);
            case GLFW.GLFW_KEY_DOWN -> nudge(0, step);
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> scale(0.05F);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> scale(-0.05F);
            case GLFW.GLFW_KEY_R -> {
                cfg.anchor = DuraHudConfig.DEFAULT_ANCHOR;
                cfg.offsetX = 0;
                cfg.offsetY = 0;
                cfg.scale = 1.0F;
                dirty = true;
            }
            case GLFW.GLFW_KEY_H -> close();
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    /** 切到 CUSTOM 锚点并接受本次拖拽的落点。 */
    private void moveTo(double x, double y) {
        cfg.anchor = DuraHudConfig.Anchor.CUSTOM;
        cfg.offsetX = (int) Math.round(x);
        cfg.offsetY = (int) Math.round(y);
        dirty = true;
    }

    /**
     * 方向键微调。<b>基准是 HUD 当前实际所在的位置</b>：CUSTOM 锚点的 offset 是绝对原点，
     * 而其他锚点上的 offset 只是相对位移 —— 直接把「相对值 + 步长」写进 CUSTOM 会让整块瞬移
     * （贴着热键栏、offset 0 时按一次「→」整块就跳到屏幕左上角）。
     */
    private void nudge(int dx, int dy) {
        HudLayout layout = HudLayout.last();
        if (cfg.anchor == DuraHudConfig.Anchor.CUSTOM) {
            cfg.offsetX += dx;
            cfg.offsetY += dy;
        } else if (layout != null) {
            cfg.anchor = DuraHudConfig.Anchor.CUSTOM;
            cfg.offsetX = layout.originX() + dx;
            cfg.offsetY = layout.originY() + dy;
        } else {
            // 还没画过（HudLayout.last() 为 null）：留在原锚点上挪相对偏移，这样一定不会跳。
            cfg.offsetX += dx;
            cfg.offsetY += dy;
        }
        dirty = true;
    }

    /**
     * 滚轮/按键缩放只改内存并标脏：1.40.0 之前每一格滚轮都写一次配置文件，
     * 在滚轮上滚一圈就是十几次磁盘写入。退出编辑界面时 {@link #removed()} 统一落盘。
     */
    private void scale(float delta) {
        cfg.scale = Math.max(0.25F, Math.min(4.0F, cfg.scale + delta));
        dirty = true;
    }

    private void flush() {
        if (dirty) {
            dirty = false;
            cfg.save();
        }
    }
}
