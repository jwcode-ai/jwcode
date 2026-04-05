import java.io.*;
import java.util.*;

/**
 * JwCode 全面测试报告生成器
 */
public class TestReport {
    
    private static final List<TestResult> results = new ArrayList<>();
    
    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║           JwCode 全面测试套件                          ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");
        
        // 1. 单元测试
        System.out.println("【阶段 1: 单元测试】");
        runTest("AgentRegistryTest", () -> runMavenTest("jwcode-core", "AgentRegistryTest"));
        runTest("ParallelAgentExecutorTest", () -> runMavenTest("jwcode-core", "ParallelAgentExecutorTest"));
        runTest("BridgeServerTest", () -> runMavenTest("jwcode-core", "BridgeServerTest"));
        
        // 2. CLI 命令测试
        System.out.println("\n【阶段 2: CLI 功能测试】");
        String jar = "jwcode-cli/target/jwcode-cli-1.0.0-SNAPSHOT.jar";
        runTest("Help 命令", () -> runCliCommand(jar, "help"));
        runTest("Version 命令", () -> runCliCommand(jar, "version"));
        runTest("Agent list 命令", () -> runCliCommand(jar, "agent list"));
        runTest("Skill list 命令", () -> runCliCommand(jar, "skill list"));
        runTest("Plan 命令", () -> runCliCommand(jar, "plan 创建一个用户登录功能"));
        runTest("Parallel demo 命令", () -> runCliCommand(jar, "parallel demo"));
        
        // 生成报告
        generateReport();
    }
    
    private static void runTest(String name, Runnable test) {
        System.out.print("  测试: " + name + " ... ");
        try {
            test.run();
            results.add(new TestResult(name, true, null));
            System.out.println("✓ PASS");
        } catch (Exception e) {
            results.add(new TestResult(name, false, e.getMessage()));
            System.out.println("✗ FAIL: " + e.getMessage());
        }
    }
    
    private static void runMavenTest(String module, String testClass) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "mvn", "test", "-pl", module, "-Dtest=" + testClass, "-q"
        );
        pb.inheritIO();
        Process p = pb.start();
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Exit code: " + exitCode);
        }
    }
    
    private static void runCliCommand(String jar, String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("java", "-jar", jar);
        pb.redirectInput(new File("/dev/stdin"));
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        
        Process p = pb.start();
        
        // 写入命令
        try (OutputStream os = p.getOutputStream();
             PrintWriter writer = new PrintWriter(os)) {
            writer.println(command);
            writer.println("exit");
            writer.flush();
        }
        
        // 读取输出
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        boolean finished = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new RuntimeException("Timeout");
        }
        
        String out = output.toString();
        if (out.contains("error") || out.contains("Exception") || out.contains("FAIL")) {
            throw new RuntimeException("Command error detected");
        }
    }
    
    private static void generateReport() {
        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║           测试报告                                     ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        
        long passed = results.stream().filter(r -> r.passed).count();
        long failed = results.size() - passed;
        double rate = results.isEmpty() ? 0 : (double) passed / results.size() * 100;
        
        System.out.println("总测试数: " + results.size());
        System.out.println("通过: " + passed);
        System.out.println("失败: " + failed);
        System.out.printf("通过率: %.1f%%\n", rate);
        
        if (failed > 0) {
            System.out.println("\n失败的测试:");
            results.stream()
                .filter(r -> !r.passed)
                .forEach(r -> System.out.println("  - " + r.name + ": " + r.error));
        }
        
        System.out.println();
        if (failed == 0) {
            System.out.println("🎉 所有测试通过！JwCode 运行正常！");
        } else {
            System.out.println("⚠️ 有 " + failed + " 个测试失败");
        }
    }
    
    static class TestResult {
        String name;
        boolean passed;
        String error;
        
        TestResult(String name, boolean passed, String error) {
            this.name = name;
            this.passed = passed;
            this.error = error;
        }
    }
}
