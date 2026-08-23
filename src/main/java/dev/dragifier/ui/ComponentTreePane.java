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

    private final ListView<FormComponent> list = new ListView<>();
    private FormModel model;
    private boolean updating;
    private Consumer<FormComponent> onPick = c -> {};

    public ComponentTreePane() {
        setSpacing(6);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #d0d0d0; -fx-border-width: 1 1 0 0;");

        Label title = new Label("Components");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(FormComponent item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item.getId() + " — " + item.getType().displayName);
            }
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
