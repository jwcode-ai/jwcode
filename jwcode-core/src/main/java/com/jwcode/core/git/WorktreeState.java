package com.jwcode.core.git;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * WorktreeState - 进入 Worktree 前的状态保存
 * 
 * 功能说明：
 * 保存进入 Worktree 前的系统状态，包括原始工作目录、原始分支、
 * 会话数据等。用于退出 Worktree 时恢复原始状态。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class WorktreeState {
    
    /** 原始工作目录 */
    private final Path originalDir;
    
    /** 原始分支名称 */
    private final String originalBranch;
    
    /** 会话数据（用于保存临时状态） */
    private final Map<String, Object> sessionData;
    
    /** 进入的 Worktree 路径 */
    private final Path worktreePath;
    
    /** 进入时间戳 */
    private final long enterTimestamp;
    
    /** 原始 user.dir 系统属性 */
    private final String originalUserDir;
    
    /**
     * 构造函数
     * 
     * @param originalDir 原始工作目录
     * @param originalBranch 原始分支名称
     * @param worktreePath 进入的 Worktree 路径
     */
    public WorktreeState(Path originalDir, String originalBranch, Path worktreePath) {
        this.originalDir = originalDir;
        this.originalBranch = originalBranch;
        this.worktreePath = worktreePath;
        this.sessionData = new HashMap<>();
        this.enterTimestamp = System.currentTimeMillis();
        this.originalUserDir = System.getProperty("user.dir");
    }
    
    /**
     * 获取原始工作目录
     * 
     * @return 原始工作目录路径
     */
    public Path getOriginalDir() {
        return originalDir;
    }
    
    /**
     * 获取原始分支名称
     * 
     * @return 原始分支名称
     */
    public String getOriginalBranch() {
        return originalBranch;
    }
    
    /**
     * 获取 Worktree 路径
     * 
     * @return Worktree 路径
     */
    public Path getWorktreePath() {
        return worktreePath;
    }
    
    /**
     * 获取进入时间戳
     * 
     * @return 进入时间戳（毫秒）
     */
    public long getEnterTimestamp() {
        return enterTimestamp;
    }
    
    /**
     * 获取原始 user.dir
     * 
     * @return 原始的 user.dir 系统属性值
     */
    public String getOriginalUserDir() {
        return originalUserDir;
    }
    
    /**
     * 获取会话数据映射
     * 
     * @return 会话数据映射
     */
    public Map<String, Object> getSessionData() {
        return new HashMap<>(sessionData);
    }
    
    /**
     * 存储会话数据
     * 
     * @param key 数据键
     * @param value 数据值
     */
    public void putSessionData(String key, Object value) {
        this.sessionData.put(key, value);
    }
    
    /**
     * 获取会话数据
     * 
     * @param key 数据键
     * @return 数据值，如果不存在返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getSessionData(String key) {
        return (T) this.sessionData.get(key);
    }
    
    /**
     * 获取在 Worktree 中停留的时间
     * 
     * @return 停留时间（毫秒）
     */
    public long getDuration() {
        return System.currentTimeMillis() - enterTimestamp;
    }
    
    /**
     * 检查是否需要恢复分支
     * 
     * @return true 如果原始分支与当前不同
     */
    public boolean needsBranchRestore(String currentBranch) {
        return originalBranch != null && !originalBranch.equals(currentBranch);
    }
    
    @Override
    public String toString() {
        return "WorktreeState{" +
               "originalDir=" + originalDir +
               ", originalBranch='" + originalBranch + '\'' +
               ", worktreePath=" + worktreePath +
               ", enterTimestamp=" + enterTimestamp +
               ", duration=" + getDuration() + "ms" +
               '}';
    }
}
