package com.hitorro.gittools.git;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs git commands as subprocesses. Modeled after pr_benchy's GitWorkspaceService.
 */
public class GitCommandRunner {

    private static final Logger log = LoggerFactory.getLogger(GitCommandRunner.class);
    private final long timeoutMs;

    public GitCommandRunner() { this(120_000); }

    public GitCommandRunner(long timeoutMs) { this.timeoutMs = timeoutMs; }

    /**
     * Run a git command in the given working directory.
     */
    public GitCommandResult run(Path workingDir, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));

        long start = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Read stdout and stderr concurrently to prevent blocking
            String stdout, stderr;
            try (var stdoutStream = process.getInputStream();
                 var stderrStream = process.getErrorStream()) {
                stdout = new String(stdoutStream.readAllBytes(), StandardCharsets.UTF_8);
                stderr = new String(stderrStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            long duration = System.currentTimeMillis() - start;

            if (!finished) {
                process.destroyForcibly();
                return new GitCommandResult(-1, "", "Timeout after " + timeoutMs + "ms", duration);
            }

            return new GitCommandResult(process.exitValue(), stdout, stderr, duration);

        } catch (IOException | InterruptedException e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Git command failed: {}", String.join(" ", command), e);
            return new GitCommandResult(-1, "", e.getMessage(), duration);
        }
    }

    /**
     * Run and return stdout. Throws if non-zero exit.
     */
    public String runOrThrow(Path workingDir, String... args) {
        GitCommandResult result = run(workingDir, args);
        if (!result.isSuccess()) {
            throw new RuntimeException("Git command failed (exit " + result.exitCode() + "): "
                    + result.stderr() + " [" + String.join(" ", args) + "]");
        }
        return result.stdout().trim();
    }
}
