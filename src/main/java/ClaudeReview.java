import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import org.json.*;

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

    String body =
        """
        {
          "model": "claude-3-5-sonnet-20241022",
          "max_tokens": 1500,
          "messages": [
            { "role": "user", "content": "%s" }
          ]
        }
        """
            .formatted(prompt.replace("\"", "\\\""));

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

    JSONArray content = json.getJSONArray("content");
    return content.getJSONObject(0).getString("text");
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
