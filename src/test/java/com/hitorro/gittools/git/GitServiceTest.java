package com.hitorro.gittools.git;

import com.hitorro.gittools.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Git Service")
class GitServiceTest {

    // Use the hitorro root repo for testing (we know it exists)
    private static final Path REPO = Path.of(System.getProperty("user.dir")).getParent();
    private final GitService gitService = new GitService();

    @Test
    void shouldDetectGitRepository() {
        assertThat(gitService.isGitRepository(REPO)).isTrue();
        assertThat(gitService.isGitRepository(Path.of("/tmp"))).isFalse();
    }

    @Test
    void shouldGetCurrentBranch() {
        String branch = gitService.getCurrentBranch(REPO);
        assertThat(branch).isNotBlank();
    }

    @Test
    void shouldGetHeadCommit() {
        String hash = gitService.getHeadCommitHash(REPO);
        assertThat(hash).hasSize(40);
    }

    @Test
    void shouldListBranches() {
        List<GitBranch> branches = gitService.listBranches(REPO);
        assertThat(branches).isNotEmpty();
        assertThat(branches.get(0).name()).isNotBlank();
    }

    @Test
    void shouldListTags() {
        // May or may not have tags
        List<GitTag> tags = gitService.listTags(REPO);
        assertThat(tags).isNotNull();
    }

    @Test
    void shouldGetLog() {
        List<GitCommit> commits = gitService.log(REPO, 5, null);
        assertThat(commits).hasSizeLessThanOrEqualTo(5);
        assertThat(commits).isNotEmpty();
        assertThat(commits.get(0).hash()).hasSize(40);
        assertThat(commits.get(0).author()).isNotBlank();
        assertThat(commits.get(0).message()).isNotBlank();
    }

    @Test
    void shouldGetCommitByHash() {
        var commits = gitService.log(REPO, 1, null);
        assertThat(commits).isNotEmpty();
        GitCommit commit = gitService.getCommit(REPO, commits.get(0).hash());
        assertThat(commit).isNotNull();
        assertThat(commit.hash()).isEqualTo(commits.get(0).hash());
    }

    @Test
    void shouldGetChangedFiles() {
        var commits = gitService.log(REPO, 1, null);
        if (!commits.isEmpty()) {
            List<String> files = gitService.getChangedFiles(REPO, commits.get(0).hash());
            assertThat(files).isNotNull();
        }
    }

    @Test
    void shouldGetRemoteUrl() {
        String url = gitService.getRemoteUrl(REPO, "origin");
        // May be null if no remote set
        // Just verify it doesn't throw
    }

    @Test
    void shouldGetRepoStats() {
        var stats = gitService.getRepoStats(REPO);
        assertThat(stats).containsKey("commitCount");
    }
}
