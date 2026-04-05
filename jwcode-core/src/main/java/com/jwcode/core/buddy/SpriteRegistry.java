package com.jwcode.core.buddy;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SpriteRegistry - 精灵注册表
 * 
 * 功能说明：
 * 管理 Companion 伙伴的精灵图像资源。
 * 支持多种主题和状态的精灵注册和查找。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class SpriteRegistry {
    
    /**
     * 精灵类型枚举
     */
    public enum SpriteType {
        /** 静态图像 */
        STATIC,
        /** 动画序列 */
        ANIMATION,
        /** 表情符号 */
        EMOJI,
        /** ASCII 艺术 */
        ASCII
    }
    
    /**
     * 精灵数据类
     */
    public static class SpriteData {
        private final String id;
        private final SpriteType type;
        private final CompanionState.ActivityState state;
        private final CompanionState.MoodState mood;
        private final List<String> frames;
        private final int frameDuration;
        
        public SpriteData(String id, SpriteType type, CompanionState.ActivityState state,
                         CompanionState.MoodState mood, List<String> frames, int frameDuration) {
            this.id = id;
            this.type = type;
            this.state = state;
            this.mood = mood;
            this.frames = frames != null ? frames : new ArrayList<>();
            this.frameDuration = frameDuration;
        }
        
        public String getId() {
            return id;
        }
        
        public SpriteType getType() {
            return type;
        }
        
        public CompanionState.ActivityState getState() {
            return state;
        }
        
        public CompanionState.MoodState getMood() {
            return mood;
        }
        
        public List<String> getFrames() {
            return frames;
        }
        
        public int getFrameDuration() {
            return frameDuration;
        }
        
        public String getCurrentFrame(int frameIndex) {
            if (frames.isEmpty()) {
                return "";
            }
            return frames.get(frameIndex % frames.size());
        }
    }
    
    /**
     * 精灵映射表（主题 -> 状态 -> 精灵数据）
     */
    private final Map<CompanionConfig.CompanionTheme, Map<CompanionState.ActivityState, SpriteData>> sprites;
    
    /**
     * 默认表情符号映射
     */
    private final Map<CompanionState.MoodState, String> emojiMap;
    
    /**
     * 默认 ASCII 艺术映射
     */
    private final Map<CompanionState.ActivityState, List<String>> asciiMap;
    
    /**
     * 构造函数
     */
    public SpriteRegistry() {
        this.sprites = new ConcurrentHashMap<>();
        this.emojiMap = new ConcurrentHashMap<>();
        this.asciiMap = new ConcurrentHashMap<>();
        
        // 注册默认表情符号
        registerDefaultEmojis();
        
        // 注册默认 ASCII 艺术
        registerDefaultAsciiArt();
    }
    
    /**
     * 注册精灵
     */
    public void registerSprite(CompanionConfig.CompanionTheme theme, SpriteData sprite) {
        sprites.computeIfAbsent(theme, k -> new ConcurrentHashMap<>())
                .put(sprite.getState(), sprite);
    }
    
    /**
     * 获取精灵
     */
    public SpriteData getSprite(CompanionConfig.CompanionTheme theme, CompanionState.ActivityState state) {
        Map<CompanionState.ActivityState, SpriteData> themeSprites = sprites.get(theme);
        if (themeSprites == null) {
            themeSprites = sprites.get(CompanionConfig.CompanionTheme.DEFAULT);
        }
        return themeSprites != null ? themeSprites.get(state) : null;
    }
    
    /**
     * 获取表情符号
     */
    public String getEmoji(CompanionState.MoodState mood) {
        return emojiMap.getOrDefault(mood, "");
    }
    
    /**
     * 获取 ASCII 艺术
     */
    public List<String> getAsciiArt(CompanionState.ActivityState state) {
        return asciiMap.getOrDefault(state, asciiMap.get(CompanionState.ActivityState.IDLE));
    }
    
    /**
     * 获取所有主题
     */
    public Set<CompanionConfig.CompanionTheme> getAllThemes() {
        return sprites.keySet();
    }
    
    /**
     * 移除主题
     */
    public void removeTheme(CompanionConfig.CompanionTheme theme) {
        sprites.remove(theme);
    }
    
    /**
     * 清除所有精灵
     */
    public void clear() {
        sprites.clear();
    }
    
    /**
     * 注册默认表情符号
     */
    private void registerDefaultEmojis() {
        emojiMap.put(CompanionState.MoodState.NEUTRAL, "");
        emojiMap.put(CompanionState.MoodState.HAPPY, "");
        emojiMap.put(CompanionState.MoodState.EXCITED, "");
        emojiMap.put(CompanionState.MoodState.FOCUSED, "🤔");
        emojiMap.put(CompanionState.MoodState.CONFUSED, "");
        emojiMap.put(CompanionState.MoodState.DISAPPOINTED, "");
        emojiMap.put(CompanionState.MoodState.TIRED, "");
        emojiMap.put(CompanionState.MoodState.SURPRISED, "");
    }
    
    /**
     * 注册默认 ASCII 艺术
     */
    private void registerDefaultAsciiArt() {
        // 空闲状态
        asciiMap.put(CompanionState.ActivityState.IDLE, Arrays.asList(
                "  ┌───────┐",
                "  │ ◕ ◕ │",
                "  │  ▽  │",
                "  └───────┘"
        ));
        
        // 思考状态
        asciiMap.put(CompanionState.ActivityState.THINKING, Arrays.asList(
                "  ┌───────┐",
                "  │ ◔ ◔ │",
                "  │  △  │",
                "  └───────┘",
                "    ╱|",
                "   | /"
        ));
        
        // 工作状态
        asciiMap.put(CompanionState.ActivityState.WORKING, Arrays.asList(
                "  ┌───────┐",
                "  │ ⊙ ⊙ │",
                "  │  ▽  │",
                "  └───────┘",
                "   ╱|＼",
                "  | / \\"
        ));
        
        // 庆祝状态
        asciiMap.put(CompanionState.ActivityState.CELEBRATING, Arrays.asList(
                "  ┌───────┐",
                "  │ ^ ^ │",
                "  │  △  │",
                "  └───────┘",
                "   \\|/",
                "    |"
        ));
        
        // 休眠状态
        asciiMap.put(CompanionState.ActivityState.SLEEPING, Arrays.asList(
                "  ┌───────┐",
                "  │ - - │",
                "  │  ▁  │",
                "  └───────┘",
                "   Zzz..."
        ));
        
        // 困惑状态
        asciiMap.put(CompanionState.ActivityState.CONFUSED, Arrays.asList(
                "  ┌───────┐",
                "  │ ? ? │",
                "  │  ○  │",
                "  └───────┘",
                "    ╱?"
        ));
    }
    
    /**
     * 注册内置主题精灵
     */
    public void registerBuiltInThemes() {
        // 默认主题
        registerDefaultTheme();
        
        // 可爱主题
        registerCuteTheme();
        
        // 极简主题
        registerMinimalTheme();
    }
    
    /**
     * 注册默认主题
     */
    private void registerDefaultTheme() {
        CompanionConfig.CompanionTheme theme = CompanionConfig.CompanionTheme.DEFAULT;
        
        registerSprite(theme, new SpriteData("default-idle", SpriteType.STATIC,
                CompanionState.ActivityState.IDLE, CompanionState.MoodState.NEUTRAL,
                Arrays.asList("🤖"), 0));
        
        registerSprite(theme, new SpriteData("default-thinking", SpriteType.STATIC,
                CompanionState.ActivityState.THINKING, CompanionState.MoodState.FOCUSED,
                Arrays.asList("🤔"), 0));
        
        registerSprite(theme, new SpriteData("default-working", SpriteType.STATIC,
                CompanionState.ActivityState.WORKING, CompanionState.MoodState.FOCUSED,
                Arrays.asList("💻"), 0));
        
        registerSprite(theme, new SpriteData("default-celebrating", SpriteType.ANIMATION,
                CompanionState.ActivityState.CELEBRATING, CompanionState.MoodState.HAPPY,
                Arrays.asList("🎉", "✨", "🌟"), 500));
        
        registerSprite(theme, new SpriteData("default-sleeping", SpriteType.STATIC,
                CompanionState.ActivityState.SLEEPING, CompanionState.MoodState.NEUTRAL,
                Arrays.asList("💤"), 0));
    }
    
    /**
     * 注册可爱主题
     */
    private void registerCuteTheme() {
        CompanionConfig.CompanionTheme theme = CompanionConfig.CompanionTheme.CUTE;
        
        registerSprite(theme, new SpriteData("cute-idle", SpriteType.STATIC,
                CompanionState.ActivityState.IDLE, CompanionState.MoodState.NEUTRAL,
                Arrays.asList("🐱"), 0));
        
        registerSprite(theme, new SpriteData("cute-thinking", SpriteType.STATIC,
                CompanionState.ActivityState.THINKING, CompanionState.MoodState.FOCUSED,
                Arrays.asList("🐱🤔"), 0));
        
        registerSprite(theme, new SpriteData("cute-working", SpriteType.STATIC,
                CompanionState.ActivityState.WORKING, CompanionState.MoodState.FOCUSED,
                Arrays.asList("🐱💻"), 0));
        
        registerSprite(theme, new SpriteData("cute-celebrating", SpriteType.ANIMATION,
                CompanionState.ActivityState.CELEBRATING, CompanionState.MoodState.HAPPY,
                Arrays.asList("🐱🎉", "🐱✨", "🐱🌟"), 500));
        
        registerSprite(theme, new SpriteData("cute-sleeping", SpriteType.STATIC,
                CompanionState.ActivityState.SLEEPING, CompanionState.MoodState.NEUTRAL,
                Arrays.asList("🐱💤"), 0));
    }
    
    /**
     * 注册极简主题
     */
    private void registerMinimalTheme() {
        CompanionConfig.CompanionTheme theme = CompanionConfig.CompanionTheme.MINIMAL;
        
        registerSprite(theme, new SpriteData("minimal-idle", SpriteType.STATIC,
                CompanionState.ActivityState.IDLE, CompanionState.MoodState.NEUTRAL,
                Arrays.asList("●"), 0));
        
        registerSprite(theme, new SpriteData("minimal-thinking", SpriteType.STATIC,
                CompanionState.ActivityState.THINKING, CompanionState.MoodState.FOCUSED,
                Arrays.asList("◐"), 0));
        
        registerSprite(theme, new SpriteData("minimal-working", SpriteType.STATIC,
                CompanionState.ActivityState.WORKING, CompanionState.MoodState.FOCUSED,
                Arrays.asList("◈"), 0));
        
        registerSprite(theme, new SpriteData("minimal-celebrating", SpriteType.ANIMATION,
                CompanionState.ActivityState.CELEBRATING, CompanionState.MoodState.HAPPY,
                Arrays.asList("★", "☆", "✦"), 500));
        
        registerSprite(theme, new SpriteData("minimal-sleeping", SpriteType.STATIC,
                CompanionState.ActivityState.SLEEPING, CompanionState.MoodState.NEUTRAL,
                Arrays.asList("○"), 0));
    }
}