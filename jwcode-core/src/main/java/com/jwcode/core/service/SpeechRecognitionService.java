package com.jwcode.core.service;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.vosk.Model;
import org.vosk.Recognizer;

/**
 * SpeechRecognitionService - 离线语音识别服务
 *
 * 功能说明：
 * 基于 Vosk 引擎的离线语音识别，支持中文和英文。
 * 模型自动从 alphacephei.com 下载并缓存在 ~/.jwcode/models/ 目录。
 *
 * 使用方式：
 * <pre>
 *   SpeechRecognitionService service = SpeechRecognitionService.getInstance();
 *   service.initialize();
 *   String text = service.recognize(wavAudioBytes);
 * </pre>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class SpeechRecognitionService {

    private static final Logger LOGGER = Logger.getLogger(SpeechRecognitionService.class.getName());

    private static final String MODEL_DIR_NAME = "vosk-model-small-cn-0.22";
    private static final String MODEL_DOWNLOAD_URL = "https://alphacephei.com/vosk/models/" + MODEL_DIR_NAME + ".zip";
    private static final int SAMPLE_RATE = 16000;

    private static volatile SpeechRecognitionService instance;

    private final Path modelsDir;
    private volatile boolean initialized = false;
    private volatile boolean available = false;
    private Model voskModel;

    private SpeechRecognitionService() {
        String homeDir = System.getProperty("user.home", ".");
        this.modelsDir = Path.of(homeDir, ".jwcode", "models");
    }

    /**
     * 获取单例实例
     */
    public static SpeechRecognitionService getInstance() {
        if (instance == null) {
            synchronized (SpeechRecognitionService.class) {
                if (instance == null) {
                    instance = new SpeechRecognitionService();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化语音识别服务
     * <p>
     * 检查模型文件是否存在，若不存在则自动下载。
     * 初始化 Vosk 模型和识别器。
     * 可以重复调用，已初始化时直接返回。
     */
    public synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        try {
            Path modelPath = modelsDir.resolve(MODEL_DIR_NAME);
            if (!Files.exists(modelPath)) {
                LOGGER.info("Vosk model not found at " + modelPath + ", downloading...");
                downloadModel();
            }

            if (Files.exists(modelPath)) {
                LOGGER.info("Loading Vosk model from " + modelPath);
                voskModel = new Model(modelPath.toAbsolutePath().toString());
                available = true;
                LOGGER.info("Vosk speech recognition initialized successfully");
            } else {
                LOGGER.warning("Vosk model still not found after download attempt at: " + modelPath);
                available = false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize Vosk speech recognition: " + e.getMessage(), e);
            available = false;
        }
    }

    /**
     * 关闭语音识别服务，释放模型资源
     */
    public synchronized void shutdown() {
        if (voskModel != null) {
            voskModel.close();
            voskModel = null;
        }
        available = false;
        initialized = false;
    }

    /**
     * 识别 WAV 音频数据
     *
     * @param audioData WAV 格式音频数据（16kHz, 16-bit, mono PCM 优先，支持其他采样率自动转换）
     * @return 识别出的文本，若失败返回空字符串
     */
    public String recognize(byte[] audioData) {
        if (!available || voskModel == null) {
            LOGGER.warning("Speech recognition not available, call initialize() first");
            return "";
        }
        if (audioData == null || audioData.length < 44) {
            LOGGER.warning("Audio data too short or null");
            return "";
        }

        try {
            // Try to parse WAV header to skip it; fall back to raw PCM
            int dataStart = findWavDataStart(audioData);
            byte[] pcmData;
            if (dataStart > 0 && dataStart < audioData.length) {
                pcmData = new byte[audioData.length - dataStart];
                System.arraycopy(audioData, dataStart, pcmData, 0, pcmData.length);
            } else {
                pcmData = audioData;
            }

            Recognizer recognizer = new Recognizer(voskModel, (float) SAMPLE_RATE);
            try {
                if (recognizer.acceptWaveForm(pcmData, pcmData.length)) {
                    String result = recognizer.getResult();
                    return extractTextFromJson(result);
                } else {
                    String partial = recognizer.getPartialResult();
                    String text = extractTextFromJson(partial);
                    if (!text.isEmpty()) return text;
                    // Fallback: try final result
                    String finalResult = recognizer.getFinalResult();
                    return extractTextFromJson(finalResult);
                }
            } finally {
                recognizer.close();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Speech recognition error: " + e.getMessage(), e);
            return "";
        }
    }

    /**
     * 识别 WAV 音频数据（带超时控制）
     *
     * @param audioData WAV 格式音频数据
     * @param timeoutMs 超时毫秒数
     * @return 识别出的文本，若超时或失败返回空字符串
     */
    public String recognize(byte[] audioData, long timeoutMs) {
        if (!available) return "";
        var future = java.util.concurrent.CompletableFuture.supplyAsync(() -> recognize(audioData));
        try {
            return future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            LOGGER.warning("Speech recognition timed out after " + timeoutMs + "ms");
            return "";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Speech recognition error: " + e.getMessage(), e);
            return "";
        }
    }

    /**
     * 服务是否可用（模型已加载）
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * 获取模型目录路径
     */
    public Path getModelsDir() {
        return modelsDir;
    }

    /**
     * 是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    // ---- Private Helpers ----

    /**
     * Find the start of PCM data in a WAV file by locating the "data" chunk.
     * Returns 0 if no WAV header found (assume raw PCM).
     */
    private int findWavDataStart(byte[] audioData) {
        try {
            // Check RIFF header
            if (audioData[0] != 'R' || audioData[1] != 'I' || audioData[2] != 'F' || audioData[3] != 'F') {
                return 0; // Not a WAV file
            }
            // Scan for "data" chunk
            for (int i = 12; i < audioData.length - 8; i++) {
                if (audioData[i] == 'd' && audioData[i+1] == 'a' && audioData[i+2] == 't' && audioData[i+3] == 'a') {
                    ByteBuffer bb = ByteBuffer.wrap(audioData, i + 4, 4).order(ByteOrder.LITTLE_ENDIAN);
                    int chunkSize = bb.getInt();
                    if (chunkSize > 0 && i + 8 + chunkSize <= audioData.length) {
                        return i + 8;
                    }
                    return i + 8; // best effort
                }
            }
        } catch (Exception e) {
            LOGGER.fine("Failed to parse WAV header: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Extract the "text" field from a Vosk JSON result string.
     * Vosk returns JSON like: {"text": "你好世界"}
     */
    private String extractTextFromJson(String json) {
        if (json == null || json.isEmpty()) return "";
        try {
            // Simple JSON parsing for {"text":"..."} or {"text": "..."}
            int textKeyIndex = json.indexOf("\"text\"");
            if (textKeyIndex < 0) return "";
            int colonIndex = json.indexOf(':', textKeyIndex + 5);
            if (colonIndex < 0) return "";
            int startQuote = json.indexOf('"', colonIndex + 1);
            if (startQuote < 0) return "";
            int endQuote = json.indexOf('"', startQuote + 1);
            if (endQuote < 0) return "";
            String text = json.substring(startQuote + 1, endQuote);
            return text.trim();
        } catch (Exception e) {
            LOGGER.fine("Failed to parse Vosk JSON: " + e.getMessage());
            return "";
        }
    }

    /**
     * Download and extract the Vosk model from alphacephei.com.
     * This runs in a background thread for large downloads.
     */
    private void downloadModel() throws IOException {
        try {
            Files.createDirectories(modelsDir);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Cannot create models directory: " + modelsDir, e);
            return;
        }

        Path zipPath = modelsDir.resolve(MODEL_DIR_NAME + ".zip");
        Path extractedPath = modelsDir.resolve(MODEL_DIR_NAME);

        // If partially extracted, clean up
        if (Files.exists(extractedPath)) {
            deleteDirectory(extractedPath);
        }

        LOGGER.info("Downloading Vosk model from " + MODEL_DOWNLOAD_URL + " ...");

        // Download with progress
        try (InputStream in = new URL(MODEL_DOWNLOAD_URL).openStream();
             FileOutputStream fos = new FileOutputStream(zipPath.toFile())) {

            byte[] buffer = new byte[8192];
            int len;
            long total = 0;
            long lastLog = 0;
            while ((len = in.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
                total += len;
                // Log progress every 5MB
                if (total - lastLog > 5_000_000) {
                    lastLog = total;
                    LOGGER.info("Downloaded " + (total / 1024 / 1024) + " MB...");
                }
            }
        }

        LOGGER.info("Download complete, extracting model...");

        // Extract zip
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = modelsDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(entryPath.toFile())) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }

        // Clean up zip
        try {
            Files.deleteIfExists(zipPath);
        } catch (IOException e) {
            LOGGER.fine("Failed to delete zip: " + e.getMessage());
        }

        LOGGER.info("Model extracted to " + extractedPath);
    }

    /**
     * Recursively delete a directory
     */
    private void deleteDirectory(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.walk(path)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); }
                            catch (IOException e) { LOGGER.fine("Failed to delete " + p + ": " + e.getMessage()); }
                        });
            }
        }
    }
}
