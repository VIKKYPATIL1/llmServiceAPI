module com.bnpp.releasenotes {
    // JavaFX
    requires javafx.controls;
    requires javafx.fxml;

    // Apache POI (PPTX) — transitively pulls in java.desktop for Color/Dimension
    requires org.apache.poi.ooxml;

    // java.desktop must be explicitly required because we use:
    //   - java.awt.Color, java.awt.Dimension, java.awt.geom.Rectangle2D  (PptGenerationService)
    //   - java.awt.Desktop  (MainController)
    requires java.desktop;

    // JSON + HTTP
    requires com.fasterxml.jackson.databind;
    requires okhttp3;

    // BootstrapFX
    requires org.kordamp.bootstrapfx.core;

    // FXML reflection access
    opens com.bnpp.releasenotes to javafx.fxml;
    opens com.bnpp.releasenotes.controller to javafx.fxml;

    // Jackson deserialises model classes by reflection
    opens com.bnpp.releasenotes.model to com.fasterxml.jackson.databind;

    exports com.bnpp.releasenotes;
    exports com.bnpp.releasenotes.controller;
    exports com.bnpp.releasenotes.model;
    exports com.bnpp.releasenotes.service;
    exports com.bnpp.releasenotes.util;
}
