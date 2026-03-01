package com.bnpp.releasenotes;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

/**
 * JavaFX application entry point.
 * mainClass in pom.xml → com.bnpp.releasenotes/com.bnpp.releasenotes.ReleaseNotesApp
 */
public class ReleaseNotesApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        URL fxml = getClass().getResource("/fxml/main.fxml");
        if (fxml == null) {
            throw new IllegalStateException("Cannot locate /fxml/main.fxml on classpath.");
        }

        FXMLLoader loader = new FXMLLoader(fxml);
        Scene scene = new Scene(loader.load(), 900, 620);

        // Custom stylesheet
        URL css = getClass().getResource("/css/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        // BootstrapFX — the CSS lives at /bootstrapfx.css inside the jar.
        // We load it via the BootstrapFX module root, not via Panel's package.
        URL bootstrapCss = getClass().getResource("/bootstrapfx.css");
        if (bootstrapCss != null) {
            scene.getStylesheets().add(bootstrapCss.toExternalForm());
        } else {
            // Fallback: try resolving from the BootstrapFX core package
            URL fallback = org.kordamp.bootstrapfx.BootstrapFX.class
                    .getResource("/bootstrapfx.css");
            if (fallback != null) {
                scene.getStylesheets().add(fallback.toExternalForm());
            }
        }

        primaryStage.setTitle("Release Notes Generator — BNP Paribas");
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(820);
        primaryStage.setMinHeight(560);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
