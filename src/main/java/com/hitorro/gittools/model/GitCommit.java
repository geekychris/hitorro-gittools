package com.hitorro.gittools.model;

import java.time.Instant;
import java.util.List;

public record GitCommit(
        String hash,
        String shortHash,
        String author,
        String authorEmail,
        Instant date,
        String message,
        int filesChanged
) {}
