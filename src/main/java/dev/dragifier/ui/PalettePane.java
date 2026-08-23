package dev.dragifier.ui;

import dev.dragifier.model.ComponentType;
import javafx.geometry.Insets;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;

/** Palette of draggable component types. */
public class PalettePane extends VBox {

    public PalettePane() {
        setSpacing(6);
        setPadding(new Insets(10));
        setPrefWidth(150);
        setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #d0d0d0; -fx-border-width: 0 1 0 0;");

        Label title = new Label("Palette");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        getChildren().add(title);

        for (ComponentType type : ComponentType.values()) {
            getChildren().add(makeItem(type));
        }

        Label hint = new Label("Drag onto the form");
        hint.setStyle("-fx-text-fill: #909090; -fx-font-size: 11px;");
        hint.setPadding(new Insets(8, 0, 0, 0));
        getChildren().add(hint);
    }

    private Label makeItem(ComponentType type) {
        Label item = new Label(type.displayName);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setPadding(new Insets(6, 10, 6, 10));
        item.setStyle("-fx-background-color: white; -fx-border-color: #c8c8c8; -fx-border-radius: 4; -fx-background-radius: 4;");
        item.setCursor(javafx.scene.Cursor.OPEN_HAND);
        item.setOnDragDetected(e -> {
            Dragboard db = item.startDragAndDrop(TransferMode.COPY);
            db.setContent(DesignCanvas.dragContent(type));
            db.setDragView(item.snapshot(new SnapshotParameters(), null));
            e.consume();
        });
        return item;
    }
}
