package com.jwcode.core.plugin;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Logger;

/**
 * 插件类加载器 — 子优先（child-first）隔离类加载器。
 *
 * <p>先尝试从插件 JAR 加载类，找不到再委托给父加载器。
 * 这确保插件可以使用自己的依赖版本，避免与核心冲突。
 */
public class PluginClassLoader extends URLClassLoader {
    private static final Logger logger = Logger.getLogger(PluginClassLoader.class.getName());

    private final String pluginId;

    public PluginClassLoader(String pluginId, URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.pluginId = pluginId;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 先检查是否已加载
        Class<?> loaded = findLoadedClass(name);
        if (loaded != null) {
            return loaded;
        }

        // 子优先：先尝试自己加载
        try {
            Class<?> found = findClass(name);
            if (resolve) {
                resolveClass(found);
            }
            return found;
        } catch (ClassNotFoundException e) {
            // 找不到则委托父加载器
        }

        return super.loadClass(name, resolve);
    }

    public String getPluginId() {
        return pluginId;
    }
}
