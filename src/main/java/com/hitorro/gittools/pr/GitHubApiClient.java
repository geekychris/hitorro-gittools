package com.hitorro.gittools.pr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * Lightweight GitHub REST API client using JDK HttpClient.
 * Modeled after pr_benchy's GitHubApiClient.
 */
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);
    private final String baseUrl;
    private final String token;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public GitHubApiClient(String token) {
        this("https://api.github.com", token);
    }

    public GitHubApiClient(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    public PullRequestResult createPullRequest(PullRequestConfig config) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("title", config.title());
            body.put("body", config.body() != null ? config.body() : "");
            body.put("head", config.headBranch());
            body.put("base", config.baseBranch());
            body.put("draft", config.draft());

            JsonNode response = apiPost(
                    "/repos/" + config.repoOwner() + "/" + config.repoName() + "/pulls",
                    body);

            int prNumber = response.get("number").asInt();
            String prUrl = response.get("html_url").asText();
            return new PullRequestResult(prNumber, prUrl, config.headBranch(), "created");

        } catch (Exception e) {
            log.error("Failed to create PR", e);
            return new PullRequestResult(0, null, config.headBranch(), "failed: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> listPullRequests(String owner, String repo, String state) {
        try {
            JsonNode response = apiGet("/repos/" + owner + "/" + repo + "/pulls?state=" + state);
            List<Map<String, Object>> prs = new ArrayList<>();
            if (response.isArray()) {
                for (JsonNode pr : response) {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("number", pr.get("number").asInt());
                    info.put("title", pr.get("title").asText());
                    info.put("state", pr.get("state").asText());
                    info.put("url", pr.get("html_url").asText());
                    prs.add(info);
                }
            }
            return prs;
        } catch (Exception e) {
            return List.of();
        }
    }

    public JsonNode apiGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body());
    }

    public JsonNode apiPost(String path, JsonNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("GitHub API error " + response.statusCode() + ": " + response.body());
        }
        return mapper.readTree(response.body());
    }
}
