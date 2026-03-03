package com.bnpp.releasenotes.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bnpp.releasenotes.model.AppConfig;
import com.bnpp.releasenotes.model.LlmSlideResponse;
import com.bnpp.releasenotes.model.McpJiraIssue;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sends JIRA issue data to the internal LLM endpoint and parses the structured
 * JSON slide response.
 *
 * ── LLM Endpoint (from Image 2) ──────────────────────────────────────────────
 * POST https://api.llm.cib.echonet/v1/openai/chat/completions
 * {
 *   "stream": false,
 *   "model": "codestral-latest-ITG",
 *   "messages": [ { "role":"user", "content":"..." } ],
 *   "temperature": 0.1,
 *   "max_tokens": 4096
 * }
 *
 * Response: Standard OpenAI format → choices[0].message.content
 *
 * ── Table Columns Produced ────────────────────────────────────────────────────
 * Sr | JIRA_No | JIRA_Description | Change_Type | Why_Change_Required | Requirement_Type | Signoff_By
 *
 * ── Column Derivation Rules ───────────────────────────────────────────────────
 * Change_Type:
 *   "Functional" → new feature, UI flow, UI enrichment, user story, enhancement
 *   "Bug"        → bug, defect, error, failure, fix, crash, issue, regression
 *   "Technical"  → refactor, upgrade, migration, configuration, dependency, CI/CD, performance
 *
 * Requirement_Type (derived from Change_Type):
 *   Functional → "New Feature"
 *   Bug        → "Bug Fix"
 *   Technical  → "Technical"
 *
 * Signoff_By: always "Chetan"
 */
public class LlmService {

    /** Max JIRA rows per slide — LLM is instructed to respect this. */
    static final int MAX_ROWS_PER_SLIDE = 5;

    private final AppConfig    config;
    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmService(AppConfig config) {
        this.config = config;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .build();
    }

    // ── Public ─────────────────────────────────────────────────────────────────

    public LlmSlideResponse generateSlides(String releaseVersion, List<McpJiraIssue> issues)
            throws IOException {

        if (issues == null || issues.isEmpty()) {
            throw new IOException("No JIRA issues to process for: " + releaseVersion);
        }

        String userContent = buildUserContent(releaseVersion, issues);
        String rawJson     = callLlm(userContent);
        return parseResponse(rawJson);
    }

    public String generateFunctionalTestCasesCsv(String jiraTitle, String jiraDescription)
            throws IOException {
        if (jiraTitle == null || jiraTitle.isBlank()) {
            throw new IOException("JIRA title is empty, unable to generate test cases.");
        }

        String userPrompt = buildFunctionalTestCasesPrompt(jiraTitle, jiraDescription);
        String rawResponse = callLlmWithSystemPrompt(buildFunctionalCsvSystemPrompt(), userPrompt);
        return sanitizeCsvOutput(rawResponse);
    }

    // ── System Prompt ──────────────────────────────────────────────────────────

