package com.jwcode.web.terminal;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.logging.Logger;

public class TerminalSession {
    private static final Logger logger = Logger.getLogger(TerminalSession.class.getName());

    private Process process;
    private final int port;
    private final long startTime;
    private final String workspaceDir;

    public TerminalSession(String ttydPath, String tsCliPath, String workspaceDir, int port) throws IOException {
        this.port = port;
        this.workspaceDir = workspaceDir;
        this.startTime = System.currentTimeMillis();

        ProcessBuilder pb = new ProcessBuilder(
            ttydPath,
            "--port", String.valueOf(port),
            "--interface", "127.0.0.1",
            "--cwd", workspaceDir,
            "node", tsCliPath, "run"
        );
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        this.process = pb.start();

        logger.info("ttyd started on port " + port + " for workspace " + workspaceDir);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> kill(process)));
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    public int getPort() {
        return port;
    }

    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }

    public String getWorkspaceDir() {
        return workspaceDir;
    }

    public void kill() {
        kill(process);
        process = null;
    }

    private static void kill(Process p) {
        if (p == null) return;
        if (p.isAlive()) {
            p.destroyForcibly();
            try { p.waitFor(); } catch (InterruptedException ignored) {}
        }
    }

    public static int findFreePort(int startFrom) {
        for (int port = startFrom; port < startFrom + 100; port++) {
            try (ServerSocket ss = new ServerSocket(port)) {
                ss.setReuseAddress(true);
                return port;
            } catch (IOException ignored) {}
        }
        throw new RuntimeException("No free port found in range " + startFrom + "-" + (startFrom + 100));
    }

    public static String findTtyd() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

        String[] names = {"ttyd.exe", "ttyd"};
        for (String dir : pathEnv.split(File.pathSeparator)) {
            for (String name : names) {
                File f = new File(dir, name);
                if (f.exists() && f.canExecute()) return f.getAbsolutePath();
            }
        }
        return null;
    }
}
