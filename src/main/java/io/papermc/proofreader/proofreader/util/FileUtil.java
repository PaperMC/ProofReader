package io.papermc.proofreader.proofreader.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class FileUtil {
    public static void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk
                    // children first
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to delete " + path, e);
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException("Failed delete " + dir + " recursively", e);
        }
    }

    public static void moveDirectory(Path sourceDir, Path targetDir) {
        try {
            Files.createDirectories(targetDir);
            try (var walk = Files.walk(sourceDir)) {
                walk.forEach(source -> {
                    try {
                        var relative = sourceDir.relativize(source);
                        var target = targetDir.resolve(relative);
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(target);
                        } else {
                            Files.copy(source, target);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to move file " + source, e);
                    }
                });
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to move " + sourceDir, e);
        }
    }
}
