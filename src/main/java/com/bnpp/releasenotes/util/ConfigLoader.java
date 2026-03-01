package com.bnpp.releasenotes.util;

import com.bnpp.releasenotes.model.AppConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads AppConfig from /config.properties on the classpath.
 * All values can be overridden at runtime via the UI Settings panel.
 */
public class ConfigLoader {

    private static final String CONFIG_FILE = "/config.properties";

    public static AppConfig load() {
        Properties props = new Properties();
        try (InputStream is = ConfigLoader.class.getResourceAsStream(CONFIG_FILE)) {
            if (is != null) {
                props.load(is);
            } else {
                System.err.println("[ConfigLoader] config.properties not found on classpath — using defaults.");
            }
        } catch (IOException e) {
            System.err.println("[ConfigLoader] Error reading config.properties: " + e.getMessage());
        }

        AppConfig cfg = new AppConfig();

        // MCP
        cfg.setMcpEndpoint(props.getProperty("mcp.endpoint",
                "https://devops.mcp.cib.echonet/jsonrpc"));
        cfg.setMcpToolName(props.getProperty("mcp.tool.name",
                "search_jira_issues"));
        cfg.setJiraProject(props.getProperty("jira.project", "ATH"));
        cfg.setMcpApiKey(props.getProperty("mcp.api.key", ""));

        // LLM
        cfg.setLlmEndpoint(props.getProperty("llm.endpoint",
                "https://api.llm.cib.echonet/v1/openai/chat/completions"));
        cfg.setLlmApiKey(props.getProperty("llm.api.key", ""));
        cfg.setLlmModel(props.getProperty("llm.model", "codestral-latest-ITG"));

        // Master slide
        cfg.setMasterSlidePath(props.getProperty("master.slide.path", ""));

        return cfg;
    }
}
