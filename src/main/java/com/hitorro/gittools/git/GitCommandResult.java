package com.hitorro.gittools.git;

import java.util.List;

public record GitCommandResult(int exitCode, String stdout, String stderr, long durationMs) {
    public boolean isSuccess() { return exitCode == 0; }
    public List<String> stdoutLines() { return stdout.lines().filter(l -> !l.isBlank()).toList(); }
}
