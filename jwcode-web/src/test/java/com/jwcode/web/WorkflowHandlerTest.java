package com.jwcode.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.ToolRegistry;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowHandlerTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private HttpClient client;
    private String baseUrl;
    private String oldWorkflowRoot;
    private String oldMemoryRoot;

    @BeforeEach
    void startServer() throws Exception {
        oldWorkflowRoot = System.getProperty("jwcode.workflow.root");
        oldMemoryRoot = System.getProperty("jwcode.memory.root");
        System.setProperty("jwcode.workflow.root", tempDir.resolve("workflows").toString());
        System.setProperty("jwcode.memory.root", tempDir.resolve("memory").toString());

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/workflows", new WorkflowHandler(new ToolRegistry(), tempDir.toString()));
        server.start();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        restoreProperty("jwcode.workflow.root", oldWorkflowRoot);
        restoreProperty("jwcode.memory.root", oldMemoryRoot);
    }

    @Test
    void startStatusEventsCancelAndResumeUseLedgerAndSavedIr() throws Exception {
        JsonNode started = post("/api/workflows/start", """
            {
              "runId": "run-http",
              "sessionId": "session-http",
              "memoryEnabled": false,
              "workflow": {
                "id": "manual",
                "schemaVersion": "workflow-ir.v1",
                "root": {
                  "type": "agent",
                  "id": "agent-1",
                  "role": "explorer",
                  "prompt": "inspect",
                  "tools": [],
                  "schema": null,
                  "maxRetries": 0,
                  "timeoutMs": 0
                }
              }
            }
            """);

        assertEquals(true, started.get("success").asBoolean());
        assertEquals("RUNNING", started.get("status").asText());
        assertTrue(Files.exists(tempDir.resolve("workflows").resolve("run-http").resolve("ir.json")));
        waitForEvents("run-http", "run.finished");

        JsonNode status = get("/api/workflows/run-http/status");
        assertEquals("COMPLETED", status.get("status").asText());
        assertEquals(1, status.get("completedEffects").asInt());

        JsonNode events = get("/api/workflows/run-http/events");
        assertEquals(true, events.get("success").asBoolean());
        assertTrue(events.get("events").size() > 0);

        JsonNode cancelled = post("/api/workflows/run-http/cancel", "{}");
        assertEquals("CANCELLED", cancelled.get("status").asText());

        HttpResponse<String> rejectedResume = rawPost("/api/workflows/run-http/resume", """
            {"sessionId":"session-http","memoryEnabled":false}
            """);
        assertEquals(409, rejectedResume.statusCode());

        JsonNode forcedResume = post("/api/workflows/run-http/resume", """
            {"sessionId":"session-http","memoryEnabled":false,"forceResume":true}
            """);
        assertEquals("RUNNING", forcedResume.get("status").asText());
    }

    @Test
    void pauseWritesPausedStatusToLedger() throws Exception {
        JsonNode paused = post("/api/workflows/run-paused/pause", "{}");

        assertEquals(true, paused.get("success").asBoolean());
        assertEquals("PAUSED", paused.get("status").asText());

        JsonNode status = get("/api/workflows/run-paused/status");
        assertEquals("PAUSED", status.get("status").asText());
    }

    @Test
    void newHandlerInstanceReplaysStatusAndEventsFromLedger() throws Exception {
        JsonNode started = post("/api/workflows/start", """
            {
              "runId": "run-replay",
              "sessionId": "session-replay",
              "memoryEnabled": false,
              "workflow": {
                "id": "manual-replay",
                "schemaVersion": "workflow-ir.v1",
                "root": {
                  "type": "agent",
                  "id": "agent-replay",
                  "role": "explorer",
                  "prompt": "inspect",
                  "tools": [],
                  "schema": null,
                  "maxRetries": 0,
                  "timeoutMs": 0
                }
              }
            }
            """);

        assertEquals(true, started.get("success").asBoolean());
        waitForEvents("run-replay", "run.finished");

        restartServerWithFreshHandler();

        JsonNode status = get("/api/workflows/run-replay/status");
        assertEquals("COMPLETED", status.get("status").asText());
        assertEquals(1, status.get("completedEffects").asInt());

        JsonNode events = get("/api/workflows/run-replay/events");
        assertEquals(true, events.get("success").asBoolean());
        assertTrue(events.get("events").size() > 0);
    }

    @Test
    void backgroundFailureReturnsImmediatelyAndIsVisibleThroughLedgerQueries() throws Exception {
        JsonNode started = post("/api/workflows/start", """
            {
              "runId": "run-failure",
              "sessionId": "session-failure",
              "memoryEnabled": false,
              "workflow": {
                "id": "manual-failure",
                "schemaVersion": "workflow-ir.v1",
                "root": {
                  "type": "tool",
                  "id": "tool-failure",
                  "toolName": "MissingWorkflowTestTool",
                  "input": {},
                  "maxRetries": 0,
                  "timeoutMs": 0
                }
              }
            }
            """);

        assertEquals(true, started.get("success").asBoolean());
        assertEquals("RUNNING", started.get("status").asText());

        waitForEvents("run-failure", "run.failed");

        JsonNode status = get("/api/workflows/run-failure/status");
        assertEquals("FAILED", status.get("status").asText());

        JsonNode events = get("/api/workflows/run-failure/events");
        assertTrue(events.get("events").toString().contains("run.failed"));
        assertTrue(events.get("events").toString().contains("tool-failure"));
    }

    private void waitForEvents(String runId, String eventType) throws Exception {
        Path events = tempDir.resolve("workflows").resolve(runId).resolve("events.jsonl");
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(events) && Files.readString(events).contains("\"type\":\"" + eventType + "\"")) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + eventType + " in " + events);
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return mapper.readTree(response.body());
    }

    private JsonNode post(String path, String body) throws Exception {
        HttpResponse<String> response = rawPost(path, body);
        assertEquals(200, response.statusCode());
        return mapper.readTree(response.body());
    }

    private void restartServerWithFreshHandler() throws Exception {
        if (server != null) {
            server.stop(0);
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/workflows", new WorkflowHandler(new ToolRegistry(), tempDir.toString()));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private HttpResponse<String> rawPost(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
