package com.jwcode.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.jwcode.core.task.Task;
import com.jwcode.core.task.TaskStatus;
import com.jwcode.core.task.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * TaskTui — /task 三列交互终端界面。
 *
 * <p>实现 Kimi Code 的任务浏览器 TUI：</p>
 * <ul>
 *   <li><b>左列</b>：任务列表（ID + 描述 + 状态）</li>
 *   <li><b>中列</b>：实时输出（滚动）</li>
 *   <li><b>右列</b>：操作区（停止/刷新/预览）</li>
 * </ul>
 */
public class TaskTui implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(TaskTui.class);

    private final Screen screen;
    private final TaskStore taskStore;
    private final int refreshIntervalMs;

    private volatile boolean running = false;
    private volatile int selectedIndex = 0;
    private volatile String filterStatus = null; // null = all

    public TaskTui(TaskStore taskStore) throws IOException {
        this(taskStore, 500);
    }

    public TaskTui(TaskStore taskStore, int refreshIntervalMs) throws IOException {
        this.taskStore = taskStore;
        this.refreshIntervalMs = refreshIntervalMs;
        this.screen = new DefaultTerminalFactory().createScreen();
    }

    /**
     * 启动 TUI 事件循环。
     */
    public void start() throws IOException {
        screen.startScreen();
        running = true;

        Thread refreshThread = new Thread(this::refreshLoop, "TaskTui-Refresh");
        refreshThread.setDaemon(true);
        refreshThread.start();

        try {
            while (running) {
                render();
                KeyStroke key = screen.readInput();
                if (key == null) continue;

                if (key.getKeyType() == KeyType.Escape || key.getKeyType() == KeyType.Character && key.getCharacter() == 'q') {
                    running = false;
                } else if (key.getKeyType() == KeyType.ArrowDown) {
                    selectedIndex = Math.min(selectedIndex + 1, getFilteredTasks().size() - 1);
                } else if (key.getKeyType() == KeyType.ArrowUp) {
                    selectedIndex = Math.max(selectedIndex - 1, 0);
                } else if (key.getKeyType() == KeyType.Character) {
                    switch (Character.toLowerCase(key.getCharacter())) {
                        case 'r' -> onRefresh();
                        case 's' -> onStopSelected();
                        case 'a' -> filterStatus = null;
                        case 'f' -> filterStatus = "FAILED";
                        case 'c' -> filterStatus = "COMPLETED";
                        case 'o' -> filterStatus = "RUNNING";
                    }
                }
            }
        } finally {
            screen.stopScreen();
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            screen.stopScreen();
        } catch (IOException e) {
            logger.debug("[TaskTui] 关闭异常", e);
        }
    }

    // ==================== 渲染 ====================

    private void render() {
        screen.clear();
        TextGraphics g = screen.newTextGraphics();
        TerminalSize size = screen.getTerminalSize();
        int width = size.getColumns();
        int height = size.getRows();

        List<Task> tasks = getFilteredTasks();
        if (selectedIndex >= tasks.size()) selectedIndex = Math.max(0, tasks.size() - 1);

        // 列宽分配
        int leftWidth = Math.max(25, width / 3);
        int rightWidth = 18;
        int midWidth = width - leftWidth - rightWidth - 2;

        // 绘制标题栏
        drawHeader(g, width);

        // 绘制三列
        int yStart = 2;
        int contentHeight = height - yStart - 2;

        drawTaskList(g, tasks, 0, yStart, leftWidth, contentHeight);
        drawOutputPane(g, tasks, leftWidth + 1, yStart, midWidth, contentHeight);
        drawActionsPane(g, leftWidth + midWidth + 2, yStart, rightWidth, contentHeight);

        // 绘制底部状态栏
        drawFooter(g, width, height - 1, tasks.size());

        try {
            screen.refresh();
        } catch (IOException e) {
            logger.debug("[TaskTui] 刷新失败", e);
        }
    }

    private void drawHeader(TextGraphics g, int width) {
        g.setBackgroundColor(TextColor.ANSI.BLUE);
        g.setForegroundColor(TextColor.ANSI.WHITE);
        String title = " JWCode 任务浏览器 (/task) ";
        g.putString(0, 0, padRight(title, width));
        g.setBackgroundColor(TextColor.ANSI.DEFAULT);
    }

    private void drawTaskList(TextGraphics g, List<Task> tasks, int x, int y, int w, int h) {
        drawPaneBorder(g, x, y, w, h, "任务列表");

        int row = y + 1;
        int maxDisplay = Math.min(tasks.size(), h - 2);
        int scrollOffset = Math.max(0, selectedIndex - maxDisplay + 1);

        for (int i = scrollOffset; i < Math.min(tasks.size(), scrollOffset + maxDisplay); i++) {
            Task task = tasks.get(i);
            boolean isSelected = i == selectedIndex;

            if (isSelected) {
                g.setBackgroundColor(TextColor.ANSI.CYAN);
                g.setForegroundColor(TextColor.ANSI.BLACK);
            } else {
                g.setBackgroundColor(TextColor.ANSI.DEFAULT);
                g.setForegroundColor(statusColor(task.getStatus()));
            }

            String line = String.format(" %s %s ",
                statusIcon(task.getStatus()),
                truncate(task.getTitle() != null ? task.getTitle() : task.getDescription(), w - 6));
            g.putString(x + 1, row, padRight(line, w - 2));

            g.setBackgroundColor(TextColor.ANSI.DEFAULT);
            row++;
        }
    }

    private void drawOutputPane(TextGraphics g, List<Task> tasks, int x, int y, int w, int h) {
        drawPaneBorder(g, x, y, w, h, "实时输出");

        if (tasks.isEmpty() || selectedIndex >= tasks.size()) {
            g.setForegroundColor(TextColor.ANSI.WHITE);
            g.putString(x + 2, y + 2, "(无选中任务)");
            return;
        }

        Task task = tasks.get(selectedIndex);
        String output = task.getOutputString();
        String[] lines = output.split("\n");

        int row = y + 1;
        int maxLines = Math.min(lines.length, h - 2);
        int scrollOffset = Math.max(0, lines.length - maxLines);

        g.setForegroundColor(TextColor.ANSI.WHITE);
        for (int i = 0; i < maxLines && row < y + h - 1; i++) {
            String line = truncate(lines[scrollOffset + i], w - 2);
            g.putString(x + 1, row, line);
            row++;
        }
    }

    private void drawActionsPane(TextGraphics g, int x, int y, int w, int h) {
        drawPaneBorder(g, x, y, w, h, "操作区");

        String[] actions = {
            "[S] 停止任务",
            "[R] 刷新",
            "[A] 全部",
            "[O] 运行中",
            "[C] 已完成",
            "[F] 失败",
            "[Q] 退出"
        };

        g.setForegroundColor(TextColor.ANSI.YELLOW);
        int row = y + 2;
        for (String action : actions) {
            if (row < y + h - 1) {
                g.putString(x + 1, row, truncate(action, w - 2));
                row++;
            }
        }
    }

    private void drawFooter(TextGraphics g, int width, int y, int taskCount) {
        g.setBackgroundColor(TextColor.ANSI.BLACK);
        g.setForegroundColor(TextColor.ANSI.WHITE);
        String status = String.format(" 共 %d 个任务 | 筛选: %s | 选中: %d ",
            taskCount, filterStatus != null ? filterStatus : "全部", selectedIndex + 1);
        g.putString(0, y, padRight(status, width));
        g.setBackgroundColor(TextColor.ANSI.DEFAULT);
    }

    private void drawPaneBorder(TextGraphics g, int x, int y, int w, int h, String title) {
        g.setForegroundColor(TextColor.ANSI.WHITE);
        // 上边框
        g.putString(x, y, "┌" + "─".repeat(Math.max(0, w - 2)) + "┐");
        // 标题
        if (title != null && !title.isEmpty() && w > title.length() + 4) {
            g.putString(x + 2, y, " " + title + " ");
        }
        // 左右边框
        for (int i = 1; i < h - 1; i++) {
            g.putString(x, y + i, "│");
            g.putString(x + w - 1, y + i, "│");
        }
        // 下边框
        g.putString(x, y + h - 1, "└" + "─".repeat(Math.max(0, w - 2)) + "┘");
    }

    // ==================== 交互处理 ====================

    private void onRefresh() {
        taskStore.load();
        logger.info("[TaskTui] 手动刷新任务列表");
    }

    private void onStopSelected() {
        List<Task> tasks = getFilteredTasks();
        if (selectedIndex < tasks.size()) {
            Task task = tasks.get(selectedIndex);
            if (task.getStatus().isActive()) {
                // 触发停止 — 实际停止逻辑由上层处理
                logger.info("[TaskTui] 请求停止任务 | taskId={}", task.getId());
                // 这里可以调用 BackgroundTaskLauncher.stop(task.getId())
                task.markStopped();
                taskStore.update(task);
            }
        }
    }

    // ==================== 工具方法 ====================

    private List<Task> getFilteredTasks() {
        List<Task> all = taskStore.list();
        if (filterStatus == null) return all;
        try {
            TaskStatus status = TaskStatus.valueOf(filterStatus);
            return all.stream().filter(t -> t.getStatus() == status).toList();
        } catch (IllegalArgumentException e) {
            return all;
        }
    }

    private void refreshLoop() {
        while (running) {
            try {
                Thread.sleep(refreshIntervalMs);
                if (running) {
                    render();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static String statusIcon(TaskStatus status) {
        if (status == null) return "?";
        switch (status) {
            case PENDING: return "○";
            case RUNNING: return "▶";
            case COMPLETED: return "✓";
            case FAILED: return "✗";
            case STOPPED: return "■";
            case CANCELLED: return "⊘";
            default: return "?";
        }
    }

    private static TextColor statusColor(TaskStatus status) {
        if (status == null) return TextColor.ANSI.DEFAULT;
        switch (status) {
            case PENDING: return TextColor.ANSI.WHITE;
            case RUNNING: return TextColor.ANSI.CYAN;
            case COMPLETED: return TextColor.ANSI.GREEN;
            case FAILED: return TextColor.ANSI.RED;
            case STOPPED: return TextColor.ANSI.YELLOW;
            case CANCELLED: return TextColor.ANSI.MAGENTA;
            default: return TextColor.ANSI.DEFAULT;
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    private static String padRight(String s, int n) {
        if (s.length() >= n) return s;
        return s + " ".repeat(n - s.length());
    }
}
