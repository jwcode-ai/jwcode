package com.jwcode.core.lsp;

/**
 * LspPosition - LSP 位置坐标
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspPosition {
    
    private int line;
    private int character;
    
    public LspPosition() {
    }
    
    public LspPosition(int line, int character) {
        this.line = line;
        this.character = character;
    }
    
    public int getLine() {
        return line;
    }
    
    public void setLine(int line) {
        this.line = line;
    }
    
    public int getCharacter() {
        return character;
    }
    
    public void setCharacter(int character) {
        this.character = character;
    }
}
