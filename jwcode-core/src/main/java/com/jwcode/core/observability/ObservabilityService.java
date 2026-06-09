package com.jwcode.core.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.service.CostTrackerService;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * ObservabilityService — global singleton that wires AnalyticsObserver,
 * CostTrackerService, and TracePersistenceObserver into the engine pipeline
 * and exposes query methods for the REST API and UI.
 *
 * <p>Subscribe to an engine's pipeline via {@link #subscribeTo(ObservationPipeline)}.
 * The subscription is idempotent — subsequent calls are no-ops.</p>
 */
public class ObservabilityService implements ObservationPipeline.Observer {

    private static final Logger LOG = Logger.getLogger(ObservabilityService.class.getName());

    private static final ObservabilityService INSTANCE = new ObservabilityService();

    private final AnalyticsObserver analyticsObserver;
    private final CostTrackerService costTracker;
    private final TracePersistenceObserver tracePersistence;
    private final ObjectMapper mapper;
    private final AtomicBoolean subscribed = new AtomicBoolean(false);

    private ObservabilityService() {
        this.analyticsObserver = new AnalyticsObserver();
        this.costTracker = new CostTrackerService();
        this.tracePersistence = new TracePersistenceObserver();
        this.mapper = new ObjectMapper();
    }

    public static ObservabilityService getInstance() {
        return INSTANCE;
    }

    // ---- Pipeline integration ----

    /**
     * Subscribe this service's observers to the given pipeline.
     * Idempotent — only the first call has effect.
     */
    public void subscribeTo(ObservationPipeline pipeline) {
        if (!subscribed.compareAndSet(false, true)) {
            return; // already subscribed
        }
        pipeline.subscribe(analyticsObserver);
        pipeline.subscribe(tracePersistence);
        pipeline.subscribe(this); // for forwarding TokenUsage to costTracker
        LOG.info("ObservabilityService subscribed to pipeline");
    }

    @Override
    public void onEvent(ObservationEvent event) {
        if (event instanceof ObservationEvent.TokenUsage tu) {
            costTracker.recordCost(tu.model(), tu.promptTokens(), tu.completionTokens());
        }
    }

    @Override
    public String getObserverName() {
        return "ObservabilityService";
    }

    // ---- Query methods ----

    /** Current session summary: analytics + total cost. */
    public Map<String, Object> getSummary() {
        AnalyticsObserver.ExecutionSummary es = analyticsObserver.getSummary();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("llmCalls", es.llmCalls());
        map.put("toolCalls", es.toolCalls());
        map.put("errors", es.errors());
        map.put("promptTokens", es.promptTokens());
        map.put("completionTokens", es.completionTokens());
        map.put("totalTokens", es.totalTokens());
        map.put("cacheCreationTokens", es.cacheCreationTokens());
        map.put("cacheReadTokens", es.cacheReadTokens());
        map.put("cacheHitRate", es.cacheHitRate());
        map.put("elapsedSeconds", es.elapsed().toMillis() / 1000.0);
        map.put("totalCostDollars", costTracker.getTotalCostDollars());
        return map;
    }

    /** Cost breakdown by model + history. */
    public Map<String, Object> getCosts() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalCostDollars", costTracker.getTotalCostDollars());

        Map<String, CostTrackerService.ModelCostSummary> byModel = costTracker.getCostByModel();
        List<Map<String, Object>> modelList = new ArrayList<>();
        for (CostTrackerService.ModelCostSummary mcs : byModel.values()) {
            modelList.add(mcs.toMap());
        }
        map.put("byModel", modelList);

        List<Map<String, Object>> historyList = new ArrayList<>();
        for (CostTrackerService.CostEntry entry : costTracker.getAllCostHistory()) {
            historyList.add(entry.toMap());
        }
        map.put("history", historyList);

        return map;
    }

    // ---- Trace run queries ----

    /** List all historical trace runs with their metadata. */
    public List<Map<String, Object>> listRuns() {
        List<Map<String, Object>> runs = new ArrayList<>();
        try {
            for (Path runDir : tracePersistence.listHistoricalRuns()) {
                Path metaFile = runDir.resolve("run-metadata.json");
                if (Files.exists(metaFile)) {
                    Map<String, Object> meta = readJsonFile(metaFile);
                    if (meta != null) {
                        runs.add(meta);
                    }
                }
            }
        } catch (IOException e) {
            LOG.warning("Error listing runs: " + e.getMessage());
        }
        return runs;
    }

    /** Get metadata for a single run. Returns null if not found. */
    public Map<String, Object> getRun(String runId) {
        try {
            for (Path runDir : tracePersistence.listHistoricalRuns()) {
                if (runDir.getFileName().toString().equals(runId)) {
                    Path metaFile = runDir.resolve("run-metadata.json");
                    if (Files.exists(metaFile)) {
                        return readJsonFile(metaFile);
                    }
                }
            }
        } catch (IOException e) {
            LOG.warning("Error finding run " + runId + ": " + e.getMessage());
        }
        return null;
    }

    /** Get paginated events for a run from trace.jsonl. */
    public Map<String, Object> getRunEvents(String runId, int page, int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> events = new ArrayList<>();
        int total = 0;

        try {
            for (Path runDir : tracePersistence.listHistoricalRuns()) {
                if (runDir.getFileName().toString().equals(runId)) {
                    Path traceFile = runDir.resolve("trace.jsonl");
                    if (!Files.exists(traceFile)) break;

                    try (BufferedReader reader = Files.newBufferedReader(traceFile, StandardCharsets.UTF_8)) {
                        int startIdx = page * size;
                        int endIdx = startIdx + size;
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (total >= startIdx && total < endIdx) {
                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> event = mapper.readValue(line, Map.class);
                                    events.add(event);
                                } catch (Exception e) {
                                    // skip malformed lines
                                }
                            }
                            total++;
                        }
                    }
                    break;
                }
            }
        } catch (IOException e) {
            LOG.warning("Error reading events: " + e.getMessage());
        }

        result.put("events", events);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /** Get the TracePersistenceObserver for manual run lifecycle (tests). */
    public TracePersistenceObserver getTracePersistence() {
        return tracePersistence;
    }

    /** Get the CostTrackerService for direct access. */
    public CostTrackerService getCostTracker() {
        return costTracker;
    }

    // ---- internal ----

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonFile(Path path) {
        if (!Files.exists(path)) return null;
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return mapper.readValue(content, Map.class);
        } catch (IOException e) {
            return null;
        }
    }
}
