package com.bnpp.releasenotes.model;

/**
 * Runtime configuration bean.
 *
 * All default values are defined ONCE in ConfigLoader (loaded from config.properties).
 * Fields here have no defaults — ConfigLoader always sets every field explicitly.
 * Fields can also be overridden at runtime via the UI Settings panel.
 */
public class AppConfig {

    // ── MCP Server (JSON-RPC 2.0) ──────────────────────────────────────────────
    /** Full JSON-RPC endpoint, e.g. https://devops.mcp.cib.echonet/jsonrpc */
    private String mcpEndpoint;

    /** Tool name used in the MCP tools/call request, e.g. "search_jira_issues" */
    private String mcpToolName;

    /** JIRA project key used in the JQL query, e.g. "ATH" */
    private String jiraProject;

    /** Bearer token for the MCP server (can be empty if not required) */
    private String mcpApiKey;

    // ── LLM (Internal OpenAI-compatible endpoint) ──────────────────────────────
    /** Full chat completions URL, e.g. https://api.llm.cib.echonet/v1/openai/chat/completions */
    private String llmEndpoint;

    /** Bearer token for the LLM endpoint */
    private String llmApiKey;

    /** Model identifier, e.g. "codestral-latest-ITG" */
    private String llmModel;

    // ── Output ────────────────────────────────────────────────────────────────
    /** Absolute path to user's .pptx master template. Empty = plain white slides. */
    private String masterSlidePath;

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Validates that all required fields are non-blank.
     * Called by MainController before starting generation.
     */
    public void validate() throws IllegalStateException {
        if (blank(mcpEndpoint)) throw new IllegalStateException("MCP Endpoint is required.");
        if (blank(jiraProject)) throw new IllegalStateException("JIRA Project key is required.");
        if (blank(llmEndpoint)) throw new IllegalStateException("LLM Endpoint is required.");
        if (blank(llmApiKey))   throw new IllegalStateException("LLM API Key is required.");
        if (blank(llmModel))    throw new IllegalStateException("LLM Model name is required.");
    }

    private boolean blank(String s) { return s == null || s.isBlank(); }

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public String getMcpEndpoint()             { return mcpEndpoint; }
    public void   setMcpEndpoint(String v)     { this.mcpEndpoint = v; }

    public String getMcpToolName()             { return mcpToolName; }
    public void   setMcpToolName(String v)     { this.mcpToolName = v; }

    public String getJiraProject()             { return jiraProject; }
    public void   setJiraProject(String v)     { this.jiraProject = v; }

    public String getMcpApiKey()               { return mcpApiKey; }
    public void   setMcpApiKey(String v)       { this.mcpApiKey = v; }

    public String getLlmEndpoint()             { return llmEndpoint; }
    public void   setLlmEndpoint(String v)     { this.llmEndpoint = v; }

    public String getLlmApiKey()               { return llmApiKey; }
    public void   setLlmApiKey(String v)       { this.llmApiKey = v; }

    public String getLlmModel()                { return llmModel; }
    public void   setLlmModel(String v)        { this.llmModel = v; }

    public String getMasterSlidePath()         { return masterSlidePath != null ? masterSlidePath : ""; }
    public void   setMasterSlidePath(String v) { this.masterSlidePath = v; }
}
