package dev.durahud.hud;

import dev.durahud.config.DuraHudConfig;
import dev.durahud.config.DuraHudConfig.Anchor;
import dev.durahud.config.DuraHudConfig.BarSide;
import dev.durahud.config.DuraHudConfig.Orientation;
import dev.durahud.config.DuraHudConfig.ValuePlacement;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 几何层的不变量测试。{@link HudLayout} 不依赖 Minecraft，所以这些断言可以在
 * 构建里直接跑（{@code gradle test}），不需要启动客户端。
 *
 * <p>覆盖 Anchor(8) × Orientation(2) × BarSide(4) × ValuePlacement(5) × 格子数(1..6)，
 * 每种组合在 320x240 与 854x480 两个窗口下都检查一遍；另外单独检查缩放取整、
 * 阈值分档的边界，并把代表性布局导出成 build/preview/layout.json 供离线预览工具使用。</p>
 */
class HudLayoutTest {

    private static final int[][] SCREENS = {{320, 240}, {854, 480}};
    /** 测试里给文字预留的宽度：真实值是 mc.textRenderer 量出来的，这里取一个上界。 */
    private static final int TEXT_WIDTH = 24;

    private record Rect(int x, int y, int w, int h) {

        boolean intersects(Rect other) {
            return x < other.x + other.w && other.x < x + w
                    && y < other.y + other.h && other.y < y + h;
        }

        boolean inside(int outerW, int outerH) {
            return x >= 0 && y >= 0 && x + w <= outerW && y + h <= outerH;
        }

        String show() {
            return "(" + x + "," + y + " " + w + "x" + h + ")";
        }
    }

    private static DuraHudConfig config(Anchor anchor, Orientation orientation, BarSide bar,
                                        ValuePlacement value) {
        DuraHudConfig cfg = new DuraHudConfig();
        cfg.anchor = anchor;
        cfg.orientation = orientation;
        cfg.barPlacement = bar;
        cfg.valuePlacement = value;
        return cfg;
    }

