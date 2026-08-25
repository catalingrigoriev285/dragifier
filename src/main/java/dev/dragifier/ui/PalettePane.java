package dev.dragifier.ui;

import dev.dragifier.model.ComponentType;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/** Palette of draggable component types, grouped by category. */
public class PalettePane extends VBox {

    /** The width a palette item needs; the pane opens at this and never shrinks below it. */
    private static final double CONTENT_WIDTH = 150;

    private record Section(Label header, List<Label> items) {}

    public PalettePane() {
        setSpacing(6);
        setPadding(new Insets(10));
        // 20 pane padding + 20 item padding + 16 icon + 8 gap
        // + ~70 widest component name + ~16 vertical scrollbar.
        setPrefWidth(CONTENT_WIDTH);
        setMinWidth(CONTENT_WIDTH);
        getStyleClass().add("side-panel");

        Label title = new Label("Palette");
        title.getStyleClass().add("panel-header");
        getChildren().add(title);

        javafx.scene.control.TextField filter = new javafx.scene.control.TextField();
        filter.setPromptText("Filter…");
        getChildren().add(filter);

        VBox items = new VBox(4);
        List<Section> sections = new ArrayList<>();
        for (ComponentType.Category category : ComponentType.Category.values()) {
            Label header = new Label(switch (category) {
                case BASIC -> "Basic";
                case CONTAINER -> "Containers";
                case OTHER -> "Other";
            });
            header.getStyleClass().add("palette-section");
            List<Label> labels = new ArrayList<>();
            for (ComponentType type : ComponentType.values()) {
                if (type.category == category) {
                    labels.add(makeItem(type));
                }
            }
            if (labels.isEmpty()) {
                continue;
            }
            items.getChildren().add(header);
            items.getChildren().addAll(labels);
            sections.add(new Section(header, labels));
        }
        filter.textProperty().addListener((obs, was, query) -> {
            String q = query == null ? "" : query.trim().toLowerCase();
            for (Section section : sections) {
                boolean any = false;
                for (Label item : section.items()) {
                    boolean match = q.isEmpty() || item.getText().toLowerCase().contains(q);
                    show(item, match);
                    any |= match;
                }
                show(section.header(), any);
            }
        });
        ScrollPane scroll = new ScrollPane(items);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);

        Label hint = new Label("Drag onto the form or into a container");
        hint.getStyleClass().add("hint-text");
        hint.setWrapText(true);
        hint.setPadding(new Insets(6, 0, 0, 0));
        getChildren().add(hint);
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
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
