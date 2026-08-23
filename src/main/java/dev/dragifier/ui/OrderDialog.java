package dev.dragifier.ui;

import dev.dragifier.model.FormComponent;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Delphi-style order editor: rearranges the form's component list, which
 * defines both z-order (last = topmost) and focus/tab order.
 */
public final class OrderDialog {

    private OrderDialog() {}

    public static Optional<List<FormComponent>> show(Window owner, List<FormComponent> components) {
        Dialog<List<FormComponent>> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Component Order");
        dialog.setHeaderText("First = bottom (drawn first, focused first) — last = top");

        ObservableList<FormComponent> items = FXCollections.observableArrayList(components);
        ListView<FormComponent> list = new ListView<>(items);
        list.setPrefSize(260, 280);
        list.setCellFactory(v -> new ListCell<>() {
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
        });

        Button top = new Button("Top");
        Button up = new Button("Up");
        Button down = new Button("Down");
        Button bottom = new Button("Bottom");
        for (Button b : List.of(top, up, down, bottom)) {
            b.setMaxWidth(Double.MAX_VALUE);
        }
        top.setOnAction(e -> move(list, items, Integer.MIN_VALUE));
        up.setOnAction(e -> move(list, items, -1));
        down.setOnAction(e -> move(list, items, 1));
        bottom.setOnAction(e -> move(list, items, Integer.MAX_VALUE));
        VBox buttons = new VBox(6, top, up, down, bottom);

        dialog.getDialogPane().setContent(new HBox(10, list, buttons));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button ->
                button == ButtonType.OK ? new ArrayList<>(items) : null);
        return dialog.showAndWait();
    }

    private static void move(ListView<FormComponent> list, ObservableList<FormComponent> items, int delta) {
        int index = list.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            return;
        }
        int target = delta == Integer.MIN_VALUE ? 0
                : delta == Integer.MAX_VALUE ? items.size() - 1
                : Math.max(0, Math.min(items.size() - 1, index + delta));
        if (target == index) {
            return;
        }
        FormComponent moved = items.remove(index);
        items.add(target, moved);
        list.getSelectionModel().select(target);
    }
}
