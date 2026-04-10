package com.example.api;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class ClaudeReview {

  static String API_URL = "https://api.anthropic.com/v1/messages";

  public static void main(String[] args) throws Exception {

    String diff = fetchDiff();

    String prompt = buildPrompt(diff);

    String response = callClaude(prompt);

    String cleaned = extractJsonArray(response);
    JSONArray issues = new JSONArray(cleaned);
    Map<String, List<DiffLine>> fileMap = parseDiff(diff);

    for (int i = 0; i < issues.length(); i++) {
      JSONObject issue = issues.getJSONObject(i);

      String file = issue.getString("file");
      int relativeLine = issue.getInt("relativeLine");
      String comment = issue.getString("comment");

      int position = mapToPosition(fileMap, file, relativeLine);

      if (position != -1) {
        postInlineComment(file, position, comment);
      }
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
  "relativeLine": number,
  "severity": "HIGH | MEDIUM | LOW",
  "comment": "clear, specific, actionable feedback"
}

Rules:
- relativeLine is the 1-based index of the added line (+) within the file
  (e.g. the 1st "+" line in the file = relativeLine 1, the 3rd = relativeLine 3)
- Count ONLY lines beginning with "+" (excluding the "+++ " file header lines)
- Reset the count to 1 for each new file in the diff
- Do NOT use actual file line numbers or diff position numbers

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
- Unnecessary complexity or "clever" code

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

- relativeLine must be the 1-based index of the "+" line within its file (e.g. 1st "+" line = 1, 5th = 5)
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
    bodyJson.put("model", "claude-haiku-4-5-20251001");
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
  static void postInlineComment(String file, int position, String comment) throws Exception {

    // Uses "position" (diff offset) as required by the GitHub PR Review Comments API
    String body =
        """
            {
              "body": "%s",
              "commit_id": "%s",
              "path": "%s",
              "position": %d
            }
            """
            .formatted(escape(comment), System.getenv("PR_SHA"), file, position);

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
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }

  // ---------------- DIFF PARSING ----------------
  static class DiffLine {
    String file;
    int lineNumber; // actual line number in the new file (for reference)
    int position; // diff position — what the GitHub API requires
    String content;

    DiffLine(String file, int lineNumber, int position, String content) {
      this.file = file;
      this.lineNumber = lineNumber;
      this.position = position;
      this.content = content;
    }
  }

  static Map<String, List<DiffLine>> parseDiff(String diff) {
    Map<String, List<DiffLine>> fileMap = new HashMap<>();

    String currentFile = null;
    int newLine = 0;
    int diffPosition = 0; // 1-indexed offset within the file's diff hunks; resets per file

    String[] lines = diff.split("\n");

    for (String line : lines) {

      if (line.startsWith("diff --git")) {
        currentFile = line.split(" b/")[1];
        fileMap.put(currentFile, new ArrayList<>());
        newLine = 0;
        diffPosition = 0; // reset for each new file

      } else if (line.startsWith("@@")) {
        String[] parts = line.split(" ");
        String newPart = parts[2];
        newLine = Integer.parseInt(newPart.split(",")[0].replace("+", ""));
        diffPosition++; // the @@ hunk header line itself counts as position 1

      } else if (line.startsWith("+") && !line.startsWith("+++")) {
        // added line — advances both diffPosition and newLine
        fileMap
            .get(currentFile)
            .add(new DiffLine(currentFile, newLine, diffPosition, line.substring(1)));
        newLine++;
        diffPosition++;

      } else if (line.startsWith("-") && !line.startsWith("---")) {
        // removed line — advances diffPosition only (line doesn't exist in new file)
        diffPosition++;

      } else if (!line.startsWith("---") && !line.startsWith("+++")) {
        // context line (unchanged) — advances both
        newLine++;
        diffPosition++;
      }
      // "---" and "+++" are file header lines; they don't count toward diff position
    }

    return fileMap;
  }

  // Maps Claude's relativeLine (Nth added line in file) → diff position for GitHub API
  static int mapToPosition(Map<String, List<DiffLine>> fileMap, String file, int relativeLine) {
    List<DiffLine> lines = fileMap.get(file);

    if (lines == null || relativeLine <= 0 || relativeLine > lines.size()) {
      return -1;
    }

    return lines.get(relativeLine - 1).position;
  }

  static String extractJsonArray(String text) {
    int start = text.indexOf('[');
    int end = text.lastIndexOf(']');

    if (start == -1 || end == -1 || end <= start) {
      throw new RuntimeException("No valid JSON array found in response:\n" + text);
    }

    return text.substring(start, end + 1);
  }
}