    private String buildSystemPrompt() {
        return """
            You are a release notes assistant. Your task is to analyze JIRA ticket data and \
            produce a structured JSON payload for PowerPoint slide generation.

            === TABLE COLUMNS (in order) ===
            Sr               | Auto-incremented serial number (integer, starts at 1 globally)
            JIRA_No          | Ticket key exactly as provided (e.g. ATH-6496)
            JIRA_Description | Ticket summary exactly as provided
            Change_Type      | One of exactly: "Functional", "Bug", or "Technical"
            Why_Change_Required | Exactly 2 concise sentences derived from the description
            Requirement_Type | Derived from Change_Type:
                               Functional  → "New Feature"
                               Bug         → "Bug Fix"
                               Technical   → "Technical"
            Signoff_By       | Always the fixed string "Chetan"

            === CHANGE_TYPE CLASSIFICATION RULES ===
            "Functional"  → description mentions: new feature, UI flow, enrichment, user story,
                            enhancement, new screen, new functionality, new behaviour
            "Bug"         → description mentions: bug, defect, error, failure, fix, crash,
                            issue, regression, incorrect, wrong, broken, not working
            "Technical"   → anything else: refactor, upgrade, migration, configuration,
                            dependency update, CI/CD, performance, security, code quality,
                            toolchain, build change, library update

            === SLIDE RULES ===
            - Maximum 5 rows per slide.
            - If there are more than 5 tickets, create additional slides.
            - Slide title format: "Release Notes - <version> (<slideNum>/<totalSlides>)"
            - Sr numbers are GLOBAL (continue across slides: 1,2,3,4,5 on slide1 then 6,7... on slide2)

            === STRICT OUTPUT RULES ===
            - Return ONLY valid JSON. No markdown, no code fences, no explanation.
            - Every string field must be non-null.
            - "sr" must be an integer, not a string.

            === REQUIRED JSON STRUCTURE ===
            {
              "releaseVersion": "<version>",
              "slides": [
                {
                  "title": "Release Notes - 2026.02.0 (1/1)",
                  "rows": [
                    {
                      "sr": 1,
                      "jiraNo": "ATH-6496",
                      "jiraDescription": "Java 11 migration for Athena UI module",
                      "changeType": "Technical",
                      "whyChangeRequired": "The application required migration from JDK 8 to Java 11 for long-term support compatibility. Tool-chain configurations, shell scripts, and classpath settings were updated accordingly.",
                      "requirementType": "Technical",
                      "signoffBy": "Chetan"
                    }
                  ]
                }
              ]
            }
            """;
    }

    private String buildFunctionalCsvSystemPrompt() {
        return """
            You are a QA engineer.
            Output must be STRICT CSV only. Do not add notes before or after CSV.

            Required header row exactly:
            "Test_Case_Number","Test_Case_Scenario","Input","Output","Description"

            Rules:
            - Generate comprehensive functional UI test scenarios and edge cases.
            - Test number format: TC-01, TC-02, TC-03 ...
            - Keep language concise but complete.
            - Every field value in every row must be double-quoted.
            - Do not output markdown, bullet points, or explanations.
            """;
    }

    private String buildFunctionalTestCasesPrompt(String jiraTitle, String jiraDescription) {
        String description = jiraDescription == null || jiraDescription.isBlank()
                ? "No description provided."
                : jiraDescription;

        return """
            Create functional UI test cases in CSV format.

            Jira Title:
            %s

            Jira Description:
            %s
            """.formatted(cleanDescription(jiraTitle), cleanDescription(description));
    }

    // ── User Prompt ────────────────────────────────────────────────────────────

    private String buildUserContent(String version, List<McpJiraIssue> issues) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate the release notes slide JSON for:\n");
        sb.append("Release Version: ").append(version).append("\n");
        sb.append("Total JIRA tickets: ").append(issues.size()).append("\n\n");
        sb.append("=== JIRA TICKETS ===\n\n");

        for (int i = 0; i < issues.size(); i++) {
            McpJiraIssue issue = issues.get(i);
            sb.append("--- Ticket ").append(i + 1).append(" ---\n");
            sb.append("Key:         ").append(issue.getKey()).append("\n");
            sb.append("Summary:     ").append(issue.getSummary()).append("\n");
            sb.append("Status:      ").append(issue.getStatus()).append("\n");
            sb.append("Description: ").append(cleanDescription(issue.safeDescription())).append("\n");
            sb.append("\n");
        }

        sb.append("Now generate the JSON. Remember:\n");
        sb.append("- Max ").append(MAX_ROWS_PER_SLIDE).append(" rows per slide\n");
        sb.append("- Sr numbers are global across slides\n");
        sb.append("- signoffBy is always \"Chetan\"\n");
        sb.append("- Return ONLY JSON, nothing else.");

