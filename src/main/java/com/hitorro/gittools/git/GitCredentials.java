package com.hitorro.gittools.git;

/**
 * Git credentials for remote operations.
 * Supports HTTPS token auth and SSH key auth.
 */
public class GitCredentials {

    private String githubToken;      // Personal access token for HTTPS
    private String githubUsername;    // Username (default: "x-access-token" for token auth)
    private String sshKeyPath;       // Path to SSH private key
    private String gitEmail;         // Git user email
    private String gitName;          // Git user name

    public GitCredentials() {
        this.githubUsername = "x-access-token";
    }

    public String getGithubToken() { return githubToken; }
    public void setGithubToken(String githubToken) { this.githubToken = githubToken; }

    public String getGithubUsername() { return githubUsername; }
    public void setGithubUsername(String githubUsername) { this.githubUsername = githubUsername; }

    public String getSshKeyPath() { return sshKeyPath; }
    public void setSshKeyPath(String sshKeyPath) { this.sshKeyPath = sshKeyPath; }

    public String getGitEmail() { return gitEmail; }
    public void setGitEmail(String gitEmail) { this.gitEmail = gitEmail; }

    public String getGitName() { return gitName; }
    public void setGitName(String gitName) { this.gitName = gitName; }

    public boolean hasToken() { return githubToken != null && !githubToken.isBlank(); }
    public boolean hasSshKey() { return sshKeyPath != null && !sshKeyPath.isBlank(); }

    /**
     * Build an authenticated HTTPS URL from a GitHub repo URL.
     * Converts https://github.com/owner/repo.git to https://x-access-token:TOKEN@github.com/owner/repo.git
     */
    public String authenticatedUrl(String remoteUrl) {
        if (!hasToken() || remoteUrl == null) return remoteUrl;
        if (remoteUrl.startsWith("https://")) {
            String rest = remoteUrl.substring("https://".length());
            // Strip existing credentials if present
            if (rest.contains("@")) rest = rest.substring(rest.indexOf("@") + 1);
            return "https://" + githubUsername + ":" + githubToken + "@" + rest;
        }
        return remoteUrl;
    }

    /**
     * Redact token from a URL for logging.
     */
    public static String redactUrl(String url) {
        if (url == null) return null;
        return url.replaceAll("://[^@]+@", "://***@");
    }
}
