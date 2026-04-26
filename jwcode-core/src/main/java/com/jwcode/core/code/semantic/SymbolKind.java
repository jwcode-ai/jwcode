package com.jwcode.core.code.semantic;

/**
 * 符号类型
 */
public enum SymbolKind {
    FILE("📄"),
    PACKAGE("📦"),
    MODULE("📂"),
    CLASS("🟦"),
    INTERFACE("🟨"),
    ENUM("🟪"),
    ANNOTATION("🟧"),
    METHOD("🔷"),
    FUNCTION("🔹"),
    FIELD("⬜"),
    VARIABLE("⚪"),
    PARAMETER("🔘"),
    CONSTANT("🔶"),
    CONSTRUCTOR("🔺"),
    PROPERTY("🏠"),
    EVENT("📡"),
    OPERATOR("➕"),
    MACRO("⚙️");

    private final String icon;

    SymbolKind(String icon) {
        this.icon = icon;
    }

    public String getIcon() {
        return icon;
    }
}
