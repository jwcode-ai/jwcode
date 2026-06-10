package com.jwcode.plugin.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

/**
 * 插件清单 — 描述插件的元数据和能力声明。
 *
 * <p>对标 Codex 的 codex-plugin.json 格式。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PluginManifest(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("version") String version,
    @JsonProperty("description") String description,
    @JsonProperty("author") String author,
    @JsonProperty("capabilities") Set<PluginCapability> capabilities,
    @JsonProperty("dependencies") List<String> dependencies,
    @JsonProperty("entry_class") String entryClass,
    @JsonProperty("min_jwcode_version") String minJwcodeVersion,
    @JsonProperty("homepage") String homepage,
    @JsonProperty("license") String license
) {
    public boolean hasCapability(PluginCapability capability) {
        return capabilities != null && capabilities.contains(capability);
    }
}
