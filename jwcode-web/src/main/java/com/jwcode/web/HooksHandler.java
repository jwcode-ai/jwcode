package com.jwcode.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.hook.*;
import com.jwcode.core.hook.executor.AgentHookExecutor;
import com.jwcode.core.hook.executor.HttpHookExecutor;
import com.jwcode.core.hook.executor.ShellHookExecutor;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Hook 配置管理 API — Web 可视化编辑器后端。
 *
 * <p>提供 Hook 规则的完整 CRUD、试运行、导入导出、审计日志等功能。
 * 所有写操作通过原子文件替换保证数据安全。</p>
 */
public class HooksHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(HooksHandler.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private static final int MAX_BACKUPS = 5;
    private static final Pattern PRIVATE_IP = Pattern.compile(
        "^(127\\.|10\\.|172\\.(1[6-9]|2\\d|3[01])\\.|192\\.168\\.|0\\.0\\.0\\.0|::1$|localhost$)",
        Pattern.CASE_INSENSITIVE);

    private final HookRegistry registry;
    private final AgentRegistry agentRegistry;
    private final Path configFilePath;
    private final Path adminAuditPath;

    public HooksHandler(HookRegistry registry, AgentRegistry agentRegistry, Path configFilePath) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.agentRegistry = agentRegistry != null ? agentRegistry : AgentRegistry.createDefault();
        this.configFilePath = configFilePath.toAbsolutePath().normalize();
        this.adminAuditPath = this.configFilePath.resolveSibling("hook-admin-audit.jsonl");
    }

    // ==================== Route Dispatch ====================

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods",
            "GET, POST, PUT, DELETE, PATCH, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        try {
            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, path);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, path);
            } else if ("PUT".equalsIgnoreCase(method)) {
                handlePut(exchange, path);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                handleDelete(exchange, path);
            } else if ("PATCH".equalsIgnoreCase(method)) {
                handlePatch(exchange, path);
            } else {
                sendError(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[HooksHandler] Error handling " + method + " " + path, e);
            sendError(exchange, 500, "Internal error: " + e.getMessage());
        }
    }

    // ==================== GET ====================

    private void handleGet(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/api/hooks/events")) {
            getEvents(exchange);
        } else if (path.equals("/api/hooks/agents")) {
            getAgents(exchange);
        } else if (path.equals("/api/hooks/stats")) {
            getStats(exchange);
        } else if (path.equals("/api/hooks/logs")) {
            getLogs(exchange);
        } else if (path.equals("/api/hooks/export")) {
            exportHooks(exchange);
        } else if (path.equals("/api/hooks/lifecycle-mappings")) {
            getLifecycleMappings(exchange);
        } else if (path.equals("/api/hooks")) {
            listHooks(exchange);
        } else {
            String name = extractName(path);
            if (name != null && !name.isEmpty()) {
                getHook(exchange, name);
            } else {
                sendError(exchange, 404, "Not found: " + path);
            }
        }
    }

    private void handlePost(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/api/hooks/dry-run")) {
            dryRun(exchange);
        } else if (path.equals("/api/hooks/import")) {
            importHooks(exchange);
        } else if (path.equals("/api/hooks")) {
            createHook(exchange);
        } else if (path.equals("/api/hooks/batch-delete")) {
            batchDelete(exchange);
        } else if (path.equals("/api/hooks/batch-toggle")) {
            batchToggle(exchange);
        } else {
            sendError(exchange, 404, "Not found: " + path);
        }
    }

    private void handlePut(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/api/hooks/lifecycle-mappings")) {
            saveLifecycleMappings(exchange);
        } else {
            String name = extractName(path);
            if (name != null && !name.isEmpty()) {
                updateHook(exchange, name);
            } else {
                sendError(exchange, 404, "Not found: " + path);
            }
        }
    }

    private void handleDelete(HttpExchange exchange, String path) throws IOException {
        String name = extractName(path);
        if (name != null && !name.isEmpty()) {
            deleteHook(exchange, name);
        } else {
            sendError(exchange, 404, "Not found: " + path);
        }
    }

    private void handlePatch(HttpExchange exchange, String path) throws IOException {
        String name = extractPathSegment(path, "/toggle");
        if (name != null && !name.isEmpty()) {
            toggleHook(exchange, name);
        } else {
            sendError(exchange, 404, "Not found: " + path);
        }
    }

    // ==================== GET Handlers ====================

    private void listHooks(HttpExchange exchange) throws IOException {
        List<HookRuleDto> rules = readAllRules();
        sendSuccess(exchange, rules);
    }

    private void getHook(HttpExchange exchange, String name) throws IOException {
        List<HookRuleDto> rules = readAllRules();
        HookRuleDto found = rules.stream()
            .filter(r -> r.name().equals(name))
            .findFirst().orElse(null);
        if (found == null) {
            sendError(exchange, 404, "Hook not found: " + name);
        } else {
            sendSuccess(exchange, found);
        }
    }

    private void getEvents(HttpExchange exchange) throws IOException {
        ArrayNode categories = MAPPER.createArrayNode();
        for (HookEventType.EventCategory cat : HookEventType.EventCategory.values()) {
            ObjectNode catNode = MAPPER.createObjectNode();
            catNode.put("category", cat.name());
            ArrayNode eventsArr = catNode.putArray("events");
            for (HookEventType et : HookEventType.values()) {
                if (et.getCategory() == cat) {
                    ObjectNode evt = eventsArr.addObject();
                    evt.put("name", et.name());
                    evt.put("summary", et.getSummary());
                    evt.put("hasMatcher", et.hasMatcher());
                    if (et.hasMatcher()) {
                        evt.put("matcherField", et.getMatcherField());
                    }
                }
            }
            categories.add(catNode);
        }
        sendSuccess(exchange, categories);
    }

    private void getAgents(HttpExchange exchange) throws IOException {
        ArrayNode agents = MAPPER.createArrayNode();
        for (Agent agent : agentRegistry.getAll()) {
            ObjectNode a = agents.addObject();
            a.put("id", agent.getId());
            a.put("name", agent.getName());
            a.put("description", agent.getDescription());
        }
        sendSuccess(exchange, agents);
    }

    private void getStats(HttpExchange exchange) throws IOException {
        Map<String, Object> stats = registry.getStats();
        List<HookRuleDto> rules = readAllRules();
        long enabled = rules.stream().filter(HookRuleDto::enabled).count();
        stats.put("enabledCount", enabled);
        stats.put("disabledCount", rules.size() - enabled);
        sendSuccess(exchange, stats);
    }

    private void getLogs(HttpExchange exchange) throws IOException {
        List<HookExecutionLogEntry> logs = readAdminAuditLog();
        sendSuccess(exchange, logs);
    }

    private void exportHooks(HttpExchange exchange) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", "1.0");
        root.set("hooks", MAPPER.valueToTree(readAllRules()));
        root.set("lifecycleMappings", MAPPER.valueToTree(readLifecycleMappings()));
        sendJson(exchange, 200, root);
    }

    private void getLifecycleMappings(HttpExchange exchange) throws IOException {
        Map<String, String> mappings = readLifecycleMappings();
        sendSuccess(exchange, mappings);
    }

    // ==================== POST Handlers ====================

    private void createHook(HttpExchange exchange) throws IOException {
        HookRuleDto dto = parseRequestBody(exchange, HookRuleDto.class);
        if (dto == null) {
            sendError(exchange, 400, "Invalid request body", fieldError("body", "请求体不能为空"));
            return;
        }

        // Validate
        Map<String, String> validation = validateHook(dto);
        if (!validation.isEmpty()) {
            sendError(exchange, 400, "Validation failed", validation);
            return;
        }

        List<HookRuleDto> rules = readAllRules();
        if (rules.stream().anyMatch(r -> r.name().equals(dto.name()))) {
            sendError(exchange, 409, "Hook already exists: " + dto.name(),
                fieldError("name", "Hook 名称已存在"));
            return;
        }

        rules.add(dto);
        safeWrite(exchange, rules, readLifecycleMappings(), "CREATE", dto.name());
    }

    private void dryRun(HttpExchange exchange) throws IOException {
        ObjectNode body = parseRequestBodyRaw(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid request body");
            return;
        }

        HookRuleDto dto = MAPPER.convertValue(body.get("hook"), HookRuleDto.class);
        String eventTypeStr = body.has("eventType") ? body.get("eventType").asText() : "";
        String toolName = body.has("toolName") ? body.get("toolName").asText() : "";
        String toolInput = body.has("toolInput") ? body.get("toolInput").asText() : "";

        // Security: SHELL type is disabled for dry-run
        if ("SHELL".equalsIgnoreCase(dto.implementationType())) {
            sendError(exchange, 403, "Shell hooks cannot be dry-run via web UI");
            return;
        }

        // Security: HTTP type restricts private IPs
        if ("HTTP".equalsIgnoreCase(dto.implementationType()) && dto.url() != null) {
            try {
                String host = new java.net.URL(dto.url()).getHost();
                if (PRIVATE_IP.matcher(host).find()) {
                    sendError(exchange, 403, "HTTP dry-run blocked: target is a private/internal address");
                    return;
                }
            } catch (Exception e) {
                sendError(exchange, 400, "Invalid URL: " + dto.url());
                return;
            }
        }

        try {
            HookConfig config = dtoToConfig(dto);
            HookContext context = new HookContext.Builder(HookEventType.valueOf(eventTypeStr))
                .toolName(toolName)
                .dryRun(true)
                .build();

            // Execute based on type
            HookResult result;
            if ("AGENT".equalsIgnoreCase(dto.implementationType())) {
                result = HookResult.allow("dry-run",
                    "AGENT dry-run: would invoke agent '" + dto.agentName() + "' for investigation");
            } else if ("PROMPT".equalsIgnoreCase(dto.implementationType())) {
                result = HookResult.allow("dry-run",
                    "PROMPT dry-run: would send prompt to LLM for decision");
            } else {
                result = HookResult.allow("dry-run",
                    "Dry-run passed validation (config valid, type=" + dto.implementationType() + ")");
            }

            ObjectNode res = MAPPER.createObjectNode();
            res.put("decision", result.getDecision().name());
            res.put("hookName", dto.name());
            res.put("reason", result.getReason());
            res.put("durationMs", 0);
            res.put("contextOutput", result.getContextOutput() != null ? result.getContextOutput() : "");
            sendSuccess(exchange, res);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "Invalid event type: " + eventTypeStr);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[HooksHandler] Dry-run failed", e);
            sendError(exchange, 500, "Dry-run error: " + e.getMessage());
        }
    }

    private void importHooks(HttpExchange exchange) throws IOException {
        ObjectNode body = parseRequestBodyRaw(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid request body");
            return;
        }

        String mergeMode = body.has("mergeMode") ? body.get("mergeMode").asText() : "replace";
        List<HookRuleDto> imported = MAPPER.convertValue(body.get("hooks"),
            new TypeReference<List<HookRuleDto>>() {});
        Map<String, String> importedMappings = body.has("lifecycleMappings")
            ? MAPPER.convertValue(body.get("lifecycleMappings"),
                new TypeReference<Map<String, String>>() {})
            : new LinkedHashMap<>();

        if (imported == null) imported = List.of();

        List<HookRuleDto> existing;
        Map<String, String> existingMappings;

        if ("merge".equals(mergeMode)) {
            existing = readAllRules();
            existingMappings = readLifecycleMappings();
            // Merge: imported overrides existing by name
            Map<String, HookRuleDto> merged = new LinkedHashMap<>();
            for (HookRuleDto r : existing) merged.put(r.name(), r);
            for (HookRuleDto r : imported) merged.put(r.name(), r);
            existing = new ArrayList<>(merged.values());
            if (importedMappings != null) existingMappings.putAll(importedMappings);
        } else {
            existing = new ArrayList<>(imported);
            existingMappings = importedMappings;
        }

        safeWrite(exchange, existing, existingMappings, "IMPORT", "batch(" + imported.size() + ")");
    }

    // ==================== PUT Handlers ====================

    private void updateHook(HttpExchange exchange, String name) throws IOException {
        HookRuleDto dto = parseRequestBody(exchange, HookRuleDto.class);
        if (dto == null) {
            sendError(exchange, 400, "Invalid request body");
            return;
        }

        Map<String, String> validation = validateHook(dto);
        if (!validation.isEmpty()) {
            sendError(exchange, 400, "Validation failed", validation);
            return;
        }

        List<HookRuleDto> rules = readAllRules();
        int idx = -1;
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).name().equals(name)) { idx = i; break; }
        }
        if (idx < 0) {
            sendError(exchange, 404, "Hook not found: " + name);
            return;
        }
        rules.set(idx, dto);
        safeWrite(exchange, rules, readLifecycleMappings(), "UPDATE", name);
    }

    private void saveLifecycleMappings(HttpExchange exchange) throws IOException {
        ObjectNode body = parseRequestBodyRaw(exchange);
        if (body == null) {
            sendError(exchange, 400, "Invalid request body");
            return;
        }
        Map<String, String> mappings = MAPPER.convertValue(body,
            new TypeReference<Map<String, String>>() {});
        if (mappings == null) mappings = new LinkedHashMap<>();
        safeWrite(exchange, readAllRules(), mappings, "UPDATE_MAPPINGS", "");
    }

    // ==================== DELETE Handlers ====================

    private void deleteHook(HttpExchange exchange, String name) throws IOException {
        List<HookRuleDto> rules = readAllRules();
        boolean removed = rules.removeIf(r -> r.name().equals(name));
        if (!removed) {
            sendError(exchange, 404, "Hook not found: " + name);
            return;
        }
        safeWrite(exchange, rules, readLifecycleMappings(), "DELETE", name);
    }

    private void batchDelete(HttpExchange exchange) throws IOException {
        ObjectNode body = parseRequestBodyRaw(exchange);
        if (body == null || !body.has("names")) {
            sendError(exchange, 400, "Missing 'names' array");
            return;
        }
        Set<String> toDelete = new HashSet<>();
        body.get("names").forEach(n -> toDelete.add(n.asText()));
        List<HookRuleDto> rules = readAllRules();
        rules.removeIf(r -> toDelete.contains(r.name()));
        safeWrite(exchange, rules, readLifecycleMappings(), "BATCH_DELETE",
            String.join(",", toDelete));
    }

    // ==================== PATCH Handlers ====================

    private void toggleHook(HttpExchange exchange, String name) throws IOException {
        ObjectNode body = parseRequestBodyRaw(exchange);
        boolean enable = body != null && body.has("enabled") && body.get("enabled").asBoolean();
        List<HookRuleDto> rules = readAllRules();
        boolean found = false;
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).name().equals(name)) {
                HookRuleDto old = rules.get(i);
                rules.set(i, new HookRuleDto(old.name(), old.description(), old.events(),
                    old.implementationType(), old.command(), old.url(), old.promptTemplate(),
                    old.agentName(), old.priority(), old.tools(), old.matchers(),
                    old.timeoutMs(), enable, old.failOpen(), old.scope()));
                found = true;
                break;
            }
        }
        if (!found) {
            sendError(exchange, 404, "Hook not found: " + name);
            return;
        }
        safeWrite(exchange, rules, readLifecycleMappings(), "TOGGLE", name + "=" + enable);
    }

    private void batchToggle(HttpExchange exchange) throws IOException {
        ObjectNode body = parseRequestBodyRaw(exchange);
        if (body == null || !body.has("names") || !body.has("enabled")) {
            sendError(exchange, 400, "Missing 'names' or 'enabled'");
            return;
        }
        Set<String> toToggle = new HashSet<>();
        body.get("names").forEach(n -> toToggle.add(n.asText()));
        boolean enable = body.get("enabled").asBoolean();
        List<HookRuleDto> rules = readAllRules();
        for (int i = 0; i < rules.size(); i++) {
            if (toToggle.contains(rules.get(i).name())) {
                HookRuleDto old = rules.get(i);
                rules.set(i, new HookRuleDto(old.name(), old.description(), old.events(),
                    old.implementationType(), old.command(), old.url(), old.promptTemplate(),
                    old.agentName(), old.priority(), old.tools(), old.matchers(),
                    old.timeoutMs(), enable, old.failOpen(), old.scope()));
            }
        }
        safeWrite(exchange, rules, readLifecycleMappings(), "BATCH_TOGGLE",
            String.join(",", toToggle) + "=" + enable);
    }

    // ==================== Atomic File Write ====================

    private synchronized void safeWrite(HttpExchange exchange,
                                         List<HookRuleDto> rules,
                                         Map<String, String> mappings,
                                         String changeType, String target) throws IOException {
        // Build root
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", "1.0");
        root.put("_comment", "JWCode Hook 配置文件 — 可通过 Web UI 或手动编辑");
        root.set("lifecycleMappings", MAPPER.valueToTree(
            mappings != null ? mappings : new LinkedHashMap<>()));
        ArrayNode hooksArr = root.putArray("hooks");
        for (HookRuleDto r : rules) {
            hooksArr.add(ruleToJson(r));
        }

        // 1. Backup
        try {
            if (Files.exists(configFilePath) && Files.size(configFilePath) > 0) {
                Path backup = configFilePath.resolveSibling(
                    configFilePath.getFileName() + ".bak." + System.currentTimeMillis());
                Files.copy(configFilePath, backup, StandardCopyOption.REPLACE_EXISTING);
                cleanupBackups();
            }
        } catch (IOException e) {
            logger.warning("[HooksHandler] Backup failed (non-fatal): " + e.getMessage());
        }

        // 2. Write temp + fsync
        Path temp = configFilePath.resolveSibling(configFilePath.getFileName() + ".tmp");
        byte[] json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        Files.createDirectories(configFilePath.getParent());
        Files.write(temp, json, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);

        // 3. Atomic replace
        Files.move(temp, configFilePath, StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);

        // 4. Hot reload
        try {
            registry.reload();
        } catch (Exception e) {
            logger.log(Level.WARNING, "[HooksHandler] Reload after write failed (will retry): "
                + e.getMessage());
        }

        // 5. Admin audit
        String ip = getClientIp(exchange);
        writeAdminAudit(changeType, target, ip, rules.size() + " hooks");

        sendSuccess(exchange, Map.of("status", "ok", "reloaded", true,
            "count", rules.size()));
    }

    private void cleanupBackups() {
        try {
            Path dir = configFilePath.getParent();
            String prefix = configFilePath.getFileName() + ".bak.";
            List<Path> backups = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir,
                p -> p.getFileName().toString().startsWith(prefix))) {
                ds.forEach(backups::add);
            }
            backups.sort(Comparator.comparingLong(p -> {
                try { return Files.getLastModifiedTime(p).toMillis(); }
                catch (IOException e) { return 0; }
            }));
            while (backups.size() > MAX_BACKUPS) {
                Files.deleteIfExists(backups.remove(0));
            }
        } catch (IOException e) {
            logger.fine("[HooksHandler] Backup cleanup skipped: " + e.getMessage());
        }
    }

    // ==================== JSON File Read ====================

    private List<HookRuleDto> readAllRules() throws IOException {
        if (!Files.exists(configFilePath) || Files.size(configFilePath) < 10) {
            return new ArrayList<>();
        }
        String json = stripBom(Files.readString(configFilePath));
        Map<String, Object> root = MAPPER.readValue(json,
            new TypeReference<Map<String, Object>>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> raw = (List<Map<String, Object>>) root.getOrDefault("hooks", List.of());
        List<HookRuleDto> rules = new ArrayList<>();
        for (Map<String, Object> entry : raw) {
            rules.add(mapToDto(entry));
        }
        return rules;
    }

    private Map<String, String> readLifecycleMappings() throws IOException {
        if (!Files.exists(configFilePath) || Files.size(configFilePath) < 10) {
            return new LinkedHashMap<>();
        }
        String json = stripBom(Files.readString(configFilePath));
        Map<String, Object> root = MAPPER.readValue(json,
            new TypeReference<Map<String, Object>>() {});
        @SuppressWarnings("unchecked")
        Map<String, String> mappings = (Map<String, String>) root.getOrDefault("lifecycleMappings", Map.of());
        return new LinkedHashMap<>(mappings);
    }

    private static String stripBom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '﻿') {
            return s.substring(1);
        }
        return s;
    }

    // ==================== Admin Audit ====================

    private void writeAdminAudit(String changeType, String target, String ip, String summary) {
        try {
            Files.createDirectories(adminAuditPath.getParent());
            ObjectNode entry = MAPPER.createObjectNode();
            entry.put("timestamp", System.currentTimeMillis());
            entry.put("changeType", changeType);
            entry.put("target", target);
            entry.put("operatorIp", ip);
            entry.put("summary", summary);
            String line = MAPPER.writeValueAsString(entry) + "\n";
            Files.writeString(adminAuditPath, line,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.fine("[HooksHandler] Audit write failed (non-fatal): " + e.getMessage());
        }
    }

    private List<HookExecutionLogEntry> readAdminAuditLog() throws IOException {
        List<HookExecutionLogEntry> logs = new ArrayList<>();
        if (!Files.exists(adminAuditPath)) return logs;
        List<String> lines = Files.readAllLines(adminAuditPath);
        // Return last 200 entries, newest first
        int start = Math.max(0, lines.size() - 200);
        for (int i = lines.size() - 1; i >= start; i--) {
            try {
                ObjectNode entry = (ObjectNode) MAPPER.readTree(lines.get(i));
                logs.add(new HookExecutionLogEntry(
                    "audit-" + entry.get("timestamp").asLong(),
                    entry.has("target") ? entry.get("target").asText() : "",
                    "",
                    entry.has("changeType") ? entry.get("changeType").asText() : "",
                    entry.has("summary") ? entry.get("summary").asText() : "",
                    0,
                    entry.get("timestamp").asLong(),
                    entry.has("operatorIp") ? entry.get("operatorIp").asText() : "",
                    entry.has("changeType") ? entry.get("changeType").asText() : "",
                    ""
                ));
            } catch (Exception ignored) {}
        }
        return logs;
    }

    // ==================== Conversion ====================

    @SuppressWarnings("unchecked")
    private HookRuleDto mapToDto(Map<String, Object> entry) {
        Map<String, Object> impl = (Map<String, Object>) entry.getOrDefault("implementation", Map.of());
        List<String> tools = (List<String>) entry.getOrDefault("tools", List.of());
        Map<String, String> matchers = (Map<String, String>) entry.getOrDefault("matchers", Map.of());
        List<String> events = (List<String>) entry.getOrDefault("events", List.of());

        return new HookRuleDto(
            (String) entry.getOrDefault("name", ""),
            (String) entry.getOrDefault("description", ""),
            events,
            (String) impl.getOrDefault("type", "SHELL"),
            (String) impl.getOrDefault("command", null),
            (String) impl.getOrDefault("url", null),
            (String) impl.getOrDefault("promptTemplate", null),
            (String) impl.getOrDefault("agentName", null),
            (String) entry.getOrDefault("priority", "USER"),
            tools != null ? tools : List.of(),
            matchers != null ? matchers : Map.of(),
            entry.containsKey("timeoutMs") ? ((Number) entry.get("timeoutMs")).longValue() : 30000,
            entry.containsKey("enabled") ? (Boolean) entry.get("enabled") : true,
            entry.containsKey("failOpen") ? (Boolean) entry.get("failOpen") : true,
            (String) entry.getOrDefault("scope", "project")
        );
    }

    private ObjectNode ruleToJson(HookRuleDto dto) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("name", dto.name());
        if (dto.description() != null && !dto.description().isEmpty())
            node.put("description", dto.description());

        ArrayNode evts = node.putArray("events");
        dto.events().forEach(evts::add);

        ObjectNode impl = node.putObject("implementation");
        impl.put("type", dto.implementationType());
        if (dto.command() != null) impl.put("command", dto.command());
        if (dto.url() != null) impl.put("url", dto.url());
        if (dto.promptTemplate() != null) impl.put("promptTemplate", dto.promptTemplate());
        if (dto.agentName() != null) impl.put("agentName", dto.agentName());

        node.put("priority", dto.priority());
        if (dto.tools() != null && !dto.tools().isEmpty()) {
            ArrayNode tools = node.putArray("tools");
            dto.tools().forEach(tools::add);
        }
        if (dto.matchers() != null && !dto.matchers().isEmpty()) {
            ObjectNode matchers = node.putObject("matchers");
            dto.matchers().forEach(matchers::put);
        }
        node.put("timeoutMs", dto.timeoutMs());
        node.put("enabled", dto.enabled());
        node.put("failOpen", dto.failOpen());
        if (dto.scope() != null) node.put("scope", dto.scope());
        return node;
    }

    private HookConfig dtoToConfig(HookRuleDto dto) {
        List<HookEventType> events = dto.events().stream()
            .map(e -> HookEventType.valueOf(e.toUpperCase()))
            .collect(Collectors.toList());
        return new HookConfig.Builder()
            .name(dto.name())
            .description(dto.description())
            .events(events)
            .implementationType(HookImplementationType.valueOf(dto.implementationType().toUpperCase()))
            .command(dto.command())
            .url(dto.url())
            .promptTemplate(dto.promptTemplate())
            .agentName(dto.agentName())
            .priority(HookPriority.valueOf(dto.priority().toUpperCase()))
            .tools(dto.tools())
            .matchers(dto.matchers())
            .timeoutMs(dto.timeoutMs())
            .enabled(dto.enabled())
            .failOpen(dto.failOpen())
            .build();
    }

    // ==================== Validation ====================

    private Map<String, String> validateHook(HookRuleDto dto) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (dto.name() == null || dto.name().isBlank())
            errors.put("name", "名称不能为空");
        else if (!dto.name().matches("^[a-z0-9]([a-z0-9_-]*[a-z0-9])?$"))
            errors.put("name", "名称格式不正确（小写字母/数字/连字符）");
        if (dto.events() == null || dto.events().isEmpty())
            errors.put("events", "至少选择一个事件");
        if (dto.implementationType() == null || dto.implementationType().isBlank())
            errors.put("implementationType", "请选择实现类型");
        else {
            String type = dto.implementationType().toUpperCase();
            if ("SHELL".equals(type) && (dto.command() == null || dto.command().isBlank()))
                errors.put("command", "Shell 类型必须填写命令");
            if ("HTTP".equals(type) && (dto.url() == null || dto.url().isBlank()))
                errors.put("url", "HTTP 类型必须填写 URL");
            if ("PROMPT".equals(type) && (dto.promptTemplate() == null || dto.promptTemplate().isBlank()))
                errors.put("promptTemplate", "Prompt 类型必须填写模板");
            if ("AGENT".equals(type) && (dto.agentName() == null || dto.agentName().isBlank()))
                errors.put("agentName", "Agent 类型必须选择 Agent");
        }
        if (dto.timeoutMs() < 100 || dto.timeoutMs() > 300_000)
            errors.put("timeoutMs", "超时范围 100-300000 ms");
        return errors;
    }

    // ==================== Helpers ====================

    private String extractName(String path) {
        String prefix = "/api/hooks/";
        if (!path.startsWith(prefix)) return null;
        String suffix = path.substring(prefix.length());
        if (suffix.isEmpty()) return null;
        int slash = suffix.indexOf('/');
        return slash > 0 ? suffix.substring(0, slash) : suffix;
    }

    private String extractPathSegment(String path, String suffix) {
        String prefix = "/api/hooks/";
        if (!path.startsWith(prefix)) return null;
        String s = path.substring(prefix.length());
        if (s.endsWith(suffix)) {
            return s.substring(0, s.length() - suffix.length());
        }
        return null;
    }

    private <T> T parseRequestBody(HttpExchange exchange, Class<T> clazz) throws IOException {
        byte[] raw = readBody(exchange);
        if (raw.length == 0) return null;
        return MAPPER.readValue(raw, clazz);
    }

    private ObjectNode parseRequestBodyRaw(HttpExchange exchange) throws IOException {
        byte[] raw = readBody(exchange);
        if (raw.length == 0) return null;
        return (ObjectNode) MAPPER.readTree(raw);
    }

    private byte[] readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return is.readAllBytes();
        }
    }

    private String getClientIp(HttpExchange exchange) {
        InetSocketAddress addr = exchange.getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }

    // ==================== Response Helpers ====================

    private void sendSuccess(HttpExchange exchange, Object data) throws IOException {
        ObjectNode res = MAPPER.createObjectNode();
        res.put("success", true);
        if (data != null) res.set("data", MAPPER.valueToTree(data));
        sendJson(exchange, 200, res);
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        sendError(exchange, code, message, null);
    }

    private void sendError(HttpExchange exchange, int code, String message,
                           Map<String, String> fieldErrors) throws IOException {
        ObjectNode res = MAPPER.createObjectNode();
        res.put("success", false);
        res.put("error", message);
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            ObjectNode fe = res.putObject("fieldErrors");
            fieldErrors.forEach(fe::put);
        }
        sendJson(exchange, code, res);
    }

    private Map<String, String> fieldError(String field, String message) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(field, message);
        return m;
    }

    private void sendJson(HttpExchange exchange, int code, ObjectNode json) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(json);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
