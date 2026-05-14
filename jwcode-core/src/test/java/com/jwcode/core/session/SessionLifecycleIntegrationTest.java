package com.jwcode.core.session;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Session 生命周期集成测试
 *
 * <p>测试 SessionManager 的完整生命周期：
 * 创建 Session → 持久化保存 → 加载 → 删除 → 清理</p>
 */
@DisplayName("Session 生命周期集成测试")
public class SessionLifecycleIntegrationTest {

    private SessionManager sessionManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager(tempDir);
    }

    @Test
    @DisplayName("创建 Session 并验证属性")
    void testCreateSession() {
        Session session = sessionManager.createSession("/test/workdir");
        assertNotNull(session, "创建的 Session 不应为 null");
        assertNotNull(session.getId(), "Session ID 不应为空");
        assertEquals("/test/workdir", session.getWorkingDirectory());
    }

    @Test
    @DisplayName("默认构造创建 Session 使用空工作目录")
    void testCreateSessionDefault() {
        Session session = sessionManager.createSession();
        assertNotNull(session);
        assertNotNull(session.getId());
    }

    @Test
    @DisplayName("保存并加载 Session")
    void testSaveAndLoadSession() {
        Session session = sessionManager.createSession("/test/workdir");
        session.setTitle("My Test Session");
        sessionManager.saveSession(session);

        Session loaded = sessionManager.loadSession(session.getId());
        assertNotNull(loaded, "保存后应能加载 Session");
        assertEquals("My Test Session", loaded.getTitle());
    }

    @Test
    @DisplayName("删除 Session")
    void testDeleteSession() {
        Session session = sessionManager.createSession("/test/workdir");
        sessionManager.saveSession(session);
        assertTrue(sessionManager.getAllSessions().size() > 0);

        boolean deleted = sessionManager.deleteSession(session.getId());
        assertTrue(deleted, "Session 应被成功删除");
    }

    @Test
    @DisplayName("获取所有 Session")
    void testGetAllSessions() {
        sessionManager.createSession("/workdir1");
        sessionManager.createSession("/workdir2");
        sessionManager.createSession("/workdir3");

        List<Session> all = sessionManager.getAllSessions();
        assertEquals(3, all.size());
    }

    @Test
    @DisplayName("获取最近 Session")
    void testGetRecentSessions() {
        sessionManager.createSession("/workdir1");
        sessionManager.createSession("/workdir2");
        sessionManager.createSession("/workdir3");

        List<Session> recent = sessionManager.getRecentSessions(2);
        assertEquals(2, recent.size(), "应返回最近 2 个 Session");
    }

    @Test
    @DisplayName("获取和设置活跃 Session")
    void testActiveSession() {
        Session session = sessionManager.createSession("/test/workdir");
        sessionManager.setActiveSession(session.getId());

        Session active = sessionManager.getActiveSession();
        assertNotNull(active);
        assertEquals(session.getId(), active.getId());
    }

    @Test
    @DisplayName("清除活跃 Session")
    void testClearActiveSession() {
        Session session = sessionManager.createSession("/test/workdir");
        sessionManager.setActiveSession(session.getId());
        assertNotNull(sessionManager.getActiveSession());

        sessionManager.clearActiveSession();
        assertNull(sessionManager.getActiveSession(), "清除后活跃 Session 应为 null");
    }

    @Test
    @DisplayName("Session 计数")
    void testSessionCount() {
        assertEquals(0, sessionManager.getSessionCount());

        sessionManager.createSession("/workdir1");
        sessionManager.createSession("/workdir2");

        assertEquals(2, sessionManager.getSessionCount());
    }
}
