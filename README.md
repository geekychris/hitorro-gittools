# hitorro-gittools

A Java 21 library for programmatic Git repository management, GitHub API integration, and automated pull request creation. Part of the [HiTorro](https://github.com/hitorro) framework (`com.hitorro`, v3.0.0).

hitorro-gittools wraps the `git` CLI via `ProcessBuilder` subprocesses rather than depending on JGit, keeping the dependency footprint minimal (Jackson + SLF4J only). It provides high-level services for querying repository state, scanning directory trees for repos, attaching custom metadata, selecting commits by filter criteria, and orchestrating branch-creation-to-PR workflows against the GitHub REST API using the JDK `HttpClient`.

## Architecture

### Class Diagram

```mermaid
classDiagram
    direction TB

    class GitCommandRunner {
        -long timeoutMs
        +run(Path, String...) GitCommandResult
        +runOrThrow(Path, String...) String
    }

    class GitCommandResult {
        <<record>>
        +int exitCode
        +String stdout
        +String stderr
        +long durationMs
        +isSuccess() boolean
        +stdoutLines() List~String~
    }

    class GitCredentials {
        -String githubToken
        -String githubUsername
        -String sshKeyPath
        -String gitEmail
        -String gitName
        +hasToken() boolean
        +hasSshKey() boolean
        +authenticatedUrl(String) String
        +redactUrl(String) String$
    }

    class GitService {
        -GitCommandRunner runner
        -GitCredentials credentials
        +log(Path, int, String) List~GitCommit~
        +logRange(Path, String, String) List~GitCommit~
        +getCommit(Path, String) GitCommit
        +listBranches(Path) List~GitBranch~
        +listTags(Path) List~GitTag~
        +clone(String, Path) void
        +fetch(Path) GitCommandResult
        +pull(Path) GitCommandResult
        +push(Path, String, String) void
        +createBranch(Path, String, String) void
        +checkout(Path, String) void
        +cherryPick(Path, String) void
        +diff(Path, String) String
        +getChangedFiles(Path, String) List~String~
        +getRepoStats(Path) Map
    }

    class RepositoryScanner {
        -GitService gitService
        +scan(Path) List~GitRepository~
        +scan(ScanConfig) List~GitRepository~
        +scanWithMetadata(ScanConfig, RepositoryTagger) List~GitRepository~
    }

    class ScanConfig {
        <<record>>
        +List~Path~ rootPaths
        +int maxDepth
        +List~String~ excludePatterns
    }

    class RepositoryTagger {
        -Path metadataFile
        -ObjectMapper mapper
        -Map metadataMap
        +getMetadata(Path) RepositoryMetadata
        +setDescription(Path, String) void
        +addTag(Path, String) void
        +removeTag(Path, String) void
        +findByTag(String) List~String~
        +getAllTags() List~String~
        +load() void
        +save() void
    }

    class CommitSelector {
        -GitService gitService
        +selectCommits(Path, String, int, String, String, Instant, Instant) List~GitCommit~
        +selectByHashes(Path, List~String~) List~GitCommit~
        +selectRange(Path, CommitRange) List~GitCommit~
    }

    class PullRequestCreator {
        -GitService gitService
        -BranchCreator branchCreator
        -GitHubApiClient githubClient
        +createPrFromCommit(Path, String, String, String, PullRequestConfig) PullRequestResult
        +createPrsFromCommits(Path, String, String, BatchPrJob) List~PullRequestResult~
    }

    class BranchCreator {
        -GitService gitService
        +createBranchFromCommit(Path, String, String, String) String
        +createBranchesFromCommits(Path, String, List~String~, String) List~String~
    }

    class GitHubApiClient {
        -String baseUrl
        -String token
        -HttpClient httpClient
        -ObjectMapper mapper
        +createPullRequest(PullRequestConfig) PullRequestResult
        +listPullRequests(String, String, String) List~Map~
        +apiGet(String) JsonNode
        +apiPost(String, JsonNode) JsonNode
    }

    GitService --> GitCommandRunner : delegates to
    GitService --> GitCredentials : uses
    GitCommandRunner --> GitCommandResult : returns
    RepositoryScanner --> GitService : uses
    RepositoryScanner --> ScanConfig : configured by
    RepositoryScanner --> RepositoryTagger : enriches with
    CommitSelector --> GitService : queries via
    PullRequestCreator --> GitService : git operations
    PullRequestCreator --> BranchCreator : branch creation
    PullRequestCreator --> GitHubApiClient : PR creation
    BranchCreator --> GitService : delegates to
```

### PR Creation Flow

```mermaid
sequenceDiagram
    participant Caller
    participant PullRequestCreator
    participant BranchCreator
    participant GitService
    participant GitCommandRunner
    participant GitHubApiClient
    participant GitHub API

    Caller->>PullRequestCreator: createPrFromCommit(repoPath, baseBranch, commitHash, remote, config)
    PullRequestCreator->>BranchCreator: createBranchFromCommit(repoPath, baseBranch, commitHash, template)
    BranchCreator->>GitService: getCurrentBranch(repoPath)
    GitService->>GitCommandRunner: run("rev-parse", "--abbrev-ref", "HEAD")
    GitCommandRunner-->>GitService: current branch name
    BranchCreator->>GitService: checkout(repoPath, baseBranch)
    BranchCreator->>GitService: createBranch(repoPath, branchName, baseBranch)
    BranchCreator->>GitService: cherryPick(repoPath, commitHash)
    BranchCreator->>GitService: checkout(repoPath, originalBranch)
    BranchCreator-->>PullRequestCreator: branchName

    PullRequestCreator->>GitService: push(repoPath, remote, branchName)
    GitService->>GitCommandRunner: run("push", authUrl, branchName)
    GitCommandRunner-->>GitService: success

    PullRequestCreator->>GitHubApiClient: createPullRequest(config)
    GitHubApiClient->>GitHub API: POST /repos/{owner}/{repo}/pulls
    GitHub API-->>GitHubApiClient: { number, html_url }
    GitHubApiClient-->>PullRequestCreator: PullRequestResult
    PullRequestCreator-->>Caller: PullRequestResult(prNumber, prUrl, branchName, status)
```

## Module Structure

```
hitorro-gittools/
  src/main/java/com/hitorro/gittools/
    git/
      GitCommandRunner.java      Subprocess execution via ProcessBuilder
      GitCommandResult.java      Record: exitCode, stdout, stderr, durationMs
      GitCredentials.java        PAT token / SSH key management, URL authentication
      GitService.java            High-level git operations (log, branch, push, clone, ...)
    model/
      GitCommit.java             Record: hash, author, date, message
      GitBranch.java             Record: name, isRemote, lastCommitHash
      GitTag.java                Record: name, commitHash, message
      GitRepository.java         Record: full repo snapshot (name, path, branch, remote, ...)
      RepositoryMetadata.java    Mutable metadata bean (description, tags, language, notes)
    scanner/
      RepositoryScanner.java     Walks directory trees to discover .git repositories
      ScanConfig.java            Record: rootPaths, maxDepth, excludePatterns
    tagger/
      RepositoryTagger.java      JSON-backed metadata store keyed by repo path
    commits/
      CommitSelector.java        Filtered commit queries (author, message, date range)
      CommitRange.java           Record: fromHash, toHash
    pr/
      PullRequestCreator.java    End-to-end commit-to-PR orchestration
      BranchCreator.java         Cherry-pick-based branch creation
      GitHubApiClient.java       GitHub REST API client (JDK HttpClient)
      PullRequestConfig.java     Record: owner, repo, base, head, title, body, draft
      PullRequestResult.java     Record: prNumber, prUrl, branchName, status
      BatchPrJob.java            Record: batch PR job definition
  src/test/java/com/hitorro/gittools/
    git/GitServiceTest.java
    scanner/RepositoryScannerTest.java
    tagger/RepositoryTaggerTest.java
```

## Key Classes

| Class | Description |
|-------|-------------|
| **GitCommandRunner** | Executes `git` as a subprocess via `ProcessBuilder`. Configurable timeout (default 120s). Returns structured `GitCommandResult` with exit code, stdout, stderr, and duration. |
| **GitService** | Facade over `GitCommandRunner` providing typed operations: `log`, `logRange`, `getCommit`, `listBranches`, `listTags`, `clone`, `fetch`, `pull`, `push`, `createBranch`, `checkout`, `cherryPick`, `diff`, `getChangedFiles`, `getRepoStats`. Automatically injects PAT tokens into push/clone URLs when `GitCredentials` is set. |
| **GitCredentials** | Holds GitHub PAT token, SSH key path, and git user identity. Converts HTTPS URLs to authenticated form (`https://x-access-token:TOKEN@github.com/...`). Provides `redactUrl()` for safe logging. |
| **RepositoryScanner** | Walks directory trees using `Files.walkFileTree` to find `.git` directories. Configurable via `ScanConfig` (max depth, exclude patterns like `node_modules`, `target`, `.m2`). Returns enriched `GitRepository` records with branch/tag counts, remote URL, and latest commit info. Optionally merges custom metadata from a `RepositoryTagger`. |
| **RepositoryTagger** | JSON-backed metadata store (Jackson `ObjectMapper`). Maps canonical repository paths to `RepositoryMetadata` objects (description, tags, language, notes). Supports tag-based queries via `findByTag()`. Auto-persists on every mutation. |
| **CommitSelector** | Queries commits with filter criteria: author substring, message substring, date range (`afterDate`/`beforeDate`), max count. Also supports explicit hash lists and hash ranges. |
| **PullRequestCreator** | Orchestrates the full commit-to-PR pipeline: creates a branch from a base via cherry-pick, pushes to remote, and opens a PR through the GitHub API. Supports batch creation via `BatchPrJob` with configurable title/body templates (supports `{hash}` placeholder). |
| **GitHubApiClient** | Lightweight GitHub REST API client built on `java.net.http.HttpClient`. Supports `createPullRequest` and `listPullRequests`. Exposes generic `apiGet`/`apiPost` for arbitrary endpoints. Bearer token authentication. |

## Build

```bash
# Build with tests
cd hitorro-gittools
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=GitServiceTest

# Run a single test method
mvn test -Dtest=RepositoryTaggerTest#shouldManageTags
```

**Requirements:** Java 21+, Maven 3.9+, `git` on `PATH`.

## Usage Examples

### Querying a Repository with GitService

```java
import com.hitorro.gittools.git.*;
import com.hitorro.gittools.model.*;
import java.nio.file.Path;

Path repo = Path.of("/home/user/my-project");
GitService git = new GitService();

// Basic queries
String branch = git.getCurrentBranch(repo);
String head = git.getHeadCommitHash(repo);
String remoteUrl = git.getRemoteUrl(repo, "origin");

// Commit log
List<GitCommit> recent = git.log(repo, 10, "main");
for (GitCommit c : recent) {
    System.out.printf("%s %s - %s%n", c.shortHash(), c.author(), c.message());
}

// Branches and tags
List<GitBranch> branches = git.listBranches(repo);
List<GitTag> tags = git.listTags(repo);

// Diff and changed files for a commit
String diff = git.diff(repo, "abc1234f");
List<String> changedFiles = git.getChangedFiles(repo, "abc1234f");

// Repository statistics
Map<String, Object> stats = git.getRepoStats(repo);
System.out.println("Total commits: " + stats.get("commitCount"));
```

### Scanning for Repositories

```java
import com.hitorro.gittools.scanner.*;
import com.hitorro.gittools.git.GitService;

RepositoryScanner scanner = new RepositoryScanner(new GitService());

// Simple scan with defaults (maxDepth=4, excludes node_modules/target/.m2)
List<GitRepository> repos = scanner.scan(Path.of("/home/user/code"));

// Custom scan configuration
ScanConfig config = new ScanConfig(
    List.of(Path.of("/home/user/work"), Path.of("/home/user/personal")),
    3,  // maxDepth
    List.of("node_modules", "vendor", ".cache")
);
List<GitRepository> allRepos = scanner.scan(config);

for (GitRepository r : allRepos) {
    System.out.printf("%-30s %-12s %s%n", r.name(), r.currentBranch(), r.remoteUrl());
}
```

### Tagging Repositories with Metadata

```java
import com.hitorro.gittools.tagger.RepositoryTagger;

RepositoryTagger tagger = new RepositoryTagger(Path.of("repo-metadata.json"));

Path repo = Path.of("/home/user/my-project");
tagger.setDescription(repo, "Main search platform");
tagger.addTag(repo, "java");
tagger.addTag(repo, "lucene");
tagger.setLanguage(repo, "Java");

// Query by tag
List<String> javaRepos = tagger.findByTag("java");

// Enrich scanned repos with metadata
RepositoryScanner scanner = new RepositoryScanner(new GitService());
ScanConfig config = ScanConfig.of(Path.of("/home/user/code"));
List<GitRepository> enriched = scanner.scanWithMetadata(config, tagger);
```

### Selecting Commits with Filters

```java
import com.hitorro.gittools.commits.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

CommitSelector selector = new CommitSelector(new GitService());
Path repo = Path.of("/home/user/my-project");

// Filter by author and date range
Instant oneWeekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
var commits = selector.selectCommits(
    repo, "main", 100,
    "chris",           // author filter (case-insensitive substring)
    "fix",             // message filter (case-insensitive substring)
    oneWeekAgo,        // after date
    null               // before date (no upper bound)
);

// Select by explicit hashes
var specific = selector.selectByHashes(repo, List.of("abc1234", "def5678"));

// Select a commit range
var range = selector.selectRange(repo, new CommitRange("abc1234", "HEAD"));
```

### Creating Pull Requests

```java
import com.hitorro.gittools.git.*;
import com.hitorro.gittools.pr.*;

// Set up credentials
GitCredentials creds = new GitCredentials();
creds.setGithubToken(System.getenv("GITHUB_TOKEN"));
creds.setGitName("CI Bot");
creds.setGitEmail("ci@example.com");

GitService git = new GitService();
git.setCredentials(creds);

GitHubApiClient github = new GitHubApiClient(creds.getGithubToken());
PullRequestCreator prCreator = new PullRequestCreator(git, github);

// Create a single PR from a commit
Path repo = Path.of("/home/user/my-project");
PullRequestConfig config = new PullRequestConfig(
    "myorg", "my-project", "main", null,
    "Fix: resolve null pointer in parser",
    "Cherry-picked from main development branch.",
    false  // not a draft
);

PullRequestResult result = prCreator.createPrFromCommit(
    repo, "main", "abc12345abcdef", "origin", config);

System.out.println("PR #" + result.prNumber() + ": " + result.prUrl());

// Batch PR creation
BatchPrJob batch = new BatchPrJob(
    List.of("aaa11111", "bbb22222", "ccc33333"),
    "main",
    "pr-bench/{hash}",
    "Benchmark: {hash}",
    "Automated benchmark PR."
);
List<PullRequestResult> results = prCreator.createPrsFromCommits(
    repo, "main", "origin", batch);
```

### Cloning with Authentication

```java
GitCredentials creds = new GitCredentials();
creds.setGithubToken(System.getenv("GITHUB_TOKEN"));
creds.setGitName("Chris Collins");
creds.setGitEmail("chris@example.com");

GitService git = new GitService();
git.setCredentials(creds);

// Clone injects token into URL and configures user.name / user.email
git.clone("https://github.com/myorg/private-repo.git", Path.of("/tmp/private-repo"));
```

## Integration

### hitorro-pr-bench

The `hitorro-pr-bench` module depends on `hitorro-gittools` as a Maven dependency and uses it to drive its PR benchmarking pipeline:

- **GitService** and **GitCredentials** for authenticated git operations (clone, push)
- **CommitSelector** to pick commits matching benchmark criteria
- **PullRequestCreator** and **BatchPrJob** to create PRs from selected commits
- **GitHubApiClient** to interact with GitHub (create PRs, list PRs, query status)

### hitorro-example-springboot / hitorro-jvs-example-springboot

Spring Boot applications can use hitorro-gittools by adding the Maven dependency. The library is Spring-agnostic -- instantiate `GitService`, `RepositoryScanner`, and other classes directly or register them as beans:

```java
@Configuration
public class GitToolsConfig {

    @Bean
    public GitCredentials gitCredentials(@Value("${github.token}") String token) {
        GitCredentials creds = new GitCredentials();
        creds.setGithubToken(token);
        return creds;
    }

    @Bean
    public GitService gitService(GitCredentials credentials) {
        GitService service = new GitService();
        service.setCredentials(credentials);
        return service;
    }

    @Bean
    public RepositoryScanner repositoryScanner(GitService gitService) {
        return new RepositoryScanner(gitService);
    }
}
```

## Configuration

### Credentials

| Property | Source | Description |
|----------|--------|-------------|
| GitHub PAT | `GitCredentials.setGithubToken()` | Personal access token for HTTPS auth and GitHub API calls |
| GitHub username | `GitCredentials.setGithubUsername()` | Defaults to `x-access-token` (standard for PAT auth) |
| SSH key path | `GitCredentials.setSshKeyPath()` | Path to private SSH key (for SSH-based remotes) |
| Git name | `GitCredentials.setGitName()` | Configured as `user.name` in cloned repos |
| Git email | `GitCredentials.setGitEmail()` | Configured as `user.email` in cloned repos |

### Environment Variables

The library does not read environment variables directly. The calling application is responsible for passing configuration values. A typical pattern:

```java
GitCredentials creds = new GitCredentials();
creds.setGithubToken(System.getenv("GITHUB_TOKEN"));
creds.setGitName(System.getenv("GIT_AUTHOR_NAME"));
creds.setGitEmail(System.getenv("GIT_AUTHOR_EMAIL"));
```

### GitCommandRunner Timeout

The default subprocess timeout is 120 seconds. Override via constructor:

```java
GitCommandRunner runner = new GitCommandRunner(300_000); // 5 minutes
GitService git = new GitService(runner);
```

### ScanConfig Defaults

| Parameter | Default |
|-----------|---------|
| `maxDepth` | 4 |
| `excludePatterns` | `node_modules`, `.m2`, `target`, `build` |

## Testing

Tests use JUnit 5 (Jupiter) with AssertJ assertions. The test suite operates against real git repositories found in the project's parent directory.

```bash
# Run all tests
mvn test

# Run specific test classes
mvn test -Dtest=GitServiceTest
mvn test -Dtest=RepositoryScannerTest
mvn test -Dtest=RepositoryTaggerTest
```

**Test classes:**

- **GitServiceTest** -- Validates core git operations (branch detection, log parsing, commit lookup, changed files, repo stats) against the actual hitorro repository.
- **RepositoryScannerTest** -- Verifies directory scanning discovers git repos, populates metadata, and handles non-existent paths gracefully.
- **RepositoryTaggerTest** -- Tests metadata CRUD (descriptions, tags, language), JSON persistence and reload, and tag-based queries. Uses `@TempDir` for isolated file I/O.

**Dependencies (test scope only):** JUnit Jupiter 5.11.4, AssertJ 3.27.3, SLF4J Simple.
