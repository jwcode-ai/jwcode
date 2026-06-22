package com.jwcode.web;

import com.jwcode.core.log.LoggingBridge;
import com.jwcode.core.tool.ToolRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Web UI 启动器
 */
public class WebLauncher {

    private static final Logger LOGGER = Logger.getLogger(WebLauncher.class.getName());

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Fatal error: " + t.getMessage(), t);
            System.exit(1);
        }
    }

    private static void run(String[] args) {
        LoggingBridge.install();
        int httpPort = 8080;
        int wsPort = 8081;
        String workspaceDir = null;
        if (args.length > 0) {
            try {
                httpPort = Integer.parseInt(args[0]);
                wsPort = httpPort + 1;
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid port number, using default: " + e.getMessage());
            }
        }
        if (args.length > 1) {
            try {
                wsPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid WebSocket port number, using default: " + e.getMessage());
            }
        }
        if (args.length > 2) {
            workspaceDir = args[2];
            System.setProperty("user.dir", workspaceDir);
        }

        try {
            WebServer server = new WebServer(httpPort, wsPort, ToolRegistry.createDefault(), workspaceDir);
            SystemStatusHandler.setWebServer(server);
            server.start();

            LOGGER.info("\n═══════════════════════════════════════════════════\n"
                + "  JWCode Backend Ready\n"
                + "═══════════════════════════════════════════════════\n"
                + "  HTTP API:   http://localhost:" + httpPort + "\n"
                + "  WebSocket:  ws://localhost:" + wsPort + "/ws\n"
                + "  Web UI:     http://localhost:" + httpPort + "\n"
                + "═══════════════════════════════════════════════════\n");

            CountDownLatch shutdownLatch = new CountDownLatch(1);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                shutdownLatch.countDown();
            }, "jwcode-shutdown"));

            try {
                shutdownLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            LOGGER.info("Shutting down...");
            server.stop();
            LOGGER.info("Server stopped.");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Startup failed: " + e.getMessage(), e);
        }
    }
}
