package com.hitorro.gittools.pr;

import com.hitorro.gittools.git.GitService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates creating PRs from commits: creates branches, pushes, and opens PRs via GitHub API.
 */
public class PullRequestCreator {

    private final GitService gitService;
    private final BranchCreator branchCreator;
    private final GitHubApiClient githubClient;

    public PullRequestCreator(GitService gitService, GitHubApiClient githubClient) {
        this.gitService = gitService;
        this.branchCreator = new BranchCreator(gitService);
        this.githubClient = githubClient;
    }

    public PullRequestResult createPrFromCommit(Path repoPath, String baseBranch,
                                                 String commitHash, String remoteName,
                                                 PullRequestConfig config) {
        // Create branch
        String branchName = branchCreator.createBranchFromCommit(repoPath, baseBranch,
                commitHash, "pr-bench/" + commitHash.substring(0, 8));

        // Push branch
        gitService.push(repoPath, remoteName, branchName);

        // Create PR via GitHub API
        var prConfig = new PullRequestConfig(
                config.repoOwner(), config.repoName(), baseBranch, branchName,
                config.title() != null ? config.title() : "Benchmark PR from " + commitHash.substring(0, 8),
                config.body(), config.draft());

        return githubClient.createPullRequest(prConfig);
    }

    public List<PullRequestResult> createPrsFromCommits(Path repoPath, String baseBranch,
                                                         String remoteName, BatchPrJob job) {
        List<PullRequestResult> results = new ArrayList<>();
        for (String hash : job.commitHashes()) {
            String title = job.titleTemplate() != null
                    ? job.titleTemplate().replace("{hash}", hash.substring(0, 8))
                    : "Benchmark PR from " + hash.substring(0, 8);
            var config = new PullRequestConfig(null, null, baseBranch, null, title, job.bodyTemplate(), false);
            try {
                results.add(createPrFromCommit(repoPath, baseBranch, hash, remoteName, config));
            } catch (Exception e) {
                results.add(new PullRequestResult(0, null, "pr-bench/" + hash.substring(0, 8), "failed: " + e.getMessage()));
            }
        }
        return results;
    }
}
