package com.jwcode.core.plugins;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * PluginLoader - 插件加载器
 * 
 * 功能说明：
 * 负责加载和卸载插件，管理插件的类加载器生命周期。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class PluginLoader {
    
    private final PluginService.PluginInfo pluginInfo;
    private URLClassLoader classLoader;
    private Path pluginPath;
    private boolean loaded;
    
    public PluginLoader(PluginService.PluginInfo pluginInfo) {
        this.pluginInfo = pluginInfo;
        this.loaded = false;
    }
    
    /**
     * 加载插件
     */
    public void load() throws IOException {
        if (loaded) {
            return;
        }
        
        // 查找插件文件
        pluginPath = findPluginFile(pluginInfo.id);
        
        if (pluginPath != null && Files.exists(pluginPath)) {
            // 创建类加载器
            URL[] urls = {pluginPath.toUri().toURL()};
            classLoader = new URLClassLoader(urls, getClass().getClassLoader());
            
            // 读取插件元数据
            readPluginManifest();
            
            loaded = true;
        } else {
            // 插件文件不存在，使用模拟数据
            loaded = true;
        }
    }
    
    /**
     * 卸载插件
     */
    public void unload() {
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException e) {
                // 忽略关闭错误
            }
            classLoader = null;
        }
        loaded = false;
    }
    
    /**
     * 获取插件类
     */
    public Class<?> getClass(String className) throws ClassNotFoundException {
        if (classLoader == null) {
            throw new IllegalStateException("插件未加载");
        }
        return classLoader.loadClass(className);
    }
    
    /**
     * 创建插件实例
     */
    @SuppressWarnings("unchecked")
    public <T> T createInstance(String className) throws Exception {
        Class<?> clazz = getClass(className);
        return (T) clazz.getDeclaredConstructor().newInstance();
    }
    
    /**
     * 查找插件文件
     */
    private Path findPluginFile(String pluginId) {
        // 在插件目录中查找
        String pluginsDir = System.getProperty("user.home") + "/.jwcode/plugins";
        String[] extensions = {".jar", ".zip"};
        
        for (String ext : extensions) {
            Path path = Paths.get(pluginsDir, pluginId + ext);
            if (Files.exists(path)) {
                return path;
            }
        }
        
        return null;
    }
    
    /**
     * 读取插件清单文件
     */
    private void readPluginManifest() {
        if (pluginPath == null) {
            return;
        }
        
        try (JarFile jarFile = new JarFile(pluginPath.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                // 读取清单属性
                String name = manifest.getMainAttributes().getValue("Plugin-Name");
                String version = manifest.getMainAttributes().getValue("Plugin-Version");
                String author = manifest.getMainAttributes().getValue("Plugin-Author");
                String mainClass = manifest.getMainAttributes().getValue("Plugin-Main-Class");
                
                if (name != null) pluginInfo.name = name;
                if (version != null) pluginInfo.version = version;
                if (author != null) pluginInfo.author = author;
                if (mainClass != null) pluginInfo.setSetting("mainClass", mainClass);
            }
        } catch (IOException e) {
            // 忽略读取错误
        }
    }
    
    /**
     * 检查是否已加载
     */
    public boolean isLoaded() {
        return loaded;
    }
    
    /**
     * 获取插件信息
     */
    public PluginService.PluginInfo getPluginInfo() {
        return pluginInfo;
    }
    
    /**
     * 获取类加载器
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }
}