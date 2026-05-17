package com.jwcode.ui;

import com.jwcode.cli.ui.EnhancedTerminal;
import com.jwcode.ui.components.Box;
import com.jwcode.ui.components.Text;
import com.jwcode.ui.layout.Align;
import com.jwcode.ui.layout.FlexDirection;

/**
 * InkPipeline 演示 - 在终端中展示 Flexbox 布局 + 格子级 Diff 渲染效果。
 *
 * <p>运行方式：</p>
 * <pre>
 *   mvn exec:java -pl jwcode-ui -Dexec.mainClass="com.jwcode.ui.InkPipelineDemo" -Dexec.classpathScope=test
 * </pre>
 * <p>或直接从 IDE 运行此 main 方法。</p>
 */
public class InkPipelineDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("JWCode InkPipeline Demo");
        System.out.println("=======================");
        System.out.println("按 Ctrl+C 退出\n");

        // 初始化增强终端
        EnhancedTerminal terminal = new EnhancedTerminal();
        terminal.hideCursor();
        terminal.clearScreen();

        // 创建 InkPipeline
        InkPipeline pipeline = new InkPipeline(terminal);

        // ========== 演示 1: ROW 布局（左右分栏） ==========
        Thread.sleep(1000);
        Box root1 = new Box();
        root1.setShowBorder(false);
        root1.setFlexDirection(FlexDirection.ROW);

        Text leftPanel = new Text("┌─ 左侧面板 ───────┐\n│  flexGrow=1      │\n│  自动占据剩余宽度  │\n└──────────────────┘");
        leftPanel.setFlexGrow(1);

        Text rightPanel = new Text("┌─ 右侧 ─┐\n│ 固定20  │\n└────────┘");
        rightPanel.setMaxWidth(20);

        root1.addChild(leftPanel);
        root1.addChild(rightPanel);

        pipeline.setRoot(root1);
        pipeline.render();
        System.out.println("\n↑ 演示 1: ROW 布局 - 左侧 flexGrow=1，右侧固定宽度 20");
        Thread.sleep(2000);

        // ========== 演示 2: COLUMN 布局（上下排列） ==========
        Box root2 = new Box();
        root2.setShowBorder(false);
        root2.setFlexDirection(FlexDirection.COLUMN);

        Text header = new Text("═══ 头部标题 ═══");
        header.setAlignment(Text.TextAlignment.CENTER);

        Text body = new Text("这是中间内容区域，flexGrow=1 自动填充剩余高度。\n第二行内容。\n第三行内容。");
        body.setFlexGrow(1);

        Text footer = new Text("── 底部状态栏 ──");
        footer.setAlignment(Text.TextAlignment.CENTER);

        root2.addChild(header);
        root2.addChild(body);
        root2.addChild(footer);

        pipeline.setRoot(root2);
        pipeline.render();
        System.out.println("\n↑ 演示 2: COLUMN 布局 - 头部 + 内容(flexGrow=1) + 底部");
        Thread.sleep(2000);

        // ========== 演示 3: CENTER 对齐 ==========
        Box root3 = new Box();
        root3.setShowBorder(false);
        root3.setFlexDirection(FlexDirection.COLUMN);
        root3.setJustifyContent(Align.CENTER);
        root3.setAlignItems(Align.CENTER);

        Text centered = new Text("★★★★★★★★★★★★★★★★\n★   居中对齐示例   ★\n★  justifyContent   ★\n★  = CENTER        ★\n★  alignItems       ★\n★  = CENTER        ★\n★★★★★★★★★★★★★★★★");

        root3.addChild(centered);

        pipeline.setRoot(root3);
        pipeline.render();
        System.out.println("\n↑ 演示 3: CENTER 对齐 - 内容在容器中居中显示");
        Thread.sleep(2000);

        // ========== 演示 4: SPACE_BETWEEN 分布 ==========
        Box root4 = new Box();
        root4.setShowBorder(false);
        root4.setFlexDirection(FlexDirection.ROW);
        root4.setJustifyContent(Align.SPACE_BETWEEN);

        Text item1 = new Text("[项目1]");
        Text item2 = new Text("[项目2]");
        Text item3 = new Text("[项目3]");

        root4.addChild(item1);
        root4.addChild(item2);
        root4.addChild(item3);

        pipeline.setRoot(root4);
        pipeline.render();
        System.out.println("\n↑ 演示 4: SPACE_BETWEEN - 三个项目均匀分布");
        Thread.sleep(2000);

        // ========== 演示 5: 嵌套布局 ==========
        Box root5 = new Box();
        root5.setShowBorder(true);
        root5.setTitle("嵌套布局");
        root5.setWidth(60);
        root5.setHeight(12);
        root5.setFlexDirection(FlexDirection.COLUMN);

        // 顶部栏：ROW
        Box topBar = new Box();
        topBar.setShowBorder(false);
        topBar.setFlexDirection(FlexDirection.ROW);
        Text logo = new Text(" JWCode ");
        logo.setBold(true);
        Text spacer = new Text("");
        spacer.setFlexGrow(1);
        Text status = new Text(" ● 在线 ");
        topBar.addChild(logo);
        topBar.addChild(spacer);
        topBar.addChild(status);

        // 中间内容
        Text content = new Text("  欢迎使用 JWCode 终端渲染管线！\n  本项目实现了类 Ink 的 Flexbox 布局\n  和格子级 Diff 增量渲染。");
        content.setFlexGrow(1);

        // 底部状态
        Text bottom = new Text("  ROW: flexGrow | COLUMN: 自动 | Diff: 格子级  ");
        bottom.setAlignment(Text.TextAlignment.CENTER);

        root5.addChild(topBar);
        root5.addChild(content);
        root5.addChild(bottom);

        pipeline.setRoot(root5);
        pipeline.render();
        System.out.println("\n↑ 演示 5: 嵌套布局 - 带边框的复杂布局");
        Thread.sleep(3000);

        // ========== 演示 6: 增量渲染（Diff 效果） ==========
        System.out.println("\n演示 6: 增量渲染 - 只更新变化区域");
        Thread.sleep(1000);

        Box root6 = new Box();
        root6.setShowBorder(true);
        root6.setTitle("增量更新演示");
        root6.setWidth(50);
        root6.setHeight(8);
        root6.setFlexDirection(FlexDirection.COLUMN);

        Text counter = new Text("计数: 0");
        counter.setAlignment(Text.TextAlignment.CENTER);
        root6.addChild(counter);

        pipeline.setRoot(root6);

        // 逐步增加计数，每次只更新变化区域
        for (int i = 1; i <= 10; i++) {
            counter.setContent("计数: " + i);
            pipeline.render();
            Thread.sleep(300);
        }

        System.out.println("\n↑ 演示完成！InkPipeline 正常工作。");
        System.out.println("特性清单:");
        System.out.println("  ✅ Flexbox 布局 (ROW/COLUMN)");
        System.out.println("  ✅ justifyContent (6种对齐)");
        System.out.println("  ✅ alignItems (4种对齐)");
        System.out.println("  ✅ flexGrow 自动填充");
        System.out.println("  ✅ 嵌套布局");
        System.out.println("  ✅ 格子级 Diff 增量渲染");
        System.out.println("  ✅ 真彩色 ANSI 输出");

        terminal.showCursor();
        System.exit(0);
    }
}
