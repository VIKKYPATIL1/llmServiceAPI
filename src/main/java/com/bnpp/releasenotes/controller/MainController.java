package com.bnpp.releasenotes.controller;

import com.bnpp.releasenotes.model.AppConfig;
import com.bnpp.releasenotes.model.LlmSlideResponse;
import com.bnpp.releasenotes.model.McpJiraIssue;
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

    // ── FXML bindings ──────────────────────────────────────────────────────────
    @FXML private TextField     releaseVersionField;
    @FXML private TextField     masterSlideField;
    @FXML private Button        browseMasterBtn;
    @FXML private Button        generateBtn;
    @FXML private ProgressBar   progressBar;
    @FXML private Label         statusLabel;
    @FXML private TextArea      logArea;

    // Settings panel (collapsible)
    @FXML private VBox          settingsPane;
    @FXML private TextField     mcpEndpointField;
    @FXML private TextField     jiraProjectField;
    @FXML private PasswordField mcpApiKeyField;
    @FXML private TextField     llmEndpointField;
    @FXML private PasswordField llmApiKeyField;
    @FXML private TextField     llmModelField;

    // ── State ──────────────────────────────────────────────────────────────────
    private AppConfig config;
    private boolean   settingsOpen = false;

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        config = ConfigLoader.load();
        populateSettings();

        progressBar.setVisible(false);
        logArea.setEditable(false);
        statusLabel.setText("Ready — enter a release version and click Generate.");

        settingsPane.setVisible(false);
        settingsPane.setManaged(false);
    }

    // ── UI Actions ─────────────────────────────────────────────────────────────

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

        // Apply any settings the user may have edited
        applySettings();

        // Validate config
        try {
            config.validate();
        } catch (IllegalStateException e) {
            showAlert(Alert.AlertType.ERROR, "Configuration Error", e.getMessage());
            return;
        }

        // Ask where to save
        FileChooser saveChooser = new FileChooser();
        saveChooser.setTitle("Save Release Notes As");
        saveChooser.setInitialFileName(
            "ReleaseNotes-" + version.replaceAll("[^a-zA-Z0-9._-]", "-") + ".pptx");
        saveChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PowerPoint Files (*.pptx)", "*.pptx"));
        File outputFile = saveChooser.showSaveDialog(generateBtn.getScene().getWindow());
        if (outputFile == null) return;

        clearLog();
        runTask(version, outputFile.getAbsolutePath());
    }

    @FXML
    private void onToggleSettings() {
        settingsOpen = !settingsOpen;
        settingsPane.setVisible(settingsOpen);
        settingsPane.setManaged(settingsOpen);
    }

    // ── Background Task ────────────────────────────────────────────────────────

    private void runTask(String version, String outputPath) {
        setBusy(true, "Starting…");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {

                // Step 1 — Fetch JIRAs from MCP
                log("📡  Calling MCP server: " + config.getMcpEndpoint());
                log("    JQL: project = " + config.getJiraProject()
                    + " and fixVersion = \"" + version + "\"");

                JiraFetchService jiraService = new JiraFetchService(config);
                List<McpJiraIssue> issues = jiraService.fetchIssues(version);

                log("✅  Fetched " + issues.size() + " JIRA issue(s):");
                issues.forEach(i -> log("    [" + i.getKey() + "] " + i.getSummary()
                                        + "  (" + i.getStatus() + ")"));

                // Step 2 — Send to LLM
                log("\n🤖  Sending to LLM: " + config.getLlmModel()
                    + " @ " + config.getLlmEndpoint());

                LlmService llmService = new LlmService(config);
                LlmSlideResponse slideResponse = llmService.generateSlides(version, issues);

                int totalSlides = slideResponse.getSlides().size();
                log("✅  LLM produced " + totalSlides + " slide(s)");
                slideResponse.getSlides().forEach(s ->
                    log("    Slide: \"" + s.getTitle() + "\" — "
                        + s.getRows().size() + " row(s)"));

                // Step 3 — Build PPTX
                log("\n📊  Building PowerPoint…");
                PptGenerationService pptService = new PptGenerationService();
                pptService.generate(slideResponse, config.getMasterSlidePath(), outputPath);
                log("✅  Saved: " + outputPath);

                return null;
            }

            @Override protected void succeeded() {
                setBusy(false, "✅ Done!");
                showOpenOption(outputPath);
            }

            @Override protected void failed() {
                Throwable ex = getException();
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                log("\n❌  FAILED: " + msg);
                setBusy(false, "❌ Failed — see log for details.");
                showAlert(Alert.AlertType.ERROR, "Generation Failed", msg);
            }
        };

        Thread thread = new Thread(task, "rn-generator");
        thread.setDaemon(true);
        thread.start();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

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
    }

    private void setBusy(boolean busy, String status) {
        Platform.runLater(() -> {
            generateBtn.setDisable(busy);
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

    private void showOpenOption(String path) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Success");
            a.setHeaderText("Release notes generated successfully!");
            a.setContentText("Saved to:\n" + path);
            ButtonType openBtn = new ButtonType("Open File");
            ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
            a.getButtonTypes().setAll(openBtn, closeBtn);
            a.showAndWait().ifPresent(btn -> {
                if (btn == openBtn) {
                    try { Desktop.getDesktop().open(new File(path)); }
                    catch (Exception ignored) {}
                }
            });
        });
    }
}
