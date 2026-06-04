package com.jwcode.core.agent;

import java.time.Instant;
import java.util.logging.Logger;

/**
 * æŽ‹ç¼©å¤šçº§é˜ˆå€¼ä½“ç³» + é˜²å¾¡æœºåˆ¶ã€? *
 * <h3>å…­çº§æ°´ä½çº§åˆ«</h3>
 * <table>
 *   <tr><th>çº§åˆ«</th><th>é˜ˆå€?</th><th>ç­–ç•¥</th><th>è¯´æ˜Ž</th></tr>
 *   <tr><td>NORMAL</td><td>0%</td><td>æ— åŠ¨ä½œ</td><td>å®‰å…¨åŒºï¼Œæ— éœ€åŽ‹ç¼©</td></tr>
 *   <tr><td>ADVISORY</td><td>30%</td><td>ä»…è®°å½•</td><td>é¢„è­¦çº§åˆ«ï¼Œä¸è§¦å‘ä»»ä½•åŠ¨ä½œï¼Œä»…ç”Ÿæˆæ—¥å¿—</td></tr>
 *   <tr><td>GENTLE</td><td>50%</td><td>MINIMAL</td><td>æ¸©å’ŒåŽ‹ç¼©ï¼šä»…æ¸…ç†å™ªå£°æ¶ˆæ¯ï¼Œä¸è°ƒç”¨LLM</td></tr>
 *   <tr><td>MODERATE</td><td>60%</td><td>AICL_PRIORITY</td><td>ä¸­åº¦åŽ‹ç¼©ï¼šAICLä¼˜å…ˆçº§æ·˜æ±°ï¼Œä¿ç•™é«˜ä»·å€¼å—</td></tr>
 *   <tr><td>AGGRESSIVE</td><td>70%</td><td>SMART</td><td>æ¿€è¿›åŽ‹ç¼©ï¼šLLMè¯­ä¹‰æ‘˜è¦ + ä¿ç•™å°¾éƒ?8æ¡</td></tr>
 *   <tr><td>CRITICAL</td><td>85%</td><td>AGGRESSIVE</td><td>å±æœºåŽ‹ç¼©ï¼šä»…ä¿ç•™å°¾éƒ?4æ?+ è­¦å‘Šé‡ç½®</td></tr>
 *   <tr><td>EMERGENCY</td><td>92%</td><td>RESET</td><td>ç´§æ€¥æƒ…å†µï¼šå¼ºåˆ¶æˆªæ–­ï¼Œå»ºè®®è¿›ç¨‹çº§Context Reset</td></tr>
 * </table>
 *
 * <h3>é˜²å¾¡æœºåˆ¶</h3>
 * <ul>
 *   <li><b>æ»žåŽå›žå·® (Hysteresis)</b>: æ¯çº§æ°´ä½ç‹¬ç«‹è¿›å…¥/é€€å‡ºé˜ˆå€¼ï¼Œé˜²æ­¢é˜ˆå€¼è¾¹ç¼˜éœ‡è¡</li>
 *   <li><b>å†·å´é—´éš” (Cooldown)</b>: æœ€å°åŽ‹ç¼©é—´éš”æ—¶é—´ï¼Œé˜²æ­¢å¿«é€Ÿè¿žç»­åŽ‹ç¼©</li>
 *   <li><b>æ•ˆçŽ‡æ£€æŸ¥ (Efficiency Check)</b>: åŽ‹ç¼©èŠ‚çœçŽ‡ < 10% æ—¶æ ‡è®°ä¸ºæ— æ•ˆ</li>
 *   <li><b>è¿žç»­æ— æ•ˆæ£€æµ‹ (Staleness Detection)</b>: è¿žç»­3æ¬¡æ— æ•ˆåŽ‹ç¼©åŽå»ºè®®å‡çº§ä¸ºReset</li>
 *   <li><b>åŽ‹ç¼©é¢„ç®— (Compaction Budget)</b>: æ¯ä¸ªä¼šè¯æœ€å¤šåŽ‹ç¼©æ¬¡æ•°ï¼Œè¶…è¿‡åŽå¼ºåˆ¶Reset</li>
 *   <li><b>çº§è”ä¿æŠ¤ (Cascade Protection)</b>: å†·å´æœŸå†…å¤šæ¬¡è§¦å‘æ—¶æå‰å‡çº§</li>
 * </ul>
 */
