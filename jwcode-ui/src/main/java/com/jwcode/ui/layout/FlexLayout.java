package com.jwcode.ui.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * FlexLayout - 轻量级 Flexbox 布局引擎
 *
 * <p>纯 Java 实现的最小 Flexbox 布局算法，支持以下属性：</p>
 * <ul>
 *   <li>flexDirection: ROW / COLUMN</li>
 *   <li>justifyContent: FLEX_START / CENTER / FLEX_END / SPACE_BETWEEN / SPACE_AROUND / SPACE_EVENLY</li>
 *   <li>alignItems: FLEX_START / CENTER / FLEX_END / STRETCH</li>
 *   <li>flexGrow / flexShrink</li>
 *   <li>padding / margin（四边独立）</li>
 *   <li>width / height（固定值或 AUTO）</li>
 *   <li>minWidth / maxWidth / minHeight / maxHeight</li>
 *   <li>alignSelf（单项覆盖 alignItems）</li>
 * </ul>
 *
 * <p>算法参考 Yoga（Facebook Flexbox 实现）的核心步骤，但做了大幅简化：
 * 不支持 flexWrap、alignContent、position/absolute 等高级属性。</p>
 */
public class FlexLayout {

    private FlexLayout() {
        // 工具类，禁止实例化
    }

    /**
     * 对一组 FlexItem 执行 Flexbox 布局计算。
     *
     * @param items          参与布局的子项
     * @param containerWidth  容器宽度
     * @param containerHeight 容器高度
     * @param direction       主轴方向
     * @param justifyContent  主轴对齐方式
     * @param alignItems      交叉轴对齐方式
     */
    public static void layout(
            List<FlexItem> items,
            int containerWidth,
            int containerHeight,
            FlexDirection direction,
            Align justifyContent,
            Align alignItems) {

        if (items == null || items.isEmpty()) return;

        boolean isRow = direction == FlexDirection.ROW;

        // 第一步：计算每个 item 的主轴尺寸
        for (FlexItem item : items) {
            int mainSize;
            int crossSize;

            if (isRow) {
                // 水平方向：主轴 = 宽度，交叉轴 = 高度
                mainSize = resolveMainSize(item, containerWidth, isRow);
                crossSize = resolveCrossSize(item, containerHeight, isRow);
            } else {
                // 垂直方向：主轴 = 高度，交叉轴 = 宽度
                mainSize = resolveMainSize(item, containerHeight, isRow);
                crossSize = resolveCrossSize(item, containerWidth, isRow);
            }

            // 应用 min/max 约束
            if (isRow) {
                item.setComputedWidth(clamp(mainSize, item.getMinWidth(), item.getMaxWidth()));
                item.setComputedHeight(clamp(crossSize, item.getMinHeight(), item.getMaxHeight()));
            } else {
                item.setComputedHeight(clamp(mainSize, item.getMinHeight(), item.getMaxHeight()));
                item.setComputedWidth(clamp(crossSize, item.getMinWidth(), item.getMaxWidth()));
            }
        }

        // 第二步：计算固定尺寸消耗 + 分配剩余空间（flexGrow）
        int totalFixedMain = 0;
        float totalFlexGrow = 0f;
        int containerMain = isRow ? containerWidth : containerHeight;

        for (FlexItem item : items) {
            int marginMain = item.getMarginMain(isRow);
            int itemMain = isRow ? item.getComputedWidth() : item.getComputedHeight();
            totalFixedMain += itemMain + marginMain;
            totalFlexGrow += item.getFlexGrow();
        }

        int remainingMain = containerMain - totalFixedMain;

        // 如果有剩余空间且有 flexGrow 项，按比例分配
        if (remainingMain > 0 && totalFlexGrow > 0) {
            for (FlexItem item : items) {
                if (item.getFlexGrow() > 0) {
                    int extra = (int) (remainingMain * item.getFlexGrow() / totalFlexGrow);
                    if (isRow) {
                        item.setComputedWidth(item.getComputedWidth() + extra);
                    } else {
                        item.setComputedHeight(item.getComputedHeight() + extra);
                    }
                }
            }
        }

        // 第三步：计算交叉轴尺寸（STRETCH 模式下拉伸）
        int containerCross = isRow ? containerHeight : containerWidth;
        for (FlexItem item : items) {
            Align align = item.getAlignSelf() != null ? item.getAlignSelf() : alignItems;
            if (align == Align.STRETCH) {
                int marginCross = item.getMarginCross(isRow);
                int stretched = Math.max(0, containerCross - marginCross);
                if (isRow) {
                    item.setComputedHeight(clamp(stretched, item.getMinHeight(), item.getMaxHeight()));
                } else {
                    item.setComputedWidth(clamp(stretched, item.getMinWidth(), item.getMaxWidth()));
                }
            }
        }

        // 第四步：主轴定位（justifyContent）
        positionMainAxis(items, containerMain, justifyContent, isRow);

        // 第五步：交叉轴定位（alignItems / alignSelf）
        positionCrossAxis(items, containerCross, alignItems, isRow);
    }

