package dev.dragifier.ui;

import dev.dragifier.model.EventSpec;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

/**
 * Bottom pane for editing a component's event handler code, with Java syntax
 * highlighting and line numbers. The code body is plain Java, inserted
 * verbatim into the generated handler lambda.
 */
public class EventEditorPane extends VBox {

    private java.util.Map<String, String> currentEvents;
    private String checkpointPrefix = "";
    private boolean updating;
    private Runnable onEdited = () -> {};
    private java.util.function.Consumer<String> checkpoint = tag -> {};

    private final Label header = new Label("Events");
    private final ComboBox<EventSpec> eventBox = new ComboBox<>();
    private final Label hint = new Label();
    private final CodeArea codeArea = new CodeArea();

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

        codeArea.getStyleClass().add("code-area");
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        Label placeholder = new Label(
                "Java code for the selected event, e.g.  label1.setText(\"Clicked!\");");
        placeholder.setStyle("-fx-text-fill: #a8a8a8; -fx-font-family: 'Consolas', monospace;");
        codeArea.setPlaceholder(placeholder);
        codeArea.textProperty().addListener((obs, was, text) -> {
            storeCode(text);
            codeArea.setStyleSpans(0, JavaSyntax.highlight(text));
        });

        VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(codeArea);
        scroll.setPrefHeight(150);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(top, scroll);
        showNone();
    }

    public void setOnEdited(Runnable onEdited) {
        this.onEdited = onEdited;
    }

    public void setCheckpoint(java.util.function.Consumer<String> checkpoint) {
        this.checkpoint = checkpoint;
    }

    public void showComponent(FormComponent c) {
        showTarget("Events — " + c.getId(), EventSpec.forType(c.getType()),
                c.getEvents(), "code:" + c.getId());
    }

    public void showForm(FormModel form) {
        showTarget("Events — " + form.getName() + " (form)", EventSpec.forForm(),
                form.getEvents(), "code:form:" + form.getName());
    }

    private void showTarget(String title, java.util.List<EventSpec> specs,
                            java.util.Map<String, String> events, String prefix) {
        currentEvents = events;
        checkpointPrefix = prefix;
        updating = true;
        header.setText(title);
        eventBox.getItems().setAll(specs);
        eventBox.getSelectionModel().selectFirst();
        boolean hasEvents = !specs.isEmpty();
        eventBox.setDisable(!hasEvents);
        codeArea.setDisable(!hasEvents);
        updating = false;
        loadCode();
    }

    public void showNone() {
        currentEvents = null;
        updating = true;
        header.setText("Events");
        eventBox.getItems().clear();
        hint.setText("");
        codeArea.replaceText("");
        eventBox.setDisable(true);
        codeArea.setDisable(true);
        updating = false;
    }

    public void focusCode() {
        codeArea.requestFocus();
    }

    private void loadCode() {
        if (currentEvents == null) {
            return;
        }
        EventSpec spec = eventBox.getValue();
        updating = true;
        if (spec == null) {
            hint.setText("");
            codeArea.replaceText("");
        } else {
            hint.setText(spec.hint());
            codeArea.replaceText(currentEvents.getOrDefault(spec.key(), ""));
        }
        updating = false;
    }

    private void storeCode(String text) {
        if (updating || currentEvents == null) {
            return;
        }
        EventSpec spec = eventBox.getValue();
        if (spec == null) {
            return;
        }
        checkpoint.accept(checkpointPrefix + ":" + spec.key());
        if (text == null || text.isBlank()) {
            currentEvents.remove(spec.key());
        } else {
            currentEvents.put(spec.key(), text);
        }
        onEdited.run();
    }
}
