package com.bnpp.releasenotes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Minimal JIRA payload used by Functional Test Case Creator flow.
 *
 * We intentionally map ONLY the two required MCP fields:
 * - description (used as Title)
 * - summary (used as Description)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpJiraFunctionalDetails {

    @JsonProperty("description")
    private String description;

    @JsonProperty("summary")
    private String summary;

    public String getDescription() {
        return description != null ? description : "";
    }

    public String getSummary() {
        return summary != null ? summary : "";
    }
}
