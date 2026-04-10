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
You are a senior software engineer performing a professional pull request review.

Your goal is to identify only real, high-value issues based on industry PR review standards.

Return ONLY a valid JSON array. No explanation. No markdown. No extra text.

Each item MUST follow this exact structure:
{
  "file": "string",
  "line": number,
  "severity": "HIGH | MEDIUM | LOW",
  "comment": "clear, specific, actionable feedback"
}

------------------------
REVIEW PRIORITIES
------------------------

Focus on issues in this priority order:

1. Correctness & Bugs
- Incorrect logic
- Null pointer risks
- Missing edge case handling
- Invalid assumptions
- Broken business logic

2. Security
- Exposure of secrets or sensitive data
- Missing input validation
- Unsafe queries or injections
- Authentication/authorization issues

3. Design & Architecture
- Violations of separation of concerns
- Poor layering (Controller/Service/Repository misuse)
- Tight coupling or poor abstractions
- Unnecessary complexity or “clever” code

4. Performance
- Inefficient loops or repeated API/DB calls
- N+1 query patterns
- Expensive operations in hot paths

5. Error Handling & Logging
- Missing or weak exception handling
- Silent failures
- Poor or excessive logging
- Logging sensitive data

6. Testing Gaps (only if visible in diff)
- Missing tests for critical logic
- Edge cases not covered
- Incorrect or misleading tests

7. Code Quality & Maintainability
- formatting or styling
- minor naming preferences
- trivial refactoring
- comments or documentation
- imports or whitespace

------------------------
SEVERITY RULES
------------------------

- HIGH → will likely cause bugs, crashes, or security issues
- MEDIUM → risky, fragile, or suboptimal implementation
- LOW → minor improvement with clear benefit

------------------------
OUTPUT RULES
------------------------

- Use ONLY line numbers from added lines (+) in the diff
- Be precise and concise (max 2 sentences per comment)
- Suggest a fix or improvement whenever possible
- Do NOT guess missing context
- Avoid duplicate or overlapping comments
- Return at most 15 issues
- If no issues found, return: []

------------------------
DIFF
------------------------
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
    bodyJson.put("model", "claude-3-haiku-20240307");
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
