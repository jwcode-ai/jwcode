package com.jwcode.ui;

import com.jwcode.cli.ui.EnhancedTerminal;
import com.jwcode.ui.components.Component;
import com.jwcode.ui.renderer.AnsiRenderer;
import com.jwcode.ui.renderer.ColorCapability;
import com.jwcode.ui.terminal.TerminalBuffer;
import com.jwcode.ui.terminal.TerminalRenderer;

/**
 * InkPipeline - 渲染管线总控制器
 *
 * <p>将 Flexbox 布局、TerminalBuffer 光栅化、格子级 Diff、ANSI 输出
 * 串联为一条完整的渲染管线：</p>
 *
 * <pre>
 * 组件树 → FlexLayout 布局 → 光栅化到 TerminalBuffer → endFrame() Diff → ANSI 输出
 * </pre>
 *
 * <p>用法：</p>
 * <pre>{@code
 * InkPipeline pipeline = new InkPipeline(enhancedTerminal);
 * pipeline.setRoot(rootComponent);
 *
 * // 每帧：
 * pipeline.render();
 * }</pre>
 */
public class InkPipeline {

    private final EnhancedTerminal terminal;
    private final TerminalBuffer buffer;
    private final TerminalRenderer renderer;
    private final AnsiRenderer ansiRenderer;

    /** 根组件 */
    private Component root;

    /** 是否初始化 */
    private boolean initialized = false;

    public InkPipeline(EnhancedTerminal terminal) {
        this.terminal = terminal;
        this.buffer = new TerminalBuffer(terminal.getTerminalWidth(), terminal.getTerminalHeight());
        this.renderer = null;
        this.ansiRenderer = new AnsiRenderer(toColorCapability(terminal.getColorLevel()));
    }

    /**
     * 使用 Lanterna TerminalRenderer 构造（兼容模式）。
     */
    public InkPipeline(TerminalRenderer renderer) {
        this.terminal = null;
        this.buffer = renderer.getBuffer();
        this.renderer = renderer;
        this.ansiRenderer = new AnsiRenderer(ColorCapability.TRUE_COLOR);
    }

    /**
     * 初始化管线（清屏 + 隐藏光标）。
     */
    public void init() {
        if (terminal != null) {
            terminal.clearScreen();
            terminal.hideCursor();
        }
        buffer.clear();
        buffer.markFullDirty();
        initialized = true;
    }

    /**
     * 设置根组件。
     */
    public void setRoot(Component root) {
        this.root = root;
    }

    /**
     * 获取根组件。
     */
    public Component getRoot() {
        return root;
    }

    /**
     * 执行一帧渲染。
     *
     * <p>完整流程：</p>
     * <ol>
     *   <li>清空 Buffer</li>
     *   <li>将根组件内容光栅化到 Buffer 中</li>
     *   <li>调用 endFrame() 获取与上一帧的 Diff</li>
     *   <li>通过 EnhancedTerminal 或 TerminalRenderer 输出 Diff</li>
     * </ol>
     */
    public void render() {
        if (root == null) return;

        if (!initialized) {
            init();
        }

        // 1. 清空当前帧 Buffer
        buffer.clear();

        // 2. 获取终端尺寸
        int termWidth = terminal != null ? terminal.getTerminalWidth() : buffer.getWidth();
        int termHeight = terminal != null ? terminal.getTerminalHeight() : buffer.getHeight();

        // 更新 Buffer 尺寸（如果终端尺寸变化）
        buffer.resize(termWidth, termHeight);

        // 3. 将根组件渲染到 Buffer
        renderComponentToBuffer(root, 0, 0, termWidth, termHeight);

        // 4. 执行 Diff
        TerminalBuffer.DiffRegion[] diffs = buffer.endFrame();

        // 5. 输出
        if (terminal != null) {
            String ansiOutput = ansiRenderer.renderDiffs(diffs, termWidth);
            terminal.renderFrame(ansiOutput);
        } else if (renderer != null) {
            renderer.refresh();
        }
    }

    /**
     * 递归将组件渲染到 TerminalBuffer 中。
     *
     * @param component 组件
     * @param offsetX   容器偏移 X
     * @param offsetY   容器偏移 Y
     * @param maxWidth  可用宽度
     * @param maxHeight 可用高度
     */
    private void renderComponentToBuffer(Component component, int offsetX, int offsetY,
                                          int maxWidth, int maxHeight) {
        if (component == null) return;

        // 获取组件在布局中的位置
        int compX = offsetX + component.getLayoutX();
        int compY = offsetY + component.getLayoutY();

        // 渲染组件为字符串
        String rendered = component.render();
        if (rendered == null || rendered.isEmpty()) return;

        String[] lines = rendered.split("\n", -1);
        for (int ly = 0; ly < lines.length && compY + ly < maxHeight; ly++) {
            String line = lines[ly];
            for (int lx = 0; lx < line.length() && compX + lx < maxWidth; lx++) {
                buffer.writeCharacter(compX + lx, compY + ly,
                        new com.googlecode.lanterna.TextCharacter(
                                line.charAt(lx),
                                com.googlecode.lanterna.TextColor.ANSI.DEFAULT,
                                com.googlecode.lanterna.TextColor.ANSI.DEFAULT));
            }
        }
    }

    /**
     * 获取 TerminalBuffer 引用。
     */
    public TerminalBuffer getBuffer() {
        return buffer;
    }

    /**
     * 关闭管线（显示光标）。
     */
    public void close() {
        if (terminal != null) {
            terminal.showCursor();
        }
    }

    /**
     * 是否已初始化。
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 将 EnhancedTerminal.ColorLevel 转换为 AnsiRenderer.ColorCapability。
     */
    private static ColorCapability toColorCapability(EnhancedTerminal.ColorLevel level) {
        switch (level) {
            case TRUE_COLOR: return ColorCapability.TRUE_COLOR;
            case COLORS_256: return ColorCapability.COLORS_256;
            default: return ColorCapability.COLORS_16;
        }
    }
}
