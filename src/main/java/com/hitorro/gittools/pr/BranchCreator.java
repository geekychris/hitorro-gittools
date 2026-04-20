package com.hitorro.gittools.pr;

import com.hitorro.gittools.git.GitService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates branches from selected commits via cherry-pick.
 */
public class BranchCreator {

    private final GitService gitService;

    public BranchCreator(GitService gitService) { this.gitService = gitService; }

    /**
     * Create a branch from a commit by checking out base, creating a new branch, and cherry-picking.
     * Returns the branch name.
     */
    public String createBranchFromCommit(Path repoPath, String baseBranch,
                                          String commitHash, String branchNameTemplate) {
        String branchName = branchNameTemplate.replace("{hash}", commitHash.substring(0, 8));
        String currentBranch = gitService.getCurrentBranch(repoPath);
        try {
            gitService.checkout(repoPath, baseBranch);
            gitService.createBranch(repoPath, branchName, baseBranch);
            gitService.cherryPick(repoPath, commitHash);
        } finally {
            // Restore original branch
            try { gitService.checkout(repoPath, currentBranch); } catch (Exception ignored) {}
        }
        return branchName;
    }

    /**
     * Create branches for multiple commits.
     */
    public List<String> createBranchesFromCommits(Path repoPath, String baseBranch,
                                                    List<String> commitHashes,
                                                    String branchNameTemplate) {
        List<String> branches = new ArrayList<>();
        for (String hash : commitHashes) {
            branches.add(createBranchFromCommit(repoPath, baseBranch, hash, branchNameTemplate));
        }
        return branches;
    }
}
