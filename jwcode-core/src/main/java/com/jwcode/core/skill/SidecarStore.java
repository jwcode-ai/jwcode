package com.jwcode.core.skill;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * 侧边文件存储基类 — 提供原子读写的模板方法模式。
 *
 * <p>子类只需实现 {@link #doSerialize()} 和 {@link #doDeserialize(String)}，
 * 序列化/反序列化的数据通过原子写入持久化。
 */
public abstract class SidecarStore {

    private static final Logger logger = Logger.getLogger(SidecarStore.class.getName());

    protected final Path storePath;

    protected SidecarStore(Path storePath) {
        this.storePath = storePath;
    }

    /**
     * 将数据持久化到磁盘（原子写入）。
     */
    protected synchronized void save() {
        try {
            String content = doSerialize();
            AtomicFileWriter.write(storePath, content);
        } catch (IOException e) {
            logger.warning("[" + getClass().getSimpleName() + "] 持久化失败: " + e.getMessage());
        }
    }

    /**
     * 从磁盘加载数据。
     */
    protected synchronized void load() {
        try {
            String content = AtomicFileWriter.readIfExists(storePath);
            if (content != null) {
                doDeserialize(content);
            }
        } catch (Exception e) {
            logger.warning("[" + getClass().getSimpleName() + "] 加载失败: " + e.getMessage());
        }
    }

    /**
     * 序列化为字符串。
     */
    protected abstract String doSerialize();

    /**
     * 从字符串反序列化。
     */
    protected abstract void doDeserialize(String content);
}
