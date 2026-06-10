package com.jwcode.core.policy;

/**
 * 网络访问规则 — 控制命令的网络访问权限。
 */
public record NetworkAccessRule(
    String domain,
    String protocol,
    PolicyRule.Action action,
    String justification
) {
    public NetworkAccessRule {
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("domain must not be blank");
        }
        if (protocol == null || protocol.isBlank()) {
            protocol = "https";
        }
        if (action == null) {
            action = PolicyRule.Action.ALLOW;
        }
    }

    /** 匹配给定的域名和协议 */
    public boolean matches(String host, String proto) {
        if (!protocol.equals(proto)) return false;
        if (domain.equals(host)) return true;
        if (domain.startsWith("*.")) {
            String suffix = domain.substring(1); // "*.example.com" → ".example.com"
            return host.endsWith(suffix);
        }
        return false;
    }

    public static NetworkAccessRule allow(String domain, String protocol) {
        return new NetworkAccessRule(domain, protocol, PolicyRule.Action.ALLOW, null);
    }

    public static NetworkAccessRule deny(String domain, String protocol, String justification) {
        return new NetworkAccessRule(domain, protocol, PolicyRule.Action.DENY, justification);
    }
}