public class CompactionThresholds {

    private static final Logger logger = Logger.getLogger(CompactionThresholds.class.getName());

    // ==================== å…­çº§æ°´ä½çº§åˆ« ====================

    public enum WatermarkLevel {
        /** æ­£å¸¸ï¼šå®‰å…¨åŒºï¼Œä¸éœ€åŽ‹ç¼© */
        NORMAL(0.0, 0.0, CompactorTrigger.Strategy.MINIMAL, "æ­£å¸¸â€”â€”å®‰å…¨åŒºï¼Œæ— éœ€åŽ‹ç¼©"),
        /** é¢„è­¦ï¼šä»…ç”Ÿæˆæ—¥å¿—ï¼Œä¸è§¦å‘åŽ‹ç¼© */
        ADVISORY(0.30, 0.20, null, "é¢„è­¦â€”â€”Tokenä½¿ç”¨è¾¾30%ï¼Œä»…ç”Ÿæˆæ—¥å¿—"),
        /** æ¸©å’Œï¼šæœ€å°åŽ‹ç¼©ï¼Œä»…æ¸…ç†å™ªå£° */
        GENTLE(0.50, 0.35, CompactorTrigger.Strategy.MINIMAL, "æ¸©å’Œâ€”â€”Tokenä½¿ç”¨è¾¾50%ï¼Œæ¸…ç†å™ªå£°æ¶ˆæ¯"),
        /** ä¸­åº¦ï¼šAICLä¼˜å…ˆçº§æ·˜æ±° */
        MODERATE(0.60, 0.48, CompactorTrigger.Strategy.AICL_PRIORITY, "ä¸­åº¦â€”â€”Tokenä½¿ç”¨è¾¾60%ï¼ŒAICLä¼˜å…ˆçº§æ·˜æ±°"),
        /** æ¿€è¿›ï¼šLLMè¯­ä¹‰æ‘˜è¦ */
        AGGRESSIVE(0.70, 0.58, CompactorTrigger.Strategy.SMART, "æ¿€è¿›â€”â€”Tokenä½¿ç”¨è¾¾70%ï¼ŒLLMè¯­ä¹‰æ‘˜è¦"),
        /** å±æœºï¼šå¼ºåŠ›åŽ‹ç¼© + è­¦å‘Š */
        CRITICAL(0.85, 0.68, CompactorTrigger.Strategy.AGGRESSIVE, "å±æœºâ€”â€”Tokenä½¿ç”¨è¾¾85%ï¼Œå¼ºåŠ›åŽ‹ç¼©+è­¦å‘Š"),
        /** ç´§æ€¥ï¼šå¼ºåˆ¶æˆªæ–­ + å»ºè®®Reset */
        EMERGENCY(0.92, 0.80, CompactorTrigger.Strategy.RESET, "ç´§æ€¥â€”â€”Tokenä½¿ç”¨è¾¾92%ï¼Œå¼ºåˆ¶æˆªæ–­+å»ºè®®Reset");

        /** è¿›å…¥é˜ˆå€¼ï¼ˆç”¨äºŽå‡çº§ï¼‰ */
        private final double enterThreshold;
        /** é€€å‡ºé˜ˆå€¼ï¼ˆç”¨äºŽé™çº§ï¼Œé€šå¸¸æ¯”è¿›å…¥é˜ˆå€¼ä½Ž5-10%ï¼‰ */
        private final double exitThreshold;
        /** å¯¹åº”çš„åŽ‹ç¼©ç­–ç•¥ */
        private final CompactorTrigger.Strategy strategy;
        /** å‹å¥½æè¿° */
        private final String description;

        WatermarkLevel(double enterThreshold, double exitThreshold,
                       CompactorTrigger.Strategy strategy, String description) {
            this.enterThreshold = enterThreshold;
            this.exitThreshold = exitThreshold;
            this.strategy = strategy;
            this.description = description;
        }

