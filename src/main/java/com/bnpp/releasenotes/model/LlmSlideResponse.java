package com.bnpp.releasenotes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Maps the structured JSON response from the LLM.
 *
 * ── Expected LLM Output Shape ─────────────────────────────────────────────────
 * {
 *   "releaseVersion": "2026.02.0",
 *   "slides": [
 *     {
 *       "title": "Release Notes - 2026.02.0 (1/2)",
 *       "rows": [
 *         {
 *           "sr": 1,
 *           "jiraNo": "ATH-6496",
 *           "jiraDescription": "Java 11 migration for Athena UI module",
 *           "changeType": "Technical",
 *           "whyChangeRequired": "The project needed to migrate from JDK 8 to Java 11 for long-term support. Tool-chain, shell scripts and classpath configurations were updated accordingly.",
 *           "requirementType": "Technical",
 *           "signoffBy": "Chetan"
 *         }
 *       ]
 *     }
 *   ]
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmSlideResponse {

    @JsonProperty("releaseVersion")
    private String releaseVersion;

    @JsonProperty("slides")
    private List<SlideData> slides;

    public String getReleaseVersion()        { return releaseVersion; }
    public void setReleaseVersion(String v)  { this.releaseVersion = v; }

    public List<SlideData> getSlides()       { return slides; }
    public void setSlides(List<SlideData> v) { this.slides = v; }

    // ── Inner: SlideData ───────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SlideData {

        @JsonProperty("title")
        private String title;

        @JsonProperty("rows")
        private List<SlideRow> rows;

        public String getTitle()           { return title; }
        public void setTitle(String v)     { this.title = v; }

        public List<SlideRow> getRows()    { return rows; }
        public void setRows(List<SlideRow> v) { this.rows = v; }
    }
}
