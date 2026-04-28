package com.jwcode.core.code.analysis;

import com.jwcode.core.code.api.TextEdit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Myers O(ND) 差分算法实现。
 *
 * <p>计算两个文本之间的最小编辑脚本，并转换为 {@link TextEdit} 列表。
 * 适用于源码级别的增量分析场景。</p>
 */
public class MyersDiff {

    /**
     * 计算从 oldText 变为 newText 所需的 TextEdit 列表。
     *
     * @param oldText 原始文本
     * @param newText 新文本
     * @return 按起始偏移量升序排列、互不重叠的 TextEdit 列表
     */
    public static List<TextEdit> computeEdits(String oldText, String newText) {
        if (oldText == null) oldText = "";
        if (newText == null) newText = "";
        if (oldText.equals(newText)) {
            return List.of();
        }

        char[] a = oldText.toCharArray();
        char[] b = newText.toCharArray();

        List<Op> ops = diff(a, b);
        return opsToTextEdits(ops);
    }

    private enum OpType { DELETE, INSERT, EQUAL }

    private record Op(OpType type, String text) {}

    private static List<Op> diff(char[] a, char[] b) {
        int n = a.length;
        int m = b.length;

        if (n == 0 && m == 0) {
            return List.of();
        }
        if (n == 0) {
            return List.of(new Op(OpType.INSERT, new String(b)));
        }
        if (m == 0) {
            return List.of(new Op(OpType.DELETE, new String(a)));
        }

        int max = n + m;
        int[] v = new int[2 * max + 1];
        List<Map<Integer, Integer>> trace = new ArrayList<>();

        for (int d = 0; d <= max; d++) {
            Map<Integer, Integer> current = new HashMap<>();
            for (int k = -d; k <= d; k += 2) {
                boolean down = (k == -d || (k != d && v[k - 1 + max] < v[k + 1 + max]));
                int x = down ? v[k + 1 + max] : v[k - 1 + max] + 1;
                int y = x - k;

                while (x < n && y < m && a[x] == b[y]) {
                    x++;
                    y++;
                }

                v[k + max] = x;
                current.put(k, x);

                if (x >= n && y >= m) {
                    trace.add(current);
                    return backtrack(trace, a, b, d, k);
                }
            }
            trace.add(current);
        }

        // Fallback (should not reach here for valid inputs)
        return List.of(new Op(OpType.DELETE, new String(a)), new Op(OpType.INSERT, new String(b)));
    }

    private static List<Op> backtrack(List<Map<Integer, Integer>> trace, char[] a, char[] b,
                                       int dEnd, int kEnd) {
        List<Op> ops = new ArrayList<>();
        int x = a.length;
        int y = b.length;

        for (int d = dEnd; d > 0; d--) {
            Map<Integer, Integer> v = trace.get(d);
            int k = kEnd;

            boolean down = (k == -d || (k != d && v.getOrDefault(k - 1, 0) < v.getOrDefault(k + 1, 0)));
            int prevK = down ? k + 1 : k - 1;
            int prevX = trace.get(d - 1).getOrDefault(prevK, 0);
            int prevY = prevX - prevK;

            while (x > prevX && y > prevY) {
                ops.add(new Op(OpType.EQUAL, String.valueOf(a[x - 1])));
                x--;
                y--;
            }

            if (down) {
                ops.add(new Op(OpType.INSERT, String.valueOf(b[y - 1])));
                y--;
            } else {
                ops.add(new Op(OpType.DELETE, String.valueOf(a[x - 1])));
                x--;
            }

            kEnd = prevK;
        }

        while (x > 0 && y > 0) {
            ops.add(new Op(OpType.EQUAL, String.valueOf(a[x - 1])));
            x--;
            y--;
        }

        Collections.reverse(ops);
        return ops;
    }

    private static List<TextEdit> opsToTextEdits(List<Op> ops) {
        List<TextEdit> edits = new ArrayList<>();
        int oldOffset = 0;
        int i = 0;

        while (i < ops.size()) {
            Op op = ops.get(i);

            if (op.type == OpType.EQUAL) {
                oldOffset += op.text.length();
                i++;
                continue;
            }

            int blockStart = oldOffset;
            StringBuilder deleteText = new StringBuilder();
            StringBuilder insertText = new StringBuilder();

            while (i < ops.size() && ops.get(i).type != OpType.EQUAL) {
                Op changeOp = ops.get(i);
                if (changeOp.type == OpType.DELETE) {
                    deleteText.append(changeOp.text);
                    oldOffset += changeOp.text.length();
                } else if (changeOp.type == OpType.INSERT) {
                    insertText.append(changeOp.text);
                }
                i++;
            }

            int blockEnd = blockStart + deleteText.length();
            edits.add(new TextEdit(blockStart, blockEnd, insertText.toString()));
        }

        return edits;
    }
}
