package com.hitorro.gittools;

import com.hitorro.gittools.model.*;
import com.hitorro.gittools.pr.*;
import com.hitorro.gittools.tagger.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what gittools persists and sends to what 3.x did on Jackson 2.
 *
 * <p>{@code jackson2-gittools-golden.txt} is the output of this same code run against
 * hitorro-gittools 3.0.2 (Jackson 2.18): the metadata file RepositoryTagger writes (property
 * order, pretty printing), how it reads files (unknown properties, coercions, trailing tokens,
 * malformed input), the GitHub request body, and how GitHubApiClient reads loosely typed
 * responses (asInt/asText with Jackson 2 semantics).</p>
 */
class Jackson3FormatCompatibilityTest {

  @Test
  void matchesJackson2Golden(@TempDir Path dir) throws Exception {
    var buf = new java.io.ByteArrayOutputStream();
    try (var ps = new java.io.PrintStream(buf, true, StandardCharsets.UTF_8)) { compute(dir, ps); }
    Map<String, String> actual = parse(buf.toString(StandardCharsets.UTF_8));
    Map<String, String> golden;
    try (var in = getClass().getResourceAsStream("/com/hitorro/gittools/jackson2-gittools-golden.txt")) {
      golden = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
    assertThat(actual.keySet()).containsExactlyElementsOf(golden.keySet());
    golden.forEach((k, v) -> assertThat(actual.get(k)).as(k).isEqualTo(v));
  }

  private static Map<String, String> parse(String text) {
    Map<String, String> m = new LinkedHashMap<>();
    for (String line : text.split("\n")) {
      int i = line.indexOf('=');
      if (i > 0) m.put(line.substring(0, i), line.substring(i + 1));
    }
    return m;
  }

  static String esc(String s) { return s == null ? "<null>" : s.replace("\\", "\\\\").replace("\n", "\\n"); }
  static String meta(RepositoryMetadata m) {
    return "description=" + q(m.getDescription()) + ";tags=" + m.getTags() + ";language=" + q(m.getLanguage()) + ";notes=" + q(m.getNotes());
  }
  static volatile String reqBody;
  static volatile String respBody;
  static volatile int respStatus;

  /** Runs every case and prints {@code key=value} lines, exactly as the generator run against 3.0.2 did. */
  static void compute(Path dir, java.io.PrintStream out) throws Exception {
    // ── writing ──
    Path f = dir.resolve("w/meta.json");
    RepositoryTagger t = new RepositoryTagger(f);
    Path r1 = Path.of("/golden/repo-one"), r2 = Path.of("/golden/repo-two");
    t.setDescription(r1, "Search \"engine\" é\ttab");
    t.addTag(r1, "java");
    t.addTag(r1, "search");
    t.setLanguage(r1, "Java");
    t.getMetadata(r1).setNotes("n");
    t.addTag(r2, "ml");
    t.save();
    out.println("tagger.write=" + esc(Files.readString(f)));

    // ── reading ──
    Map<String, String> inputs = new LinkedHashMap<>();
    inputs.put("declOrder", "{\"/golden/a\":{\"description\":\"d\",\"tags\":[\"x\"],\"language\":\"L\",\"notes\":null}}");
    inputs.put("alphaOrder", "{\"/golden/a\":{\"language\":\"L\",\"notes\":\"n\",\"tags\":[\"x\",\"y\"],\"description\":\"d\"}}");
    inputs.put("unknownProperty", "{\"/golden/a\":{\"description\":\"d\",\"tags\":[\"x\"],\"stars\":5}}");
    inputs.put("nullTags", "{\"/golden/a\":{\"description\":\"d\",\"tags\":null}}");
    inputs.put("missingTags", "{\"/golden/a\":{\"description\":\"d\"}}");
    inputs.put("singleStringTags", "{\"/golden/a\":{\"tags\":\"x\"}}");
    inputs.put("numberDescription", "{\"/golden/a\":{\"description\":5,\"tags\":[1,true]}}");
    inputs.put("objectDescription", "{\"/golden/a\":{\"description\":{\"x\":1},\"tags\":[\"x\"]}}");
    inputs.put("trailingTokens", "{\"/golden/a\":{\"description\":\"d\",\"tags\":[\"x\"]}} {\"junk\":1}");
    inputs.put("trailingGarbage", "{\"/golden/a\":{\"description\":\"d\",\"tags\":[\"x\"]}} xyz");
    inputs.put("truncated", "{\"/golden/a\":{\"description\":\"d\"");
    inputs.put("empty", "");
    inputs.put("nullDoc", "null");
    inputs.put("emptyObject", "{}");
    inputs.put("entryNull", "{\"/golden/a\":null,\"/golden/b\":{\"tags\":[\"z\"]}}");
    inputs.put("comments", "{/*c*/\"/golden/a\":{\"tags\":[\"x\"]}}");
    inputs.put("singleQuotes", "{'/golden/a':{'tags':['x']}}");
    for (var e : inputs.entrySet()) {
      Path in = dir.resolve("r-" + e.getKey() + ".json");
      Files.writeString(in, e.getValue());
      String got;
      try {
        RepositoryTagger rt = new RepositoryTagger(in);
        got = "allTags=" + rt.getAllTags() + ";a={" + meta(rt.getMetadata(Path.of("/golden/a"))) + "}";
      } catch (Throwable ex) {
        got = "throws " + ex.getClass().getName();
      }
      out.println("tagger.read." + e.getKey() + "=" + got);
    }

    // ── GitHub API ──
    HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    srv.createContext("/", ex -> {
      reqBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      byte[] b = respBody.getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(respStatus, b.length);
      ex.getResponseBody().write(b);
      ex.close();
    });
    srv.start();
    String base = "http://127.0.0.1:" + srv.getAddress().getPort();
    GitHubApiClient c = new GitHubApiClient(base, "tok");

    Map<String, String> creates = new LinkedHashMap<>();
    creates.put("ok", "{\"number\":12,\"html_url\":\"https://x/pr/12\",\"extra\":[1]}");
    creates.put("numberString", "{\"number\":\"12\",\"html_url\":\"u\"}");
    creates.put("numberBadString", "{\"number\":\"abc\",\"html_url\":\"u\"}");
    creates.put("numberDouble", "{\"number\":3.7,\"html_url\":\"u\"}");
    creates.put("numberBool", "{\"number\":true,\"html_url\":\"u\"}");
    creates.put("numberNull", "{\"number\":null,\"html_url\":\"u\"}");
    creates.put("numberObject", "{\"number\":{},\"html_url\":\"u\"}");
    creates.put("numberHuge", "{\"number\":12345678901,\"html_url\":\"u\"}");
    creates.put("urlNull", "{\"number\":1,\"html_url\":null}");
    creates.put("urlNumber", "{\"number\":1,\"html_url\":5}");
    creates.put("urlObject", "{\"number\":1,\"html_url\":{\"a\":1}}");
    creates.put("urlArray", "{\"number\":1,\"html_url\":[1]}");
    creates.put("urlMissing", "{\"number\":1}");
    creates.put("trailingTokens", "{\"number\":1,\"html_url\":\"u\"} {}");
    creates.put("emptyBody", "");
    for (var e : creates.entrySet()) {
      respStatus = 201; respBody = e.getValue();
      PullRequestResult res = c.createPullRequest(new PullRequestConfig("owner", "repo", "feat/x", "main", "Title \"q\" é", e.getKey().equals("ok") ? null : "body\nline", e.getKey().equals("ok")));
      out.println("gh.create." + e.getKey() + "=" + res.prNumber() + "|" + q(res.prUrl()) + "|" + status(res.status()));
      if (e.getKey().equals("ok") || e.getKey().equals("numberString")) out.println("gh.create.request." + e.getKey() + "=" + esc(reqBody));
    }
    respStatus = 422; respBody = "{\"message\":\"bad\"}";
    PullRequestResult err = c.createPullRequest(new PullRequestConfig("o", "r", "h", "b", "t", "b", false));
    out.println("gh.create.http422=" + err.prNumber() + "|" + q(err.prUrl()) + "|" + status(err.status()));

    Map<String, String> lists = new LinkedHashMap<>();
    lists.put("ok", "[{\"number\":1,\"title\":\"t\",\"state\":\"open\",\"html_url\":\"u\"},{\"number\":2,\"title\":null,\"state\":5,\"html_url\":\"v\"}]");
    lists.put("notArray", "{\"message\":\"x\"}");
    lists.put("badNumber", "[{\"number\":\"x\",\"title\":\"t\",\"state\":\"s\",\"html_url\":\"u\"}]");
    lists.put("objectTitle", "[{\"number\":1,\"title\":{},\"state\":\"s\",\"html_url\":\"u\"}]");
    lists.put("missingTitle", "[{\"number\":1,\"state\":\"s\",\"html_url\":\"u\"}]");
    lists.put("garbage", "not json");
    for (var e : lists.entrySet()) {
      respStatus = 200; respBody = e.getValue();
      out.println("gh.list." + e.getKey() + "=" + show(c.listPullRequests("o", "r", "open")));
    }
    srv.stop(0);
  }

  static String q(Object s) { return s == null ? "<null>" : "'" + s + "'"; }
  static String show(List<Map<String, Object>> l) {
    StringBuilder b = new StringBuilder("[");
    for (var m : l) { b.append("{"); m.forEach((k, v) -> b.append(k).append('=').append(q(v)).append(v == null ? "" : ":" + v.getClass().getSimpleName()).append(',')); b.append("}"); }
    return b.append("]").toString();
  }
  /** "failed: <message>": the message is Jackson's parse error text, which isn't part of the contract; keep the shape. */
  static String status(String s) {
    if (s == null || !s.startsWith("failed: ")) return s;
    String m = s.substring(8);
    if (m.startsWith("GitHub API error")) return s;
    if (m.startsWith("Cannot invoke")) return "failed: NPE";
    return "failed: <" + (m.length() > 0 ? "message" : "empty") + ">";
  }
}
