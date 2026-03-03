package com.bnpp.releasenotes.controller;

import com.bnpp.releasenotes.model.AppConfig;
import com.bnpp.releasenotes.model.LlmSlideResponse;
import com.bnpp.releasenotes.model.McpJiraFunctionalDetails;
import com.bnpp.releasenotes.model.McpJiraIssue;
import com.bnpp.releasenotes.service.FunctionalTestCaseExcelService;
import com.bnpp.releasenotes.service.JiraFetchService;
import com.bnpp.releasenotes.service.LlmService;
import com.bnpp.releasenotes.service.PptGenerationService;
import com.bnpp.releasenotes.util.ConfigLoader;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    // Release Notes tab
    @FXML private TextField releaseVersionField;
    @FXML private TextField masterSlideField;
    @FXML private Button browseMasterBtn;
    @FXML private Button generateBtn;

    // Functional Test Cases tab
    @FXML private TextField jiraReferenceField;
    @FXML private Button generateExcelBtn;
    @FXML private Button addCommentBtn;

    // Shared
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private TextArea logArea;

    // Settings panel (collapsible)
    @FXML private VBox settingsPane;
    @FXML private TextField mcpEndpointField;
    @FXML private TextField jiraProjectField;
    @FXML private PasswordField mcpApiKeyField;
    @FXML private TextField llmEndpointField;
    @FXML private PasswordField llmApiKeyField;
    @FXML private TextField llmModelField;

    private AppConfig config;
    private boolean settingsOpen = false;

    private String latestFunctionalCsv;
    private String latestFunctionalJiraId;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        config = ConfigLoader.load();
        populateSettings();

        progressBar.setVisible(false);
        logArea.setEditable(false);
        statusLabel.setText("Ready — choose a tab and run generation.");

        settingsPane.setVisible(false);
        settingsPane.setManaged(false);

        addCommentBtn.setDisable(true);
    }

    @FXML
    private void onBrowseMaster() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Master Slide Template (.pptx)");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PowerPoint Files (*.pptx)", "*.pptx"));
        File f = fc.showOpenDialog(browseMasterBtn.getScene().getWindow());
        if (f != null) {
            masterSlideField.setText(f.getAbsolutePath());
            config.setMasterSlidePath(f.getAbsolutePath());
        }
    }

    @FXML
    private void onGenerate() {
        String version = releaseVersionField.getText().trim();
        if (version.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Missing Input",
                    "Please enter a release version (e.g. 2026.02.0).");
            return;
        }

        applySettings();

        try {
            config.validate();
        } catch (IllegalStateException e) {
            showAlert(Alert.AlertType.ERROR, "Configuration Error", e.getMessage());
            return;
        }

        FileChooser saveChooser = new FileChooser();
        saveChooser.setTitle("Save Release Notes As");
        saveChooser.setInitialFileName(
                "ReleaseNotes-" + version.replaceAll("[^a-zA-Z0-9._-]", "-") + ".pptx");
        saveChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PowerPoint Files (*.pptx)", "*.pptx"));
        File outputFile = saveChooser.showSaveDialog(generateBtn.getScene().getWindow());
        if (outputFile == null) return;

        clearLog();
        runReleaseNotesTask(version, outputFile.getAbsolutePath());
    }

    @FXML
    private void onGenerateFunctionalExcel() {
        String jiraId = jiraReferenceField.getText().trim();
        if (jiraId.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Missing Input",
                    "Please enter a JIRA reference (e.g. ATH-6497).");
            return;
        }

        applySettings();

        try {
            config.validate();
        } catch (IllegalStateException e) {
            showAlert(Alert.AlertType.ERROR, "Configuration Error", e.getMessage());
            return;
        }

        FileChooser saveChooser = new FileChooser();
        saveChooser.setTitle("Save Functional Test Cases As");
        saveChooser.setInitialFileName(
                "FunctionalTestCases-" + jiraId.replaceAll("[^a-zA-Z0-9._-]", "-") + ".xlsx");
        saveChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files (*.xlsx)", "*.xlsx"));
        File outputFile = saveChooser.showSaveDialog(generateExcelBtn.getScene().getWindow());
        if (outputFile == null) return;

        clearLog();
        runFunctionalExcelTask(jiraId, outputFile.getAbsolutePath());
    }

    @FXML
    private void onAddCommentToJira() {
        if (latestFunctionalCsv == null || latestFunctionalCsv.isBlank() ||
                latestFunctionalJiraId == null || latestFunctionalJiraId.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "No Generated Content",
                    "Please generate and verify the functional test case Excel file first.");
            return;
        }

        applySettings();

        try {
            config.validate();
        } catch (IllegalStateException e) {
            showAlert(Alert.AlertType.ERROR, "Configuration Error", e.getMessage());
            return;
        }

        runAddCommentTask(latestFunctionalJiraId, latestFunctionalCsv);
    }

    @FXML
    private void onToggleSettings() {
        settingsOpen = !settingsOpen;
        settingsPane.setVisible(settingsOpen);
        settingsPane.setManaged(settingsOpen);
    }

    private void runReleaseNotesTask(String version, String outputPath) {
        setBusy(true, "Starting release notes generation…");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                log("📡  Calling MCP server: " + config.getMcpEndpoint());
                log("    JQL: project = " + config.getJiraProject()
                        + " and fixVersion = \"" + version + "\"");

                JiraFetchService jiraService = new JiraFetchService(config);
                List<McpJiraIssue> issues = jiraService.fetchIssues(version);

                log("✅  Fetched " + issues.size() + " JIRA issue(s):");
                issues.forEach(i -> log("    [" + i.getKey() + "] " + i.getSummary()
                        + "  (" + i.getStatus() + ")"));

                log("\n🤖  Sending to LLM: " + config.getLlmModel()
                        + " @ " + config.getLlmEndpoint());

                LlmService llmService = new LlmService(config);
                LlmSlideResponse slideResponse = llmService.generateSlides(version, issues);

                int totalSlides = slideResponse.getSlides().size();
                log("✅  LLM produced " + totalSlides + " slide(s)");
                slideResponse.getSlides().forEach(s ->
                        log("    Slide: \"" + s.getTitle() + "\" — "
                                + s.getRows().size() + " row(s)"));

                log("\n📊  Building PowerPoint…");
                PptGenerationService pptService = new PptGenerationService();
                pptService.generate(slideResponse, config.getMasterSlidePath(), outputPath);
                log("✅  Saved: " + outputPath);

                return null;
            }

            @Override
            protected void succeeded() {
                setBusy(false, "✅ Done!");
                showOpenOption("Release notes generated successfully!", outputPath);
            }

            @Override
            protected void failed() {
                handleTaskFailure(getException(), "Generation Failed");
            }
        };

        startTask(task, "release-notes-generator");
    }

    private void runFunctionalExcelTask(String jiraId, String outputPath) {
        setBusy(true, "Starting functional test case generation…");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                log("📡  Calling MCP tool get_jira_by_reference for: " + jiraId);

                JiraFetchService jiraService = new JiraFetchService(config);
                McpJiraFunctionalDetails issue = jiraService.fetchIssueByReference(jiraId);

                String jiraTitle = issue.getDescription();
                String jiraDescription = issue.getSummary();

                if (jiraTitle == null || jiraTitle.isBlank()) {
                    jiraTitle = jiraDescription;
                }
                if (jiraDescription == null || jiraDescription.isBlank()) {
                    jiraDescription = jiraTitle;
                }

                log("✅  Title (MCP description): " + safeLogValue(jiraTitle));
                log("✅  Description (MCP summary): " + safeLogValue(jiraDescription));

                LlmService llmService = new LlmService(config);
                log("\n🤖  Generating functional test case CSV using LLM…");
                String csv = llmService.generateFunctionalTestCasesCsv(jiraTitle, jiraDescription);

                FunctionalTestCaseExcelService excelService = new FunctionalTestCaseExcelService();
                log("📄  Building Excel file…");
                excelService.generateExcelFromCsv(csv, outputPath);

                latestFunctionalCsv = csv;
                latestFunctionalJiraId = jiraId;

                log("✅  Saved: " + outputPath);
                return null;
            }

            @Override
            protected void succeeded() {
                setBusy(false, "✅ Done!");
                addCommentBtn.setDisable(false);
                showOpenOption("Functional test cases generated successfully!", outputPath);
            }

            @Override
            protected void failed() {
                handleTaskFailure(getException(), "Functional Test Case Generation Failed");
            }
        };

        startTask(task, "functional-testcase-generator");
    }

    private void runAddCommentTask(String jiraId, String csvContent) {
        setBusy(true, "Adding comment to JIRA…");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                FunctionalTestCaseExcelService excelService = new FunctionalTestCaseExcelService();
                String markdown = excelService.toMarkdownTable(csvContent);

                JiraFetchService jiraService = new JiraFetchService(config);
                log("📡  Calling MCP tool add_jira_comment for task_id=" + jiraId);
                jiraService.addCommentToJira(jiraId, markdown);
                log("✅  Comment posted to JIRA: " + jiraId);

                return null;
            }

            @Override
            protected void succeeded() {
                setBusy(false, "✅ Jira comment added successfully!");
                showAlert(Alert.AlertType.INFORMATION, "Success",
                        "Functional test case comment posted to JIRA " + jiraId + ".");
            }

            @Override
            protected void failed() {
                handleTaskFailure(getException(), "Add Comment Failed");
            }
        };

        startTask(task, "jira-comment-adder");
    }

    private void startTask(Task<Void> task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private void handleTaskFailure(Throwable ex, String title) {
        String msg = ex != null && ex.getMessage() != null ? ex.getMessage() : String.valueOf(ex);
        log("\n❌  FAILED: " + msg);
        setBusy(false, "❌ Failed — see log for details.");
        showAlert(Alert.AlertType.ERROR, title, msg);
    }

    private String safeLogValue(String value) {
        if (value == null) return "(empty)";
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() > 180 ? clean.substring(0, 177) + "..." : clean;
    }

    private void applySettings() {
        config.setMcpEndpoint(mcpEndpointField.getText().trim());
        config.setJiraProject(jiraProjectField.getText().trim());
        config.setMcpApiKey(mcpApiKeyField.getText().trim());
        config.setLlmEndpoint(llmEndpointField.getText().trim());
        config.setLlmApiKey(llmApiKeyField.getText().trim());
        config.setLlmModel(llmModelField.getText().trim());
        config.setMasterSlidePath(masterSlideField.getText().trim());
    }

    private void populateSettings() {
        mcpEndpointField.setText(config.getMcpEndpoint());
        jiraProjectField.setText(config.getJiraProject());
        mcpApiKeyField.setText(config.getMcpApiKey());
        llmEndpointField.setText(config.getLlmEndpoint());
        llmApiKeyField.setText(config.getLlmApiKey());
        llmModelField.setText(config.getLlmModel());
        masterSlideField.setText(config.getMasterSlidePath());
    }

    private void setBusy(boolean busy, String status) {
        Platform.runLater(() -> {
            generateBtn.setDisable(busy);
            generateExcelBtn.setDisable(busy);
            addCommentBtn.setDisable(busy || latestFunctionalCsv == null || latestFunctionalCsv.isBlank());
            progressBar.setVisible(busy);
            progressBar.setProgress(busy ? ProgressBar.INDETERMINATE_PROGRESS : 1.0);
            statusLabel.setText(status);
        });
    }

    private void log(String msg) {
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }

    private void clearLog() {
        Platform.runLater(() -> logArea.clear());
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(type);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(msg);
            a.showAndWait();
        });
    }

    private void showOpenOption(String title, String path) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Success");
            a.setHeaderText(title);
            a.setContentText("Saved to:\n" + path);
            ButtonType openBtn = new ButtonType("Open File");
            ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
            a.getButtonTypes().setAll(openBtn, closeBtn);
            a.showAndWait().ifPresent(btn -> {
                if (btn == openBtn) {
                    try {
                        Desktop.getDesktop().open(new File(path));
                    } catch (Exception ignored) {
                    }
                }
            });
        });
    }
}
