package com.jwcode.core.test;

import com.jwcode.core.llm.fragment.*;
import com.jwcode.core.llm.fragment.impl.*;
import com.jwcode.core.session.Session;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 片段注入测试 — 验证注入顺序、大小限制和去重。
 */
class FragmentInjectionTest {

    private FragmentRegistry registry;
    private Session session;

    @BeforeEach
    void setUp() {
        registry = FragmentRegistry.getInstance();
        registry.clear();
        session = new Session("test-frag", System.getProperty("user.dir"));
    }

    @Test
    @DisplayName("片段按类别顺序注入")
    void fragmentsShouldInjectInCategoryOrder() {
        registry.register(new AgentRoleFragment());
        registry.register(new FileEditGuidelinesFragment());
        registry.register(new EnvironmentInfoFragment());

        List<ContextualFragment> sorted = registry.getAllSorted();
        assertEquals(3, sorted.size());

        // SYSTEM_IDENTITY → BEHAVIORAL_RULES → ENVIRONMENT
        assertTrue(sorted.get(0).getCategory().ordinal()
            <= sorted.get(1).getCategory().ordinal());
        assertTrue(sorted.get(1).getCategory().ordinal()
            <= sorted.get(2).getCategory().ordinal());
    }

    @Test
    @DisplayName("带去重标记的片段不重复注入")
    void fragmentWithMarkerShouldNotDuplicate() {
        var fragment = new EnvironmentInfoFragment();
        registry.register(fragment);

        FragmentContext ctx = new FragmentContext(session, null, null, null);

        // 第一次注入
        List<FragmentResult> results1 = registry.buildAndInject(ctx, session);
        assertEquals(1, results1.stream()
            .filter(r -> r.fragmentId().equals("environment-info")).count());

        // 第二次注入应跳过（已通过 session 跟踪）
        List<FragmentResult> results2 = registry.buildAndInject(ctx, session);
        assertEquals(0, results2.size());
    }

    @Test
    @DisplayName("禁用片段不应注入")
    void disabledFragmentShouldNotInject() {
        // ToolDefinitionsFragment 默认禁用
        var fragment = new ToolDefinitionsFragment();
        registry.register(fragment);

        FragmentContext ctx = new FragmentContext(session, null, null, null);
        List<FragmentResult> results = registry.buildAndInject(ctx, session);

        assertEquals(0, results.stream()
            .filter(r -> r.fragmentId().equals("tool-definitions")).count());
    }

    @Test
    @DisplayName("片段注册表是线程安全的单例")
    void registryShouldBeSingleton() {
        FragmentRegistry r1 = FragmentRegistry.getInstance();
        FragmentRegistry r2 = FragmentRegistry.getInstance();
        assertSame(r1, r2);
    }

    @Test
    @DisplayName("审计日志记录注入")
    void auditLogShouldRecordInjections() {
        registry.register(new EnvironmentInfoFragment());
        FragmentContext ctx = new FragmentContext(session, null, null, null);
        registry.buildAndInject(ctx, session);

        var entries = registry.getAuditLog().getEntriesBySession(session.getId());
        assertTrue(entries.size() > 0);
    }
}