        public double getEnterThreshold() { return enterThreshold; }
        public double getExitThreshold() { return exitThreshold; }
        public CompactorTrigger.Strategy getStrategy() { return strategy; }
        public String getDescription() { return description; }

        /**
         * æ ¹æ®å½“å‰ä½¿ç”¨çŽ‡å’Œå½“å‰çº§åˆ«ï¼Œè®¡ç®—æ–°çº§åˆ«ã€?         * æ»žåŽå›žå·®ï¼šå‡çº§ç”¨è¿›å…¥é˜ˆå€¼ï¼Œé™çº§ç”¨é€€å‡ºé˜ˆå€¼ã€?         */
        public static WatermarkLevel evaluate(double usageRatio, WatermarkLevel currentLevel) {
            if (currentLevel == null) currentLevel = NORMAL;

            WatermarkLevel[] levels = values();

            // å‡çº§æ£€æŸ¥ï¼šä»Žå½“å‰çº§åˆ«å¾€ä¸Šæ‰¾ï¼ŒæŸ¥çœ‹æ˜¯å¦è¶…è¿‡æ›´é«˜çº§åˆ«çš„è¿›å…¥é˜ˆå€?
            WatermarkLevel highestReached = currentLevel;
            for (WatermarkLevel level : levels) {
                if (level == NORMAL) continue;
                if (level.enterThreshold > 0 && usageRatio >= level.enterThreshold) {
                    if (level.ordinal() > highestReached.ordinal()) {
                        highestReached = level;
                    }
                }
            }

            // é™çº§æ£€æŸ¥ï¼šåªæœ‰å½“ä½¿ç”¨çŽ‡ä½ŽäºŽå½“å‰çº§åˆ«çš„é€€å‡ºé˜ˆå€¼æ—¶æ‰é™çº§
            if (highestReached.ordinal() < currentLevel.ordinal()) {
                return highestReached;
            }
            if (highestReached == currentLevel && currentLevel.exitThreshold > 0
                    && usageRatio < currentLevel.exitThreshold) {
                // æ‰¾åˆ°æœ€é«˜æ»¡è¶³çš„è¾ƒä½Žçº§åˆ«
                for (int i = levels.length - 1; i >= 0; i--) {
                    WatermarkLevel level = levels[i];
                    if (level == NORMAL) break;
                    if (level.exitThreshold > 0 && usageRatio < level.exitThreshold) {
                        continue;
                    }
                    return level;
                }
                return NORMAL;
            }

            return highestReached;
        }
    }

    // ==================== é˜²å¾¡çŠ¶æ€ ====================

    /** åŽ‹ç¼©å†·å´æ—¶é—´(æ¯«ç§’)ï¼Œå†·å´æœŸå†…ä¸åšæ›´é«˜çº§åˆ«åŽ‹ç¼© */
    private static final long COOLDOWN_MS = 30_000; // 30ç§?
    /** å¿«é€Ÿè§¦å‘å†·å´æ—¶é—´(æ¯«ç§’)ï¼Œå†·å´æœŸå†…è§¦å‘æ¬¡æ•°è¶…è¿‡æ­¤å€¼å‡çº§ */
    private static final long RAPID_TRIGGER_COOLDOWN_MS = 60_000; // 60ç§?
    /** å¿«é€Ÿè§¦å‘æ¬¡æ•°é˜ˆå€¼ï¼Œè¶…è¿‡åŽå‡çº§ */
    private static final int RAPID_TRIGGER_LIMIT = 3;
    /** åŽ‹ç¼©æ•ˆçŽ‡æœ€ä½Žé˜ˆå€¼ï¼ˆ10%ï¼‰ */
    private static final double MIN_EFFICIENCY_RATIO = 0.10;
    /** è¿žç»­æ— æ•ˆåŽ‹ç¼©æ¬¡æ•°é˜ˆå€¼ï¼Œè¶…è¿‡åŽå»ºè®®Reset */
    private static final int STALENESS_LIMIT = 3;
    /** æ¯ä¸ªä¼šè¯æœ€å¤§åŽ‹ç¼©æ¬¡æ•° */
    private static final int MAX_COMPACTIONS_PER_SESSION = 10;
    /** ç´§æ€¥æˆªæ–­é˜ˆå€¼ï¼Œè¶…è¿‡åŽå¼ºåˆ¶æˆªæ–­ä¸ç­‰å¾…åŽ‹ç¼© */
    private static final double EMERGENCY_CUTOFF = 0.95;

