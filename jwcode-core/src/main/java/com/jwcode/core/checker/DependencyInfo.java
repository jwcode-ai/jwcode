package com.jwcode.core.checker;

import java.time.LocalDateTime;

/**
 * 单个依赖项的检测信息
 */
public class DependencyInfo {
    
    private final String name;
    private final String type;
    private DependencyStatus status;
    private String message;
    private String version;
    private LocalDateTime checkTime;
    private String suggestedFix;

    public DependencyInfo(String name, String type) {
        this.name = name;
        this.type = type;
        this.status = DependencyStatus.CHECKING;
        this.checkTime = LocalDateTime.now();
    }

    public static DependencyInfo of(String name, String type) {
        return new DependencyInfo(name, type);
    }

    public DependencyInfo available() {
        this.status = DependencyStatus.AVAILABLE;
        return this;
    }

    public DependencyInfo unavailable(String message) {
        this.status = DependencyStatus.UNAVAILABLE;
        this.message = message;
        return this;
    }

    public DependencyInfo skipped(String reason) {
        this.status = DependencyStatus.SKIPPED;
        this.message = reason;
        return this;
    }

    public DependencyInfo withVersion(String version) {
        this.version = version;
        return this;
    }

    public DependencyInfo withFix(String suggestedFix) {
        this.suggestedFix = suggestedFix;
        return this;
    }

    // Getters
    public String getName() { return name; }
    public String getType() { return type; }
    public DependencyStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public String getVersion() { return version; }
    public LocalDateTime getCheckTime() { return checkTime; }
    public String getSuggestedFix() { return suggestedFix; }

    public boolean isAvailable() {
        return status == DependencyStatus.AVAILABLE;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s): %s", status, name, type, 
            message != null ? message : status.getDescription());
    }
}
