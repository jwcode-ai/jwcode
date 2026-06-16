package com.jwcode.core.skill;

/**
 * 技能使用记录 — 追踪单个技能的使用情况。
 */
public class UsageRecord {

    private String id;
    private int useCount;
    private int viewCount;
    private int patchCount;
    private long lastUsedAt;
    private long lastViewedAt;
    private long lastPatchedAt;
    private long createdAt;
    private String state;  // active, stale, archived, pinned
    private boolean pinned;

    public UsageRecord() {}

    public UsageRecord(String id) {
        this.id = id;
        this.createdAt = System.currentTimeMillis();
        this.state = "active";
    }

    /** 记录一次使用 */
    public void recordUse() {
        this.useCount++;
        this.lastUsedAt = System.currentTimeMillis();
    }

    /** 记录一次查看 */
    public void recordView() {
        this.viewCount++;
        this.lastViewedAt = System.currentTimeMillis();
    }

    /** 记录一次修改 */
    public void recordPatch() {
        this.patchCount++;
        this.lastPatchedAt = System.currentTimeMillis();
    }

    // Getters and Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getUseCount() { return useCount; }
    public void setUseCount(int useCount) { this.useCount = useCount; }

    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }

    public int getPatchCount() { return patchCount; }
    public void setPatchCount(int patchCount) { this.patchCount = patchCount; }

    public long getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(long lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public long getLastViewedAt() { return lastViewedAt; }
    public void setLastViewedAt(long lastViewedAt) { this.lastViewedAt = lastViewedAt; }

    public long getLastPatchedAt() { return lastPatchedAt; }
    public void setLastPatchedAt(long lastPatchedAt) { this.lastPatchedAt = lastPatchedAt; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
}
