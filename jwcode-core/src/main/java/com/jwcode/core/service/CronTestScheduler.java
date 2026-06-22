package com.jwcode.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * CronTestScheduler — reads .jwcode/crons.json and schedules automated test runs.
 *
 * <p>Supports two test types:
 * <ul>
 *   <li><b>tool</b> — runs ToolTestCoordinator for tool chain smoke tests</li>
 *   <li><b>scenario</b> — runs EvalTask-based scenario tests</li>
 * </ul>
 *
 * <p>Scheduling uses simple interval-based polling (not true cron), checking every minute.
 * The scheduler fires a callback when a test run is due.</p>
 */
public class CronTestScheduler {

    private static final Logger LOG = Logger.getLogger(CronTestScheduler.class.getName());
    private static final Path CRONS_PATH = Paths.get(System.getProperty("user.dir"), ".jwcode", "crons.json");

    private final ObjectMapper mapper;
    private final ScheduledExecutorService executor;
    private final List<CronEntry> entries;
    private TestRunCallback callback;
    private volatile boolean running;

    public CronTestScheduler() {
        this.mapper = new ObjectMapper();
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jwcode-cron-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.entries = new ArrayList<>();
        this.running = false;
    }

    @FunctionalInterface
    public interface TestRunCallback {
        void onTestDue(String type, String description, CronEntry entry);
    }

    public void start(TestRunCallback callback) {
        this.callback = callback;
        loadEntries();
        if (entries.isEmpty()) {
            LOG.info("No cron entries configured — scheduler idle");
        }
        running = true;
        executor.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.MINUTES);
        LOG.info("CronTestScheduler started with " + entries.size() + " entries");
    }

    public void stop() {
        running = false;
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOG.info("CronTestScheduler stopped");
    }

    public void reload() {
        entries.clear();
        loadEntries();
        LOG.info("CronTestScheduler reloaded: " + entries.size() + " entries");
    }

    public boolean isRunning() { return running; }
    public int getEntryCount() { return entries.size(); }

    // ---- internal ----

    private void loadEntries() {
        if (!Files.exists(CRONS_PATH)) {
            LOG.info("No crons.json found at " + CRONS_PATH);
            return;
        }
        try {
            String content = Files.readString(CRONS_PATH, StandardCharsets.UTF_8);
            List<Map<String, Object>> raw = mapper.readValue(content,
                new TypeReference<List<Map<String, Object>>>() {});
            if (raw != null) {
                for (Map<String, Object> r : raw) {
                    CronEntry entry = new CronEntry(
                        (String) r.getOrDefault("cron", ""),
                        (String) r.getOrDefault("type", "tool"),
                        (String) r.getOrDefault("description", ""),
                        r.containsKey("scenarios") ? toStringList(r.get("scenarios")) : List.of("*")
                    );
                    entries.add(entry);
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to load crons.json: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object val) {
        if (val instanceof List) return (List<String>) val;
        if (val instanceof String s) return List.of(s);
        return List.of("*");
    }

    private void tick() {
        if (!running) return;
        LocalDateTime now = LocalDateTime.now();

        for (CronEntry entry : entries) {
            if (matchesCron(entry.cron, now)) {
                if (!entry.wasTriggeredThisMinute(now)) {
                    entry.markTriggered(now);
                    LOG.info("Cron triggered: " + entry.type + " — " + entry.description);
                    if (callback != null) {
                        try {
                            callback.onTestDue(entry.type, entry.description, entry);
                        } catch (Exception e) {
                            LOG.severe("Cron callback error: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private boolean matchesCron(String expression, LocalDateTime now) {
        if (expression == null || expression.isBlank()) return false;
        String[] parts = expression.trim().split("\\s+");
        if (parts.length < 5) return false;

        return matchesField(parts[0], now.getMinute())
            && matchesField(parts[1], now.getHour())
            && matchesField(parts[2], now.getDayOfMonth())
            && matchesField(parts[3], now.getMonthValue())
            && matchesField(parts[4], now.getDayOfWeek().getValue() % 7);
    }

    private boolean matchesField(String field, int value) {
        if ("*".equals(field)) return true;
        for (String part : field.split(",")) {
            try {
                if (Integer.parseInt(part.trim()) == value) return true;
            } catch (NumberFormatException ignored) {
                // Non-numeric cron field part — skip
            }
        }
        return false;
    }

    public static class CronEntry {
        public final String cron;
        public final String type;
        public final String description;
        public final List<String> scenarios;
        private volatile LocalDateTime lastTriggered;

        CronEntry(String cron, String type, String description, List<String> scenarios) {
            this.cron = cron;
            this.type = type;
            this.description = description;
            this.scenarios = scenarios;
        }

        synchronized boolean wasTriggeredThisMinute(LocalDateTime now) {
            return lastTriggered != null
                && ChronoUnit.MINUTES.between(lastTriggered, now) < 1;
        }

        synchronized void markTriggered(LocalDateTime now) {
            this.lastTriggered = now;
        }
    }
}

