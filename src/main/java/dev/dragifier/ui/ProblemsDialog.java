package dev.dragifier.ui;

import dev.dragifier.runner.AppRunner;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Lists compile errors; "Go to Code" (or double-click) jumps to the offending event code. */
public final class ProblemsDialog {

    private ProblemsDialog() {}

    public static void show(Window owner, List<AppRunner.CompileError> errors,
                            Consumer<AppRunner.CompileError> onGoTo) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Problems");
        dialog.setHeaderText(errors.size() + (errors.size() == 1 ? " compile error" : " compile errors"));

        ListView<AppRunner.CompileError> list = new ListView<>();
        list.getItems().setAll(errors);
        list.setPrefSize(560, 220);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(AppRunner.CompileError item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String file = item.file().isEmpty() ? "?"
                            : Path.of(item.file()).getFileName().toString();
                    String firstLine = item.message() == null ? ""
                            : item.message().lines().findFirst().orElse("");
                    setText(file + ":" + item.line() + "  —  " + firstLine);
                }
            }
        });
        list.getSelectionModel().selectFirst();

        ButtonType goTo = new ButtonType("Go to Code", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().setContent(list);
        dialog.getDialogPane().getButtonTypes().addAll(goTo, ButtonType.CLOSE);

        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) {
                onGoTo.accept(list.getSelectionModel().getSelectedItem());
                dialog.close();
            }
        });
        dialog.setResultConverter(button -> {
            if (button == goTo && list.getSelectionModel().getSelectedItem() != null) {
                onGoTo.accept(list.getSelectionModel().getSelectedItem());
            }
            return null;
        });
        dialog.show();
    }
}
