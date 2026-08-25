package dev.dragifier.codegen;

/**
 * The {@code UI.java} helper emitted into every generated project — dialogs,
 * clipboard, files and notifications as one small plain-Java class users call
 * from event code: {@code UI.alert("Hi")}, {@code if (UI.confirm("Sure?")) ...},
 * {@code UI.notifySuccess("Saved")}. Plain JavaFX only: generated apps have no
 * third-party libraries on their classpath.
 */
public final class RuntimeApi {

    public static final String FILE_NAME = "UI.java";

    private RuntimeApi() {}

    public static final String SOURCE = """
            import javafx.animation.FadeTransition;
            import javafx.animation.PauseTransition;
            import javafx.application.Platform;
            import javafx.beans.value.ChangeListener;
            import javafx.collections.FXCollections;
            import javafx.collections.ObservableList;
            import javafx.geometry.Insets;
            import javafx.geometry.Pos;
            import javafx.scene.control.Alert;
            import javafx.scene.control.ButtonType;
            import javafx.scene.control.Label;
            import javafx.scene.control.TextInputDialog;
            import javafx.scene.input.Clipboard;
            import javafx.scene.input.ClipboardContent;
            import javafx.scene.layout.HBox;
            import javafx.scene.layout.VBox;
            import javafx.stage.DirectoryChooser;
            import javafx.stage.FileChooser;
            import javafx.stage.Popup;
            import javafx.stage.Window;
            import javafx.util.Duration;

            import java.io.File;
            import java.io.InputStream;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.nio.file.StandardCopyOption;
            import java.util.ArrayList;
            import java.util.HashMap;
            import java.util.List;
            import java.util.Map;

            /** Helper API for Dragifier apps. */
            public final class UI {

                private UI() {}

                // ------------------------------------------------------------ dialogs

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

                /** Opens a folder chooser; returns the chosen folder path or null. */
                public static String chooseFolder() {
                    File f = new DirectoryChooser().showDialog(null);
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

                // ------------------------------------------------------ notifications

                private static final List<Popup> TOASTS = new ArrayList<>();

                /** Shows a small message in the corner of the window for a few seconds. */
                public static void notify(String message) {
                    toast("", message, "info", 4);
                }

                /** Shows a titled notification in the corner of the window. */
                public static void notify(String title, String message) {
                    toast(title, message, "info", 4);
                }

                public static void notifySuccess(String message) {
                    toast("Success", message, "success", 4);
                }

                public static void notifyWarning(String message) {
                    toast("Warning", message, "warning", 5);
                }

                public static void notifyError(String message) {
                    toast("Error", message, "error", 6);
                }

                private static void toast(String title, String message, String kind, double seconds) {
                    if (!Platform.isFxApplicationThread()) {
                        Platform.runLater(() -> toast(title, message, kind, seconds));
                        return;
                    }
                    Window owner = null;
                    for (Window w : Window.getWindows()) {
                        if (w.isShowing() && w.isFocused()) {
                            owner = w;
                            break;
                        }
                    }
                    if (owner == null) {
                        for (Window w : Window.getWindows()) {
                            if (w.isShowing()) {
                                owner = w;
                                break;
                            }
                        }
                    }
                    if (owner == null) {
                        alert(title == null || title.isEmpty() ? message : title + ": " + message);
                        return;
                    }
                    String accent = switch (kind) {
                        case "success" -> "#1a7f37";
                        case "warning" -> "#9a6700";
                        case "error" -> "#cf222e";
                        default -> "#0969da";
                    };
                    String glyph = switch (kind) {
                        case "success" -> "\\u2714";
                        case "warning" -> "\\u26a0";
                        case "error" -> "\\u2716";
                        default -> "\\u2139";
                    };
                    Label icon = new Label(glyph);
                    icon.setStyle("-fx-text-fill: " + accent + "; -fx-font-size: 18px;");
                    VBox text = new VBox(2);
                    if (title != null && !title.isEmpty()) {
                        Label t = new Label(title);
                        t.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
                        text.getChildren().add(t);
                    }
                    Label body = new Label(message == null ? "" : message);
                    body.setWrapText(true);
                    body.setMaxWidth(280);
                    body.setStyle("-fx-text-fill: white;");
                    text.getChildren().add(body);
                    HBox box = new HBox(10, icon, text);
                    box.setAlignment(Pos.CENTER_LEFT);
                    box.setPadding(new Insets(10, 14, 10, 12));
                    box.setMinWidth(220);
                    box.setStyle("-fx-background-color: #24292f; -fx-background-radius: 6;"
                            + " -fx-border-color: " + accent + "; -fx-border-width: 0 0 0 4; -fx-border-radius: 6;"
                            + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 12, 0, 0, 3);");

                    Popup popup = new Popup();
                    popup.setAutoFix(false);
                    popup.getContent().add(box);
                    Window target = owner;
                    ChangeListener<Number> follow = (obs, was, is) -> positionToasts();
                    popup.setOnHidden(e -> {
                        TOASTS.remove(popup);
                        target.xProperty().removeListener(follow);
                        target.yProperty().removeListener(follow);
                        target.widthProperty().removeListener(follow);
                        target.heightProperty().removeListener(follow);
                        positionToasts();
                    });
                    box.setOnMouseClicked(e -> popup.hide());
                    TOASTS.add(popup);
                    popup.show(owner);
                    target.xProperty().addListener(follow);
                    target.yProperty().addListener(follow);
                    target.widthProperty().addListener(follow);
                    target.heightProperty().addListener(follow);
                    positionToasts();

                    FadeTransition in = new FadeTransition(Duration.millis(180), box);
                    in.setFromValue(0);
                    in.setToValue(1);
                    in.play();
                    PauseTransition wait = new PauseTransition(Duration.seconds(seconds));
                    wait.setOnFinished(e -> {
                        FadeTransition out = new FadeTransition(Duration.millis(250), box);
                        out.setFromValue(1);
                        out.setToValue(0);
                        out.setOnFinished(x -> popup.hide());
                        out.play();
                    });
                    wait.play();
                }

                /** Stacks the open toasts bottom-right of their windows, newest at the bottom. */
                private static void positionToasts() {
                    Map<Window, Double> bottoms = new HashMap<>();
                    for (int i = TOASTS.size() - 1; i >= 0; i--) {
                        Popup p = TOASTS.get(i);
                        Window w = p.getOwnerWindow();
                        if (w == null) {
                            continue;
                        }
                        double bottom = bottoms.getOrDefault(w, w.getY() + w.getHeight() - 16);
                        double y = bottom - p.getHeight();
                        p.setX(w.getX() + w.getWidth() - p.getWidth() - 16);
                        p.setY(y);
                        bottoms.put(w, y - 8);
                    }
                }

                // ---------------------------------------------------------------- files

                /** Reads a whole text file; returns null when it cannot be read. */
                public static String readFile(String path) {
                    try {
                        return Files.readString(Path.of(path));
                    } catch (Exception ex) {
                        return null;
                    }
                }

                /** Writes (replaces) a text file; returns false when it cannot be written. */
                public static boolean writeFile(String path, String text) {
                    try {
                        Files.writeString(Path.of(path), text == null ? "" : text);
                        return true;
                    } catch (Exception ex) {
                        return false;
                    }
                }

                // ---------------------------------------------------------------- data

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

                // --------------------------------------------------------------- numbers

                /**
                 * Evaluates an arithmetic expression: {@code + - * / %}, parentheses and
                 * unary minus; the symbols {@code × ÷ −} and a decimal comma are accepted.
                 * Returns NaN when the expression is invalid.
                 */
                public static double eval(String expression) {
                    try {
                        String s = expression == null ? "" : expression
                                .replace('\\u00d7', '*').replace('\\u00f7', '/').replace('\\u2212', '-').replace(',', '.');
                        int[] pos = {0};
                        double value = parseSum(s, pos);
                        skipSpaces(s, pos);
                        return pos[0] == s.length() ? value : Double.NaN;
                    } catch (RuntimeException ex) {
                        return Double.NaN;
                    }
                }

                /** Formats a number for display: {@code 2.0 → "2"}, NaN/infinite → {@code "Error"}. */
                public static String formatNumber(double value) {
                    if (Double.isNaN(value) || Double.isInfinite(value)) {
                        return "Error";
                    }
                    if (value == Math.rint(value) && Math.abs(value) < 1e15) {
                        return String.valueOf((long) value);
                    }
                    return String.valueOf(Math.round(value * 1e10) / 1e10);
                }

                private static void skipSpaces(String s, int[] pos) {
                    while (pos[0] < s.length() && Character.isWhitespace(s.charAt(pos[0]))) {
                        pos[0]++;
                    }
                }

                private static double parseSum(String s, int[] pos) {
                    double value = parseProduct(s, pos);
                    while (true) {
                        skipSpaces(s, pos);
                        if (pos[0] < s.length() && s.charAt(pos[0]) == '+') {
                            pos[0]++;
                            value += parseProduct(s, pos);
                        } else if (pos[0] < s.length() && s.charAt(pos[0]) == '-') {
                            pos[0]++;
                            value -= parseProduct(s, pos);
                        } else {
                            return value;
                        }
                    }
                }

                private static double parseProduct(String s, int[] pos) {
                    double value = parseUnary(s, pos);
                    while (true) {
                        skipSpaces(s, pos);
                        char op = pos[0] < s.length() ? s.charAt(pos[0]) : ' ';
                        if (op == '*') {
                            pos[0]++;
                            value *= parseUnary(s, pos);
                        } else if (op == '/') {
                            pos[0]++;
                            value /= parseUnary(s, pos);
                        } else if (op == '%') {
                            pos[0]++;
                            value %= parseUnary(s, pos);
                        } else {
                            return value;
                        }
                    }
                }

                private static double parseUnary(String s, int[] pos) {
                    skipSpaces(s, pos);
                    if (pos[0] < s.length() && s.charAt(pos[0]) == '-') {
                        pos[0]++;
                        return -parseUnary(s, pos);
                    }
                    if (pos[0] < s.length() && s.charAt(pos[0]) == '+') {
                        pos[0]++;
                        return parseUnary(s, pos);
                    }
                    return parseAtom(s, pos);
                }

                private static double parseAtom(String s, int[] pos) {
                    skipSpaces(s, pos);
                    if (pos[0] < s.length() && s.charAt(pos[0]) == '(') {
                        pos[0]++;
                        double value = parseSum(s, pos);
                        skipSpaces(s, pos);
                        if (pos[0] >= s.length() || s.charAt(pos[0]) != ')') {
                            throw new IllegalArgumentException("missing )");
                        }
                        pos[0]++;
                        return value;
                    }
                    int start = pos[0];
                    while (pos[0] < s.length() && (Character.isDigit(s.charAt(pos[0])) || s.charAt(pos[0]) == '.')) {
                        pos[0]++;
                    }
                    if (start == pos[0]) {
                        throw new IllegalArgumentException("number expected");
                    }
                    return Double.parseDouble(s.substring(start, pos[0]));
                }
            }
            """;
}