    /** ä¸Šæ¬¡åŽ‹ç¼©æ—¶é—´ */
    private Instant lastCompactionTime = null;
    /** å½“å‰çŸ­æœŸå†·å´æœŸå†…è§¦å‘æ¬¡æ•° */
    private int rapidTriggerCount = 0;
    /** å¿«é€Ÿè§¦å‘çª—å£å¼€å§‹æ—¶é—´ */
    private Instant rapidTriggerWindowStart = null;
    /** å½“å‰ä¼šè¯å†…å·²æ‰§è¡Œçš„åŽ‹ç¼©æ¬¡æ•° */
    private int compactionCount = 0;
    /** è¿žç»­æ— æ•ˆåŽ‹ç¼©æ¬¡æ•° */
    private int consecutiveIneffectiveCount = 0;
    /** ä¸Šæ¬¡åŽ‹ç¼©æ•ˆçŽ‡ï¼ˆèŠ‚çœtokenæ•°/åŽ‹ç¼©å‰tokenæ•°ï¼‰ */
    private double lastEfficiency = 1.0;
    /** å½“å‰æ°´ä½çº§åˆ« */
    private WatermarkLevel currentLevel = WatermarkLevel.NORMAL;
    /** æ˜¯å¦å·²ç»å»ºè®®è¿‡Reset */
    private boolean resetRecommended = false;
    /** æ˜¯å¦å·²å¼ºåˆ¶Reset */
    private boolean resetEnforced = false;

    // ==================== å…¬å…±æŽ¥å£ ====================

    /**
     * æ ¹æ®å½“å‰ä½¿ç”¨çŽ‡è¯„ä¼°æ°´ä½çº§åˆ«ï¼Œå¸¦æ»žåŽå›žå·®ã€?     */
    public WatermarkLevel evaluateLevel(double usageRatio) {
        WatermarkLevel newLevel = WatermarkLevel.evaluate(usageRatio, currentLevel);
        if (newLevel != currentLevel) {
            logger.info(String.format("[CompactionThresholds] æ°´ä½å‡çº§: %s â†?%s (usage=%.1f%%)",
                    currentLevel.name(), newLevel.name(), usageRatio * 100));
            currentLevel = newLevel;
        }
        return currentLevel;
    }

    /**
     * åˆ¤æ–­ç»™å®šæ°´ä½çº§åˆ«æ˜¯å¦éœ€è¦åŽ‹ç¼©ã€?     * NORMAL å’Œ ADVISORY çº§åˆ«ä¸è§¦å‘åŽ‹ç¼©ã€?     */
    public boolean needsCompaction(WatermarkLevel level) {
        if (level == null) return false;
        return level.ordinal() >= WatermarkLevel.GENTLE.ordinal();
    }

    /**
     * èŽ·å–å½“å‰æ°´ä½çº§åˆ«å¯¹åº”çš„åŽ‹ç¼©ç­–ç•¥ã€?     */
    public CompactorTrigger.Strategy getStrategyForLevel(WatermarkLevel level) {
        if (level == null || level.getStrategy() == null) {
            return CompactorTrigger.Strategy.MINIMAL;
        }
        return level.getStrategy();
    }

