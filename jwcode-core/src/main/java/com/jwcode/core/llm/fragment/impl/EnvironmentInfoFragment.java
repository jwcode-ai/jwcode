package com.jwcode.core.llm.fragment.impl;

import com.jwcode.core.llm.fragment.ContextualFragment;
import com.jwcode.core.llm.fragment.FragmentCategory;
import com.jwcode.core.llm.fragment.FragmentContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 环境信息片段 — 注入 OS、Java 版本、当前时间、工作目录等信息。
 *
 * <p>重构自 LLMQueryEngine.injectEnvironmentInfo()。
 */
public class EnvironmentInfoFragment implements ContextualFragment {

    @Override
    public String getId() {
        return "environment-info";
    }

    @Override
    public FragmentCategory getCategory() {
        return FragmentCategory.ENVIRONMENT;
    }

    @Override
    public String getDedupMarker() {
        return "[ENV_INFO]";
    }

    @Override
    public String build(FragmentContext ctx) {
        String osName = System.getProperty("os.name", "Unknown");
        String osVersion = System.getProperty("os.version", "Unknown");
        String osArch = System.getProperty("os.arch", "Unknown");
        String javaVersion = System.getProperty("java.version", "Unknown");
        String userName = System.getProperty("user.name", "Unknown");
        String workingDir = ctx.getWorkingDirectory();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
        String currentTime = formatter.format(Instant.now());

        return """
            [ENV_INFO]
            【当前环境信息】
            - 操作系统：%s %s (%s)
            - Java 版本：%s
            - 当前用户：%s
            - 当前时间：%s
            - 工作目录：%s

            注意：以上环境信息随用户操作动态更新。当用户询问"当前工作目录"、
            "现在时间"、"操作系统"等问题时，请以上述信息为准。
            """.formatted(osName, osVersion, osArch, javaVersion, userName, currentTime, workingDir);
    }
}
