package com.jwcode.parser.model;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 类型描述符 — 描述一个 Java 类型的完整信息。
 *
 * <p>包含类型名称、种类（class/interface/enum）、泛型参数、
 * 父类和实现的接口等信息。</p>
 */
@Data
public class TypeDescriptor {

    /** 类型名称 */
    private String name;

    /** 类型种类：class / interface / enum / @interface / record */
    private String kind;

    /** 泛型参数列表 */
    private List<String> typeParameters;

    /** 父类全限定名 */
    private String superClass;

    /** 实现的接口列表 */
    private List<String> interfaces;

    /** 修饰符列表 */
    private List<String> modifiers;

    public TypeDescriptor() {
        this.typeParameters = Collections.emptyList();
        this.interfaces = Collections.emptyList();
        this.modifiers = Collections.emptyList();
    }

    /**
     * 是否为接口
     */
    public boolean isInterface() {
        return "interface".equals(kind);
    }

    /**
     * 是否为枚举
     */
    public boolean isEnum() {
        return "enum".equals(kind);
    }

    /**
     * 是否为注解
     */
    public boolean isAnnotation() {
        return "@interface".equals(kind);
    }

    /**
     * 是否为抽象类
     */
    public boolean isAbstract() {
        return modifiers != null && modifiers.contains("abstract");
    }

    /**
     * 是否为 final 类
     */
    public boolean isFinal() {
        return modifiers != null && modifiers.contains("final");
    }

    /**
     * 是否为密封类 (Java 17+)
     */
    public boolean isSealed() {
        return modifiers != null && modifiers.contains("sealed");
    }
}