    /**
     * åŽ‹ç¼©å‰æ£€æŸ¥é˜²å¾¡æœºåˆ¶ï¼Œè¿”å›žæ˜¯å¦å…è®¸æ‰§è¡Œã€?     * å¦‚æžœä¸å…è®¸ï¼Œè¿”å›žçš„ DefenseDecision ä¼šåŒ…å«å‡çº§æç¤ºã€?     */
    public DefenseDecision checkDefenses(WatermarkLevel requestedLevel) {
        Instant now = Instant.now();

        // 0. ç´§æ€¥æˆªæ–­ï¼šä¸ç­‰å¾…åŽ‹ç¼©ï¼Œç›´æŽ¥æˆªæ–­
        if (requestedLevel == WatermarkLevel.EMERGENCY) {
            logger.warning("[CompactionThresholds] ç´§æ€¥çº§åˆ«ï¼Œè·³è¿‡åŽ‹ç¼©ï¼Œå»ºè®®ç«‹å³æ‰§è¡ŒContext Reset");
            return DefenseDecision.skipAndSuggestReset("ç´§æ€¥æ°´ä½ï¼Œå»ºè®®ç«‹å³æ‰§è¡ŒContext Reset");
        }

        // 1. å¼ºåˆ¶Resetï¼šå·²ç»è§¦å‘è¿‡å¼ºåˆ¶Resetï¼Œä¸åšæ›´å¤šåŽ‹ç¼©
        if (resetEnforced) {
            return DefenseDecision.deny("å·²è§¦å‘å¼ºåˆ¶Resetï¼Œè¯·æ‰§è¡ŒContext Reset");
        }

        // 2. åŽ‹ç¼©é¢„ç®—è€—å°½
        if (compactionCount >= MAX_COMPACTIONS_PER_SESSION) {
            logger.warning("[CompactionThresholds] åŽ‹ç¼©æ¬¡æ•°è¶…é™? " + compactionCount
                    + "/" + MAX_COMPACTIONS_PER_SESSION + "ï¼Œå»ºè®®Context Reset");
            resetEnforced = true;
            return DefenseDecision.skipAndSuggestReset("åŽ‹ç¼©æ¬¡æ•°è€—å°½ï¼ˆ" + compactionCount
                    + "/" + MAX_COMPACTIONS_PER_SESSION + "ï¼‰ï¼Œå»ºè®®æ‰§è¡ŒContext Reset");
        }

        // 3. è¿žç»­æ— æ•ˆåŽ‹ç¼©æ£€æµ‹
        if (consecutiveIneffectiveCount >= STALENESS_LIMIT) {
            logger.warning("[CompactionThresholds] è¿žç»­" + consecutiveIneffectiveCount
                    + "æ¬¡æ— æ•ˆåŽ‹ç¼©ï¼Œå»ºè®®Context Reset");
            resetRecommended = true;
            return DefenseDecision.skipAndSuggestReset("è¿žç»­" + consecutiveIneffectiveCount
                    + "æ¬¡æ— æ•ˆåŽ‹ç¼©ï¼ŒåŽ‹ç¼©å·²å¤±åŽ»æ„ä¹‰ï¼Œå»ºè®®æ‰§è¡ŒContext Reset");
        }

        // 4. å†·å´é—´éš”æ£€æŸ¥
        if (lastCompactionTime != null) {
            long elapsedMs = now.toEpochMilli() - lastCompactionTime.toEpochMilli();

            // å†·å´æœŸå†…ï¼Œåªèƒ½æ‰§è¡Œæ›´ä½Žçº§åˆ«çš„åŽ‹ç¼©ï¼Œä¸èƒ½å‡çº§
            if (elapsedMs < COOLDOWN_MS && compactionCount > 0) {
                if (requestedLevel.ordinal() > currentLevel.ordinal()) {
                    logger.info("[CompactionThresholds] å†·å´æœŸå†…ï¼Œé™åˆ¶å‡çº§: è¯·æ±‚="
                            + requestedLevel.name() + ", å½“å‰=" + currentLevel.name());
                    return DefenseDecision.defer("å†·å´æœŸå†…ï¼Œæš‚ç¼“å‡çº§"
                            + "ï¼ˆè·ç¦»ä¸Šæ¬¡åŽ‹ç¼©" + (elapsedMs / 1000) + "ç§’ï¼‰");
                }
            }
        }

        // 5. å¿«é€Ÿè§¦å‘æ£€æµ‹ï¼ˆçº§è”ä¿æŠ¤ï¼‰
        if (rapidTriggerWindowStart != null) {
            long windowElapsedMs = now.toEpochMilli() - rapidTriggerWindowStart.toEpochMilli();
            if (windowElapsedMs < RAPID_TRIGGER_COOLDOWN_MS
                    && rapidTriggerCount >= RAPID_TRIGGER_LIMIT) {
                logger.warning("[CompactionThresholds] å¿«é€Ÿè§¦å‘æ£€æµ‹: " + rapidTriggerCount
                        + "æ¬¡åœ¨" + (windowElapsedMs / 1000) + "ç§’å†…ï¼Œæå‰å‡çº§ä¸ºCRITICAL");
                return DefenseDecision.escalate("å¿«é€Ÿè§¦å‘æ£€æµ‹ï¼Œæå‰å‡çº§ä¸ºCRITICALè­¦å‘Š",
                        CompactorTrigger.Strategy.AGGRESSIVE);
            }
        }

        return DefenseDecision.allow();
    }

