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
    private final javafx.scene.control.MenuButton insertMenu = new javafx.scene.control.MenuButton("Insert");
    private java.util.function.Supplier<java.util.List<String>> formNames = java.util.List::of;
    private java.util.function.Supplier<FormModel> contextForm = () -> null;

    private final javafx.stage.Popup completionPopup = new javafx.stage.Popup();
    private final javafx.scene.control.ListView<String> completionList = new javafx.scene.control.ListView<>();
    private int completionPrefixLength;

    public EventEditorPane() {
        setSpacing(6);
        setPadding(new Insets(8, 10, 8, 10));
        getStyleClass().add("bottom-panel");

        header.getStyleClass().add("panel-header");
        hint.getStyleClass().add("mono-hint");

        eventBox.setConverter(new StringConverter<>() {
            @Override public String toString(EventSpec spec) {
                return spec == null ? "" : spec.displayName();
            }
            @Override public EventSpec fromString(String s) {
                return null;
            }
        });
        eventBox.setOnAction(e -> loadCode());

        buildInsertMenu();
        HBox top = new HBox(10, header, eventBox, insertMenu, hint);
        top.setStyle("-fx-alignment: center-left;");

        codeArea.getStyleClass().add("code-area");
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        Label placeholder = new Label(
                "Java code for the selected event, e.g.  label1.setText(\"Clicked!\");");
        placeholder.getStyleClass().add("mono-hint");
        codeArea.setPlaceholder(placeholder);
        codeArea.textProperty().addListener((obs, was, text) -> {
            storeCode(text);
            codeArea.setStyleSpans(0, JavaSyntax.highlight(text));
            if (completionPopup.isShowing()) {
                javafx.application.Platform.runLater(this::openCompletion);
            }
        });
        setupCompletion();

        VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(codeArea);
        scroll.setPrefHeight(150);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(top, scroll);
        showNone();
    }

    public void setOnEdited(Runnable onEdited) {
        this.onEdited = onEdited;
    }

    /** Supplies the project's form names for the "Open form" snippet submenu. */
    public void setFormNames(java.util.function.Supplier<java.util.List<String>> formNames) {
        this.formNames = formNames;
    }

    /** Supplies the active form, for autocomplete's id → type lookup. */
    public void setContextForm(java.util.function.Supplier<FormModel> contextForm) {
        this.contextForm = contextForm;
    }

    // ------------------------------------------------------------ completion

    private void setupCompletion() {
        completionList.setPrefSize(280, 160);
        completionList.setFocusTraversable(false);
        completionList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                acceptCompletion();
            }
        });
        completionPopup.getContent().add(completionList);
        completionPopup.setAutoHide(true);

        codeArea.addEventHandler(javafx.scene.input.KeyEvent.KEY_TYPED, e -> {
            if (".".equals(e.getCharacter())) {
                javafx.application.Platform.runLater(this::openCompletion);
            }
        });
        codeArea.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.SPACE && e.isControlDown()) {
                openCompletion();
                e.consume();
                return;
            }
            if (!completionPopup.isShowing()) {
                return;
            }
            switch (e.getCode()) {
                case DOWN -> {
                    completionList.getSelectionModel().selectNext();
                    e.consume();
                }
                case UP -> {
                    completionList.getSelectionModel().selectPrevious();
                    e.consume();
                }
                case ENTER, TAB -> {
                    acceptCompletion();
                    e.consume();
                }
                case ESCAPE -> {
                    completionPopup.hide();
                    e.consume();
                }
                default -> { }
            }
        });
    }

    private void openCompletion() {
        if (codeArea.isDisabled()) {
            return;
        }
        int caret = codeArea.getCaretPosition();
        String before = codeArea.getText(0, caret);

        int p = before.length();
        while (p > 0 && Character.isJavaIdentifierPart(before.charAt(p - 1))) {
            p--;
        }
        String prefix = before.substring(p);
        String qualifier = null;
        if (p > 0 && before.charAt(p - 1) == '.') {
            int q = p - 1;
            while (q > 0 && Character.isJavaIdentifierPart(before.charAt(q - 1))) {
                q--;
            }
            qualifier = before.substring(q, p - 1);
        }

        java.util.List<String> candidates = candidatesFor(qualifier);
        String lowerPrefix = prefix.toLowerCase();
        java.util.List<String> filtered = candidates.stream()
                .filter(s -> s.toLowerCase().startsWith(lowerPrefix))
                .toList();
        if (filtered.isEmpty()) {
            completionPopup.hide();
            return;
        }
        completionPrefixLength = prefix.length();
        completionList.getItems().setAll(filtered);
        completionList.getSelectionModel().selectFirst();
        codeArea.getCaretBounds().ifPresent(bounds ->
                completionPopup.show(codeArea, bounds.getMinX(), bounds.getMaxY() + 2));
    }

    private java.util.List<String> candidatesFor(String qualifier) {
        if (qualifier == null || qualifier.isEmpty()) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            FormModel form = contextForm.get();
            if (form != null) {
                for (FormComponent c : form.getComponents()) {
                    ids.add(c.getId());
                }
            }
            ids.add("UI");
            ids.add("stage");
            return ids;
        }
        if (qualifier.equals("UI")) {
            return CompletionCatalog.uiHelpers();
        }
        if (qualifier.equals("stage")) {
            return CompletionCatalog.stageMethods();
        }
        FormModel form = contextForm.get();
        if (form != null) {
            for (FormComponent c : form.getComponents()) {
                if (c.getId().equals(qualifier)) {
                    return CompletionCatalog.methodsFor(c.getType());
                }
            }
        }
        return java.util.List.of();
    }

    private void acceptCompletion() {
        String chosen = completionList.getSelectionModel().getSelectedItem();
        completionPopup.hide();
        if (chosen == null) {
            return;
        }
        int caret = codeArea.getCaretPosition();
        codeArea.replaceText(caret - completionPrefixLength, caret, chosen);
        codeArea.requestFocus();
    }

    private void buildInsertMenu() {
        insertMenu.setGraphic(new org.kordamp.ikonli.javafx.FontIcon(
                org.kordamp.ikonli.feather.Feather.PLUS_SQUARE));
        javafx.scene.control.Menu openForm = new javafx.scene.control.Menu("Open form");
        openForm.setOnShowing(e -> {
            openForm.getItems().clear();
            for (String name : formNames.get()) {
                javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(name);
                item.setOnAction(a -> insertSnippet("new " + name + "().show();\n"));
                openForm.getItems().add(item);
            }
        });
        openForm.getItems().add(new javafx.scene.control.MenuItem("…"));
        insertMenu.getItems().addAll(
                snippet("Show message", "UI.alert(\"Hello!\");\n"),
                snippet("Ask confirmation", "if (UI.confirm(\"Are you sure?\")) {\n    \n}\n"),
                snippet("Ask for input", "String answer = UI.prompt(\"Enter a value:\", \"\");\n"),
                snippet("Set label text", "label1.setText(\"New text\");\n"),
                openForm,
                snippet("Close this window", "stage.close();\n"),
                snippet("Open link", "UI.openLink(\"https://example.com\");\n"),
                snippet("Copy to clipboard", "UI.copyToClipboard(\"text\");\n"),
                snippet("Print to console", "System.out.println(\"debug\");\n"));
    }

    private javafx.scene.control.MenuItem snippet(String label, String code) {
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(label);
        item.setOnAction(e -> insertSnippet(code));
        return item;
    }

    private void insertSnippet(String code) {
        if (codeArea.isDisabled()) {
            return;
        }
        codeArea.insertText(codeArea.getCaretPosition(), code);
        codeArea.requestFocus();
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
        insertMenu.setDisable(!hasEvents);
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
        insertMenu.setDisable(true);
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
