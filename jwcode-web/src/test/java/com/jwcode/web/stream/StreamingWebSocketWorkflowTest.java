package com.jwcode.web.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.ToolRegistry;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingWebSocketWorkflowTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private StreamingWebSocketHandler server;
    private RecordingClient client;
    private String oldWorkflowRoot;
    private String oldMemoryRoot;

    @BeforeEach
    void startServer() throws Exception {
        oldWorkflowRoot = System.getProperty("jwcode.workflow.root");
        oldMemoryRoot = System.getProperty("jwcode.memory.root");
        System.setProperty("jwcode.workflow.root", tempDir.resolve("workflows").toString());
        System.setProperty("jwcode.memory.root", tempDir.resolve("memory").toString());

        int port = freePort();
        server = new StreamingWebSocketHandler(port, new ToolRegistry());
        server.start();

        client = new RecordingClient(new URI("ws://127.0.0.1:" + port));
        assertTrue(client.connectBlocking(5, TimeUnit.SECONDS));
        client.send("{\"type\":\"auth\",\"token\":\"default-token\"}");
        waitForType("auth_success");
    }

    @AfterEach
    void stopServer() throws Exception {
        if (client != null) {
            client.closeBlocking();
        }
        if (server != null) {
            server.stop(1000);
        }
        restoreProperty("jwcode.workflow.root", oldWorkflowRoot);
        restoreProperty("jwcode.memory.root", oldMemoryRoot);
    }

    @Test
    void workflowMessagesDriveLedgerEventsAndResumeContract() throws Exception {
        client.send("""
            {
              "type": "workflow_start",
              "runId": "ws-run",
              "sessionId": "ws-session",
              "memoryEnabled": false,
              "workflow": {
                "id": "ws-ir",
                "schemaVersion": "workflow-ir.v1",
                "root": {
                  "type": "agent",
                  "id": "ws-agent",
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

        JsonNode started = waitForType("workflow_started");
        assertEquals("ws-session", started.get("sessionId").asText());
        assertEquals("RUNNING", payload(started).get("status").asText());
        assertTrue(Files.exists(tempDir.resolve("workflows").resolve("ws-run").resolve("ir.json")));

        JsonNode event = waitForType("workflow_event");
        assertEquals("ws-run", payload(event).get("runId").asText());
        JsonNode progress = waitForType("workflow_progress");
        assertEquals("ws-run", payload(progress).get("runId").asText());
        JsonNode finished = waitForType("workflow_finished");
        assertEquals("COMPLETED", payload(finished).get("status").asText());

        client.send("{\"type\":\"workflow_status\",\"runId\":\"ws-run\",\"sessionId\":\"ws-session\"}");
        JsonNode status = waitForPayloadStatus("workflow_progress", "COMPLETED");
        assertEquals("COMPLETED", payload(status).get("status").asText());

        client.send("{\"type\":\"workflow_pause\",\"runId\":\"ws-run\",\"sessionId\":\"ws-session\"}");
        JsonNode paused = waitForPayloadStatus("workflow_progress", "PAUSED");
        assertEquals("ws-run", payload(paused).get("runId").asText());

        client.send("{\"type\":\"workflow_cancel\",\"runId\":\"ws-run\",\"sessionId\":\"ws-session\"}");
        JsonNode cancelled = waitForPayloadStatus("workflow_finished", "CANCELLED");
        assertEquals("ws-run", payload(cancelled).get("runId").asText());

        client.send("{\"type\":\"workflow_resume\",\"runId\":\"ws-run\",\"sessionId\":\"ws-session\",\"memoryEnabled\":false}");
        JsonNode rejectedResume = waitForType("workflow_error");
        assertTrue(payload(rejectedResume).get("error").asText().contains("Cannot resume cancelled workflow"));

        int beforeForcedResume = client.messages.size();
        client.send("{\"type\":\"workflow_resume\",\"runId\":\"ws-run\",\"sessionId\":\"ws-session\",\"memoryEnabled\":false,\"forceResume\":true}");
        JsonNode forcedResume = waitForPayloadStatusSince("workflow_started", "RESUMING", beforeForcedResume);
        assertEquals("ws-run", payload(forcedResume).get("runId").asText());
        JsonNode resumedFinished = waitForPayloadStatusSince("workflow_finished", "COMPLETED", beforeForcedResume);
        assertEquals("ws-run", payload(resumedFinished).get("runId").asText());
    }

    private JsonNode waitForPayloadStatusSince(String type, String status, int startIndex) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            for (int i = Math.max(0, startIndex); i < client.messages.size(); i++) {
                JsonNode message = client.messages.get(i);
                if (type.equals(message.path("type").asText())) {
                    JsonNode payload = payload(message);
                    if (status.equals(payload.path("status").asText())) {
                        return message;
                    }
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for " + type + " status=" + status + ", messages=" + client.messages);
    }

    private JsonNode waitForPayloadStatus(String type, String status) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            for (JsonNode message : client.messages) {
                if (type.equals(message.path("type").asText())) {
                    JsonNode payload = payload(message);
                    if (status.equals(payload.path("status").asText())) {
                        return message;
                    }
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for " + type + " status=" + status + ", messages=" + client.messages);
    }

    private JsonNode waitForType(String type) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            for (JsonNode message : client.messages) {
                if (type.equals(message.path("type").asText())) {
                    return message;
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for " + type + ", messages=" + client.messages);
    }

    private JsonNode payload(JsonNode message) throws Exception {
        JsonNode data = message.get("data");
        assertNotNull(data);
        if (data.isTextual()) {
            return mapper.readTree(data.asText());
        }
        return data;
    }

    private int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private final class RecordingClient extends WebSocketClient {
        private final CopyOnWriteArrayList<JsonNode> messages = new CopyOnWriteArrayList<>();

        private RecordingClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
        }

        @Override
        public void onMessage(String message) {
            try {
                messages.add(mapper.readTree(message));
            } catch (Exception e) {
                throw new AssertionError("Invalid websocket message: " + message, e);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
        }

        @Override
        public void onError(Exception ex) {
        }
    }
}
