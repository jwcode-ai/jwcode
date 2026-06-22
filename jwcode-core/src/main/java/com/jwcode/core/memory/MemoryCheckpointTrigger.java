package com.jwcode.core.memory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryCheckpointTrigger {
    private static final double[] THRESHOLDS = {0.20, 0.45, 0.70};
    private final Map<String, Integer> lastTriggeredThreshold = new ConcurrentHashMap<>();

    public boolean shouldTrigger(String sessionId, double usageRatio) {
        int thresholdIndex = thresholdIndex(usageRatio);
        if (thresholdIndex < 0) {
            return false;
        }
        return lastTriggeredThreshold.merge(sessionId, thresholdIndex, Math::max) == thresholdIndex
            && thresholdIndex > lastTriggeredThreshold.getOrDefault(sessionId + ":last-returned", -1)
            && markReturned(sessionId, thresholdIndex);
    }

    private boolean markReturned(String sessionId, int thresholdIndex) {
        lastTriggeredThreshold.put(sessionId + ":last-returned", thresholdIndex);
        return true;
    }

    private int thresholdIndex(double usageRatio) {
        for (int i = THRESHOLDS.length - 1; i >= 0; i--) {
            if (usageRatio >= THRESHOLDS[i]) {
                return i;
            }
        }
        return -1;
    }
}
