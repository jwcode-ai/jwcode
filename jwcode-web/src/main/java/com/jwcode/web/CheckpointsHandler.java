package com.jwcode.web;

import com.jwcode.core.planner.checkpoint.CheckpointManager;
import com.sun.net.httpserver.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** /api/checkpoints — 检查点列表 + 恢复 */
public class CheckpointsHandler implements HttpHandler {
    private final CheckpointManager cpm;

    public CheckpointsHandler() {
        this.cpm = new CheckpointManager(Path.of(System.getProperty("user.dir", ".")));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Content-Type", "application/json");

        if ("GET".equals(exchange.getRequestMethod())) {
            List<String> ids = cpm.listCheckpoints();
            String json = "{\"checkpoints\":[";
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) json += ",";
                json += "\"" + ids.get(i) + "\"";
            }
            json += "]}";
            byte[] resp = json.getBytes();
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
        } else if ("POST".equals(exchange.getRequestMethod())) {
            // 恢复检查点
            String path = exchange.getRequestURI().getPath();
            String id = path.substring(path.lastIndexOf('/') + 1);
            try {
                var cp = cpm.loadCheckpoint(id);
                byte[] resp = (cp != null
                    ? "{\"success\":true,\"taskId\":\"" + cp.getTaskId() + "\"}"
                    : "{\"success\":false,\"error\":\"not found\"}").getBytes();
                exchange.sendResponseHeaders(200, resp.length);
                exchange.getResponseBody().write(resp);
            } catch (Exception e) {
                byte[] resp = ("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}").getBytes();
                exchange.sendResponseHeaders(500, resp.length);
                exchange.getResponseBody().write(resp);
            }
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }
}
