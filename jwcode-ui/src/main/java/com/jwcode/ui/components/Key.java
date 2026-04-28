package com.jwcode.ui.components;

/**
 * 简化的按键表示类（用于 PromptInput 等组件）。
 */
public class Key {
    
    private final Type type;
    private final char character;
    
    public enum Type {
        CHAR, UP, DOWN, LEFT, RIGHT,
        HOME, END, DELETE, BACKSPACE, ENTER, TAB,
        ESCAPE, PAGE_UP, PAGE_DOWN
    }
    
    public Key(Type type) {
        this(type, '\0');
    }
    
    public Key(char character) {
        this(Type.CHAR, character);
    }
    
    public Key(Type type, char character) {
        this.type = type;
        this.character = character;
    }
    
    public Type getType() {
        return type;
    }
    
    public char getChar() {
        return character;
    }
    
    public static Key charKey(char c) {
        return new Key(c);
    }
    
    public static Key control(Type type) {
        return new Key(type);
    }
}
