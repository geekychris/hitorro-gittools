package com.hitorro.gittools.pr;

import com.hitorro.util.json.JsonMappers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import com.hitorro.util.json.JsonValues;

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
        this.mapper = JsonMappers.json();
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

            int prNumber = JsonValues.asInt(response.get("number"));
            String prUrl = JsonValues.asText(response.get("html_url"));
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
                    info.put("number", JsonValues.asInt(pr.get("number")));
                    info.put("title", JsonValues.asText(pr.get("title")));
                    info.put("state", JsonValues.asText(pr.get("state")));
                    info.put("url", JsonValues.asText(pr.get("html_url")));
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
                // JsonMappers, not body.toString(): on Jackson 3 that throws past 500 nesting levels.
                .POST(HttpRequest.BodyPublishers.ofString(JsonMappers.json().writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("GitHub API error " + response.statusCode() + ": " + response.body());
        }
        return mapper.readTree(response.body());
    }
}
