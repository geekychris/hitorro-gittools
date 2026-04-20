package com.hitorro.gittools.git;

import com.hitorro.gittools.model.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * High-level git operations wrapping GitCommandRunner.
 */
public class GitService {

    private final GitCommandRunner runner;
    private GitCredentials credentials;

    public GitService() { this(new GitCommandRunner()); }
    public GitService(GitCommandRunner runner) { this.runner = runner; }

    public void setCredentials(GitCredentials credentials) { this.credentials = credentials; }
    public GitCredentials getCredentials() { return credentials; }

    public boolean isGitRepository(Path path) {
        return Files.isDirectory(path.resolve(".git"));
    }

    public String getCurrentBranch(Path repoPath) {
        return runner.runOrThrow(repoPath, "rev-parse", "--abbrev-ref", "HEAD");
    }

    public String getHeadCommitHash(Path repoPath) {
        return runner.runOrThrow(repoPath, "rev-parse", "HEAD");
    }

    public String getRemoteUrl(Path repoPath, String remoteName) {
        var result = runner.run(repoPath, "remote", "get-url", remoteName);
        return result.isSuccess() ? result.stdout().trim() : null;
    }

    public List<String> listRemotes(Path repoPath) {
        return runner.run(repoPath, "remote").stdoutLines();
    }

    public List<GitBranch> listBranches(Path repoPath) {
        var result = runner.run(repoPath, "branch", "-a", "--format=%(refname:short)\t%(objectname:short)\t%(if)%(HEAD)%(then)*%(end)");
        List<GitBranch> branches = new ArrayList<>();
        for (String line : result.stdoutLines()) {
            String[] parts = line.split("\t", -1);
            String name = parts.length > 0 ? parts[0].trim() : "";
            String hash = parts.length > 1 ? parts[1].trim() : "";
            if (name.isEmpty()) continue;
            boolean isRemote = name.startsWith("remotes/") || name.contains("/");
            branches.add(new GitBranch(name, isRemote, hash));
        }
        return branches;
    }

    public List<GitTag> listTags(Path repoPath) {
        var result = runner.run(repoPath, "tag", "-l", "--format=%(refname:short)\t%(objectname:short)\t%(subject)");
        List<GitTag> tags = new ArrayList<>();
        for (String line : result.stdoutLines()) {
            String[] parts = line.split("\t", -1);
            String name = parts.length > 0 ? parts[0].trim() : "";
            String hash = parts.length > 1 ? parts[1].trim() : "";
            String msg = parts.length > 2 ? parts[2].trim() : "";
            if (name.isEmpty()) continue;
            tags.add(new GitTag(name, hash, msg));
        }
        return tags;
    }

    public List<GitCommit> log(Path repoPath, int maxCount, String branch) {
        List<String> args = new ArrayList<>(List.of("log",
                "--format=%H\t%h\t%an\t%ae\t%aI\t%s",
                "-n", String.valueOf(Math.max(1, Math.min(maxCount, 500)))));
        if (branch != null && !branch.isBlank()) args.add(branch);

        var result = runner.run(repoPath, args.toArray(new String[0]));
        return parseCommitLines(result.stdoutLines());
    }

    public List<GitCommit> logRange(Path repoPath, String fromHash, String toHash) {
        var result = runner.run(repoPath, "log",
                "--format=%H\t%h\t%an\t%ae\t%aI\t%s",
                fromHash + ".." + toHash);
        return parseCommitLines(result.stdoutLines());
    }

    public GitCommit getCommit(Path repoPath, String hash) {
        var result = runner.run(repoPath, "log", "-1",
                "--format=%H\t%h\t%an\t%ae\t%aI\t%s", hash);
        var commits = parseCommitLines(result.stdoutLines());
        return commits.isEmpty() ? null : commits.get(0);
    }

    public String diff(Path repoPath, String commitHash) {
        return runner.runOrThrow(repoPath, "diff", commitHash + "~1", commitHash);
    }

    public List<String> getChangedFiles(Path repoPath, String commitHash) {
        return runner.run(repoPath, "diff-tree", "--no-commit-id", "--name-only", "-r", commitHash).stdoutLines();
    }

    // Branch operations
    public void createBranch(Path repoPath, String branchName, String startPoint) {
        runner.runOrThrow(repoPath, "checkout", "-b", branchName, startPoint);
    }

