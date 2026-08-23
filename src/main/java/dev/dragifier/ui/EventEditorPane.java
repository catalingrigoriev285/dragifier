package dev.dragifier.ui;

import dev.dragifier.model.EventSpec;
import dev.dragifier.model.FormComponent;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Bottom pane for editing a component's event handler code. The code body is
 * plain Java, inserted verbatim into the generated handler lambda.
 */
public class EventEditorPane extends VBox {

    private FormComponent current;
    private boolean updating;
    private Runnable onEdited = () -> {};

    private final Label header = new Label("Events");
    private final ComboBox<EventSpec> eventBox = new ComboBox<>();
    private final Label hint = new Label();
    private final TextArea codeArea = new TextArea();

    public EventEditorPane() {
        setSpacing(6);
        setPadding(new Insets(8, 10, 8, 10));
        setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #d0d0d0; -fx-border-width: 1 0 0 0;");

        header.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        hint.setStyle("-fx-text-fill: #909090; -fx-font-size: 11px; -fx-font-family: 'Consolas', monospace;");

        eventBox.setConverter(new StringConverter<>() {
            @Override public String toString(EventSpec spec) {
                return spec == null ? "" : spec.displayName();
            }
            @Override public EventSpec fromString(String s) {
                return null;
            }
        });
        eventBox.setOnAction(e -> loadCode());

        HBox top = new HBox(10, header, eventBox, hint);
        top.setStyle("-fx-alignment: center-left;");

        codeArea.setPromptText("Java code for the selected event, e.g.\nlabel1.setText(\"Clicked!\");");
        codeArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");
        codeArea.setPrefRowCount(6);
        VBox.setVgrow(codeArea, Priority.ALWAYS);
        codeArea.textProperty().addListener((obs, was, text) -> storeCode(text));

        getChildren().addAll(top, codeArea);
        showNone();
    }

    public void setOnEdited(Runnable onEdited) {
        this.onEdited = onEdited;
    }

    public void showComponent(FormComponent c) {
        current = c;
        updating = true;
        header.setText("Events — " + c.getId());
        eventBox.getItems().setAll(EventSpec.forType(c.getType()));
        eventBox.getSelectionModel().selectFirst();
        eventBox.setDisable(false);
        codeArea.setDisable(false);
        updating = false;
        loadCode();
    }

    public void showNone() {
        current = null;
        updating = true;
        header.setText("Events");
        eventBox.getItems().clear();
        hint.setText("");
        codeArea.clear();
        eventBox.setDisable(true);
        codeArea.setDisable(true);
        updating = false;
    }

    public void focusCode() {
        codeArea.requestFocus();
    }

    private void loadCode() {
        if (current == null) {
            return;
        }
        EventSpec spec = eventBox.getValue();
        updating = true;
        if (spec == null) {
            hint.setText("");
            codeArea.clear();
        } else {
            hint.setText(spec.hint());
            codeArea.setText(current.getEvents().getOrDefault(spec.key(), ""));
        }
        updating = false;
    }

    private void storeCode(String text) {
        if (updating || current == null) {
            return;
        }
        EventSpec spec = eventBox.getValue();
        if (spec == null) {
            return;
        }
        if (text == null || text.isBlank()) {
            current.getEvents().remove(spec.key());
        } else {
            current.getEvents().put(spec.key(), text);
        }
        onEdited.run();
    }
}
