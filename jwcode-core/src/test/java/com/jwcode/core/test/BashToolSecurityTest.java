package com.jwcode.core.test;

import com.jwcode.core.tool.input.BashInput;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BashTool 安全测试 — 验证注入检测和危险命令识别。
 */
class BashToolSecurityTest {

    @Test
    @DisplayName("rm -rf / 应为危险命令")
    void rmRfRootShouldBeDangerous() {
        BashInput input = new BashInput("rm -rf / --no-preserve-root");
        assertTrue(input.isDangerousCommand());
    }

    @Test
    @DisplayName("taskkill /f /im java 应为危险命令")
    void taskkillJavaShouldBeDangerous() {
        BashInput input = new BashInput("taskkill /F /IM java.exe");
        assertTrue(input.isDangerousCommand());
    }

    @Test
    @DisplayName("Stop-Process -Name java 应为危险命令")
    void stopProcessJavaShouldBeDangerous() {
        BashInput input = new BashInput("Stop-Process -Name java");
        assertTrue(input.isDangerousCommand());
    }

    @Test
    @DisplayName("kill -9 应为危险命令")
    void kill9ShouldBeDangerous() {
        BashInput input = new BashInput("kill -9 12345");
        assertTrue(input.isDangerousCommand());
    }

    @Test
    @DisplayName("普通 echo 不应为危险命令")
    void echoShouldNotBeDangerous() {
        BashInput input = new BashInput("echo hello world");
        assertFalse(input.isDangerousCommand());
    }

    @Test
    @DisplayName("git status 不应为危险命令")
    void gitStatusShouldNotBeDangerous() {
        BashInput input = new BashInput("git status");
        assertFalse(input.isDangerousCommand());
    }

    @Test
    @DisplayName("默认超时应为 600000ms")
    void defaultTimeoutShouldBe10Minutes() {
        BashInput input = new BashInput("echo test");
        assertEquals(600000, input.getTimeoutMillis());
    }

    @Test
    @DisplayName("默认应要求审批")
    void defaultShouldRequireApproval() {
        BashInput input = new BashInput("echo test");
        assertTrue(input.requiresApproval());
    }

    @Test
    @DisplayName("background 默认应为 false")
    void defaultBackgroundShouldBeFalse() {
        BashInput input = new BashInput("echo test");
        assertFalse(input.isBackground());
    }

    @Test
    @DisplayName("policyContext 字段应可设置")
    void policyContextShouldBeSettable() {
        BashInput input = new BashInput("echo test", "test desc", 30000, null, null, false, false, "safe-cmd");
        assertEquals("safe-cmd", input.policyContext());
    }

    @Test
    @DisplayName("dd if=/dev/zero 应为危险命令")
    void ddZeroShouldBeDangerous() {
        BashInput input = new BashInput("dd if=/dev/zero of=/dev/sda");
        assertTrue(input.isDangerousCommand());
    }

    @Test
    @DisplayName("wmic process delete 应为危险命令")
    void wmicProcessDeleteShouldBeDangerous() {
        BashInput input = new BashInput("wmic process where name='java.exe' call terminate");
        assertTrue(input.isDangerousCommand());
    }
}