    public void checkout(Path repoPath, String branchOrHash) {
        runner.runOrThrow(repoPath, "checkout", branchOrHash);
    }

    public void cherryPick(Path repoPath, String commitHash) {
        runner.runOrThrow(repoPath, "cherry-pick", commitHash);
    }

    public void push(Path repoPath, String remoteName, String branchName) {
        if (credentials != null && credentials.hasToken()) {
            // Use authenticated URL for push
            String remoteUrl = getRemoteUrl(repoPath, remoteName);
            if (remoteUrl != null) {
                String authUrl = credentials.authenticatedUrl(remoteUrl);
                runner.runOrThrow(repoPath, "push", authUrl, branchName);
                return;
            }
        }
        runner.runOrThrow(repoPath, "push", remoteName, branchName);
    }

    // Remote operations (require credentials for private repos)

    /** Clone a repository to a local directory. */
    public void clone(String remoteUrl, Path targetDir) {
        String url = (credentials != null && credentials.hasToken())
                ? credentials.authenticatedUrl(remoteUrl)
                : remoteUrl;
        runner.runOrThrow(targetDir.getParent(), "clone", url, targetDir.getFileName().toString());

        // Configure user identity if credentials provide it
        if (credentials != null) {
            if (credentials.getGitName() != null) {
                runner.run(targetDir, "config", "user.name", credentials.getGitName());
            }
            if (credentials.getGitEmail() != null) {
                runner.run(targetDir, "config", "user.email", credentials.getGitEmail());
            }
        }
    }

    /** Fetch all remotes. */
    public GitCommandResult fetch(Path repoPath) {
        return runner.run(repoPath, "fetch", "--all");
    }

    /** Fetch a specific remote. */
    public GitCommandResult fetch(Path repoPath, String remoteName) {
        return runner.run(repoPath, "fetch", remoteName);
    }

    /** Pull current branch from remote. */
    public GitCommandResult pull(Path repoPath) {
        return runner.run(repoPath, "pull");
    }

    /** Pull specific branch from remote. */
    public GitCommandResult pull(Path repoPath, String remoteName, String branch) {
        return runner.run(repoPath, "pull", remoteName, branch);
    }

    /** Push with force option. */
    public void pushForce(Path repoPath, String remoteName, String branchName) {
        if (credentials != null && credentials.hasToken()) {
            String remoteUrl = getRemoteUrl(repoPath, remoteName);
            if (remoteUrl != null) {
                runner.runOrThrow(repoPath, "push", "--force",
                        credentials.authenticatedUrl(remoteUrl), branchName);
                return;
            }
        }
        runner.runOrThrow(repoPath, "push", "--force", remoteName, branchName);
    }

    /** Set remote URL (useful after authentication changes). */
    public void setRemoteUrl(Path repoPath, String remoteName, String url) {
        runner.runOrThrow(repoPath, "remote", "set-url", remoteName, url);
    }

    /** Add a remote. */
    public void addRemote(Path repoPath, String remoteName, String url) {
        runner.runOrThrow(repoPath, "remote", "add", remoteName, url);
    }

    // Stats
    public Map<String, Object> getRepoStats(Path repoPath) {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            var countResult = runner.run(repoPath, "rev-list", "--count", "HEAD");
            if (countResult.isSuccess()) stats.put("commitCount", Integer.parseInt(countResult.stdout().trim()));
        } catch (Exception e) { stats.put("commitCount", 0); }
        try {
            var contribResult = runner.run(repoPath, "shortlog", "-s", "-n", "HEAD");
            if (contribResult.isSuccess()) stats.put("contributorCount", contribResult.stdoutLines().size());
        } catch (Exception e) { stats.put("contributorCount", 0); }
        return stats;
    }

    private List<GitCommit> parseCommitLines(List<String> lines) {
        List<GitCommit> commits = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 6) continue;
            Instant date;
            try { date = Instant.parse(parts[4].trim()); }
            catch (Exception e) { date = Instant.EPOCH; }
            commits.add(new GitCommit(
                    parts[0].trim(), parts[1].trim(),
                    parts[2].trim(), parts[3].trim(),
                    date, parts[5].trim(), 0));
        }
        return commits;
    }

    public GitCommandRunner getRunner() { return runner; }
}