    // ==================== 主轴尺寸解析 ====================

    /**
     * 解析主轴尺寸：优先使用固定值，其次 flexBasis，最后内容自适应。
     */
    private static int resolveMainSize(FlexItem item, int containerMain, boolean isRow) {
        int fixed = isRow ? item.getWidth() : item.getHeight();
        if (fixed >= 0) return fixed;
        if (item.getFlexBasis() >= 0) return item.getFlexBasis();
        return 0; // 内容自适应时初始为 0，由 flexGrow 分配
    }

    /**
     * 解析交叉轴尺寸。
     */
    private static int resolveCrossSize(FlexItem item, int containerCross, boolean isRow) {
        int fixed = isRow ? item.getHeight() : item.getWidth();
        if (fixed >= 0) return fixed;
        return 0; // 初始为 0，STRETCH 会拉伸
    }

    // ==================== 主轴定位 ====================

    private static void positionMainAxis(
            List<FlexItem> items, int containerMain, Align justifyContent, boolean isRow) {

        // 计算所有 item 占用的主轴空间（含 margin）
        int totalUsed = 0;
        for (FlexItem item : items) {
            totalUsed += (isRow ? item.getComputedWidth() : item.getComputedHeight())
                    + item.getMarginMain(isRow);
        }

        int remaining = containerMain - totalUsed;
        int gap = 0;      // item 之间的间距
        int startOffset = 0; // 起始偏移

        switch (justifyContent) {
            case FLEX_START:
            default:
                startOffset = 0;
                gap = 0;
                break;
            case CENTER:
                startOffset = remaining / 2;
                gap = 0;
                break;
            case FLEX_END:
                startOffset = remaining;
                gap = 0;
                break;
            case SPACE_BETWEEN:
                startOffset = 0;
                gap = items.size() > 1 ? remaining / (items.size() - 1) : 0;
                break;
            case SPACE_AROUND:
                gap = items.size() > 0 ? remaining / items.size() : 0;
                startOffset = gap / 2;
                break;
            case SPACE_EVENLY:
                gap = items.size() > 0 ? remaining / (items.size() + 1) : 0;
                startOffset = gap;
                break;
        }

        int cursor = startOffset;
        for (FlexItem item : items) {
            int marginStart = isRow ? item.getMarginLeft() : item.getMarginTop();
            cursor += marginStart;

            if (isRow) {
                item.setX(cursor);
            } else {
                item.setY(cursor);
            }

            int itemMain = isRow ? item.getComputedWidth() : item.getComputedHeight();
            cursor += itemMain;

            int marginEnd = isRow ? item.getMarginRight() : item.getMarginBottom();
            cursor += marginEnd + gap;
        }
    }

    // ==================== 交叉轴定位 ====================

    private static void positionCrossAxis(
            List<FlexItem> items, int containerCross, Align alignItems, boolean isRow) {

        for (FlexItem item : items) {
            Align align = item.getAlignSelf() != null ? item.getAlignSelf() : alignItems;
            int itemCross = isRow ? item.getComputedHeight() : item.getComputedWidth();
            int marginStart = isRow ? item.getMarginTop() : item.getMarginLeft();
            int marginEnd = isRow ? item.getMarginBottom() : item.getMarginRight();
            int available = containerCross - itemCross - marginStart - marginEnd;

            int offset;
            switch (align) {
                case FLEX_START:
                default:
                    offset = marginStart;
                    break;
                case CENTER:
                    offset = marginStart + available / 2;
                    break;
                case FLEX_END:
                    offset = marginStart + Math.max(0, available);
                    break;
                case STRETCH:
                    offset = marginStart;
                    break;
            }

            if (isRow) {
                item.setY(offset);
            } else {
                item.setX(offset);
            }
        }
    }

    // ==================== 工具方法 ====================

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== 便捷重载 ====================

    /**
     * 使用默认对齐方式（FLEX_START）进行布局。
     */
    public static void layout(List<FlexItem> items, int containerWidth, int containerHeight, FlexDirection direction) {
        layout(items, containerWidth, containerHeight, direction, Align.FLEX_START, Align.FLEX_START);
    }

    /**
     * 使用默认方向（COLUMN）和对齐方式进行布局。
     */
    public static void layout(List<FlexItem> items, int containerWidth, int containerHeight) {
        layout(items, containerWidth, containerHeight, FlexDirection.COLUMN, Align.FLEX_START, Align.FLEX_START);
    }
}
