package com.jwcode.core.code.semantic;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 符号节点
 */
public class SymbolNode {
    private final String id;
    private final String name;
    private final String qualifiedName;
    private final SymbolKind kind;
    private final Location location;
    private final List<String> modifiers;
    private final String language;
    private final String documentation;
    private final Map<String, Object> metadata;
    
    public SymbolNode(String id, String name, SymbolKind kind, Location location) {
        this(id, name, name, kind, location, List.of(), "unknown", "", Map.of());
    }
    
    public SymbolNode(String id, String name, String qualifiedName, SymbolKind kind,
                     Location location, List<String> modifiers, String language,
                     String documentation, Map<String, Object> metadata) {
        this.id = id;
        this.name = name;
        this.qualifiedName = qualifiedName;
        this.kind = kind;
        this.location = location;
        this.modifiers = modifiers;
        this.language = language;
        this.documentation = documentation;
        this.metadata = metadata;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getQualifiedName() { return qualifiedName; }
    public SymbolKind getKind() { return kind; }
    public Location getLocation() { return location; }
    public List<String> getModifiers() { return modifiers; }
    public String getLanguage() { return language; }
    public String getDocumentation() { return documentation; }
    public Map<String, Object> getMetadata() { return metadata; }
    
    public boolean isPublic() {
        return modifiers.contains("public");
    }
    
    public boolean isPrivate() {
        return modifiers.contains("private");
    }
    
    public boolean isStatic() {
        return modifiers.contains("static");
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SymbolNode)) return false;
        return id.equals(((SymbolNode) o).id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    @Override
    public String toString() {
        return String.format("%s[%s] %s", kind.getIcon(), name, location);
    }
}