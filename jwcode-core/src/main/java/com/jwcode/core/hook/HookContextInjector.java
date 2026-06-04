package com.jwcode.core.hook;

import java.util.logging.Logger;

/**
 * Wraps hook context outputs in XML tags for injection into the agent message stream.
 *
 * <p>Hook output is treated as "coming from the user" (trust level near Tier 1).
 * The agent sees it as a system reminder block but should not echo it back verbatim.
 * The XML tags allow the agent to distinguish hook-injected content from user messages.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 * String hookOutput = hookResult.getContextOutput();
 * String injected = HookContextInjector.wrap(hookOutput, eventType, hookName);
 * // Prepend to the next user message before sending to LLM
 * </pre>
 */
public final class HookContextInjector {

    private static final Logger logger = Logger.getLogger(HookContextInjector.class.getName());

    private HookContextInjector() {}

    /**
     * Wrap hook context output in an XML-tagged block for injection.
     *
     * @param contextOutput the hook's stdout text to inject
     * @param eventType     the hook event type (e.g. "PostToolUse", "Stop")
     * @param hookName      the hook executor name
     * @return XML-wrapped injection string, or empty string if input is blank
     */
    public static String wrap(String contextOutput, String eventType, String hookName) {
        if (contextOutput == null || contextOutput.isBlank()) {
            return "";
        }
        return "\n<hook-output type=\"" + escape(eventType) + "\" name=\"" + escape(hookName) + "\">\n"
            + contextOutput.trim() + "\n"
            + "</hook-output>";
    }

    /**
     * Build an injectable block from a HookResult.
     *
     * @param result    the hook execution result
     * @param eventType the event type string
     * @return XML-wrapped injection, or empty string if result has no contextOutput
     */
    public static String fromResult(HookResult result, String eventType) {
        if (result == null || !result.hasContextOutput()) {
            return "";
        }
        return wrap(result.getContextOutput(), eventType, result.getHookName());
    }

    /**
     * Build injectable blocks from all context outputs accumulated in a HookResult.
     * Individual outputs are separated by "---" in the accumulated string;
     * this method extracts and wraps each one.
     *
     * @param result    the merged hook result from HookChain
     * @param eventType the event type string
     * @return concatenated XML-wrapped blocks
     */
    public static String fromMergedResult(HookResult result, String eventType) {
        if (result == null || !result.hasContextOutput()) {
            return "";
        }
        return wrap(result.getContextOutput(), eventType, result.getHookName());
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
