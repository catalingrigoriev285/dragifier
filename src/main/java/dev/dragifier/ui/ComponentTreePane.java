package dev.dragifier.ui;

import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/** Lists every component on the form; selection is kept in sync with the canvas. */
public class ComponentTreePane extends VBox {

    private static final String DRAG_PREFIX = "dragifier-tree:";

    private final ListView<FormComponent> list = new ListView<>();
    private FormModel model;
    private boolean updating;
    private Consumer<FormComponent> onPick = c -> {};
    private java.util.function.BiConsumer<Integer, Integer> onReorder = (from, to) -> {};

    public ComponentTreePane() {
        setSpacing(6);
        setPadding(new Insets(10));
        getStyleClass().add("side-panel");

        Label title = new Label("Components");
        title.getStyleClass().add("panel-header");

        list.setCellFactory(v -> {
            ListCell<FormComponent> cell = new ListCell<>() {
                @Override
                protected void updateItem(FormComponent item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(item.getId() + " — " + item.getType().displayName);
                        setGraphic(Icons.forType(item.getType()));
                    }
                }
            };
            cell.setOnDragDetected(e -> {
                if (cell.getItem() != null) {
                    var db = cell.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
                    var content = new javafx.scene.input.ClipboardContent();
                    content.putString(DRAG_PREFIX + cell.getIndex());
                    db.setContent(content);
                    e.consume();
                }
            });
            cell.setOnDragOver(e -> {
                if (e.getDragboard().hasString() && e.getDragboard().getString().startsWith(DRAG_PREFIX)) {
                    e.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
                }
                e.consume();
            });
            cell.setOnDragDropped(e -> {
                String payload = e.getDragboard().hasString() ? e.getDragboard().getString() : "";
                if (payload.startsWith(DRAG_PREFIX)) {
                    int from = Integer.parseInt(payload.substring(DRAG_PREFIX.length()));
                    int to = cell.getItem() == null ? list.getItems().size() - 1 : cell.getIndex();
                    onReorder.accept(from, to);
                    e.setDropCompleted(true);
                }
                e.consume();
            });
            return cell;
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, was, picked) -> {
            if (!updating && picked != null) {
                onPick.accept(picked);
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        getChildren().addAll(title, list);
    }

    public void setOnPick(Consumer<FormComponent> onPick) {
        this.onPick = onPick;
    }

    public void setOnReorder(java.util.function.BiConsumer<Integer, Integer> onReorder) {
        this.onReorder = onReorder;
    }

    public void setModel(FormModel model) {
        this.model = model;
        refresh();
    }

    public void refresh() {
        updating = true;
        list.getItems().setAll(model == null ? java.util.List.of() : model.getComponents());
        updating = false;
    }

    public void select(FormComponent c) {
        updating = true;
        if (c == null) {
            list.getSelectionModel().clearSelection();
        } else {
            list.getSelectionModel().select(c);
        }
        updating = false;
    }
}