    /**
     * åŽ‹ç¼©åŽå›žè°ƒï¼Œè®°å½•ç»Ÿè®¡ä¿¡æ¯ã€?     *
     * @param tokensBefore åŽ‹ç¼©å‰tokenæ•°
     * @param tokensAfter  åŽ‹ç¼©åŽtokenæ•°
     */
    public void recordCompaction(long tokensBefore, long tokensAfter) {
        Instant now = Instant.now();
        long saved = Math.max(0, tokensBefore - tokensAfter);
        double efficiency = tokensBefore > 0 ? (double) saved / tokensBefore : 0.0;

        // æ›´æ–°åŽ‹ç¼©æ¬¡æ•°
        compactionCount++;
        lastCompactionTime = now;
        lastEfficiency = efficiency;

        // æ›´æ–°å¿«é€Ÿè§¦å‘è®¡æ•°
        if (rapidTriggerWindowStart == null
                || now.toEpochMilli() - rapidTriggerWindowStart.toEpochMilli() > RAPID_TRIGGER_COOLDOWN_MS) {
            rapidTriggerWindowStart = now;
            rapidTriggerCount = 1;
        } else {
            rapidTriggerCount++;
        }

        // æ•ˆçŽ‡æ£€æŸ¥
        if (efficiency < MIN_EFFICIENCY_RATIO) {
            consecutiveIneffectiveCount++;
            logger.warning(String.format("[CompactionThresholds] ç¬?%dæ¬¡ä½Žæ•ˆåŽ‹ç¼©: æ•ˆçŽ‡=%.1f%% (èŠ‚çœ=%d/%d)",
                    consecutiveIneffectiveCount, efficiency * 100, saved, tokensBefore));
        } else {
            consecutiveIneffectiveCount = 0; // æœ‰æ•ˆåŽ‹ç¼©é‡ç½®è¿žç»­æ— æ•ˆè®¡æ•°
        }

        logger.info(String.format("[CompactionThresholds] åŽ‹ç¼©è®°å½•: #%d, æ•ˆçŽ‡=%.1f%%, ä¿å­?%d tokens"
                        + ", è¿žç»­æ— æ•ˆ=%d, å¿«é€Ÿè§¦å‘=%d",
                compactionCount, efficiency * 100, saved,
                consecutiveIneffectiveCount, rapidTriggerCount));
    }

    /**
     * é‡ç½®æ‰€æœ‰é˜²å¾¡çŠ¶æ€ï¼ˆä¾‹å¦‚è°ƒç”¨Context ResetåŽï¼‰ã€?     */
    public void resetDefenseState() {
        lastCompactionTime = null;
        rapidTriggerCount = 0;
        rapidTriggerWindowStart = null;
        compactionCount = 0;
        consecutiveIneffectiveCount = 0;
        lastEfficiency = 1.0;
        currentLevel = WatermarkLevel.NORMAL;
        resetRecommended = false;
        resetEnforced = false;
        logger.info("[CompactionThresholds] é˜²å¾¡çŠ¶æ€å·²é‡ç½®");
    }

