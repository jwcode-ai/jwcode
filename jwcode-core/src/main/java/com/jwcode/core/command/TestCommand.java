package com.jwcode.core.command;

import com.jwcode.core.session.Session;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** /test - run the project test script via npm. */
public class TestCommand implements Command {
    @Override public String getName() { return "test"; }
    @Override public String getDescription() { return "Run the project test script"; }
    @Override public String getUsage() { return "test [extra args]"; }
    @Override public String getCategory() { return "tools"; }
    @Override public CommandSource getSource() { return CommandSource.TOOLS; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        return runPackageScript("test", args, session);
    }

    static CommandResult runPackageScript(String script, String[] args, Session session) {
        String baseDir = session != null && session.getWorkingDirectory() != null
            ? session.getWorkingDirectory() : System.getProperty("user.dir");
        String npm = System.getProperty("os.name").toLowerCase().contains("win") ? "npm.cmd" : "npm";
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(npm); cmd.add("run"); cmd.add(script);
        for (String a : args) {
            if (a != null && !a.isBlank()) {
                cmd.add(a);
            }
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).directory(new File(baseDir)).redirectErrorStream(true);
            pb.environment().putIfAbsent("FORCE_COLOR", "0");
            Process p = pb.start();
            byte[] out = p.getInputStream().readAllBytes();
            int code = p.waitFor();
            String body = new String(out, StandardCharsets.UTF_8).trim();
            if (body.length() > 12000) body = body.substring(body.length() - 12000);
            if (body.isEmpty()) body = "(no output)";
            return CommandResult.success("npm " + script + " exited with code " + code + "\n\n```\n" + body + "\n```");
        } catch (Exception e) {
            return CommandResult.error("Failed to run npm " + script + ": " + e.getMessage());
        }
    }
}
