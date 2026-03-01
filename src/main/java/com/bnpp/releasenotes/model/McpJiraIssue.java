package com.bnpp.releasenotes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maps one JIRA issue from the MCP server response.
 *
 * ── MCP Response Chain ────────────────────────────────────────────────────────
 * The MCP server returns SSE (Server-Sent Events). Each event looks like:
 *
 *   event: message
 *   data: { "jsonrpc":"2.0", "id":"1", "result": { "content": [ { "type":"text", "text":"[{...}]" } ] } }
 *
 * The "text" value inside content[0] is a raw JSON STRING containing the array
 * of JIRA issues. JiraFetchService parses that string into List<McpJiraIssue>.
 *
 * ── Visible Fields from Image 1 ──────────────────────────────────────────────
 * {
 *   "id":        "10522370",
 *   "key":       "ATH-6496",
 *   "summary":   "Java 11 migration for Athena UI module",
 *   "status":    "Delivered",
 *   "priority":  { "name": "Important", "id": "10501", "self": "...", "iconUrl": "..." },
 *   "assignee":  { "displayName": "Chetan KACHAVE", "emailAddress": "...", "self": "...",
 *                  "avatarUrls": { "48x48":"...", "24x24":"...", "16x16":"...", "32x32":"..." },
 *                  "active": true, "timeZone": "Asia/Kolkata" },
 *   "createdAt": "2026-02-10T11:38:48.000+0100",
 *   "updatedAt": "2026-02-10T09:52:07.000+0100",
 *   "description":"h2. Java 11 migration for Athena UI module\\n\\n..."
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpJiraIssue {

    /** Internal JIRA id (numeric string), e.g. "10522370" */
    @JsonProperty("id")
    private String id;

    /** JIRA ticket key, e.g. "ATH-6496" */
    @JsonProperty("key")
    private String key;

    /** Ticket title / one-liner, e.g. "Java 11 migration for Athena UI module" */
    @JsonProperty("summary")
    private String summary;

    /** Status as a plain string, e.g. "Delivered" */
    @JsonProperty("status")
    private String status;

    /** Priority object */
    @JsonProperty("priority")
    private Priority priority;

    /** Assignee object */
    @JsonProperty("assignee")
    private Assignee assignee;

    /** Full description in Jira wiki markup (h2., \n, *bullets*, etc.) */
    @JsonProperty("description")
    private String description;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("updatedAt")
    private String updatedAt;

    // ── Getters ────────────────────────────────────────────────────────────────

    public String getId()          { return id; }
    public String getKey()         { return key; }
    public String getSummary()     { return summary; }
    public String getStatus()      { return status; }
    public Priority getPriority()  { return priority; }
    public Assignee getAssignee()  { return assignee; }
    public String getDescription() { return description; }
    public String getCreatedAt()   { return createdAt; }
    public String getUpdatedAt()   { return updatedAt; }

    /** Safe helper — returns description or empty string if null. */
    public String safeDescription() {
        return description != null ? description : "";
    }

    /** Safe helper — returns priority name or "Unknown". */
    public String getPriorityName() {
        return priority != null && priority.getName() != null ? priority.getName() : "Unknown";
    }

    /** Safe helper — returns assignee display name or "Unassigned". */
    public String getAssigneeDisplayName() {
        return assignee != null && assignee.getDisplayName() != null
               ? assignee.getDisplayName() : "Unassigned";
    }

    // ── Nested: Priority ───────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Priority {
        @JsonProperty("name")    private String name;
        @JsonProperty("id")      private String id;
        @JsonProperty("self")    private String self;
        @JsonProperty("iconUrl") private String iconUrl;

        public String getName()    { return name; }
        public String getId()      { return id; }
        public String getSelf()    { return self; }
        public String getIconUrl() { return iconUrl; }
    }

    // ── Nested: Assignee ───────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Assignee {
        @JsonProperty("self")          private String self;
        @JsonProperty("displayName")   private String displayName;
        @JsonProperty("emailAddress")  private String emailAddress;
        @JsonProperty("active")        private boolean active;
        @JsonProperty("timeZone")      private String timeZone;
        @JsonProperty("avatarUrls")    private AvatarUrls avatarUrls;

        public String getDisplayName()  { return displayName; }
        public String getEmailAddress() { return emailAddress; }
        public boolean isActive()       { return active; }
        public String getTimeZone()     { return timeZone; }
        public AvatarUrls getAvatarUrls(){ return avatarUrls; }
    }

    // ── Nested: AvatarUrls ─────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AvatarUrls {
        @JsonProperty("48x48") private String large;
        @JsonProperty("24x24") private String medium;
        @JsonProperty("16x16") private String small;
        @JsonProperty("32x32") private String standard;

        public String getLarge()    { return large; }
        public String getMedium()   { return medium; }
        public String getSmall()    { return small; }
        public String getStandard() { return standard; }
    }

    @Override
    public String toString() {
        return "McpJiraIssue{key='" + key + "', summary='" + summary
               + "', status='" + status + "'}";
    }
}