    /**
     * æ ‡è®°å¼ºåˆ¶Resetï¼ˆç”±å¤–éƒ¨è°ƒç”¨ï¼Œä¾‹å¦‚æ‰§è¡Œå®ŒContext ResetåŽï¼‰ã€?     */
    public void markResetExecuted() {
        resetDefenseState();
    }

    /**
     * èŽ·å–é˜²å¾¡çŠ¶æ€æ‘˜è¦ï¼ˆç”¨äºŽæ—¥å¿—/è§‚æµ‹ï¼‰ã€?     */
    public String getDefenseSummary() {
        return String.format(
                "DefenseState{level=%s, compactions=%d/%d, ineffective=%d/%d, rapid=%d, resetRecommended=%b, resetEnforced=%b}",
                currentLevel.name(), compactionCount, MAX_COMPACTIONS_PER_SESSION,
                consecutiveIneffectiveCount, STALENESS_LIMIT,
                rapidTriggerCount, resetRecommended, resetEnforced);
    }

    // ==================== Getters ====================

    public WatermarkLevel getCurrentLevel() { return currentLevel; }
    public int getCompactionCount() { return compactionCount; }
    public int getConsecutiveIneffectiveCount() { return consecutiveIneffectiveCount; }
    public double getLastEfficiency() { return lastEfficiency; }
    public boolean isResetRecommended() { return resetRecommended; }
    public boolean isResetEnforced() { return resetEnforced; }
    public int getMaxCompactionsPerSession() { return MAX_COMPACTIONS_PER_SESSION; }
    public static double getEmergencyCutoff() { return EMERGENCY_CUTOFF; }

    // ==================== é˜²å¾¡å†³ç­–ç»“æžœ ====================

    /**
     * é˜²å¾¡æœºåˆ¶çš„å†³ç­–ç»“æžœã€?     */
    public static class DefenseDecision {
        public enum Action {
            /** æ”¾è¡Œï¼Œå…è®¸æ‰§è¡ŒåŽ‹ç¼© */
            ALLOW,
            /** å»¶è¿Ÿï¼Œæš‚æ—¶ä¸åšæ›´é«˜çº§åˆ«åŽ‹ç¼© */
            DEFER,
            /** æ‹’ç»ï¼Œä¸å…è®¸ä»»ä½•åŽ‹ç¼© */
            DENY,
            /** è·³è¿‡åŽ‹ç¼©å¹¶å»ºè®®Reset */
            SKIP_AND_RESET,
            /** æå‰å‡çº§ï¼Œä½¿ç”¨æŒ‡å®šç­–ç•¥ */
            ESCALATE
        }

        private final Action action;
        private final String reason;
        private final CompactorTrigger.Strategy escalatedStrategy;

        private DefenseDecision(Action action, String reason, CompactorTrigger.Strategy escalatedStrategy) {
            this.action = action;
            this.reason = reason;
            this.escalatedStrategy = escalatedStrategy;
        }

        public static DefenseDecision allow() {
            return new DefenseDecision(Action.ALLOW, null, null);
        }

        public static DefenseDecision defer(String reason) {
            return new DefenseDecision(Action.DEFER, reason, null);
        }

        public static DefenseDecision deny(String reason) {
            return new DefenseDecision(Action.DENY, reason, null);
        }

        public static DefenseDecision skipAndSuggestReset(String reason) {
            return new DefenseDecision(Action.SKIP_AND_RESET, reason, null);
        }

        public static DefenseDecision escalate(String reason, CompactorTrigger.Strategy strategy) {
            return new DefenseDecision(Action.ESCALATE, reason, strategy);
        }

        public Action getAction() { return action; }
        public String getReason() { return reason; }
        public CompactorTrigger.Strategy getEscalatedStrategy() { return escalatedStrategy; }

        public boolean isAllowed() { return action == Action.ALLOW; }
        public boolean isDeferred() { return action == Action.DEFER; }
        public boolean isDenied() { return action == Action.DENY; }
        public boolean shouldReset() { return action == Action.SKIP_AND_RESET; }
        public boolean shouldEscalate() { return action == Action.ESCALATE; }
    }
}

