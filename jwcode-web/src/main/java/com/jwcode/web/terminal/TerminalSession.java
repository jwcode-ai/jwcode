package com.jwcode.web.terminal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class TerminalSession extends WebSocketServer {
    private static final Logger logger = Logger.getLogger(TerminalSession.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TRANSCRIPT_CHARS = 50_000;
    private static final int INITIAL_COLS = 80;
    private static final int INITIAL_ROWS = 24;

    private final int port;
    private final long startTime;
    private final String workspaceDir;
    private final PtyProcess process;
    private final OutputStream stdin;
    private final Thread outputPump;
    private final Set<WebSocket> clients = ConcurrentHashMap.newKeySet();
    private final Object transcriptLock = new Object();
    private final StringBuilder transcript = new StringBuilder();
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public TerminalSession(String workspaceDir, int port) throws IOException {
        super(new InetSocketAddress("127.0.0.1", port));
        this.port = port;
        this.workspaceDir = Path.of(workspaceDir).toAbsolutePath().normalize().toString();
        this.startTime = System.currentTimeMillis();

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");
        env.put("LANG", "en_US.UTF-8");

        try {
            this.process = new PtyProcessBuilder()
                .setCommand(createShellCommand())
                .setEnvironment(env)
                .setDirectory(this.workspaceDir)
                .setRedirectErrorStream(true)
                .setInitialColumns(INITIAL_COLS)
                .setInitialRows(INITIAL_ROWS)
                .start();
        } catch (IOException e) {
            throw new IOException("Failed to start PTY: " + e.getMessage(), e);
        }
        this.stdin = process.getOutputStream();

        this.outputPump = new Thread(this::pumpOutput, "terminal-output-pump-" + port);
        this.outputPump.setDaemon(true);
        this.outputPump.start();

        try {
            start();
        } catch (Exception e) {
            kill();
            throw new IOException("Failed to start terminal websocket server: " + e.getMessage(), e);
        }

        logger.info("Local terminal session started on port " + port + " for workspace " + this.workspaceDir);
        Runtime.getRuntime().addShutdownHook(new Thread(this::kill));
    }

    private String[] createShellCommand() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            if (findExecutable("powershell.exe") != null || findExecutable("pwsh.exe") != null) {
                String shell = findExecutable("pwsh.exe") != null ? "pwsh.exe" : "powershell.exe";
                return new String[]{shell, "-NoLogo", "-NoExit", "-ExecutionPolicy", "Bypass"};
            }
            return new String[]{"cmd.exe"};
        }
        String bash = findExecutable("/bin/bash");
        if (bash != null) {
            return new String[]{bash};
        }
        return new String[]{"sh"};
    }

    private String findExecutable(String name) {
        if (name.contains(File.separator)) {
            File f = new File(name);
            return f.exists() && f.canExecute() ? f.getAbsolutePath() : null;
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) return null;
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File f = new File(dir, name);
            if (f.exists() && f.canExecute()) return f.getAbsolutePath();
        }
        return null;
    }

    private void pumpOutput() {
        try (InputStream is = process.getInputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while (!stopped.get() && (read = is.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                String chunk = new String(buffer, 0, read, Charset.defaultCharset());
                appendTranscript(chunk);
                sendToClients(chunk);
            }
        } catch (IOException e) {
            if (!stopped.get()) {
                logger.warning("Terminal output pump stopped: " + e.getMessage());
            }
        } finally {
            if (!stopped.get()) {
                sendToClients("\r\n[terminal closed]\r\n");
                closeAllClients();
            }
        }
    }

    private void appendTranscript(String chunk) {
        synchronized (transcriptLock) {
            transcript.append(chunk);
            if (transcript.length() > MAX_TRANSCRIPT_CHARS) {
                transcript.delete(0, transcript.length() - MAX_TRANSCRIPT_CHARS);
            }
        }
    }

    private String snapshotTranscript() {
        synchronized (transcriptLock) {
            return transcript.toString();
        }
    }

    private void sendToClients(String data) {
        for (WebSocket client : clients) {
            if (client != null && client.isOpen()) {
                try {
                    client.send(data);
                } catch (Exception e) {
                    logger.fine("Terminal broadcast failed: " + e.getMessage());
                }
            }
        }
    }

    private void closeAllClients() {
        for (WebSocket client : clients) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
        clients.clear();
    }

    private void writeInput(String data) {
        try {
            stdin.write(data.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException e) {
            logger.warning("Failed to write terminal input: " + e.getMessage());
        }
    }

    /** 调整 PTY 窗口大小（由前端 resize 控制消息触发）。 */
    public void resize(int cols, int rows) {
        if (cols <= 0 || rows <= 0) return;
        try {
            process.setWinSize(new WinSize(cols, rows));
        } catch (Exception e) {
            logger.fine("Terminal resize failed: " + e.getMessage());
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        clients.add(conn);
        String transcriptSnapshot = snapshotTranscript();
        if (!transcriptSnapshot.isEmpty()) {
            conn.send(transcriptSnapshot);
        }
        conn.send("\r\n\u001B[32mConnected to local shell\u001B[0m\r\n");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        // 控制消息：{"type":"resize","cols":N,"rows":M} —— 不转发给 shell
        if (message.startsWith("{")) {
            try {
                JsonNode node = MAPPER.readTree(message);
                if (node != null && node.hasNonNull("type") && "resize".equals(node.get("type").asText())) {
                    int cols = node.hasNonNull("cols") ? node.get("cols").asInt() : 0;
                    int rows = node.hasNonNull("rows") ? node.get("rows").asInt() : 0;
                    resize(cols, rows);
                    return;
                }
            } catch (Exception ignored) {
                // 不是合法 JSON 控制消息，按普通输入处理
            }
        }

        writeInput(message);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        clients.remove(conn);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.warning("Terminal websocket error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        logger.info("Terminal websocket server started on port " + port);
    }

    public boolean isRunning() {
        return process != null && process.isAlive() && !stopped.get();
    }

    public int getPort() {
        return port;
    }

    public String getWorkspaceDir() {
        return workspaceDir;
    }

    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }

    public String getWsUrl() {
        return "ws://127.0.0.1:" + port + "/ws";
    }

    public void kill() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        closeAllClients();
        try {
            stdin.close();
        } catch (IOException ignored) {
        }
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            super.stop();
        } catch (Exception ignored) {
        }
    }

    public static int findFreePort(int startFrom) {
        for (int port = startFrom; port < startFrom + 100; port++) {
            try (java.net.ServerSocket ss = new java.net.ServerSocket(port)) {
                ss.setReuseAddress(true);
                return port;
            } catch (IOException ignored) {
            }
        }
        throw new RuntimeException("No free port found in range " + startFrom + "-" + (startFrom + 100));
    }

    public static String findTtyd() {
        return findShellExecutable();
    }

    public static String findShellExecutable() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String[] candidates = {"powershell.exe", "pwsh.exe", "cmd.exe"};
            for (String candidate : candidates) {
                String found = findExecutableStatic(candidate);
                if (found != null) return found;
            }
            return null;
        }
        String[] candidates = {"/bin/bash", "bash", "/bin/sh", "sh"};
        for (String candidate : candidates) {
            String found = findExecutableStatic(candidate);
            if (found != null) return found;
        }
        return null;
    }

    private static String findExecutableStatic(String name) {
        if (name.contains(File.separator)) {
            File f = new File(name);
            return f.exists() && f.canExecute() ? f.getAbsolutePath() : null;
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) return null;
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File f = new File(dir, name);
            if (f.exists() && f.canExecute()) return f.getAbsolutePath();
        }
        return null;
    }
}
