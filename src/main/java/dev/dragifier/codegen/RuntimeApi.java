package dev.dragifier.codegen;

/**
 * The {@code UI.java} helper emitted into every generated project — DevelNext's
 * dialog/clipboard/file modules, as one small plain-Java class users call from
 * event code: {@code UI.alert("Hi")}, {@code if (UI.confirm("Sure?")) ...}
 */
public final class RuntimeApi {

    public static final String FILE_NAME = "UI.java";

    private RuntimeApi() {}

    public static final String SOURCE = """
            import javafx.collections.FXCollections;
            import javafx.collections.ObservableList;
            import javafx.scene.control.Alert;
            import javafx.scene.control.ButtonType;
            import javafx.scene.control.TextInputDialog;
            import javafx.scene.input.Clipboard;
            import javafx.scene.input.ClipboardContent;
            import javafx.stage.FileChooser;

            import java.io.File;
            import java.io.InputStream;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.nio.file.StandardCopyOption;

            /** Helper API for Dragifier apps. */
            public final class UI {

                private UI() {}

                /** Shows an information message box. */
                public static void alert(String message) {
                    Alert a = new Alert(Alert.AlertType.INFORMATION, message);
                    a.setHeaderText(null);
                    a.showAndWait();
                }

                /** Asks an OK/Cancel question; returns true when OK was chosen. */
                public static boolean confirm(String message) {
                    Alert a = new Alert(Alert.AlertType.CONFIRMATION, message);
                    a.setHeaderText(null);
                    return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
                }

                /** Asks the user to type a value; returns null when cancelled. */
                public static String prompt(String message, String defaultValue) {
                    TextInputDialog d = new TextInputDialog(defaultValue);
                    d.setHeaderText(null);
                    d.setContentText(message);
                    return d.showAndWait().orElse(null);
                }

                public static void copyToClipboard(String text) {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(text);
                    Clipboard.getSystemClipboard().setContent(content);
                }

                /** Opens a file-open dialog; returns the chosen path or null. */
                public static String openFileDialog() {
                    File f = new FileChooser().showOpenDialog(null);
                    return f == null ? null : f.getAbsolutePath();
                }

                /** Opens a file-save dialog; returns the chosen path or null. */
                public static String saveFileDialog() {
                    File f = new FileChooser().showSaveDialog(null);
                    return f == null ? null : f.getAbsolutePath();
                }

                /** Opens a link in the system browser. */
                public static void openLink(String url) {
                    try {
                        String os = System.getProperty("os.name", "").toLowerCase();
                        if (os.contains("win")) {
                            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                        } else if (os.contains("mac")) {
                            new ProcessBuilder("open", url).start();
                        } else {
                            new ProcessBuilder("xdg-open", url).start();
                        }
                    } catch (Exception ignored) {
                    }
                }

                /** A table row: {@code table1.getItems().add(UI.row("Ana", "20"));} */
                public static ObservableList<String> row(String... values) {
                    return FXCollections.observableArrayList(values);
                }

                /** Copies a bundled resource to a temp file and returns its path. */
                public static String resourceToTempFile(String resource) {
                    try (InputStream in = UI.class.getResourceAsStream(resource)) {
                        Path tmp = Files.createTempFile("app-resource", ".bin");
                        tmp.toFile().deleteOnExit();
                        Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                        return tmp.toAbsolutePath().toString();
                    } catch (Exception ex) {
                        throw new RuntimeException("Could not load resource " + resource, ex);
                    }
                }
            }
            """;
}
