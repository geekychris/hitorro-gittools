package com.hitorro.gittools.model;

public record GitBranch(String name, boolean isRemote, String lastCommitHash) {}