        return sb.toString();
    }

    /**
     * Strips Jira wiki markup (h1., h2., *bold*, {code} blocks etc.)
     * to give the LLM clean plain text to work with.
     */
    private String cleanDescription(String raw) {
        if (raw == null || raw.isBlank()) return "No description provided.";

        String cleaned = raw
            // Jira headings: h1. h2. etc.
            .replaceAll("h[1-6]\\.", "")
            // Jira bold/italic: *text* _text_
            .replaceAll("[*_]", "")
            // Jira code blocks
            .replaceAll("\\{code[^}]*\\}.*?\\{code\\}", "[code block]")
            .replaceAll("\\{[^}]+\\}", "")
            // Normalise whitespace
            .replaceAll("\\\\r\\\\n|\\\\n|\\r\\n|\\r", " ")
            .replaceAll("\\s{2,}", " ")
            .trim();

        // Truncate to 600 chars — keeps prompt size reasonable
        return cleaned.length() > 600 ? cleaned.substring(0, 597) + "..." : cleaned;
    }

    // ── LLM HTTP Call ──────────────────────────────────────────────────────────

    /**
     * Calls the internal OpenAI-compatible endpoint exactly as seen in Image 2.
     */
    private String callLlm(String userContent) throws IOException {
        return callLlmWithSystemPrompt(buildSystemPrompt(), userContent);
    }

    private String callLlmWithSystemPrompt(String systemPrompt, String userContent) throws IOException {
        // Build request body matching Image 2 exactly
        String bodyJson = mapper.writeValueAsString(
            mapper.createObjectNode()
                .put("stream", false)
                .put("model",  config.getLlmModel())
                .put("temperature", 0.1)
                .put("max_tokens",  4096)
                .set("messages", mapper.createArrayNode()
                    .add(mapper.createObjectNode()
                        .put("role", "system")
                        .put("content", systemPrompt))
                    .add(mapper.createObjectNode()
                        .put("role", "user")
                        .put("content", userContent)))
        );

        Request request = new Request.Builder()
                .url(config.getLlmEndpoint())
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + config.getLlmApiKey())
                .post(RequestBody.create(bodyJson, MediaType.parse("application/json")))
                .build();

        try (Response response = http.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException(
                    "LLM endpoint error HTTP " + response.code() + ": " + respBody);
            }

            // Parse standard OpenAI response: choices[0].message.content
            JsonNode root = mapper.readTree(respBody);

            if (root.has("error")) {
                throw new IOException(
                    "LLM returned error: " + root.path("error").path("message").asText());
            }

            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                throw new IOException(
                    "LLM response has no 'choices'. Full response: " + respBody);
            }

            return choices.get(0).path("message").path("content").asText();
        }
    }

    private String sanitizeCsvOutput(String raw) throws IOException {
        String cleaned = raw == null ? "" : raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("(?s)^```[a-zA-Z]*\\s*", "")
                    .replaceAll("\\s*```$", "")
                    .trim();
        }

        if (!cleaned.contains("Test_Case_Number")) {
            throw new IOException("LLM did not return expected CSV header for functional test cases.");
        }
        return cleaned;
    }

    // ── Response Parsing ───────────────────────────────────────────────────────

    private LlmSlideResponse parseResponse(String raw) throws IOException {
        String cleaned = raw.trim();

        // Strip accidental markdown code fences
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("(?s)^```[a-z]*\\s*", "")
                             .replaceAll("\\s*```$", "")
                             .trim();
        }

        // Find the JSON object boundaries in case there's preamble text
        int start = cleaned.indexOf('{');
        int end   = cleaned.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }

        try {
            LlmSlideResponse result = mapper.readValue(cleaned, LlmSlideResponse.class);

            // Enforce signoffBy = "Chetan" regardless of LLM output
            if (result.getSlides() != null) {
                result.getSlides().forEach(slide -> {
                    if (slide.getRows() != null) {
                        slide.getRows().forEach(row -> row.setSignoffBy("Chetan"));
                    }
                });
            }

            return result;

        } catch (Exception e) {
            throw new IOException(
                "Failed to parse LLM JSON response.\n"
                + "First 600 chars of LLM output:\n"
                + cleaned.substring(0, Math.min(600, cleaned.length())), e);
        }
    }
}
