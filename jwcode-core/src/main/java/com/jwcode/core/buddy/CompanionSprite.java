package com.jwcode.core.buddy;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * CompanionSprite - 伙伴精灵渲染
 * 
 * 功能说明：
 * 负责 Companion 伙伴的视觉渲染，支持多种渲染模式：
 * - 表情符号模式
 * - ASCII 艺术模式
 * - 动画序列模式
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class CompanionSprite {
    
    /**
     * 渲染模式枚举
     */
    public enum RenderMode {
        /** 表情符号 */
        EMOJI,
        /** ASCII 艺术 */
        ASCII,
        /** 动画 */
        ANIMATION,
        /** 文本 */
        TEXT
    }
    
    /**
     * 伙伴状态
     */
    private final CompanionState state;
    
    /**
     * 伙伴配置
     */
    private final CompanionConfig config;
    
    /**
     * 精灵注册表
     */
    private final SpriteRegistry spriteRegistry;
    
    /**
     * 当前渲染模式
     */
    private RenderMode renderMode;
    
    /**
     * 当前帧索引（用于动画）
     */
    private int currentFrameIndex;
    
    /**
     * 动画调度器
     */
    private final ScheduledExecutorService animator;
    
    /**
     * 动画运行标志
     */
    private volatile boolean animating;
    
    /**
     * 渲染监听器
     */
    private final List<Consumer<RenderEvent>> renderListeners;
    
    /**
     * 构造函数
     */
    public CompanionSprite(CompanionState state, CompanionConfig config, SpriteRegistry spriteRegistry) {
        this.state = state;
        this.config = config;
        this.spriteRegistry = spriteRegistry;
        this.renderMode = RenderMode.EMOJI;
        this.currentFrameIndex = 0;
        this.animator = Executors.newScheduledThreadPool(1);
        this.animating = false;
        this.renderListeners = new CopyOnWriteArrayList<>();
    }
    
    /**
     * 构造函数（简化版）
     */
    public CompanionSprite() {
        this(new CompanionState(), new CompanionConfig(), new SpriteRegistry());
    }
    
    /**
     * 添加渲染监听器
     */
    public void addRenderListener(Consumer<RenderEvent> listener) {
        this.renderListeners.add(listener);
    }
    
    /**
     * 移除渲染监听器
     */
    public void removeRenderListener(Consumer<RenderEvent> listener) {
        this.renderListeners.remove(listener);
    }
    
    /**
     * 获取当前渲染模式
     */
    public RenderMode getRenderMode() {
        return renderMode;
    }
    
    /**
     * 设置渲染模式
     */
    public void setRenderMode(RenderMode renderMode) {
        this.renderMode = renderMode;
        emitRenderEvent(new RenderEvent(RenderEventType.MODE_CHANGED, 
                Map.of("mode", renderMode)));
    }
    
    /**
     * 渲染当前帧
     */
    public String render() {
        if (!config.isEnabled()) {
            return "";
        }
        
        String result;
        switch (renderMode) {
            case EMOJI:
                result = renderEmoji();
                break;
            case ASCII:
                result = renderAscii();
                break;
            case ANIMATION:
                result = renderAnimation();
                break;
            case TEXT:
                result = renderText();
                break;
            default:
                result = renderEmoji();
        }
        
        emitRenderEvent(new RenderEvent(RenderEventType.RENDERED, 
                Map.of("content", result, "mode", renderMode)));
        
        return result;
    }
    
    /**
     * 渲染表情符号
     */
    private String renderEmoji() {
        // 获取当前状态对应的表情符号
        String emoji = spriteRegistry.getEmoji(state.getMoodState());
        if (emoji != null && !emoji.isEmpty() && config.isShowEmojis()) {
            return emoji;
        }
        
        // 根据状态返回默认表情
        switch (state.getActivityState()) {
            case IDLE:
                return "";
            case THINKING:
                return "";
            case WORKING:
                return "";
            case CELEBRATING:
                return "";
            case SLEEPING:
                return "";
            case CONFUSED:
                return "";
            default:
                return "";
        }
    }
    
    /**
     * 渲染 ASCII 艺术
     */
    private String renderAscii() {
        List<String> lines = spriteRegistry.getAsciiArt(state.getActivityState());
        return String.join("\n", lines);
    }
    
    /**
     * 渲染动画
     */
    private String renderAnimation() {
        SpriteRegistry.SpriteData sprite = spriteRegistry.getSprite(
                config.getTheme(), state.getActivityState());
        
        if (sprite == null || sprite.getFrames().isEmpty()) {
            return renderEmoji();
        }
        
        String frame = sprite.getCurrentFrame(currentFrameIndex);
        
        // 如果是动画类型，启动动画循环
        if (sprite.getType() == SpriteRegistry.SpriteType.ANIMATION && 
            sprite.getFrames().size() > 1) {
            startAnimation(sprite);
        }
        
        return frame;
    }
    
    /**
     * 渲染文本
     */
    private String renderText() {
        StringBuilder sb = new StringBuilder();
        
        // 显示状态文本
        sb.append("[").append(state.getActivityState()).append("]");
        
        // 显示情绪
        sb.append(" ").append(state.getMoodState());
        
        return sb.toString();
    }
    
    /**
     * 启动动画
     */
    private synchronized void startAnimation(SpriteRegistry.SpriteData sprite) {
        if (animating) {
            return;
        }
        
        animating = true;
        int frameDuration = sprite.getFrameDuration();
        
        animator.scheduleAtFixedRate(() -> {
            if (!animating) {
                return;
            }
            
            currentFrameIndex = (currentFrameIndex + 1) % sprite.getFrames().size();
            
            emitRenderEvent(new RenderEvent(RenderEventType.FRAME_CHANGED,
                    Map.of("frame", currentFrameIndex, "content", 
                           sprite.getCurrentFrame(currentFrameIndex))));
        }, frameDuration, frameDuration, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 停止动画
     */
    public synchronized void stopAnimation() {
        animating = false;
    }
    
    /**
     * 更新渲染
     */
    public void update() {
        // 当状态变化时调用
        currentFrameIndex = 0;
    }
    
    /**
     * 发射渲染事件
     */
    private void emitRenderEvent(RenderEvent event) {
        for (Consumer<RenderEvent> listener : renderListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                // 忽略监听器异常
            }
        }
    }
    
    /**
     * 关闭渲染器
     */
    public void shutdown() {
        stopAnimation();
        animator.shutdown();
        try {
            if (!animator.awaitTermination(5, TimeUnit.SECONDS)) {
                animator.shutdownNow();
            }
        } catch (InterruptedException e) {
            animator.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 获取渲染尺寸
     */
    public int getRenderSize() {
        return config.getSize().getPixels();
    }
    
    /**
     * 获取渲染宽度
     */
    public int getRenderWidth() {
        return config.getSize().getPixels();
    }
    
    /**
     * 获取渲染高度
     */
    public int getRenderHeight() {
        return config.getSize().getPixels();
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 渲染事件类型枚举
     */
    public enum RenderEventType {
        MODE_CHANGED,
        RENDERED,
        FRAME_CHANGED,
        SIZE_CHANGED
    }
    
    /**
     * 渲染事件类
     */
    public static class RenderEvent {
        private final RenderEventType type;
        private final Map<String, Object> data;
        private final java.time.Instant timestamp;
        
        public RenderEvent(RenderEventType type, Map<String, Object> data) {
            this.type = type;
            this.data = data != null ? new HashMap<>(data) : new HashMap<>();
            this.timestamp = java.time.Instant.now();
        }
        
        public RenderEventType getType() {
            return type;
        }
        
        public Map<String, Object> getData() {
            return new HashMap<>(data);
        }
        
        public java.time.Instant getTimestamp() {
            return timestamp;
        }
    }
}