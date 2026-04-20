package com.hitorro.gittools.scanner;

import com.hitorro.gittools.git.GitService;
import com.hitorro.gittools.model.GitCommit;
import com.hitorro.gittools.model.GitRepository;
import com.hitorro.gittools.tagger.RepositoryTagger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Scans directory trees for git repositories.
 */
public class RepositoryScanner {

    private static final Logger log = LoggerFactory.getLogger(RepositoryScanner.class);
    private final GitService gitService;

    public RepositoryScanner(GitService gitService) {
        this.gitService = gitService;
    }

    public List<GitRepository> scan(Path root) {
        return scan(ScanConfig.of(root));
    }

    public List<GitRepository> scan(ScanConfig config) {
        List<GitRepository> repos = new CopyOnWriteArrayList<>();
        for (Path root : config.rootPaths()) {
            if (!Files.isDirectory(root)) continue;
            try {
                Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), config.maxDepth(),
                        new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                                String name = dir.getFileName().toString();
                                // Skip excluded directories
                                for (String exclude : config.excludePatterns()) {
                                    if (name.equals(exclude) || name.startsWith(".")) {
                                        if (!name.equals(".git")) return FileVisitResult.SKIP_SUBTREE;
                                    }
                                }
                                // Check if this is a git repo
                                if (Files.isDirectory(dir.resolve(".git"))) {
                                    try {
                                        repos.add(buildRepoInfo(dir));
                                    } catch (Exception e) {
                                        log.debug("Failed to read repo {}: {}", dir, e.getMessage());
                                    }
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                                return FileVisitResult.CONTINUE;
                            }
                        });
            } catch (IOException e) {
                log.error("Failed to scan {}: {}", root, e.getMessage());
            }
        }
        repos.sort(Comparator.comparing(r -> r.name().toLowerCase()));
        return repos;
    }

    public List<GitRepository> scanWithMetadata(ScanConfig config, RepositoryTagger tagger) {
        List<GitRepository> repos = scan(config);
        List<GitRepository> enriched = new ArrayList<>();
        for (GitRepository repo : repos) {
            var meta = tagger.getMetadata(repo.path());
            enriched.add(new GitRepository(
                    repo.name(), repo.path(),
                    meta.getDescription() != null ? meta.getDescription() : repo.description(),
                    meta.getTags() != null && !meta.getTags().isEmpty() ? meta.getTags() : repo.tags(),
                    repo.currentBranch(), repo.branchCount(), repo.tagCount(),
                    repo.remoteUrl(), repo.lastCommitHash(), repo.lastCommitMessage(),
                    repo.lastCommitDate(), repo.lastCommitAuthor()));
        }
        return enriched;
    }

    private GitRepository buildRepoInfo(Path repoPath) {
        String name = repoPath.getFileName().toString();
        String currentBranch = null;
        String remoteUrl = null;
        String lastHash = null, lastMsg = null, lastAuthor = null;
        java.time.Instant lastDate = null;
        int branchCount = 0, tagCount = 0;

        try { currentBranch = gitService.getCurrentBranch(repoPath); } catch (Exception e) {}
        try { remoteUrl = gitService.getRemoteUrl(repoPath, "origin"); } catch (Exception e) {}
        try {
            var commits = gitService.log(repoPath, 1, null);
            if (!commits.isEmpty()) {
                GitCommit c = commits.get(0);
                lastHash = c.shortHash();
                lastMsg = c.message();
                lastAuthor = c.author();
                lastDate = c.date();
            }
        } catch (Exception e) {}
        try { branchCount = gitService.listBranches(repoPath).size(); } catch (Exception e) {}
        try { tagCount = gitService.listTags(repoPath).size(); } catch (Exception e) {}

        return new GitRepository(name, repoPath, null, List.of(),
                currentBranch, branchCount, tagCount, remoteUrl,
                lastHash, lastMsg, lastDate, lastAuthor);
    }
}