    /**
     * 旧版 check() 固定 {@code scale = 1}、从不设 offset、还整段跳过 CUSTOM，于是
     * 「窗口放得下、整块却被推出屏」这类问题一直藏着。这里把缩放（含 0 / 负数 / NaN）、
     * 偏移（含 100000 这种手改值）、小于 HUD 的窗口全部交叉跑一遍：
     * 装得下就必须完整落在窗口里，装不下也只允许从 (0,0) 开始溢出、绝不能是负坐标。
     */
    @Test
    void scalesOffsetsAndTinyWindowsStayOnScreen() {
        List<String> failures = new ArrayList<>();
        float[] scales = {1.0F, 0.25F, 0.5F, 2.0F, 4.0F, 0.0F, -3.0F, Float.NaN};
        int[] offsets = {0, 200, -200, 100000};
        int[][] screens = {{320, 240}, {854, 480}, {120, 80}, {40, 30}};
        for (Anchor anchor : Anchor.values()) {
            for (float scale : scales) {
                for (int offset : offsets) {
                    for (int[] screen : screens) {
                        for (int cells : new int[] {1, 6}) {
                            DuraHudConfig cfg = config(anchor, Orientation.HORIZONTAL, BarSide.ABOVE,
                                    ValuePlacement.AUTO);
                            cfg.scale = scale;
                            cfg.offsetX = offset;
                            cfg.offsetY = offset;
                            HudLayout layout = HudLayout.geometry(cfg, cells, screen[0], screen[1], TEXT_WIDTH);
                            String name = anchor + "/scale=" + scale + "/offset=" + offset + "/"
                                    + screen[0] + "x" + screen[1] + "/" + cells;
                            if (layout.scale() < 0.25F || layout.scale() > 4.0F
                                    || !Float.isFinite(layout.scale())) {
                                failures.add(name + " 缩放没被夹进 [0.25, 4]: " + layout.scale());
                                continue;
                            }
                            int w = layout.screenW();
                            int h = layout.screenH();
                            // 2.0.1 起允许最多露出半个格子（每边）；装不下时不给余量、退回「从屏内开始画」。
                            int allowX = allowance(22, layout.scale(), w, screen[0]);
                            int allowY = allowance(22, layout.scale(), h, screen[1]);
                            if (w > screen[0]) {
                                if (layout.originX() != 0) {
                                    failures.add(name + " 装不下却没从屏内开始画: " + layout.originX());
                                }
                            } else if (layout.originX() < -allowX || layout.originX() + w > screen[0] + allowX) {
                                failures.add(name + " 横向露出超过半格 origin=" + layout.originX()
                                        + " size=" + w + " 窗口=" + screen[0] + " 余量=" + allowX);
                            }
                            if (h > screen[1]) {
                                if (layout.originY() != 0) {
                                    failures.add(name + " 装不下却没从屏内开始画: " + layout.originY());
                                }
                            } else if (layout.originY() < -allowY || layout.originY() + h > screen[1] + allowY) {
                                failures.add(name + " 纵向露出超过半格 origin=" + layout.originY()
                                        + " size=" + h + " 窗口=" + screen[1] + " 余量=" + allowY);
                            }
                            if (!layout.containsScreen(layout.originX() + w / 2.0, layout.originY() + h / 2.0)) {
                                failures.add(name + " 块中心点不算在 HUD 内");
                            }
                        }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), () -> failures.size() + " 处违反不变量：\n"
                + String.join("\n", failures.subList(0, Math.min(20, failures.size()))));
    }

    @Test
    void everyCombinationKeepsItsInvariants() {
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (Anchor anchor : Anchor.values()) {
            for (Orientation orientation : Orientation.values()) {
                for (BarSide bar : BarSide.values()) {
                    for (ValuePlacement value : ValuePlacement.values()) {
                        for (int cells = 1; cells <= 6; cells++) {
                            for (int[] screen : SCREENS) {
                                checked++;
                                check(failures, anchor, orientation, bar, value, cells, screen[0], screen[1]);
                            }
                        }
                    }
                }
            }
        }
        // lambda 只能捕获 effectively final 的局部变量，先取一份快照
        final int totalChecked = checked;
        assertTrue(failures.isEmpty(), () -> totalChecked + " 种组合里有 " + failures.size() + " 处违反不变量：\n"
                + String.join("\n", failures.subList(0, Math.min(20, failures.size()))));
    }

    private static void check(List<String> failures, Anchor anchor, Orientation orientation, BarSide bar,
                              ValuePlacement value, int cells, int sw, int sh) {
        String name = anchor + "/" + orientation + "/" + bar + "/" + value + "/" + cells + "/" + sw + "x" + sh;
        DuraHudConfig cfg = config(anchor, orientation, bar, value);
        HudLayout layout = HudLayout.geometry(cfg, cells, sw, sh, TEXT_WIDTH);
        int localW = layout.localW();
        int localH = layout.localH();

        // 1) 相邻格子不重叠，且间距不小于格子本身
        Rect[] slots = new Rect[cells];
        for (int i = 0; i < cells; i++) {
            slots[i] = new Rect(layout.cellLocalX(i), layout.cellLocalY(i), Metrics.CELL_W, Metrics.CELL_H);
            if (!slots[i].inside(localW, localH)) {
                failures.add(name + " 格子 " + i + slots[i].show() + " 超出局布局 " + localW + "x" + localH);
            }
        }
        for (int i = 0; i < cells; i++) {
            for (int j = i + 1; j < cells; j++) {
                if (slots[i].intersects(slots[j])) {
                    failures.add(name + " 格子 " + i + slots[i].show() + " 与格子 " + j + slots[j].show() + " 重叠");
                }
            }
        }
        // 间距由构造保证不小于格子尺寸（pitchX = CELL_W + 间隙），断言它恒真没意义；
        // 这里改查更容易出错的那一半：每个格子都得落在布局自己的范围内。
        for (int i = 0; i < cells; i++) {
            if (!slots[i].inside(localW, localH)) {
                failures.add(name + " 格子 " + i + slots[i].show() + " 超出布局 " + localW + "x" + localH);
            }
        }

        // 2) AUTO 时数值必须在条的对侧
        if (value == ValuePlacement.AUTO && bar == layout.textSide()) {
            failures.add(name + " AUTO 时数值与耐久条同侧: " + bar);
        }

        // 3) 耐久条不压自己的格子，也不压任何别的格子；文字同理
        for (int i = 0; i < cells; i++) {
            Rect slot = slots[i];
            Rect barRect = new Rect(slot.x() + layout.barOffsetX(), slot.y() + layout.barOffsetY(),
                    layout.barLength(), Metrics.BAR_H);
            if (!barRect.inside(localW, localH)) {
                failures.add(name + " 耐久条 " + barRect.show() + " 超出布局 " + localW + "x" + localH);
            }
            for (int j = 0; j < cells; j++) {
                if (barRect.intersects(slots[j])) {
                    failures.add(name + " 耐久条 " + barRect.show() + " 压到格子 " + j + slots[j].show());
                }
            }
            if (layout.hasText()) {
                Rect textRect = new Rect(slot.x() + layout.textOffsetX(TEXT_WIDTH),
                        slot.y() + layout.textOffsetY(), TEXT_WIDTH, layout.textHeight());
                if (!textRect.inside(localW, localH)) {
                    failures.add(name + " 文字 " + textRect.show() + " 超出布局 " + localW + "x" + localH);
                }
                for (int j = 0; j < cells; j++) {
                    if (textRect.intersects(slots[j])) {
                        failures.add(name + " 文字 " + textRect.show() + " 压到格子 " + j + slots[j].show());
                    }
                }
                if (textRect.intersects(barRect)) {
                    failures.add(name + " 文字 " + textRect.show() + " 压到自己的耐久条 " + barRect.show());
                }
            }
        }

        // 4) 非自定义锚点：能装下的轴必须完整可见，装不下的轴必须从屏幕内开始画
        //    （HudLayout 会把它钳制到 SCREEN_MARGIN 处）。
        if (anchor != Anchor.CUSTOM) {
            int right = layout.originX() + layout.screenW();
            int bottom = layout.originY() + layout.screenH();
            boolean tooWide = layout.screenW() > sw;
            boolean tooTall = layout.screenH() > sh;
            String detail = name + " 整体跑出窗口：origin=(" + layout.originX() + "," + layout.originY()
                    + ") 尺寸=" + layout.screenW() + "x" + layout.screenH() + " 窗口=" + sw + "x" + sh;
            boolean escaped = (!tooWide && (layout.originX() < 0 || right > sw))
                    || (!tooTall && (layout.originY() < 0 || bottom > sh));
            if (escaped) {
                failures.add(detail + "（该轴装得下，却没完整落在窗口里）");
            } else if ((tooWide && layout.originX() < 0) || (tooTall && layout.originY() < 0)) {
                failures.add(detail + "（装不下时应从屏幕内开始画）");
            }
        }
    }

    @Test
    void screenSizeIsTheScaledLocalSize() {
        DuraHudConfig cfg = config(Anchor.TOP_LEFT, Orientation.HORIZONTAL, BarSide.BELOW, ValuePlacement.AUTO);
        cfg.scale = 1.3F;
        HudLayout layout = HudLayout.geometry(cfg, 4, 854, 480, TEXT_WIDTH);
        assertEquals(Math.round(layout.localW() * 1.3F), layout.screenW());
        assertEquals(Math.round(layout.localH() * 1.3F), layout.screenH());
    }

    @Test
    void textIsOptional() {
        DuraHudConfig cfg = config(Anchor.TOP_LEFT, Orientation.HORIZONTAL, BarSide.BELOW, ValuePlacement.AUTO);
        HudLayout layout = HudLayout.geometry(cfg, 3, 320, 240, 0);
        assertFalse(layout.hasText());
        assertNull(layout.textSide());
        assertEquals(0, layout.textWidth());
    }

    /**
     * 切换显示模式不许动几何：OFF / VALUE / PERCENT 在每一组「条位置 x 数值位置」下，整块尺寸、
     * 格子坐标、条与文字的偏移都必须逐项相同。
     *
     * <p>这是用户报过的问题：模式一变格子就挪。根因是预留宽度按模式取样本、槽位高度按模式取
     * 一行或两行，两者都进了几何。现在预留量与模式无关，模式只决定槽位里画几个字、画在哪。</p>
     */
    @Test
    void valueDisplayNeverMovesTheGeometry() {
        List<String> failures = new ArrayList<>();
        for (BarSide bar : BarSide.values()) {
            for (ValuePlacement value : ValuePlacement.values()) {
                Snap base = snap(bar, value, DuraHudConfig.ValueDisplay.VALUE);
                for (DuraHudConfig.ValueDisplay display : DuraHudConfig.ValueDisplay.values()) {
                    Snap other = snap(bar, value, display);
                    if (!base.equals(other)) {
                        failures.add(bar + "/" + value + "：" + display + " 与 VALUE 的几何不同\n      "
                                + base.show() + "\n      " + other.show());
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), "切换显示模式动了几何，格子会跟着挪：\n  " + String.join("\n  ", failures));
    }

    /** 一帧几何里所有可能被显示模式影响的量，逐项比较用。 */
    private record Snap(int localW, int localH, int padLeft, int padTop, int pitchX, int pitchY,
                        int originX, int originY, int barOffsetX, int barOffsetY, int barLength,
                        int textWidth, int textHeight, int textOffsetY, String cells, String textSide) {

        String show() {
            return "local=" + localW + "x" + localH + " pad=(" + padLeft + "," + padTop + ")"
                    + " pitch=(" + pitchX + "," + pitchY + ") origin=(" + originX + "," + originY + ")"
                    + " bar=(" + barOffsetX + "," + barOffsetY + " 长" + barLength + ")"
                    + " text=(宽" + textWidth + " 高" + textHeight + " dy" + textOffsetY + " side=" + textSide + ")"
                    + " cells=" + cells;
        }
    }

    private static Snap snap(BarSide bar, ValuePlacement value, DuraHudConfig.ValueDisplay display) {
        DuraHudConfig cfg = config(Anchor.TOP_LEFT, Orientation.HORIZONTAL, bar, value);
        cfg.valueDisplay = display;
        HudLayout layout = HudLayout.geometry(cfg, 4, 854, 480, TEXT_WIDTH);
        StringBuilder cells = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            cells.append(layout.cellLocalX(i)).append(',').append(layout.cellLocalY(i)).append(';');
        }
        return new Snap(layout.localW(), layout.localH(), layout.padLeft(), layout.padTop(),
                layout.pitchX(), layout.pitchY(), layout.originX(), layout.originY(),
                layout.barOffsetX(), layout.barOffsetY(), layout.barLength(),
                layout.textWidth(), layout.textHeight(), layout.textOffsetY(),
                cells.toString(), String.valueOf(layout.textSide()));
    }

    @Test
    void barLengthFallsBackToCellWidthThenClamps() {
        DuraHudConfig cfg = new DuraHudConfig();
        cfg.barPlacement = BarSide.BELOW;
        assertEquals(Metrics.CELL_W, HudLayout.resolveBarLength(cfg));
        cfg.barPlacement = BarSide.LEFT;
        assertEquals(Metrics.AUTO_SIDE_BAR, HudLayout.resolveBarLength(cfg));
        cfg.barLength = 5000;
        assertEquals(Metrics.BAR_FULL_W, HudLayout.resolveBarLength(cfg));
        cfg.barLength = 1;
        assertEquals(Metrics.BAR_MIN, HudLayout.resolveBarLength(cfg));
    }

    @Test
    void severityThresholdsAreStrictlyBelow() {
        DuraHudConfig cfg = new DuraHudConfig();
        cfg.warnThreshold = 50;
        cfg.critThreshold = 20;
        assertEquals(Severity.CRIT, Severity.of(cfg, 0.199F));
        assertEquals(Severity.WARN, Severity.of(cfg, 0.20F));
        assertEquals(Severity.WARN, Severity.of(cfg, 0.499F));
        assertEquals(Severity.GOOD, Severity.of(cfg, 0.50F));
        assertEquals(Severity.GOOD, Severity.of(cfg, 1.0F));
    }

    @Test
    void customAnchorIsStillPulledBackOnScreen() {
        DuraHudConfig cfg = config(Anchor.CUSTOM, Orientation.HORIZONTAL, BarSide.BELOW, ValuePlacement.AUTO);
        // 正常范围的偏移要原样生效（钳制不能把用户摆好的位置挪走）。
        cfg.offsetX = 30;
        cfg.offsetY = 40;
        HudLayout placed = HudLayout.geometry(cfg, 4, 854, 480, TEXT_WIDTH);
        assertEquals(30, placed.originX());
        assertEquals(40, placed.originY());
        // 手改配置 / 换过分辨率留下的极端偏移：必须被拉回屏幕内，否则编辑框也跟着出屏，救不回来。
        cfg.offsetX = 100000;
        cfg.offsetY = 100000;
        HudLayout far = HudLayout.geometry(cfg, 4, 320, 240, TEXT_WIDTH);
        assertTrue(far.originX() >= -11 && far.originX() + far.screenW() <= 320 + 11,
                "右下越界：" + far.originX() + " + " + far.screenW());
        assertTrue(far.originY() >= -11 && far.originY() + far.screenH() <= 240 + 11,
                "下越界：" + far.originY() + " + " + far.screenH());
        cfg.offsetX = -100000;
        cfg.offsetY = -100000;
        HudLayout negative = HudLayout.geometry(cfg, 4, 320, 240, TEXT_WIDTH);
        assertTrue(negative.originX() >= -11 && negative.originY() >= -11,
                "左上越界：" + negative.originX() + "," + negative.originY());
    }

    @Test
    void halfACellMayHangOffTheEdge() {
        DuraHudConfig cfg = config(Anchor.TOP_LEFT, Orientation.HORIZONTAL, BarSide.BELOW, ValuePlacement.AUTO);
        int allowX = Metrics.CELL_W / 2;
        int allowY = Metrics.CELL_H / 2;
        // 用户想把 HUD 稍微推过屏幕边缘：推 8 像素就真的过去 8 像素，不再被 SCREEN_MARGIN 弹回来。
        cfg.offsetX = -8;
        cfg.offsetY = -8;
        HudLayout nudged = HudLayout.geometry(cfg, 3, 320, 240, TEXT_WIDTH);
        assertEquals(Metrics.SCREEN_MARGIN - 8, nudged.originX());
        assertEquals(Metrics.SCREEN_MARGIN - 8, nudged.originY());
        // 极端偏移只给半个格子的余量，再多就拦住。
        cfg.offsetX = -100000;
        cfg.offsetY = -100000;
        HudLayout topLeft = HudLayout.geometry(cfg, 3, 320, 240, TEXT_WIDTH);
        assertEquals(-allowX, topLeft.originX());
        assertEquals(-allowY, topLeft.originY());
        // 右下方向同理：右 / 下边最多露出半格。
        cfg.anchor = Anchor.BOTTOM_RIGHT;
        cfg.offsetX = 100000;
        cfg.offsetY = 100000;
        HudLayout bottomRight = HudLayout.geometry(cfg, 3, 320, 240, TEXT_WIDTH);
        assertEquals(320 + allowX - bottomRight.screenW(), bottomRight.originX());
        assertEquals(240 + allowY - bottomRight.screenH(), bottomRight.originY());
        // 锚点自己不受这份余量影响：装得下时仍旧完整可见（HOTBAR_LEFT 靠它把整块顶上屏幕）。
        cfg.anchor = Anchor.HOTBAR_LEFT;
        cfg.offsetX = 0;
        cfg.offsetY = 0;
        HudLayout hotbar = HudLayout.geometry(cfg, 3, 320, 240, TEXT_WIDTH);
        assertTrue(hotbar.originY() >= 0 && hotbar.originY() + hotbar.screenH() <= 240,
                "热键栏锚点该完整可见：" + hotbar.originY() + " + " + hotbar.screenH());
    }

    /** 一条边上允许露出的像素数：半个格子（按缩放换算），再被块自身的一半夹一次；装不下时为 0。 */
    private static int allowance(int cellSize, float scale, int size, int limit) {
        if (size > limit) {
            return 0;
        }
        return Math.max(0, Math.min(Math.round(cellSize * scale / 2.0F), size / 2));
    }

    /**
     * 出厂默认值 = 推荐布局（2.1.4 起）：贴在热键栏右侧，耐久条与两行数值都在格子上方。
     *
     * <p>这条也是防回归：默认值被谁改回旧的那套（快捷栏上方 / 条在格子下方 / 不显示数值），
     * 新装用户的观感就变了，这里会立刻红。</p>
     */
    @Test
    void shippedDefaultsSitRightOfTheHotbarWithBarAndValuesOnTop() {
        DuraHudConfig cfg = new DuraHudConfig();
        assertEquals(Anchor.HOTBAR_RIGHT, cfg.anchor, "默认锚点");
        assertEquals(BarSide.ABOVE, cfg.barPlacement, "默认耐久条位置");
        assertEquals(ValuePlacement.ABOVE, cfg.valuePlacement, "默认数值位置");
        assertEquals(DuraHudConfig.ValueDisplay.VALUE, cfg.valueDisplay, "默认数值内容");
        HudLayout layout = HudLayout.geometry(cfg, 5, 854, 480, TEXT_WIDTH);
        assertEquals(854 / 2 + 91 + 4, layout.originX(), "默认横向位置：热键栏右边缘再往右 4px");
        assertEquals(480 - layout.screenH(), layout.originY(), "默认纵向位置：底边贴着屏幕底部（= 热键栏底边）");
        assertEquals(Metrics.CELL_H + Metrics.BAR_GAP + Metrics.BAR_H + Metrics.TEXT_GAP + Metrics.TEXT_H * 2,
                layout.localH(), "默认块高 = 格子 + 条 + 两行数值");
    }

    // ------------------------------------------------------------------
    // 离线预览：把有代表性的布局写成 JSON，tools/preview.mjs 会把它画成 PNG
    // ------------------------------------------------------------------

    private static final float[] RATIOS = {1.0F, 0.62F, 0.15F, 0.40F, 0.80F};

    @Test
    void exportPreviewGeometry() throws IOException {
        List<String> cases = new ArrayList<>();
        cases.add(previewCase("default-hotbar-right-above-above", Anchor.HOTBAR_RIGHT, Orientation.HORIZONTAL,
                BarSide.ABOVE, ValuePlacement.ABOVE, 5, 854, 480));
        cases.add(previewCase("horizontal-below-auto", Anchor.HOTBAR_ABOVE, Orientation.HORIZONTAL,
                BarSide.BELOW, ValuePlacement.AUTO, 5, 854, 480));
        cases.add(previewCase("horizontal-above-same-side", Anchor.HOTBAR_ABOVE, Orientation.HORIZONTAL,
                BarSide.ABOVE, ValuePlacement.ABOVE, 5, 854, 480));
        cases.add(previewCase("horizontal-right-auto", Anchor.TOP_LEFT, Orientation.HORIZONTAL,
                BarSide.RIGHT, ValuePlacement.AUTO, 5, 854, 480));
        cases.add(previewCase("vertical-left-auto", Anchor.TOP_LEFT, Orientation.VERTICAL,
                BarSide.LEFT, ValuePlacement.AUTO, 4, 854, 480));
        cases.add(previewCase("narrow-320x240-below-auto", Anchor.HOTBAR_ABOVE, Orientation.HORIZONTAL,
                BarSide.BELOW, ValuePlacement.AUTO, 6, 320, 240));
        String json = "{\n  \"cellSize\": [" + Metrics.CELL_W + ", " + Metrics.CELL_H + "],\n"
                + "  \"cellUv\": [" + Metrics.CELL_U + ", " + Metrics.CELL_V + "],\n"
                + "  \"selectUv\": [" + Metrics.SELECT_U + ", " + Metrics.SELECT_V + ", "
                + Metrics.SELECT_W + ", " + Metrics.SELECT_H + "],\n"
                + "  \"iconOffset\": [" + Metrics.ICON_X + ", " + Metrics.ICON_Y + ", "
                + Metrics.ICON_SIZE + "],\n"
                + "  \"barHeight\": " + Metrics.BAR_H + ",\n"
                + "  \"barStep\": " + Metrics.BAR_STEP + ",\n"
                + "  \"capLeft\": " + Metrics.CAP_LEFT + ",\n"
                + "  \"capRight\": " + Metrics.CAP_RIGHT + ",\n"
                + "  \"barFullW\": " + Metrics.BAR_FULL_W + ",\n"
                + "  \"cases\": [\n" + String.join(",\n", cases) + "\n  ]\n}\n";
        Path out = Path.of("build", "preview", "layout.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, json, StandardCharsets.UTF_8);
        // 写完读回来核对：文件存在是恒真的（writeString 不抛就存在），要防的是写空 / 写坏。
        String written = Files.readString(out, StandardCharsets.UTF_8);
        assertEquals(json, written);
        assertTrue(written.contains("narrow-320x240-below-auto"), "预览用例必须全部写进文件");
    }

    private static String previewCase(String name, Anchor anchor, Orientation orientation, BarSide bar,
                                      ValuePlacement value, int cells, int sw, int sh) {
        DuraHudConfig cfg = config(anchor, orientation, bar, value);
        HudLayout layout = HudLayout.geometry(cfg, cells, sw, sh, TEXT_WIDTH);
        StringBuilder sb = new StringBuilder();
        sb.append("    {\"name\": \"").append(name).append("\", \"screen\": [").append(sw).append(", ").append(sh)
                .append("], \"origin\": [").append(layout.originX()).append(", ").append(layout.originY())
                .append("], \"scale\": ").append(layout.scale())
                .append(", \"local\": [").append(layout.localW()).append(", ").append(layout.localH())
                .append("], \"textOffsetY\": ").append(layout.hasText() ? layout.textOffsetY() : 0)
                .append(", \"textHeight\": ").append(layout.hasText() ? layout.textHeight() : 0)
                .append(", \"cells\": [");
        for (int i = 0; i < cells; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("[").append(layout.cellLocalX(i)).append(", ").append(layout.cellLocalY(i)).append("]");
        }
        sb.append("], \"entries\": [");
        for (int i = 0; i < cells; i++) {
            float ratio = RATIOS[i % RATIOS.length];
            Severity severity = Severity.of(cfg, ratio);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("{\"ratio\": ").append(ratio)
                    .append(", \"barColorIndex\": ").append(severity.barColorIndex)
                    .append(", \"textColor\": ").append(severity.textColor)
                    .append(", \"critical\": ").append(severity == Severity.CRIT)
                    .append("}");
        }
        sb.append("], \"barOffset\": [").append(layout.barOffsetX()).append(", ").append(layout.barOffsetY())
                .append("], \"barLength\": ").append(layout.barLength())
                .append(", \"textSide\": \"").append(layout.textSide()).append("\"")
                .append(", \"textWidth\": ").append(layout.textWidth())
                .append(", \"textOffsetX\": ").append(layout.hasText() ? layout.textOffsetX(TEXT_WIDTH) : 0)
                .append("}");
        return sb.toString();
    }

    /**
     * 槽位高度仍是两行（常量），但单行（百分比）贴着它那一侧：上下两侧时数字紧挨耐久条，
     * 中间只留 TEXT_GAP；左右两侧时纵向居中，位置与 2.1.2 的公式一模一样。
     */
    @Test
    void singleLineHugsTheSideItSitsOn() {
        for (BarSide side : new BarSide[]{BarSide.ABOVE, BarSide.BELOW}) {
            ValuePlacement value = side == BarSide.ABOVE ? ValuePlacement.ABOVE : ValuePlacement.BELOW;
            HudLayout layout = HudLayout.geometry(
                    config(Anchor.TOP_LEFT, Orientation.HORIZONTAL, side, value), 3, 854, 480, TEXT_WIDTH);
            assertEquals(Metrics.TEXT_H * 2, layout.textHeight(), side + " 的槽位高度不是两行");
            int cellY = layout.cellLocalY(0);
            int lineTop = cellY + layout.textOffsetY() + layout.singleLineOffset();
            int barTop = cellY + layout.barOffsetY();
            int gap = side == BarSide.ABOVE
                    ? barTop - (lineTop + Metrics.TEXT_H)
                    : lineTop - (barTop + Metrics.BAR_H);
            assertEquals(Metrics.TEXT_GAP, gap,
                    side + " 的单行与耐久条之间应当只留 " + Metrics.TEXT_GAP + "px");
        }
        for (ValuePlacement value : new ValuePlacement[]{ValuePlacement.LEFT, ValuePlacement.RIGHT}) {
            HudLayout layout = HudLayout.geometry(
                    config(Anchor.TOP_LEFT, Orientation.HORIZONTAL, BarSide.BELOW, value), 3, 854, 480, TEXT_WIDTH);
            assertEquals((Metrics.CELL_H - Metrics.TEXT_H + 1) / 2,
                    layout.textOffsetY() + layout.singleLineOffset(),
                    value + " 的单行没有在格子高度里居中（改这里等于改 2.1.2 的观感）");
        }
    }

    /** 数值纵向微调只挪数字：原点、整块尺寸、格距与槽位一个像素都不动，越界值夹到 ±9。 */
    @Test
    void valueOffsetNudgesOnlyTheNumbers() {
        HudLayout first = HudLayout.geometry(
                config(Anchor.HOTBAR_RIGHT, Orientation.HORIZONTAL, BarSide.ABOVE, ValuePlacement.ABOVE),
                4, 854, 480, TEXT_WIDTH);
        int originX = first.originX();
        int originY = first.originY();
        int localW = first.localW();
        int localH = first.localH();
        int pitchX = first.pitchX();
        int pitchY = first.pitchY();
        int textOffsetY = first.textOffsetY();
        assertEquals(0, first.valueOffsetY(), "默认不加偏移");

        DuraHudConfig nudged = config(Anchor.HOTBAR_RIGHT, Orientation.HORIZONTAL, BarSide.ABOVE, ValuePlacement.ABOVE);
        nudged.valueOffsetY = -7;
        HudLayout second = HudLayout.geometry(nudged, 4, 854, 480, TEXT_WIDTH);
        assertEquals(-7, second.valueOffsetY());
        assertEquals(originX, second.originX(), "微调不该挪整块 HUD");
        assertEquals(originY, second.originY(), "微调不该挪整块 HUD");
        assertEquals(localW, second.localW(), "微调不该改整块宽度");
        assertEquals(localH, second.localH(), "微调不该改整块高度");
        assertEquals(pitchX, second.pitchX(), "微调不该改格距");
        assertEquals(pitchY, second.pitchY(), "微调不该改格距");
        assertEquals(textOffsetY, second.textOffsetY(), "微调不该改槽位位置");

        DuraHudConfig wild = config(Anchor.HOTBAR_RIGHT, Orientation.HORIZONTAL, BarSide.ABOVE, ValuePlacement.ABOVE);
        wild.valueOffsetY = 1000;
        assertEquals(DuraHudConfig.VALUE_OFFSET_LIMIT,
                HudLayout.geometry(wild, 4, 854, 480, TEXT_WIDTH).valueOffsetY(),
                "越界的微调要夹到上限（直接改字段的路径也必须安全）");
    }

    /** 左右两侧的数值在固定槽位里居中：位数或模式变了只挪槽位内部，格子不动。 */
    @Test
    void textIsCentredInsideTheReservedSlotOnBothSides() {
        for (ValuePlacement value : new ValuePlacement[]{ValuePlacement.LEFT, ValuePlacement.RIGHT}) {
            DuraHudConfig cfg = config(Anchor.TOP_LEFT, Orientation.HORIZONTAL, BarSide.BELOW, value);
            HudLayout layout = HudLayout.geometry(cfg, 3, 854, 480, TEXT_WIDTH);
            int slotLeft = value == ValuePlacement.LEFT
                    ? -layout.textSkip() - Metrics.TEXT_GAP - layout.textWidth()
                    : Metrics.CELL_W + layout.textSkip() + Metrics.TEXT_GAP;
            for (int width = 1; width <= layout.textWidth(); width++) {
                assertEquals(slotLeft + (layout.textWidth() - width) / 2, layout.textOffsetX(width),
                        value + " 槽位内没有居中（字宽 " + width + "）");
            }
            Rect slot = new Rect(layout.cellLocalX(0) + slotLeft, layout.cellLocalY(0) + layout.textOffsetY(),
                    layout.textWidth(), layout.textHeight());
            assertTrue(slot.inside(layout.localW(), layout.localH()),
                    value + " 槽位 " + slot.show() + " 超出布局 " + layout.localW() + "x" + layout.localH());
        }
    }

    /**
     * 源码级守卫：预留宽度与槽位高度都必须与显示模式无关。单靠几何测试拦不住「谁又把模式传进
     * 预留计算」这类回归，所以这里直接盯源码。
     */
    @Test
    void textSlotReservationNeverReadsTheDisplayMode() throws IOException {
        String atlas = read("src/main/java/dev/durahud/hud/Atlas.java");
        assertFalse(Pattern.compile("reserveTextWidth\\s*\\([^)]*ValueDisplay").matcher(atlas).find(),
                "预留宽度不许再收显示模式参数：它一跟模式变，格子间距就跟着变");
        assertTrue(atlas.contains("Metrics.TEXT_SAMPLES"), "预留宽度要取样本表里最宽的那一个");
        String layout = read("src/main/java/dev/durahud/hud/HudLayout.java");
        assertFalse(layout.contains("valueDisplay"), "几何层不许读显示模式：读一次格子就会跟着动");
    }

    // ------------------------------------------------------------------

    private static String read(String relative) throws IOException {
        Path file = locate(relative);
        Assumptions.assumeTrue(file != null, "找不到 " + relative + "，跳过源码级守卫（需要从工程根运行测试）");
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** 从当前目录往上找，最多找三层，这样从工程根或子目录跑测试都能命中。 */
    private static Path locate(String relative) {
        Path dir = Path.of("").toAbsolutePath();
        for (int up = 0; up < 4 && dir != null; up++, dir = dir.getParent()) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
