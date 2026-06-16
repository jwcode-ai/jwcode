package com.jwcode.core.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 原子文件写入工具 — 写入临时文件后原子重命名，防止文件损坏。
 */
public class AtomicFileWriter {

    /**
     * 原子写入字符串内容到文件。
     *
     * @param path    目标文件路径
     * @param content 要写入的内容
     */
    public static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 从文件原子读取全部内容。
     */
    public static String read(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("文件不存在: " + path);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /**
     * 安全读取 — 文件不存在时返回 null。
     */
    public static String readIfExists(Path path) {
        if (!Files.isRegularFile(path)) return null;
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private AtomicFileWriter() {}
}
