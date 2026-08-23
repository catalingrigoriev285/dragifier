package dev.dragifier.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Output console for compile progress and the running app's stdout/stderr. */
public class ConsolePane extends VBox {

    private final TextArea area = new TextArea();

    public ConsolePane() {
        setSpacing(6);
        setPadding(new Insets(8, 10, 8, 10));
        getStyleClass().add("bottom-panel");

        area.setEditable(false);
        area.setWrapText(false);
        area.getStyleClass().add("console-area");
        VBox.setVgrow(area, Priority.ALWAYS);

        Button clear = new Button("Clear");
        clear.setOnAction(e -> area.clear());
        HBox bar = new HBox(clear);

        getChildren().addAll(area, bar);
    }

    public void append(String line) {
        area.appendText(line + "\n");
    }

    public void clear() {
        area.clear();
    }
}
