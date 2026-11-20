/*
 * Copyright (c) 2006-2025 Chris Collins
 */
package com.hitorro.gittools.pr;

import com.hitorro.util.json.JsonMappers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GitHubApiClient#apiPost} sends the body as compact JSON, as on 3.x, also when it nests
 * 600 levels deep (Jackson 2's limit was 1000; Jackson 3's {@code JsonNode.toString()} throws past
 * 500).
 */
class GitHubApiClientBodyTest {

    @Test
    void postsTheBodyAsCompactJson() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/", ex -> {
            received.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(out); }
        });
        srv.start();
        try {
            String deep = "[".repeat(600) + "]".repeat(600);
            String json = "{\"title\":\"caf\\u00e9 \\\"x\\\"\",\"n\":1.10,\"deep\":" + deep + "}";
            JsonNode body = JsonMappers.json().readTree(json);
            GitHubApiClient c = new GitHubApiClient("http://127.0.0.1:" + srv.getAddress().getPort(), "t");
            JsonNode resp = c.apiPost("/repos/o/r/pulls", body);
            assertThat(resp.get("ok").booleanValue()).isTrue();
            assertThat(received.get()).isEqualTo("{\"title\":\"café \\\"x\\\"\",\"n\":1.1,\"deep\":" + deep + "}");
        } finally {
            srv.stop(0);
        }
    }
}
