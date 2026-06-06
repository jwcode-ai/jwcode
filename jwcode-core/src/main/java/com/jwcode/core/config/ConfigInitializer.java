package com.jwcode.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * Copies bundled config templates to ~/.jwcode/ on first run.
 * Non-destructive — never overwrites existing files.
 */
public class ConfigInitializer {

    private static final Logger logger = Logger.getLogger(ConfigInitializer.class.getName());

    private static final String[] TEMPLATES = {
        "config.yaml",
        "settings.json",
    };

    private final Path jwcodeDir;

    public ConfigInitializer() {
        String userHome = System.getProperty("user.home");
        this.jwcodeDir = Paths.get(userHome, ".jwcode");
    }

    public ConfigInitializer(Path jwcodeDir) {
        this.jwcodeDir = jwcodeDir;
    }

    /**
     * Copy all bundled templates to ~/.jwcode/ if the target file doesn't exist.
     */
    public void initialize() {
        try {
            Files.createDirectories(jwcodeDir);
        } catch (IOException e) {
            logger.warning("Failed to create ~/.jwcode directory: " + e.getMessage());
            return;
        }

        for (String templateName : TEMPLATES) {
            copyIfMissing(templateName);
        }
    }

    private void copyIfMissing(String templateName) {
        Path target = jwcodeDir.resolve(templateName);
        if (Files.exists(target)) {
            return;
        }

        String resourcePath = "/config-templates/" + templateName;
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                logger.fine("Template not found on classpath: " + resourcePath);
                return;
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Initialized config template: " + target);
        } catch (IOException e) {
            logger.warning("Failed to copy template " + templateName + ": " + e.getMessage());
        }
    }
}
