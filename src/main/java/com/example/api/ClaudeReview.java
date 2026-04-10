package com.example.api;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import org.json.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class ClaudeReview {

  static String API_URL = "https://api.anthropic.com/v1/messages";

  public static void main(String[] args) throws Exception {

    String diff = fetchDiff();

    String prompt = buildPrompt(diff);

    String response = callClaude(prompt);

    JSONArray issues = new JSONArray(response);

    for (int i = 0; i < issues.length(); i++) {
      JSONObject issue = issues.getJSONObject(i);

      postInlineComment(issue.getString("file"), issue.getInt("line"), issue.getString("comment"));
    }

    System.out.println("Review completed.");
  }

  // ---------------- FETCH DIFF ----------------
  static String fetchDiff() throws Exception {
    String pr = System.getenv("PR");
    String repo = System.getenv("REPO");

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("https://api.github.com/repos/" + repo + "/pulls/" + pr))
            .header("Authorization", "token " + System.getenv("GITHUB_TOKEN"))
            .header("Accept", "application/vnd.github.v3.diff")
            .build();

    HttpClient client = HttpClient.newHttpClient();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.body().length() > 15000) {
      return response.body().substring(0, 15000);
    }
    return response.body();
  }

  // ---------------- PROMPT ----------------
  static String buildPrompt(String diff) {
    return """
You are a senior software engineer reviewing a pull request.

Return ONLY valid JSON array.
No explanation.
No markdown.
No extra text.

Each item must contain:
- file (string)
- line (number)
- comment (string)
- severity (HIGH | MEDIUM | LOW)

Rules:
- Only use valid JSON
- Be precise and actionable
- Use real line numbers from diff

Diff:
"""
        + diff;
  }

  // ---------------- CLAUDE API ----------------
  static String callClaude(String prompt) throws Exception {

    JSONObject userMessage = new JSONObject();
    userMessage.put("role", "user");
    userMessage.put("content", prompt);

    JSONArray messages = new JSONArray();
    messages.put(userMessage);

    JSONObject bodyJson = new JSONObject();
    bodyJson.put("model", "claude-3-5-sonnet-20241022");
    bodyJson.put("temperature", 0);
    bodyJson.put("max_tokens", 1500);
    bodyJson.put("messages", messages);

    String body = bodyJson.toString();

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("x-api-key", System.getenv("ANTHROPIC_API_KEY"))
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

    HttpClient client = HttpClient.newHttpClient();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    JSONObject json = new JSONObject(response.body());

    if (json.has("error")) {
      throw new RuntimeException("Claude API error: " + json.getJSONObject("error"));
    }

    JSONArray content = json.getJSONArray("content");

    for (int i = 0; i < content.length(); i++) {
      JSONObject part = content.getJSONObject(i);

      if ("text".equals(part.getString("type"))) {
        return part.getString("text");
      }
    }

    throw new RuntimeException("No text content found in Claude response");
  }

  // ---------------- INLINE COMMENT ----------------
  static void postInlineComment(String file, int line, String comment) throws Exception {

    String body =
        """
        {
          "body": "%s",
          "commit_id": "%s",
          "path": "%s",
          "line": %d,
          "side": "RIGHT"
        }
        """
            .formatted(escape(comment), System.getenv("PR_SHA"), file, line);

    String repo = System.getenv("REPO");
    String pr = System.getenv("PR");

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("https://api.github.com/repos/" + repo + "/pulls/" + pr + "/comments"))
            .header("Authorization", "token " + System.getenv("GITHUB_TOKEN"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

    HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  // ---------------- ESCAPE JSON ----------------
  static String escape(String s) {
    return s.replace("\"", "\\\"");
  }
}
