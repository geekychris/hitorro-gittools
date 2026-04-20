package com.hitorro.gittools.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record GitRepository(
        String name,
        Path path,
        String description,
        List<String> tags,
        String currentBranch,
        int branchCount,
        int tagCount,
        String remoteUrl,
        String lastCommitHash,
        String lastCommitMessage,
        Instant lastCommitDate,
        String lastCommitAuthor
) {}
