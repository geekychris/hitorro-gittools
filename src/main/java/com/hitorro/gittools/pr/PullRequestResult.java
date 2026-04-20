package com.hitorro.gittools.pr;

public record PullRequestResult(int prNumber, String prUrl, String branchName, String status) {}
