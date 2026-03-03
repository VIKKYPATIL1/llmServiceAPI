package com.bnpp.releasenotes.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bnpp.releasenotes.model.AppConfig;
import com.bnpp.releasenotes.model.McpJiraFunctionalDetails;
import com.bnpp.releasenotes.model.McpJiraIssue;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Fetches JIRA issues from the MCP server using JSON-RPC 2.0.
 */
public class JiraFetchService {

    private static final String JSONRPC_VERSION = "2.0";
    private static final String REQUEST_ID = "1";
    private static final String METHOD = "tools/call";
    private static final String ENVIRONMENT = "default";

    private final AppConfig config;
    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public JiraFetchService(AppConfig config) {
        this.config = config;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    public List<McpJiraIssue> fetchIssues(String fixVersion) throws IOException {
        JsonNode arguments = mapper.createObjectNode()
                .put("jql", "project = " + config.getJiraProject() + " and fixVersion = \"" + fixVersion + "\"")
                .put("environment", ENVIRONMENT)
                .put("extra_fields", "");

        String responseBody = callTool(config.getMcpToolName(), arguments);
        return parseIssuesFromJsonRpcResponse(responseBody);
    }

    public McpJiraFunctionalDetails fetchIssueByReference(String jiraId) throws IOException {
        JsonNode arguments = mapper.createObjectNode()
                .put("jiraID", jiraId)
                .put("environment", ENVIRONMENT)
                .put("extra_fields", "");

        String responseBody = callTool("get_jira_by_reference", arguments);
        JsonNode payload = parsePayloadFromJsonRpcResponse(responseBody);

        if (payload.isArray() && payload.size() > 0) {
            return mapper.treeToValue(payload.get(0), McpJiraFunctionalDetails.class);
        }
        if (payload.isObject()) {
            return mapper.treeToValue(payload, McpJiraFunctionalDetails.class);
        }

        throw new IOException("No JIRA issue returned for reference: " + jiraId);
    }

    public void addCommentToJira(String taskId, String commentMarkdown) throws IOException {
        JsonNode arguments = mapper.createObjectNode()
                .put("task_id", taskId)
                .put("comment", commentMarkdown)
                .put("environment", ENVIRONMENT);

        String responseBody = callTool("add_jira_comment", arguments);
        JsonNode root = mapper.readTree(extractDataJson(responseBody));
        if (root.has("error")) {
            throw new IOException("MCP JSON-RPC error while adding comment: " + root.get("error"));
        }

        JsonNode result = root.path("result");
        if (result.path("isError").asBoolean(false)) {
            throw new IOException("MCP add_jira_comment returned isError=true: " + result);
        }
    }

    private String callTool(String toolName, JsonNode arguments) throws IOException {
        String requestBody = buildJsonRpcRequest(toolName, arguments);

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
                throw new IOException("MCP server error HTTP " + response.code() + ": " + body);
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    private String buildJsonRpcRequest(String toolName, JsonNode arguments) throws IOException {
        return mapper.writeValueAsString(
                mapper.createObjectNode()
                        .put("jsonrpc", JSONRPC_VERSION)
                        .put("id", REQUEST_ID)
                        .put("method", METHOD)
                        .set("params", mapper.createObjectNode()
                                .put("name", toolName)
                                .set("arguments", arguments))
        );
    }

    private List<McpJiraIssue> parseIssuesFromJsonRpcResponse(String rawBody) throws IOException {
        JsonNode payload = parsePayloadFromJsonRpcResponse(rawBody);
        if (!payload.isArray()) {
            throw new IOException("Expected issue list array but got: " + payload);
        }

        List<McpJiraIssue> issues = mapper.readValue(payload.toString(), new TypeReference<>() {});
        if (issues.isEmpty()) {
            throw new IOException("No JIRA issues found. Check your fixVersion / JQL query.");
        }
        return issues;
    }

    private JsonNode parsePayloadFromJsonRpcResponse(String rawBody) throws IOException {
        JsonNode root = mapper.readTree(extractDataJson(rawBody));

        if (root.has("error")) {
            throw new IOException("MCP JSON-RPC error: " + root.get("error"));
        }

        JsonNode result = root.path("result");
        if (result.path("isError").asBoolean(false)) {
            throw new IOException("MCP returned isError=true: " + result);
        }

        JsonNode content = result.path("content");
        if (!content.isArray() || content.isEmpty()) {
            throw new IOException("MCP response missing result.content. Full response: " + root);
        }

        String payloadText = content.get(0).path("text").asText();
        if (payloadText == null || payloadText.isBlank()) {
            throw new IOException("MCP result.content[0].text is empty.");
        }

        return mapper.readTree(payloadText);
    }

    private String extractDataJson(String rawBody) throws IOException {
        String dataJson = null;
        for (String line : rawBody.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                dataJson = trimmed.substring(5).trim();
                break;
            }
        }

        if (dataJson == null) {
            dataJson = rawBody.trim();
        }

        if (dataJson.isEmpty()) {
            throw new IOException("Empty response from MCP server.");
        }
        return dataJson;
    }
}
