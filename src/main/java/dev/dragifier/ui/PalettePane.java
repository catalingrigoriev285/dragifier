package dev.dragifier.ui;

import dev.dragifier.model.ComponentType;
import javafx.geometry.Insets;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Palette of draggable component types. */
public class PalettePane extends VBox {

    public PalettePane() {
        setSpacing(6);
        setPadding(new Insets(10));
        setPrefWidth(170);
        getStyleClass().add("side-panel");

        Label title = new Label("Palette");
        title.getStyleClass().add("panel-header");
        getChildren().add(title);

        javafx.scene.control.TextField filter = new javafx.scene.control.TextField();
        filter.setPromptText("Filter…");
        getChildren().add(filter);

        VBox items = new VBox(4);
        for (ComponentType type : ComponentType.values()) {
            Label item = makeItem(type);
            items.getChildren().add(item);
        }
        filter.textProperty().addListener((obs, was, query) -> {
            String q = query == null ? "" : query.trim().toLowerCase();
            for (javafx.scene.Node node : items.getChildren()) {
                boolean match = q.isEmpty()
                        || ((Label) node).getText().toLowerCase().contains(q);
                node.setVisible(match);
                node.setManaged(match);
            }
        });
        ScrollPane scroll = new ScrollPane(items);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);

        Label hint = new Label("Drag onto the form");
        hint.getStyleClass().add("hint-text");
        hint.setPadding(new Insets(6, 0, 0, 0));
        getChildren().add(hint);
    }

    private Label makeItem(ComponentType type) {
        Label item = new Label(type.displayName, Icons.forType(type));
        item.setMaxWidth(Double.MAX_VALUE);
        item.getStyleClass().add("palette-item");
        item.setOnDragDetected(e -> {
            Dragboard db = item.startDragAndDrop(TransferMode.COPY);
            db.setContent(DesignCanvas.dragContent(type));
            db.setDragView(item.snapshot(new SnapshotParameters(), null));
            e.consume();
        });
        return item;
    }
}
