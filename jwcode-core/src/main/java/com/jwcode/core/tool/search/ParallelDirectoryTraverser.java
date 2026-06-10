package com.jwcode.core.tool.search;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * ForkJoinPool 并行目录遍历器 — 替代单线程 Files.walkFileTree。
 *
 * <p>大目录（>1K 条目）自动拆分为子任务并行遍历。
 * 预期性能（50K 文件）：300-800ms → 50-150ms。
 */
public class ParallelDirectoryTraverser {
    private static final Logger logger = Logger.getLogger(ParallelDirectoryTraverser.class.getName());

    private static final int SEQUENTIAL_THRESHOLD = 100;
    private static final ForkJoinPool POOL = new ForkJoinPool(
        Math.max(2, Runtime.getRuntime().availableProcessors()));

    /**
     * 遍历目录树，收集匹配的文件路径。
     *
     * @param start 起始目录
     * @param fileFilter 文件过滤器
     * @param maxResults 最大结果数
     * @param token 取消令牌
     * @return 匹配的文件路径列表
     */
    public static List<Path> traverse(Path start, Predicate<Path> fileFilter,
                                       int maxResults, SearchCancellationToken token)
            throws IOException {
        if (!Files.isDirectory(start)) {
            if (fileFilter.test(start)) return List.of(start);
            return List.of();
        }

        List<Path> results = Collections.synchronizedList(new ArrayList<>());
        TraverseTask task = new TraverseTask(start, fileFilter, results, maxResults, token);
        POOL.invoke(task);
        return results;
    }

    /**
     * 遍历目录树，收集所有匹配的文件路径。
     */
    public static List<Path> traverse(Path start, Predicate<Path> fileFilter,
                                       int maxResults) throws IOException {
        return traverse(start, fileFilter, maxResults, SearchCancellationToken.none());
    }

    private static class TraverseTask extends RecursiveTask<Void> {
        private final Path dir;
        private final Predicate<Path> fileFilter;
        private final List<Path> results;
        private final int maxResults;
        private final SearchCancellationToken token;

        TraverseTask(Path dir, Predicate<Path> fileFilter, List<Path> results,
                     int maxResults, SearchCancellationToken token) {
            this.dir = dir;
            this.fileFilter = fileFilter;
            this.results = results;
            this.maxResults = maxResults;
            this.token = token;
        }

        @Override
        protected Void compute() {
            if (results.size() >= maxResults || token.isCancelled()) return null;

            try {
                List<Path> subDirs = new ArrayList<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    for (Path entry : stream) {
                        if (results.size() >= maxResults) break;
                        token.throwIfCancelled();

                        if (Files.isDirectory(entry)) {
                            subDirs.add(entry);
                        } else if (fileFilter.test(entry)) {
                            results.add(entry);
                        }
                    }
                }

                if (subDirs.size() <= SEQUENTIAL_THRESHOLD) {
                    // 顺序处理子目录
                    for (Path subDir : subDirs) {
                        if (results.size() >= maxResults) break;
                        new TraverseTask(subDir, fileFilter, results, maxResults, token).compute();
                    }
                } else {
                    // 并行处理子目录
                    List<TraverseTask> tasks = new ArrayList<>();
                    for (Path subDir : subDirs) {
                        if (results.size() >= maxResults) break;
                        TraverseTask t = new TraverseTask(subDir, fileFilter, results, maxResults, token);
                        tasks.add(t);
                        t.fork();
                    }
                    for (TraverseTask t : tasks) {
                        t.join();
                    }
                }
            } catch (IOException e) {
                logger.fine("[ParallelTraverser] 遍历目录失败: " + dir + " — " + e.getMessage());
            } catch (SearchCancellationToken.SearchCancelledException e) {
                // 正常取消
            }

            return null;
        }
    }
}
