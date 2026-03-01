package com.bnpp.releasenotes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents one row in the release notes table.
 *
 * ── Table Columns ─────────────────────────────────────────────────────────────
 * Sr | JIRA_No | JIRA_Description | Change_Type | Why_Change_Required | Requirement_Type | Signoff_By
 *
 * ── Column Derivation Rules ───────────────────────────────────────────────────
 *
 *  Sr               → Auto-incremented serial number (1, 2, 3…)
 *
 *  JIRA_No          → McpJiraIssue.key  (e.g. "ATH-6496")
 *
 *  JIRA_Description → McpJiraIssue.summary  (e.g. "Java 11 migration for Athena UI module")
 *
 *  Change_Type      → Derived by LLM from McpJiraIssue.description:
 *                      "Functional"  — new feature, UI flow, UI enrichment, user story
 *                      "Bug"         — bug, defect, error, failure, fix, issue
 *                      "Technical"   — refactor, upgrade, migration, config, dependency, CI/CD
 *
 *  Why_Change_Required → Concise 2-sentence explanation derived from McpJiraIssue.description
 *
 *  Requirement_Type → Derived from Change_Type:
 *                      Functional  → "New Feature"
 *                      Bug         → "Bug Fix"
 *                      Technical   → "Technical"
 *
 *  Signoff_By       → Fixed value: "Chetan"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SlideRow {

    /** Serial number within the slide (1-based). Set by PptGenerationService. */
    @JsonProperty("sr")
    private int sr;

    /** JIRA ticket key, e.g. "ATH-6496" */
    @JsonProperty("jiraNo")
    private String jiraNo;

    /** JIRA summary / title */
    @JsonProperty("jiraDescription")
    private String jiraDescription;

    /**
     * "Functional" | "Bug" | "Technical"
     * Determined by LLM based on description content.
     */
    @JsonProperty("changeType")
    private String changeType;

    /**
     * 2-sentence concise explanation of why this change was required.
     * Derived by LLM from the JIRA description.
     */
    @JsonProperty("whyChangeRequired")
    private String whyChangeRequired;

    /**
     * "New Feature" (when changeType = Functional)
     * "Bug Fix"     (when changeType = Bug)
     * "Technical"   (when changeType = Technical)
     */
    @JsonProperty("requirementType")
    private String requirementType;

    /** Fixed value: "Chetan" */
    @JsonProperty("signoffBy")
    private String signoffBy = "Chetan";

    // ── Constructors ───────────────────────────────────────────────────────────

    public SlideRow() {}

    public SlideRow(int sr, String jiraNo, String jiraDescription,
                    String changeType, String whyChangeRequired,
                    String requirementType) {
        this.sr               = sr;
        this.jiraNo           = jiraNo;
        this.jiraDescription  = jiraDescription;
        this.changeType       = changeType;
        this.whyChangeRequired= whyChangeRequired;
        this.requirementType  = requirementType;
        this.signoffBy        = "Chetan";
    }

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public int    getSr()                              { return sr; }
    public void   setSr(int sr)                        { this.sr = sr; }

    public String getJiraNo()                          { return safe(jiraNo); }
    public void   setJiraNo(String v)                  { this.jiraNo = v; }

    public String getJiraDescription()                 { return safe(jiraDescription); }
    public void   setJiraDescription(String v)         { this.jiraDescription = v; }

    public String getChangeType()                      { return safe(changeType); }
    public void   setChangeType(String v)              { this.changeType = v; }

    public String getWhyChangeRequired()               { return safe(whyChangeRequired); }
    public void   setWhyChangeRequired(String v)       { this.whyChangeRequired = v; }

    public String getRequirementType()                 { return safe(requirementType); }
    public void   setRequirementType(String v)         { this.requirementType = v; }

    public String getSignoffBy()                       { return signoffBy != null ? signoffBy : "Chetan"; }
    public void   setSignoffBy(String v)               { this.signoffBy = v; }

    private String safe(String s) { return s != null ? s : ""; }

    @Override
    public String toString() {
        return "SlideRow{sr=" + sr + ", jiraNo='" + jiraNo
               + "', changeType='" + changeType + "'}";
    }
}
