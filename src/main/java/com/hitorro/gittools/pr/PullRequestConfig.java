package com.hitorro.gittools.pr;

public record PullRequestConfig(
        String repoOwner,
        String repoName,
        String baseBranch,
        String headBranch,
        String title,
        String body,
        boolean draft
) {}
