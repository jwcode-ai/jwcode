package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.service.SpeechRecognitionService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SpeechHandler - 语音识别 REST API 处理器
 *
 * 提供离线语音识别功能，接收前端录制的音频数据并返回识别文本。
 *
 * API:
 *   POST /api/speech/recognize
 *     Content-Type: audio/wav (或 application/octet-stream)
 *     Body: WAV 格式音频数据 (16kHz, 16-bit, mono PCM)
 *     Response: {"text": "识别结果文本"}
 *
 *   GET /api/speech/status
 *     Response: {"available": true/false, "initialized": true/false}
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class SpeechHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(SpeechHandler.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_AUDIO_SIZE = 10 * 1024 * 1024; // 10MB max

    private final SpeechRecognitionService speechService;

    public SpeechHandler() {
        this.speechService = SpeechRecognitionService.getInstance();
        // Initialize in background to avoid blocking server startup
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Brief delay to let server settle
                speechService.initialize();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to initialize speech recognition", e);
            }
        }, "speech-init").start();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // CORS headers
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        try {
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/status")) {
                handleStatus(exchange);
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/recognize")) {
                handleRecognize(exchange);
            } else {
                sendJson(exchange, 404, createError("Endpoint not found: " + method + " " + path));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Speech handler error", e);
            sendJson(exchange, 500, createError("Internal error: " + e.getMessage()));
        }
    }

    /**
     * GET /api/speech/status — return service availability
     */
    private void handleStatus(HttpExchange exchange) throws IOException {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("available", speechService.isAvailable());
        node.put("initialized", speechService.isInitialized());
        sendJson(exchange, 200, node);
    }

    /**
     * POST /api/speech/recognize — transcribe audio to text
     */
    private void handleRecognize(HttpExchange exchange) throws IOException {
        if (!speechService.isAvailable()) {
            boolean started = speechService.isInitialized();
            ObjectNode error = createError(
                    "Speech recognition is " + (started ? "still initializing" : "not available") +
                    ". Please ensure Vosk model is downloaded and try again.");
            if (!started) {
                // Try to initialize now
                speechService.initialize();
                error.put("retry", true);
            }
            sendJson(exchange, 503, error);
            return;
        }

        // Read audio data from request body
        int contentLength = exchange.getRequestHeaders().getFirst("Content-Length") != null
                ? Integer.parseInt(exchange.getRequestHeaders().getFirst("Content-Length"))
                : -1;

        if (contentLength > MAX_AUDIO_SIZE) {
            sendJson(exchange, 413, createError("Audio file too large (max " + (MAX_AUDIO_SIZE / 1024 / 1024) + "MB)"));
            return;
        }

        byte[] audioData;
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            int total = 0;
            while ((len = is.read(buffer)) > 0) {
                total += len;
                if (total > MAX_AUDIO_SIZE) {
                    sendJson(exchange, 413, createError("Audio file too large"));
                    return;
                }
                baos.write(buffer, 0, len);
            }
            audioData = baos.toByteArray();
        }

        if (audioData.length < 100) {
            sendJson(exchange, 400, createError("Audio data too short"));
            return;
        }

        LOGGER.info("Processing speech recognition request: " + audioData.length + " bytes");

        // Transcribe
        String text = speechService.recognize(audioData);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("text", text);
        sendJson(exchange, 200, response);
    }

    // ---- Response helpers ----

    private void sendJson(HttpExchange exchange, int status, ObjectNode node) throws IOException {
        byte[] bytes = node.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private ObjectNode createError(String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("error", message);
        return node;
    }
}
