package com.bnpp.releasenotes.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bnpp.releasenotes.model.AppConfig;
import com.bnpp.releasenotes.model.McpJiraIssue;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Fetches JIRA issues from the MCP server using JSON-RPC 2.0.
 *
 * ── Protocol (from Image 1) ───────────────────────────────────────────────────
 * POST https://devops.mcp.cib.echonet/jsonrpc
 * Body:
 * {
 *   "jsonrpc": "2.0",
 *   "id": "1",
 *   "method": "tools/call",
 *   "params": {
 *     "name": "search_jira_issues",
 *     "arguments": {
 *       "jql": "project = ATH and fixVersion = \"2026.02.0\"",
 *       "environment": "default",
 *       "extra_fields": ""
 *     }
 *   }
 * }
 *
 * ── Response Format (SSE) ─────────────────────────────────────────────────────
 * The response arrives as Server-Sent Events:
 *
 *   event: message
 *   data: {"jsonrpc":"2.0","id":"1","result":{"content":[{"type":"text","text":"[{...}]"}],"isError":false}}
 *
 * Parsing chain:
 *   1. Read raw response body
 *   2. Extract the "data: ..." line
 *   3. Parse outer JSON → result.content[0].text
 *   4. Parse the text value as a JSON array → List<McpJiraIssue>
 */
public class JiraFetchService {

    private static final String JSONRPC_VERSION = "2.0";
    private static final String REQUEST_ID      = "1";
    private static final String METHOD          = "tools/call";
    private static final String ENVIRONMENT     = "default";

    private final AppConfig    config;
    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public JiraFetchService(AppConfig config) {
        this.config = config;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)  // SSE can be slow
                .build();
    }

    // ── Public ─────────────────────────────────────────────────────────────────

    /**
     * Fetches all JIRA issues for the given fixVersion.
     *
     * @param fixVersion e.g. "2026.02.0"
     * @return parsed list of JIRA issues
     */
    public List<McpJiraIssue> fetchIssues(String fixVersion) throws IOException {
        String requestBody = buildJsonRpcRequest(fixVersion);

        Request request = new Request.Builder()
                .url(config.getMcpEndpoint())
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream, application/json")
                .addHeader("Authorization", "Bearer " + config.getMcpApiKey())
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "(empty)";
                throw new IOException(
                    "MCP server error HTTP " + response.code()
                    + " for fixVersion='" + fixVersion + "': " + body);
            }

            String rawBody = response.body().string();
            return parseSseResponse(rawBody);
        }
    }

    // ── Request Builder ────────────────────────────────────────────────────────

    /**
     * Builds the JSON-RPC 2.0 request body exactly as seen in Image 1.
     * Uses ObjectMapper to ensure all values are correctly JSON-escaped.
     */
    private String buildJsonRpcRequest(String fixVersion) throws IOException {
        // Build the full request using ObjectMapper so all strings are properly escaped
        String jql = "project = " + config.getJiraProject()
                   + " and fixVersion = \"" + fixVersion + "\"";

        return mapper.writeValueAsString(
            mapper.createObjectNode()
                .put("jsonrpc", JSONRPC_VERSION)
                .put("id",      REQUEST_ID)
                .put("method",  METHOD)
                .set("params",  mapper.createObjectNode()
                    .put("name", config.getMcpToolName())
                    .set("arguments", mapper.createObjectNode()
                        .put("jql",          jql)
                        .put("environment",  ENVIRONMENT)
                        .put("extra_fields", "")
                    )
                )
        );
    }

    // ── SSE Response Parsing ───────────────────────────────────────────────────

    /**
     * Parses the SSE response body.
     *
     * SSE format (each line is either "event: X" or "data: {...}"):
     *   event: message
     *   data: {"jsonrpc":"2.0","id":"1","result":{"content":[{"type":"text","text":"[{...}]"}],"isError":false}}
     *
     * We find the "data:" line and parse it.
     */
    private List<McpJiraIssue> parseSseResponse(String rawBody) throws IOException {
        // Find the "data:" line in the SSE stream
        String dataJson = null;
        for (String line : rawBody.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                dataJson = trimmed.substring(5).trim();
                break;
            }
        }

        // If no SSE framing, the body might be plain JSON directly
        if (dataJson == null) {
            dataJson = rawBody.trim();
        }

        if (dataJson.isEmpty()) {
            throw new IOException("Empty response from MCP server.");
        }

        return parseJsonRpcResult(dataJson);
    }

    /**
     * Drills into:
     *   jsonRpcResponse → result → content[0] → text → (parse as JSON array)
     */
    private List<McpJiraIssue> parseJsonRpcResult(String dataJson) throws IOException {
        JsonNode root = mapper.readTree(dataJson);

        // Check for JSON-RPC level error
        if (root.has("error")) {
            JsonNode err = root.get("error");
            throw new IOException("MCP JSON-RPC error: " + err.toString());
        }

        // Check isError flag inside result
        JsonNode result = root.path("result");
        if (result.path("isError").asBoolean(false)) {
            throw new IOException("MCP returned isError=true: " + result.toString());
        }

        // Navigate: result → content → [0] → text
        JsonNode content = result.path("content");
        if (!content.isArray() || content.size() == 0) {
            throw new IOException(
                "MCP response missing 'result.content' array.\nFull response: " + dataJson);
        }

        String issuesJson = content.get(0).path("text").asText();
        if (issuesJson.isBlank()) {
            throw new IOException(
                "MCP result.content[0].text is empty — no JIRA data returned.");
        }

        // The text value IS a JSON array string like "[{\"id\":...},{...}]"
        try {
            List<McpJiraIssue> issues = mapper.readValue(
                issuesJson, new TypeReference<List<McpJiraIssue>>() {});

            if (issues.isEmpty()) {
                throw new IOException(
                    "No JIRA issues found. Check your fixVersion / JQL query.");
            }

            return issues;

        } catch (Exception e) {
            throw new IOException(
                "Failed to parse JIRA issues JSON from MCP text field.\n"
                + "Content (first 500 chars): "
                + issuesJson.substring(0, Math.min(500, issuesJson.length())), e);
        }
    }
}
