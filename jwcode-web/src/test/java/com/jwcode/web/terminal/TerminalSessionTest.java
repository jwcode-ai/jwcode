package com.jwcode.web.terminal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

class TerminalSessionTest {

    @Test
    @DisplayName("findFreePort returns bindable port in expected range")
    void testFindFreePort() throws IOException {
        int port = TerminalSession.findFreePort(8900);
        assertTrue(port >= 8900, "port should be >= startFrom");
        assertTrue(port < 9000, "port should be < startFrom + 100");

        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            assertTrue(ss.isBound(), "found port should be bindable");
        }
    }

    @Test
    @DisplayName("findFreePort with different startFrom values")
    void testFindFreePortDifferentStart() {
        int port = TerminalSession.findFreePort(8950);
        assertTrue(port >= 8950, "should start search from 8950");
    }

    @Test
    @DisplayName("findFreePort returns same port when not consumed")
    void testFindFreePortConsistent() {
        int p1 = TerminalSession.findFreePort(8970);
        int p2 = TerminalSession.findFreePort(8970);
        assertEquals(p1, p2, "same start should yield same port if not consumed");
    }

    @Test
    @DisplayName("findTtyd searches PATH without throwing")
    void testFindTtyd() {
        String path = TerminalSession.findTtyd();
        if (path != null) {
            assertTrue(new java.io.File(path).isAbsolute() || path.contains(java.io.File.separator),
                "found ttyd path should look valid");
        }
    }
}
