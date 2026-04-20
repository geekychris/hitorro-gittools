package com.hitorro.gittools.commits;

import com.hitorro.gittools.git.GitService;
import com.hitorro.gittools.model.GitCommit;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Selects commits with various filter criteria.
 */
public class CommitSelector {

    private final GitService gitService;

    public CommitSelector(GitService gitService) { this.gitService = gitService; }

    public List<GitCommit> selectCommits(Path repoPath, String branch, int maxCount,
                                          String authorFilter, String messageFilter,
                                          Instant afterDate, Instant beforeDate) {
        var commits = gitService.log(repoPath, maxCount, branch);
        return commits.stream()
                .filter(c -> authorFilter == null || c.author().toLowerCase().contains(authorFilter.toLowerCase()))
                .filter(c -> messageFilter == null || c.message().toLowerCase().contains(messageFilter.toLowerCase()))
                .filter(c -> afterDate == null || c.date().isAfter(afterDate))
                .filter(c -> beforeDate == null || c.date().isBefore(beforeDate))
                .toList();
    }

    public List<GitCommit> selectByHashes(Path repoPath, List<String> hashes) {
        return hashes.stream()
                .map(h -> gitService.getCommit(repoPath, h))
                .filter(c -> c != null)
                .toList();
    }

    public List<GitCommit> selectRange(Path repoPath, CommitRange range) {
        return gitService.logRange(repoPath, range.fromHash(), range.toHash());
    }
}
