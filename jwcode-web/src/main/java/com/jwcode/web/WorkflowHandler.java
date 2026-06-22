package com.jwcode.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.core.hands.LocalAgentHand;
import com.jwcode.core.hands.ToolHand;
import com.jwcode.core.memory.FileMemoryLayer;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;
import com.jwcode.core.workflow.EffectVM;
import com.jwcode.core.workflow.WorkflowArtifactStore;
import com.jwcode.core.workflow.WorkflowCompiler;
import com.jwcode.core.workflow.WorkflowEvent;
import com.jwcode.core.workflow.WorkflowIR;
import com.jwcode.core.workflow.WorkflowInput;
import com.jwcode.core.workflow.WorkflowLedger;
import com.jwcode.core.workflow.WorkflowResult;
import com.jwcode.core.workflow.WorkflowState;
import com.jwcode.core.workflow.WorkflowStatus;
import com.jwcode.core.workflow.ir.AgentNode;
import com.jwcode.core.workflow.ir.ErrorMode;
import com.jwcode.core.workflow.ir.PhaseNode;
import com.jwcode.core.workflow.ir.PipelineNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkflowHandler implements HttpHandler {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final WorkflowCompiler compiler = new WorkflowCompiler();
    private final ToolRegistry toolRegistry;
    private final Path workspaceDir;
    private final ExecutorService workflowExecutor;

    public WorkflowHandler(ToolRegistry toolRegistry, String workspaceDir) {
        this.toolRegistry = toolRegistry;
        this.workspaceDir = Path.of(workspaceDir == null ? System.getProperty("user.dir") : workspaceDir);
        AtomicInteger threadId = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "http-workflow-" + threadId.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.workflowExecutor = Executors.newCachedThreadPool(threadFactory);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("POST".equalsIgnoreCase(method) && "/api/workflows/start".equals(path)) {
                start(exchange);
                return;
            }
            if (path.matches("/api/workflows/[^/]+/resume") && "POST".equalsIgnoreCase(method)) {
                resume(exchange, runIdFrom(path));
                return;
            }
            if (path.matches("/api/workflows/[^/]+/cancel") && "POST".equalsIgnoreCase(method)) {
                cancel(exchange, runIdFrom(path));
                return;
            }
            if (path.matches("/api/workflows/[^/]+/pause") && "POST".equalsIgnoreCase(method)) {
                pause(exchange, runIdFrom(path));
                return;
            }
            if (path.matches("/api/workflows/[^/]+/status") && "GET".equalsIgnoreCase(method)) {
                status(exchange, runIdFrom(path));
                return;
            }
            if (path.matches("/api/workflows/[^/]+/events") && "GET".equalsIgnoreCase(method)) {
                events(exchange, runIdFrom(path));
                return;
            }
            send(exchange, 404, error("Unknown workflow endpoint"));
        } catch (Exception e) {
            send(exchange, 500, error(e.getMessage()));
        }
    }

    private void start(HttpExchange exchange) throws Exception {
        JsonNode body = readBody(exchange);
        String runId = text(body, "runId", "wf-" + UUID.randomUUID());
        WorkflowIR ir = workflowFromBody(body, runId);
        saveWorkflowIR(runId, ir);
        WorkflowInput input = workflowInput(body, text(body, "sessionId", runId));
        executeAsync(runId, ir, input);
        send(exchange, 200, runResponse(runId, input.sessionId(), WorkflowStatus.RUNNING, null));
    }

    private void resume(HttpExchange exchange, String runId) throws Exception {
        JsonNode body = readBody(exchange);
        WorkflowLedger ledger = new WorkflowLedger(runId, runDir(runId));
        boolean forceResume = body.path("forceResume").asBoolean(false);
        if (ledger.replayState().status() == WorkflowStatus.CANCELLED && !forceResume) {
            send(exchange, 409, error("Cannot resume cancelled workflow without forceResume=true"));
            return;
        }
        WorkflowIR ir = body.has("workflow") ? workflowFromBody(body, runId) : readWorkflowIR(runId);
        saveWorkflowIR(runId, ir);
        WorkflowInput input = workflowInput(body, text(body, "sessionId", runId), forceResume);
        executeAsync(runId, ir, input);
        send(exchange, 200, runResponse(runId, input.sessionId(), WorkflowStatus.RUNNING, null));
    }

    private void cancel(HttpExchange exchange, String runId) throws IOException {
        new WorkflowLedger(runId, runDir(runId)).append("run.cancelled", Map.of());
        send(exchange, 200, runResponse(runId, "", WorkflowStatus.CANCELLED, null));
    }

    private void pause(HttpExchange exchange, String runId) throws IOException {
        new WorkflowLedger(runId, runDir(runId)).append("run.paused", Map.of());
        send(exchange, 200, runResponse(runId, "", WorkflowStatus.PAUSED, null));
    }

    private void status(HttpExchange exchange, String runId) throws IOException {
        WorkflowState state = Files.exists(runDir(runId).resolve("events.jsonl"))
            ? new WorkflowLedger(runId, runDir(runId)).replayState()
            : new WorkflowState(runId);
        ObjectNode json = runResponse(runId, "", state.status(), null);
        json.put("completedEffects", state.completedEffectsCount());
        json.put("completedPhases", state.completedPhasesCount());
        json.put("tokensUsed", state.tokensUsed());
        send(exchange, 200, json);
    }

    private void events(HttpExchange exchange, String runId) throws IOException {
        ArrayNode events = mapper.createArrayNode();
        if (Files.exists(runDir(runId).resolve("events.jsonl"))) {
            for (WorkflowEvent event : new WorkflowLedger(runId, runDir(runId)).replay()) {
                events.add(mapper.valueToTree(event));
            }
        }
        ObjectNode json = mapper.createObjectNode();
        json.put("success", true);
        json.put("runId", runId);
        json.set("events", events);
        send(exchange, 200, json);
    }

    private WorkflowResult execute(String runId, WorkflowIR ir, WorkflowInput input) {
        Path dir = runDir(runId);
        WorkflowLedger ledger = new WorkflowLedger(runId, dir);
        EffectVM vm = new EffectVM(
            ledger,
            new WorkflowArtifactStore(dir),
            new LocalAgentHand(),
            new ToolHand(new ToolExecutor(toolRegistry, new com.jwcode.core.permission.PermissionManagerChecker(), null, null), workspaceDir),
            new FileMemoryLayer(memoryRoot()));
        return vm.execute(runId, ir, input);
    }

    private void executeAsync(String runId, WorkflowIR ir, WorkflowInput input) {
        workflowExecutor.submit(() -> execute(runId, ir, input));
    }

    private WorkflowInput workflowInput(JsonNode body, String sessionId) {
        return workflowInput(body, sessionId, false);
    }

    private WorkflowInput workflowInput(JsonNode body, String sessionId, boolean forceResume) {
        JsonNode payload = body.has("input") ? body.get("input") : JsonNodeFactory.instance.objectNode();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("transport", "http");
        metadata.put("projectId", text(body, "projectId", workspaceDir.toAbsolutePath().normalize().toString()));
        metadata.put("memoryEnabled", !body.has("memoryEnabled") || body.get("memoryEnabled").asBoolean(true));
        metadata.put("checkpointPolicy", text(body, "checkpointPolicy", "phase-and-token"));
        if (forceResume) {
            metadata.put("forceResume", true);
        }
        return new WorkflowInput(sessionId, payload, metadata);
    }

    private WorkflowIR workflowFromBody(JsonNode body, String runId) {
        JsonNode workflow = body.get("workflow");
        if (workflow != null && workflow.isObject()) {
            return compiler.fromJson(workflow.toString());
        }
        if (workflow != null && !workflow.isObject()) {
            throw new IllegalArgumentException("workflow must be a JSON IR object");
        }
        return defaultWorkflow(runId, text(body, "message", "Execute workflow"));
    }

    private WorkflowIR defaultWorkflow(String runId, String message) {
        return new WorkflowIR("http-" + runId, new PipelineNode("root-pipeline", List.of(
            new PhaseNode("p1-explore", "explore", List.of(new AgentNode("e1-explore", "explorer", message, List.of(), null, 0, 0))),
            new PhaseNode("p2-verify", "verify", List.of(new AgentNode("e2-verify", "verifier", "Verify: " + message, List.of(), null, 0, 0)))
        ), ErrorMode.FAIL_FAST), null, "workflow-ir.v1");
    }

    private ObjectNode runResponse(String runId, String sessionId, WorkflowStatus status, String error) {
        ObjectNode json = mapper.createObjectNode();
        json.put("success", error == null);
        json.put("runId", runId);
        json.put("sessionId", sessionId == null ? "" : sessionId);
        json.put("status", status.name());
        json.put("workflowRoot", runDir(runId).toString());
        if (error != null) {
            json.put("error", error);
        }
        return json;
    }

    private JsonNode readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            String raw = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return raw.isBlank() ? JsonNodeFactory.instance.objectNode() : mapper.readTree(raw);
        }
    }

    private void send(HttpExchange exchange, int statusCode, ObjectNode json) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(json);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private ObjectNode error(String message) {
        ObjectNode json = mapper.createObjectNode();
        json.put("success", false);
        json.put("error", message == null ? "Workflow request failed" : message);
        return json;
    }

    private void saveWorkflowIR(String runId, WorkflowIR ir) throws IOException {
        Files.createDirectories(runDir(runId));
        Files.writeString(runDir(runId).resolve("ir.json"), compiler.toJson(ir), StandardCharsets.UTF_8);
    }

    private WorkflowIR readWorkflowIR(String runId) throws IOException {
        Path irFile = runDir(runId).resolve("ir.json");
        if (!Files.exists(irFile)) {
            throw new IllegalArgumentException("Workflow IR not found for runId: " + runId);
        }
        return compiler.fromJson(Files.readString(irFile, StandardCharsets.UTF_8));
    }

    private Path runDir(String runId) {
        return workflowRoot().resolve(runId);
    }

    private Path workflowRoot() {
        return Path.of(System.getProperty(
            "jwcode.workflow.root",
            Path.of(System.getProperty("user.home"), ".jwcode", "workflows").toString()));
    }

    private Path memoryRoot() {
        return Path.of(System.getProperty(
            "jwcode.memory.root",
            Path.of(System.getProperty("user.home"), ".jwcode").toString()));
    }

    private static String runIdFrom(String path) {
        String prefix = "/api/workflows/";
        String remaining = path.substring(prefix.length());
        return remaining.substring(0, remaining.indexOf('/'));
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? fallback : value.asText();
    }
}
