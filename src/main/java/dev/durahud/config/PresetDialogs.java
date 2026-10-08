package dev.durahud.config;

import dev.durahud.DuraHudClient;

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 导入 / 导出预设用的原生文件选择框。
 *
 * <p>用 AWT 的 {@link FileDialog}：各平台上弹的就是系统自己的「打开 / 另存为」框（Windows 上是
 * 资源管理器那一套），不引第三方依赖、也不在游戏窗口里叠一层 Swing 界面。</p>
 *
 * <p>弹框会阻塞，所以整套调用<b>必须在工作线程里</b>跑（见 {@link DuraHudConfigScreen}）——
 * 在渲染线程上弹原生框会让游戏卡住甚至被判成无响应。无图形环境时 {@link #available()} 为
 * false，界面会退回 config 目录下的默认路径。</p>
 */
final class PresetDialogs {

    private static final String SUFFIX = ".txt";

    private PresetDialogs() {
    }

    /** 当前环境能不能弹原生框。 */
    static boolean available() {
        try {
            return !GraphicsEnvironment.isHeadless();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 弹「另存为」框；取消或出错返回 null。用户没写扩展名时补上 .txt。 */
    static Path chooseSave(File directory, String defaultName, String title) {
        Path chosen = open(directory, defaultName, title, FileDialog.SAVE);
        if (chosen == null) {
            return null;
        }
        String name = chosen.getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(SUFFIX) && name.indexOf('.') < 0) {
            return chosen.resolveSibling(name + SUFFIX);
        }
        return chosen;
    }

    /** 弹「打开」框；取消或出错返回 null。 */
    static Path chooseOpen(File directory, String defaultName, String title) {
        return open(directory, defaultName, title, FileDialog.LOAD);
    }

    private static Path open(File directory, String defaultName, String title, int mode) {
        Frame owner = null;
        try {
            // 无主 FileDialog 在部分系统上会跑到游戏窗口背后：给它一个隐形且置顶的 owner。
            owner = new Frame();
            owner.setAlwaysOnTop(true);
            FileDialog dialog = new FileDialog(owner, title, mode);
            dialog.setFilenameFilter((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(SUFFIX));
            if (defaultName != null && !defaultName.isEmpty()) {
                dialog.setFile(defaultName);
            }
            if (directory != null) {
                dialog.setDirectory(directory.getAbsolutePath());
            }
            dialog.setAlwaysOnTop(true);
            dialog.setVisible(true);   // 阻塞到用户选完或取消
            Path chosen = path(dialog.getDirectory(), dialog.getFile());
            dialog.dispose();
            return chosen;
        } catch (Throwable t) {
            DuraHudClient.LOGGER.warn("[DuraHUD] 文件选择框出错：{}", t.toString());
            return null;
        } finally {
            if (owner != null) {
                owner.dispose();
            }
        }
    }

    /** 目录 + 文件名拼成路径；用户点了取消时名字是 null 或空串。 */
    private static Path path(String directory, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        File file = new File(name);
        if (!file.isAbsolute() && directory != null && !directory.isEmpty()) {
            file = new File(directory, name);
        }
        return file.toPath();
    }
}
