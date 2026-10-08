package dev.durahud.config;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置界面的结构性守卫：下面两条规则各对应一类真实发生过的崩溃。
 *
 * <p>界面每换一页都会重建全部控件，所以「本页第几行」这类字段必须在 {@code init()} 里清空；
 * 漏一个就会在某次换页之后指向别的控件。取控件时也不许直接强转类型。</p>
 */
class ScreenRowResetTest {

    private static final String SCREEN = "src/main/java/dev/durahud/config/DuraHudConfigScreen.java";

    @Test
    void everyRowFieldIsResetBeforeEachPageIsBuilt() throws IOException {
        String src = read(SCREEN);
        List<String> rows = new ArrayList<>();
        Matcher names = Pattern.compile("private\\s+int\\s+(\\w+Row)\\s*=").matcher(src);
        while (names.find()) {
            rows.add(names.group(1));
        }
        assertFalse(rows.isEmpty(), "没有找到任何 xxxRow 字段，守卫测试本身需要跟着改");
        String init = methodBody(src, "protected void init()");
        for (String row : rows) {
            assertTrue(init.contains(row + " = -1;"),
                    row + " 没有在 init() 里复位：换页后它会指到上一页的行，取值就指错控件");
        }
    }

    @Test
    void widgetTypesAreNeverCastBlindly() throws IOException {
        String src = read(SCREEN);
        assertFalse(Pattern.compile("\\(\\s*(NumberSliderWidget|ButtonWidget|ClickableWidget)\\s*\\)\\s*widgets\\.get\\(")
                        .matcher(src).find(),
                "widgets.get(...) 的结果不许直接强转：同一个行号在不同页上是不同控件，转错就崩");
        assertTrue(src.contains("instanceof NumberSliderWidget slider"), "取滑条时要按类型判断");
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

    /** 取出以 {@code signature} 开头那个方法的函数体，遇到第一个单独一行的四个空格加右花括号为止。 */
    private static String methodBody(String src, String signature) {
        int start = src.indexOf(signature);
        assertTrue(start >= 0, "源码里找不到 " + signature);
        int end = src.indexOf("\n    }", start);
        assertTrue(end > start, signature + " 没有正常结束");
        return src.substring(start, end);
    }
}
