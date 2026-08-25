package dev.dragifier.ui;

import dev.dragifier.ai.AiSettings;
import dev.dragifier.ai.OpenRouterClient;
import dev.dragifier.ai.Transport;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;

import java.io.IOException;
import java.util.List;

/** The OpenRouter key, the model to use, and whether AI changes are compile-checked. */
public final class AiSettingsDialog {

    private AiSettingsDialog() {}

    public static void show(Window owner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("AI Settings");
        dialog.setHeaderText("Connect Dragifier to OpenRouter");
        dialog.setResizable(true);

        boolean fromEnvironment = AiSettings.apiKeyFromEnvironment();

        PasswordField hidden = new PasswordField();
        TextField shown = new TextField();
        shown.textProperty().bindBidirectional(hidden.textProperty());
        hidden.setText(AiSettings.apiKey());
        hidden.setPromptText("sk-or-…");
        CheckBox reveal = new CheckBox("Show");
        shown.visibleProperty().bind(reveal.selectedProperty());
        shown.managedProperty().bind(reveal.selectedProperty());
        hidden.visibleProperty().bind(reveal.selectedProperty().not());
        hidden.managedProperty().bind(reveal.selectedProperty().not());
        StackPane keyField = new StackPane(hidden, shown);
        HBox.setHgrow(keyField, Priority.ALWAYS);
        hidden.setDisable(fromEnvironment);
        shown.setDisable(fromEnvironment);

        ComboBox<String> model = new ComboBox<>();
        model.setEditable(true);  // any slug can be typed, so a stale list never blocks anyone
        model.getItems().setAll(AiSettings.FALLBACK_MODELS);
        model.setValue(AiSettings.model());
        model.setMaxWidth(Double.MAX_VALUE);

        Button refresh = new Button("Refresh");
        Button check = new Button("Check key");
        Label result = new Label();
        result.setWrapText(true);
        result.getStyleClass().add("hint-text");

        CheckBox verify = new CheckBox("Check that AI changes compile, and let it fix what it breaks");
        verify.setSelected(AiSettings.autoVerify());

        Label storage = new Label(fromEnvironment
                ? "Using the " + AiSettings.ENV_VAR + " environment variable, so nothing is stored on disk."
                : "Stored in plain text in your user preferences. Set " + AiSettings.ENV_VAR
                        + " in your environment instead to keep it out of the registry.");
        storage.setWrapText(true);
        storage.getStyleClass().add("hint-text");

        Hyperlink getKey = new Hyperlink("Get a key at openrouter.ai/keys");
        getKey.setOnAction(e -> openLink("https://openrouter.ai/keys"));

        OpenRouterClient client = new OpenRouterClient();
        refresh.setOnAction(e -> refreshModels(client, model, refresh, result));
        check.setOnAction(e -> checkKey(client, hidden, check, result));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(4, 4, 4, 4));
        ColumnConstraints labels = new ColumnConstraints();
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        fields.setFillWidth(true);
        grid.getColumnConstraints().addAll(labels, fields, new ColumnConstraints());

        grid.addRow(0, new Label("API key"), keyField, reveal);
        grid.add(storage, 1, 1, 2, 1);
        grid.add(getKey, 1, 2, 2, 1);
        grid.addRow(3, new Label("Model"), model, refresh);
        grid.add(check, 1, 4);
        grid.add(result, 1, 5, 2, 1);
        grid.add(verify, 1, 6, 2, 1);
        grid.setPrefWidth(560);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Save", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(button -> {
            if (button.getButtonData() != ButtonBar.ButtonData.OK_DONE) {
                return;
            }
            if (!fromEnvironment) {
                AiSettings.setApiKey(hidden.getText());
            }
            AiSettings.setModel(model.getValue());
            AiSettings.setAutoVerify(verify.isSelected());
        });
    }

    /** Fetches the live model list; a failure falls back to the built-in shortlist. */
    private static void refreshModels(OpenRouterClient client, ComboBox<String> model,
                                      Button refresh, Label result) {
        refresh.setDisable(true);
        result.setText("Fetching the model list…");
        background(() -> {
            try {
                List<Transport.ModelInfo> models = client.listModels();
                String current = model.getValue();
                Platform.runLater(() -> {
                    model.getItems().setAll(models.stream().map(Transport.ModelInfo::id).toList());
                    model.setValue(current);
                    result.setText(models.size() + " models available. "
                            + "Any id can also be typed in by hand.");
                    refresh.setDisable(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    model.getItems().setAll(AiSettings.FALLBACK_MODELS);
                    result.setText("Could not fetch the list (" + message(ex)
                            + "). Showing a short built-in list, which may be out of date.");
                    refresh.setDisable(false);
                });
            }
        });
    }

    private static void checkKey(OpenRouterClient client, PasswordField key,
                                 Button check, Label result) {
        if (key.getText().isBlank() && !AiSettings.apiKeyFromEnvironment()) {
            result.setText("Enter a key first.");
            return;
        }
        // the client reads the stored key, so save what is typed before asking
        String previous = AiSettings.apiKey();
        if (!AiSettings.apiKeyFromEnvironment()) {
            AiSettings.setApiKey(key.getText());
        }
        check.setDisable(true);
        result.setText("Checking…");
        background(() -> {
            String text;
            try {
                text = client.checkKey();
            } catch (Exception ex) {
                text = message(ex);
                if (!AiSettings.apiKeyFromEnvironment()) {
                    AiSettings.setApiKey(previous);  // don't leave a bad key stored
                }
            }
            String outcome = text;
            Platform.runLater(() -> {
                result.setText(outcome);
                check.setDisable(false);
            });
        });
    }

    private static void background(Runnable work) {
        Thread thread = new Thread(work, "dragifier-ai-settings");
        thread.setDaemon(true);
        thread.start();
    }

    private static String message(Exception ex) {
        return ex.getMessage() == null ? ex.toString() : ex.getMessage();
    }

    private static void openLink(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (IOException ignored) {
            // opening a browser is best-effort
        }
    }
}
