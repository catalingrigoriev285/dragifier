package dev.dragifier.ui;

import dev.dragifier.ai.AiSession;
import dev.dragifier.ai.AiSettings;
import dev.dragifier.ai.Transport;
import dev.dragifier.runner.AppRunner;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/**
 * The chat side of the assistant: a transcript, a composer, and a status line.
 *
 * <p>Deliberately a dumb view — it renders what it is told and calls back when
 * the user does something, the same shape as every other pane here. What
 * actually happens to the project lives in {@link AiSession}.
 */
public class AiChatPane extends VBox {

    /** Wider than the inspector's 254: a chat column any narrower wraps every other word. */
    public static final double CONTENT_WIDTH = 320;

    private final VBox messages = new VBox(10);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final TextArea input = new TextArea();
    private final Button send = new Button("Send");
    private final Button stop = new Button("Stop");
    private final Label modelLabel = new Label();
    private final Label status = new Label();
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final HBox statusBar;
    private final Label cost = new Label();

    /** The bubble currently being streamed into, if a turn is running. */
    private TextArea streaming;
    private double sessionCost;

    private Runnable onSend = () -> {};
    private Runnable onStop = () -> {};
    private Runnable onOpenSettings = () -> {};

    public AiChatPane() {
        setSpacing(8);
        setPadding(new Insets(10));
        setPrefWidth(CONTENT_WIDTH);
        setMinWidth(CONTENT_WIDTH);
        getStyleClass().addAll("side-panel", "ai-pane");

        Label header = new Label("AI Assistant");
        header.getStyleClass().add("panel-header");
        modelLabel.getStyleClass().add("hint-text");
        Button settings = new Button(null, new FontIcon(Feather.SETTINGS));
        settings.getStyleClass().addAll("flat", "button-icon", "small");
        settings.setTooltip(new Tooltip("AI settings"));
        settings.setFocusTraversable(false);
        settings.setOnAction(e -> onOpenSettings.run());
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox top = new HBox(6, header, headerSpacer, modelLabel, settings);
        top.setAlignment(Pos.CENTER_LEFT);

        messages.setPadding(new Insets(2, 2, 2, 2));
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        spinner.setPrefSize(14, 14);
        spinner.setMaxSize(14, 14);
        status.getStyleClass().add("hint-text");
        statusBar = new HBox(6, spinner, status);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        showStatus("");

        input.setPromptText("Describe the app you want, e.g. “make me a calculator”");
        input.setWrapText(true);
        input.setPrefRowCount(3);
        input.getStyleClass().add("ai-input");
        input.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                fireSend();
            }
        });

        send.setDefaultButton(false);
        send.setOnAction(e -> fireSend());
        stop.setOnAction(e -> onStop.run());
        stop.setVisible(false);
        stop.setManaged(false);
        cost.getStyleClass().add("hint-text");
        Region buttonSpacer = new Region();
        HBox.setHgrow(buttonSpacer, Priority.ALWAYS);
        HBox buttons = new HBox(6, send, stop, buttonSpacer, cost);
        buttons.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(top, scroll, statusBar, input, buttons);
        refreshModelLabel();
        greet();
    }

    // ---------------------------------------------------------------- wiring

    public void setOnSend(Runnable onSend) {
        this.onSend = onSend;
    }

    public void setOnStop(Runnable onStop) {
        this.onStop = onStop;
    }

    public void setOnOpenSettings(Runnable onOpenSettings) {
        this.onOpenSettings = onOpenSettings;
    }

    /** The composer's text, cleared by {@link #consumeInput()}. */
    public String consumeInput() {
        String text = input.getText().strip();
        input.clear();
        return text;
    }

    public void focusInput() {
        input.requestFocus();
    }

    public void refreshModelLabel() {
        modelLabel.setText(AiSettings.configured() ? AiSettings.model() : "no API key");
    }

    // --------------------------------------------------------------- rendering

    private void greet() {
        Label hint = new Label("""
                Describe what you want and I'll build it — the layout and the code behind it.

                Try: "make me a calculator", "add a settings window", or "make the buttons bigger".""");
        hint.setWrapText(true);
        hint.getStyleClass().add("hint-text");
        messages.getChildren().add(hint);
    }

    public void addUserMessage(String text) {
        messages.getChildren().add(bubble("You", text, "ai-bubble-user"));
        scrollToBottom();
    }

    /** Opens an assistant bubble that {@link #appendDelta} then streams into. */
    public void beginAssistant() {
        VBox bubble = bubble("AI", "", "ai-bubble-assistant");
        streaming = (TextArea) bubble.getChildren().get(1);
        messages.getChildren().add(bubble);
        scrollToBottom();
    }

    public void appendDelta(String text) {
        if (streaming == null) {
            beginAssistant();
        }
        streaming.appendText(text);
        fitToText(streaming);
        scrollToBottom();
    }

    /** Closes the current bubble and appends what actually happened to the project. */
    public void endAssistant(AiSession.Turn turn) {
        if (streaming != null && streaming.getText().isBlank() && !turn.reply().isBlank()) {
            streaming.setText(turn.reply());  // nothing streamed (prose reply, or ops came first)
            fitToText(streaming);
        }
        streaming = null;
        Node summary = summary(turn);
        if (summary != null) {
            messages.getChildren().add(summary);
        }
        addUsage(turn.usage());
        scrollToBottom();
    }

    public void addError(String message) {
        VBox bubble = bubble("Error", message, "ai-bubble-error");
        messages.getChildren().add(bubble);
        streaming = null;
        scrollToBottom();
    }

    /** Ground truth about the turn, built from the apply report rather than the model's prose. */
    private Node summary(AiSession.Turn turn) {
        VBox box = new VBox(2);
        box.getStyleClass().add("ai-summary");
        int applied = turn.report().applied();
        if (applied > 0) {
            box.getChildren().add(note("✓ " + applied
                    + (applied == 1 ? " change applied" : " changes applied"), "ai-status-ok"));
        }
        if (turn.compiled() && applied > 0) {
            box.getChildren().add(note(turn.repaired()
                    ? "✓ compiles (after a fix)" : "✓ compiles", "ai-status-ok"));
        }
        for (AppRunner.CompileError error : capped(turn.errors())) {
            box.getChildren().add(note("✗ " + firstLine(error.message()), "ai-status-error"));
        }
        if (!turn.errors().isEmpty()) {
            box.getChildren().add(note("Press Ctrl+Z to undo this change, or ask me to try again.",
                    "ai-status-error"));
        }
        for (String warning : turn.report().warnings()) {
            box.getChildren().add(note("! " + warning, "ai-status-warn"));
        }
        if (!turn.note().isBlank()) {
            box.getChildren().add(note(turn.note(), "ai-status-warn"));
        }
        return box.getChildren().isEmpty() ? null : box;
    }

    private static List<AppRunner.CompileError> capped(List<AppRunner.CompileError> errors) {
        return errors.size() <= 5 ? errors : errors.subList(0, 5);
    }

    private Label note(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().addAll("ai-note", styleClass);
        return label;
    }

    /**
     * A message. The body is a read-only {@link TextArea} rather than a Label so
     * generated code can be selected and copied — the whole point of asking the
     * assistant to explain something.
     */
    private VBox bubble(String role, String text, String styleClass) {
        Label who = new Label(role);
        who.getStyleClass().add("ai-role");
        TextArea body = new TextArea(text);
        body.setEditable(false);
        body.setWrapText(true);
        body.getStyleClass().add("ai-body");
        body.setFocusTraversable(false);
        fitToText(body);
        VBox box = new VBox(2, who, body);
        box.getStyleClass().addAll("ai-bubble", styleClass);
        return box;
    }

    /**
     * Grows the bubble with its content. A TextArea has no "size to fit", so this
     * estimates from the line count — good enough, and it keeps the transcript
     * scrolling as one column instead of nesting scrollbars inside every message.
     */
    private void fitToText(TextArea area) {
        int lines = 1;
        for (String line : area.getText().split("\n", -1)) {
            lines += 1 + line.length() / 42;  // ~42 characters fit at this pane width
        }
        area.setPrefRowCount(Math.min(40, Math.max(1, lines)));
        area.setPrefHeight(Math.min(40, Math.max(1, lines)) * 17.0 + 12);
    }

    private void addUsage(Transport.Usage usage) {
        if (!usage.known()) {
            return;
        }
        if (usage.cost() != null) {
            sessionCost += usage.cost();
        }
        String line = "↑" + usage.promptTokens() + " ↓" + usage.completionTokens();
        if (usage.cost() != null) {
            line += String.format(" · ≈$%.4f", usage.cost());
        }
        messages.getChildren().add(note(line, "ai-usage"));
        cost.setText(sessionCost > 0 ? String.format("session ≈$%.4f", sessionCost) : "");
    }

    // ----------------------------------------------------------------- state

    public void showStatus(String message) {
        boolean busy = message != null && !message.isBlank();
        status.setText(busy ? message : "");
        statusBar.setVisible(busy);
        statusBar.setManaged(busy);
    }

    /** Swaps Send for Stop and blocks a second turn while one is running. */
    public void setBusy(boolean busy) {
        send.setDisable(busy);
        input.setDisable(busy);
        stop.setVisible(busy);
        stop.setManaged(busy);
        if (!busy) {
            showStatus("");
        }
    }

    private void fireSend() {
        if (!send.isDisabled()) {
            onSend.run();
        }
    }

    private void scrollToBottom() {
        // after layout, or the height it scrolls against is the previous one
        javafx.application.Platform.runLater(() -> scroll.setVvalue(1.0));
    }

    private static String firstLine(String message) {
        return message == null ? "" : message.lines().findFirst().orElse("");
    }
}
