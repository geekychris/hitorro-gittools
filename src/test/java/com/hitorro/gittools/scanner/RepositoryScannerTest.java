package com.hitorro.gittools.scanner;

import com.hitorro.gittools.git.GitService;
import com.hitorro.gittools.model.GitRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Repository Scanner")
class RepositoryScannerTest {

    private final RepositoryScanner scanner = new RepositoryScanner(new GitService());

    @Test
    void shouldFindGitRepositories() {
        // Scan the hitorro parent directory which contains many git repos
        Path root = Path.of(System.getProperty("user.dir")).getParent();
        List<GitRepository> repos = scanner.scan(root);
        assertThat(repos).isNotEmpty();
        // The hitorro root itself is a git repo
        assertThat(repos).anyMatch(r -> r.name().contains("hitorro") || r.path().toString().contains("hitorro"));
    }

    @Test
    void shouldPopulateRepoInfo() {
        Path root = Path.of(System.getProperty("user.dir")).getParent();
        List<GitRepository> repos = scanner.scan(root);
        assertThat(repos).isNotEmpty();
        GitRepository first = repos.get(0);
        assertThat(first.name()).isNotBlank();
        assertThat(first.path()).isNotNull();
        assertThat(first.currentBranch()).isNotNull();
    }

    @Test
    void shouldHandleNonExistentPath() {
        List<GitRepository> repos = scanner.scan(Path.of("/nonexistent/path"));
        assertThat(repos).isEmpty();
    }
}
