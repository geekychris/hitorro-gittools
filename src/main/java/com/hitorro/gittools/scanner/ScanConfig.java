package com.hitorro.gittools.scanner;

import java.nio.file.Path;
import java.util.List;

public record ScanConfig(List<Path> rootPaths, int maxDepth, List<String> excludePatterns) {
    public ScanConfig(List<Path> rootPaths) { this(rootPaths, 4, List.of("node_modules", ".m2", "target", "build")); }
    public static ScanConfig of(Path root) { return new ScanConfig(List.of(root)); }
}
