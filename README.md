# Release Notes Generator — BNP Paribas

Generates PowerPoint release notes from JIRA tickets via the internal MCP server and LLM.

```
MCP JSON-RPC ──► JIRA Issues ──► LLM (codestral) ──► Structured JSON ──► .pptx
```

---

## Output Table Schema

Each slide contains a table with these **7 columns**:

| Sr | JIRA No | JIRA Description | Change Type | Why Change Required | Requirement Type | Signoff By |
|----|---------|-----------------|-------------|---------------------|-----------------|-----------|
| 1 | ATH-6496 | Java 11 migration for Athena UI module | Technical | The application required migration... | Technical | Chetan |

**Column derivation rules:**

| Column | Source |
|--------|--------|
| Sr | Auto-incremented globally across all slides |
| JIRA No | `key` field from MCP response |
| JIRA Description | `summary` field from MCP response |
| Change Type | LLM-classified from `description`: **Functional** / **Bug** / **Technical** |
| Why Change Required | 2-sentence LLM summary from `description` |
| Requirement Type | Functional→*New Feature* · Bug→*Bug Fix* · Technical→*Technical* |
| Signoff By | Fixed: **Chetan** |

**Change Type Classification:**
- `Functional` → new feature, UI flow, enrichment, user story, new screen
- `Bug` → bug, defect, error, failure, fix, crash, regression
- `Technical` → refactor, upgrade, migration, configuration, dependency, toolchain

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java JDK | 17 |
| Maven | 3.8+ |

---

## Quick Start

### 1. Set your API keys in `src/main/resources/config.properties`

```properties
mcp.endpoint=https://devops.mcp.cib.echonet/jsonrpc
mcp.tool.name=search_jira_issues
jira.project=ATH
mcp.api.key=YOUR_MCP_TOKEN

llm.endpoint=https://api.llm.cib.echonet/v1/openai/chat/completions
llm.api.key=YOUR_LLM_TOKEN
llm.model=codestral-latest-ITG

master.slide.path=   # optional: /path/to/your/template.pptx
```

> All values can also be set/overridden in the **⚙ API & Connection Settings** panel in the UI.

### 2. Run

```bash
mvn clean javafx:run
```

### 3. Use the UI

1. Enter the **Fix Version** (e.g. `2026.02.0`)
2. Optionally browse to your branded `.pptx` master slide
3. Click **Generate PPT →**
4. Choose where to save the output file
5. The log panel shows each step as it runs

---

## MCP Protocol Details

The app sends a **JSON-RPC 2.0** POST request:

```json
POST https://devops.mcp.cib.echonet/jsonrpc

{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "search_jira_issues",
    "arguments": {
      "jql": "project = ATH and fixVersion = \"2026.02.0\"",
      "environment": "default",
      "extra_fields": ""
    }
  }
}
```

The response arrives as **SSE (Server-Sent Events)**:

```
event: message
data: {"jsonrpc":"2.0","id":"1","result":{"content":[{"type":"text","text":"[{...issues...}]"}],"isError":false}}
```

Parsing chain: `data` line → `result.content[0].text` → parse as JSON array of JIRA issues.

---

## Project Structure

```
src/main/java/com/bnpp/releasenotes/
├── ReleaseNotesApp.java              ← JavaFX entry point
├── controller/
│   └── MainController.java           ← UI logic & task orchestration
├── model/
│   ├── AppConfig.java                ← Runtime config (MCP + LLM endpoints)
│   ├── McpJiraIssue.java             ← POJO for MCP JIRA response (id, key, summary, status, priority, assignee, description)
│   ├── SlideRow.java                 ← 7-column table row POJO
│   └── LlmSlideResponse.java         ← LLM JSON output model
├── service/
│   ├── JiraFetchService.java         ← JSON-RPC 2.0 + SSE response parser
│   ├── LlmService.java               ← Internal LLM client + prompt builder
│   └── PptGenerationService.java     ← Apache POI PPTX table builder
└── util/
    └── ConfigLoader.java             ← Loads config.properties
```
