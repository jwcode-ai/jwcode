package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.command.Command;
import com.jwcode.core.command.CommandRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;

/**
 * GET /api/commands - serves the unified command manifest.
 *
 * <p>Returns the complete list of commands registered in the singleton
 * {@link CommandRegistry}, making the Java backend the single source of truth
 * for slash commands consumed by the TS CLI and the web frontend.
 */
public class CommandsHandler implements HttpHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final CommandRegistry registry;

    public CommandsHandler(CommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && (origin.startsWith("http://localhost")
            || origin.startsWith("https://localhost")
            || origin.startsWith("http://127.0.0.1")
            || origin.startsWith("https://127.0.0.1"))) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Vary", "Origin");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        ArrayNode data = MAPPER.createArrayNode();
        if (registry != null) {
            for (Command cmd : registry.getAllCommands()) {
                ObjectNode n = MAPPER.createObjectNode();
                n.put("name", cmd.getName());
                n.put("description", cmd.getDescription());
                n.put("category", cmd.getCategory());
                n.put("usage", cmd.getUsage());
                n.put("requiresArgs", cmd.requiresArgs());
                n.put("source", cmd.getSource().name());
                ArrayNode aliases = MAPPER.createArrayNode();
                for (String a : cmd.getAliases()) {
                    aliases.add(a);
                }
                n.set("aliases", aliases);
                data.add(n);
            }
        }
        ObjectNode resp = MAPPER.createObjectNode();
        resp.put("success", true);
        resp.set("data", data);
        sendJson(exchange, 200, resp);
    }

    private void sendJson(HttpExchange exchange, int code, ObjectNode json) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(json);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        ObjectNode resp = MAPPER.createObjectNode();
        resp.put("success", false);
        resp.put("error", message);
        sendJson(exchange, code, resp);
    }
}
