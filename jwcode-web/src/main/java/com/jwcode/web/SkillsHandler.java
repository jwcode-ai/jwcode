package com.jwcode.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.skill.Skill;
import com.jwcode.core.skill.SkillDefinition;
import com.jwcode.core.skill.SkillMarkdownParser;
import com.jwcode.core.skill.SkillRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SkillsHandler implements HttpHandler {

    private static final Path USER_SKILLS_DIR = Path.of(System.getProperty("user.home"), ".jwcode", "skills");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillRegistry skillRegistry = new SkillRegistry();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        try {
            if ("GET".equalsIgnoreCase(method)) {
                if (path.matches("/api/skills/[^/]+")) {
                    getSkill(exchange, lastSegment(path));
                } else {
                    listSkills(exchange);
                }
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                if ("/api/skills/import".equals(path)) {
                    importSkill(exchange);
                } else if ("/api/skills".equals(path)) {
                    createSkill(exchange);
                } else if (path.matches("/api/skills/[^/]+/toggle")) {
                    sendJson(exchange, 200, okMessage("技能状态切换已接收"));
                } else {
                    sendJson(exchange, 404, error("不支持的端点"));
                }
                return;
            }

            if ("PUT".equalsIgnoreCase(method) && path.matches("/api/skills/[^/]+")) {
                updateSkill(exchange, lastSegment(path));
                return;
            }

            sendJson(exchange, 405, error("Method not allowed"));
        } catch (Exception e) {
            sendJson(exchange, 500, error("技能操作失败: " + e.getMessage()));
        }
    }

    private void listSkills(HttpExchange exchange) throws IOException {
        ArrayNode data = objectMapper.createArrayNode();
        for (Skill skill : skillRegistry.getAll()) {
            data.add(skillJson(skill));
        }
        sendJson(exchange, 200, ok(data));
    }

    private void getSkill(HttpExchange exchange, String skillId) throws IOException {
        Optional<Skill> skill = skillRegistry.get(skillId);
        if (skill.isEmpty()) {
            sendJson(exchange, 404, error("技能不存在: " + skillId));
            return;
        }
        sendJson(exchange, 200, ok(skillJson(skill.get())));
    }

    private void createSkill(HttpExchange exchange) throws IOException {
        SkillForm form = readSkillForm(exchange);
        if (isBlank(form.id)) {
            sendJson(exchange, 400, error("技能 ID 不能为空"));
            return;
        }

        Path target = skillPath(form.id);
        if (Files.exists(target)) {
            sendJson(exchange, 409, error("技能已存在: " + form.id));
            return;
        }

        writeSkill(target, form);
        reloadSkill(target, form.id);
        sendJson(exchange, 200, ok(skillJson(skillRegistry.get(form.id).orElseThrow())));
    }

    private void updateSkill(HttpExchange exchange, String currentId) throws IOException {
        SkillForm form = readSkillForm(exchange);
        String nextId = isBlank(form.id) ? currentId : form.id.trim();
        Path currentPath = skillPath(currentId);
        if (!Files.exists(currentPath)) {
            sendJson(exchange, 404, error("技能不存在: " + currentId));
            return;
        }

        Path nextPath = skillPath(nextId);
        if (!currentId.equals(nextId) && Files.exists(nextPath)) {
            sendJson(exchange, 409, error("目标技能 ID 已存在: " + nextId));
            return;
        }

        skillRegistry.unregister(currentId);
        Files.deleteIfExists(currentPath);
        writeSkill(nextPath, form);
        reloadSkill(nextPath, nextId);
        sendJson(exchange, 200, ok(skillJson(skillRegistry.get(nextId).orElseThrow())));
    }

    private void importSkill(HttpExchange exchange) throws IOException {
        ImportForm form = readImportForm(exchange);
        if (isBlank(form.content)) {
            sendJson(exchange, 400, error("导入内容不能为空"));
            return;
        }

        String sourceName = isBlank(form.fileName) ? "imported.skill.md" : form.fileName;
        SkillDefinition def = SkillMarkdownParser.parseContent(form.content, sourceName, Skill.Provenance.USER_MANUAL);
        if (def == null || isBlank(def.id())) {
            sendJson(exchange, 400, error("无法解析技能文件"));
            return;
        }

        Path target = skillPath(def.id());
        Files.createDirectories(USER_SKILLS_DIR);
        Files.writeString(target, form.content, StandardCharsets.UTF_8);
        reloadSkill(target, def.id());
        sendJson(exchange, 200, ok(skillJson(skillRegistry.get(def.id()).orElseThrow())));
    }

    private void reloadSkill(Path file, String skillId) throws IOException {
        skillRegistry.unregister(skillId);
        SkillDefinition def = SkillMarkdownParser.parse(file, Skill.Provenance.USER_MANUAL);
        if (def == null) {
            throw new IOException("无法解析技能文件: " + file);
        }
        Skill skill = def.toSkill();
        skill.setSource(file.toAbsolutePath().toString());
        skill.setStatus(Skill.LoadStatus.LOADED);
        skill.setLoadedAt(System.currentTimeMillis());
        skill.setCategory(inferCategory(skill, file));
        skillRegistry.register(skill);
    }

    private void writeSkill(Path target, SkillForm form) throws IOException {
        Files.createDirectories(USER_SKILLS_DIR);
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("id: ").append(form.id.trim()).append('\n');
        sb.append("name: ").append(valueOr(form.name, form.id)).append('\n');
        sb.append("description: ").append(valueOr(form.description, "")).append('\n');
        if (!isBlank(form.category)) sb.append("category: ").append(form.category.trim()).append('\n');
        if (!isBlank(form.trigger)) sb.append("trigger: ").append(form.trigger.trim()).append('\n');
        appendList(sb, "tags", form.tags);
        appendList(sb, "tools", form.requiredTools);
        if (!isBlank(form.injection)) sb.append("injection: ").append(form.injection.trim()).append('\n');
        sb.append("---\n\n");
        sb.append(valueOr(form.systemPrompt, "# " + valueOr(form.name, form.id) + "\n\nPlease describe this skill behavior.\n"));
        Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);
    }

    private Skill.Category inferCategory(Skill skill, Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.contains("code") || hasTag(skill, "code") || hasTag(skill, "refactor")) return Skill.Category.CODE;
        if (name.contains("test") || hasTag(skill, "test")) return Skill.Category.TEST;
        if (name.contains("doc") || hasTag(skill, "doc") || hasTag(skill, "readme")) return Skill.Category.DOCUMENT;
        if (name.contains("deploy") || hasTag(skill, "devops")) return Skill.Category.DEVOPS;
        if (hasTag(skill, "analysis") || hasTag(skill, "review") || hasTag(skill, "security")) return Skill.Category.ANALYSIS;
        return Skill.Category.CUSTOM;
    }

    private boolean hasTag(Skill skill, String needle) {
        if (skill.getTags() == null) return false;
        for (String tag : skill.getTags()) {
            if (tag != null && tag.toLowerCase().contains(needle)) return true;
        }
        return false;
    }

    private SkillForm readSkillForm(HttpExchange exchange) throws IOException {
        JsonNode node = objectMapper.readTree(readBody(exchange));
        SkillForm form = new SkillForm();
        form.id = text(node, "id");
        form.name = text(node, "name");
        form.description = text(node, "description");
        form.category = text(node, "category");
        form.trigger = text(node, "trigger");
        form.systemPrompt = text(node, "systemPrompt");
        form.injection = text(node, "injection");
        form.tags = list(node, "tags");
        form.requiredTools = list(node, "requiredTools");
        return form;
    }

    private ImportForm readImportForm(HttpExchange exchange) throws IOException {
        JsonNode node = objectMapper.readTree(readBody(exchange));
        ImportForm form = new ImportForm();
        form.fileName = text(node, "fileName");
        form.content = text(node, "content");
        return form;
    }

    private byte[] readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return is.readAllBytes();
        }
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    private List<String> list(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (item != null && !item.asText().isBlank()) {
                result.add(item.asText().trim());
            }
        }
        return result;
    }

    private void appendList(StringBuilder sb, String key, List<String> values) {
        if (values == null || values.isEmpty()) return;
        sb.append(key).append(": [");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(values.get(i));
        }
        sb.append("]\n");
    }

    private ObjectNode ok(JsonNode data) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("success", true);
        node.set("data", data);
        return node;
    }

    private ObjectNode okMessage(String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("success", true);
        node.put("message", message);
        return node;
    }

    private ObjectNode error(String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("success", false);
        node.put("error", message);
        return node;
    }

    private ObjectNode skillJson(Skill skill) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", skill.getId());
        node.put("name", skill.getName());
        node.put("description", skill.getDescription());
        node.put("category", skill.getCategory() != null ? skill.getCategory().name().toLowerCase() : "custom");
        node.put("enabled", true);
        node.put("icon", categoryIcon(skill.getCategory()));
        node.put("source", skill.getSource() != null ? skill.getSource() : "");
        node.put("editable", skill.getSource() != null &&
            Path.of(skill.getSource()).toAbsolutePath().normalize().startsWith(USER_SKILLS_DIR.toAbsolutePath().normalize()));
        node.put("trigger", skill.getTriggerPattern() != null ? skill.getTriggerPattern() : "");
        node.put("injection", skill.getInjectionStrategy() != null ? skill.getInjectionStrategy().name().toLowerCase() : "lazy");
        node.put("systemPrompt", skill.getSystemPrompt() != null ? skill.getSystemPrompt() : "");

        ArrayNode tags = node.putArray("tags");
        if (skill.getTags() != null) skill.getTags().forEach(tags::add);
        ArrayNode tools = node.putArray("requiredTools");
        if (skill.getRequiredTools() != null) skill.getRequiredTools().forEach(tools::add);
        return node;
    }

    private void sendJson(HttpExchange exchange, int status, ObjectNode body) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String categoryIcon(Skill.Category category) {
        if (category == null) return "⭐";
        return switch (category) {
            case CODE -> "💻";
            case ANALYSIS -> "🔍";
            case DOCUMENT -> "📄";
            case TEST -> "🧪";
            case DEVOPS -> "🚀";
            default -> "⭐";
        };
    }

    private Path skillPath(String id) {
        return USER_SKILLS_DIR.resolve(id + ".skill.md");
    }

    private String lastSegment(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String valueOr(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static class SkillForm {
        String id;
        String name;
        String description;
        String category;
        String trigger;
        String systemPrompt;
        String injection;
        List<String> tags = List.of();
        List<String> requiredTools = List.of();
    }

    private static class ImportForm {
        String fileName;
        String content;
    }
}
