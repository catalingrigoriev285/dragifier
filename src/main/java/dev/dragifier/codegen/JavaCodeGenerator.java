package dev.dragifier.codegen;

import dev.dragifier.model.ComponentType;
import dev.dragifier.model.EventSpec;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import dev.dragifier.model.ProjectModel;
import dev.dragifier.ui.Renderer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates Java sources from a project: one {@code Stage} subclass per form
 * (named after the form, components as fields so event handlers can reference
 * them by id) plus a {@code Main} application class that opens the main form.
 * Opening another form from user code is plain Java: {@code new Form2().show();}
 */
public final class JavaCodeGenerator {

    public static final String MAIN_CLASS = "Main";
    /** Entry point that does not extend Application, so classpath launches work too. */
    public static final String LAUNCHER_CLASS = "Launcher";
    public static final String ICON_RESOURCE = "app_icon.png";

    private JavaCodeGenerator() {}

    /** The form's generated class name, derived from its name. */
    public static String className(FormModel form) {
        StringBuilder sb = new StringBuilder();
        for (char ch : form.getName().toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                sb.append(ch);
            }
        }
        if (sb.isEmpty() || Character.isDigit(sb.charAt(0))) {
            return "Form";
        }
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        return sb.toString();
    }

    /** The jar resource name for an Image component's bytes. */
    public static String imageResource(FormModel form, FormComponent c) {
        return className(form) + "_" + c.getId() + ".img";
    }

    /** All sources for the project, filename → content. */
    public static Map<String, String> generateProject(ProjectModel project) {
        Map<String, String> sources = new LinkedHashMap<>();
        for (FormModel form : project.getForms()) {
            sources.put(className(form) + ".java", formSource(project, form));
        }
        sources.put(MAIN_CLASS + ".java", mainSource(project));
        sources.put(LAUNCHER_CLASS + ".java",
                "public class " + LAUNCHER_CLASS + " {\n"
                + "    public static void main(String[] args) {\n"
                + "        javafx.application.Application.launch(" + MAIN_CLASS + ".class, args);\n"
                + "    }\n"
                + "}\n");
        sources.put(RuntimeApi.FILE_NAME, RuntimeApi.SOURCE);
        return sources;
    }

    private static String mainSource(ProjectModel project) {
        return "import javafx.application.Application;\n"
                + "import javafx.stage.Stage;\n\n"
                + "public class " + MAIN_CLASS + " extends Application {\n\n"
                + "    @Override\n"
                + "    public void start(Stage primaryStage) {\n"
                + "        new " + className(project.effectiveMain()) + "().show();\n"
                + "    }\n\n"
                + "    public static void main(String[] args) {\n"
                + "        launch(args);\n"
                + "    }\n"
                + "}\n";
    }

    private static String formSource(ProjectModel project, FormModel form) {
        String cls = className(form);
        StringBuilder out = new StringBuilder();
        out.append("import javafx.animation.Animation;\n");
        out.append("import javafx.animation.KeyFrame;\n");
        out.append("import javafx.animation.Timeline;\n");
        out.append("import javafx.beans.property.ReadOnlyStringWrapper;\n");
        out.append("import javafx.collections.ObservableList;\n");
        out.append("import javafx.geometry.Pos;\n");
        out.append("import javafx.scene.Scene;\n");
        out.append("import javafx.scene.control.*;\n");
        out.append("import javafx.scene.image.Image;\n");
        out.append("import javafx.scene.image.ImageView;\n");
        out.append("import javafx.scene.layout.AnchorPane;\n");
        out.append("import javafx.scene.layout.Pane;\n");
        out.append("import javafx.scene.media.Media;\n");
        out.append("import javafx.scene.media.MediaPlayer;\n");
        out.append("import javafx.scene.media.MediaView;\n");
        out.append("import javafx.scene.web.WebView;\n");
        out.append("import javafx.stage.Stage;\n");
        out.append("import javafx.util.Duration;\n");
        out.append("import java.io.File;\n\n");
        out.append("public class ").append(cls).append(" extends Stage {\n\n");
        out.append("    private final Stage stage = this;\n");
        for (FormComponent c : form.getComponents()) {
            out.append("    private ").append(javaTypeFor(c)).append(" ").append(c.getId()).append(";\n");
        }
        out.append("\n");
        out.append("    public ").append(cls).append("() {\n");
        out.append("        AnchorPane root = new AnchorPane();\n");
        out.append("        root.setPrefSize(").append(fmt(form.getWidth()))
           .append(", ").append(fmt(form.getHeight())).append(");\n\n");

        for (FormComponent c : form.getComponents()) {
            String var = c.getId();
            if (c.getType() == ComponentType.TIMER) {
                appendTimer(out, c);
                continue;
            }
            out.append("        ").append(var).append(" = new ").append(javaTypeFor(c)).append("();\n");
            if (hasText(c)) {
                out.append("        ").append(var).append(".setText(\"")
                   .append(escape(c.getText())).append("\");\n");
            }
            appendTypeSpecific(out, form, c, cls);
            appendPosition(out, form, c);
            if (c.getType() == ComponentType.IMAGE_VIEW || c.getType() == ComponentType.MEDIA_PLAYER) {
                out.append("        ").append(var).append(".setFitWidth(").append(fmt(c.getWidth())).append(");\n");
                out.append("        ").append(var).append(".setFitHeight(").append(fmt(c.getHeight())).append(");\n");
            } else {
                out.append("        ").append(var).append(".setPrefSize(").append(fmt(c.getWidth()))
                   .append(", ").append(fmt(c.getHeight())).append(");\n");
                out.append("        ").append(var).append(".setStyle(\"")
                   .append(escape(Renderer.styleFor(c))).append("\");\n");
            }
            if (c.isDisabled()) {
                out.append("        ").append(var).append(".setDisable(true);\n");
            }
            if (!c.getAlignment().isEmpty() && Renderer.supportsAlignment(c.getType())) {
                String pos = switch (c.getAlignment()) {
                    case "CENTER" -> "Pos.CENTER";
                    case "RIGHT" -> "Pos.CENTER_RIGHT";
                    default -> "Pos.CENTER_LEFT";
                };
                out.append("        ").append(var).append(".setAlignment(").append(pos).append(");\n");
            }
            if (!c.getTooltip().isEmpty()) {
                boolean isControl = c.getType() != ComponentType.PANEL && c.getType() != ComponentType.IMAGE_VIEW;
                if (isControl) {
                    out.append("        ").append(var).append(".setTooltip(new Tooltip(\"")
                       .append(escape(c.getTooltip())).append("\"));\n");
                } else {
                    out.append("        Tooltip.install(").append(var).append(", new Tooltip(\"")
                       .append(escape(c.getTooltip())).append("\"));\n");
                }
            }
            appendEvents(out, c);
            out.append("        root.getChildren().add(").append(var).append(");\n\n");
        }

        appendFormEvents(out, form);
        out.append("        setResizable(").append(form.isResizable()).append(");\n");
        if (project.hasWindowIcon()) {
            out.append("        getIcons().add(new Image(").append(cls)
               .append(".class.getResourceAsStream(\"/").append(ICON_RESOURCE).append("\")));\n");
        }
        out.append("        setTitle(\"").append(escape(form.getTitle())).append("\");\n");
        out.append("        setScene(new Scene(root));\n");
        out.append("    }\n");
        out.append("}\n");
        return out.toString();
    }

    private static void appendTypeSpecific(StringBuilder out, FormModel form, FormComponent c, String cls) {
        String var = c.getId();
        switch (c.getType()) {
            case IMAGE_VIEW -> {
                if (!c.getImageData().isEmpty()) {
                    out.append("        ").append(var).append(".setImage(new Image(")
                       .append(cls).append(".class.getResourceAsStream(\"/")
                       .append(imageResource(form, c)).append("\")));\n");
                }
            }
            case COMBO_BOX, LIST_VIEW -> {
                var items = Renderer.itemList(c);
                if (!items.isEmpty()) {
                    out.append("        ").append(var).append(".getItems().addAll(");
                    for (int i = 0; i < items.size(); i++) {
                        if (i > 0) {
                            out.append(", ");
                        }
                        out.append("\"").append(escape(items.get(i))).append("\"");
                    }
                    out.append(");\n");
                }
                if (c.getType() == ComponentType.COMBO_BOX && !c.getText().isEmpty()) {
                    out.append("        ").append(var).append(".setPromptText(\"")
                       .append(escape(c.getText())).append("\");\n");
                }
            }
            case PROGRESS_BAR -> out.append("        ").append(var).append(".setProgress(")
                    .append(c.getValue() / 100.0).append(");\n");
            case TABLE_VIEW -> {
                var columns = Renderer.lines(c.getColumns());
                for (int i = 0; i < columns.size(); i++) {
                    String colVar = var + "Col" + i;
                    final int idx = i;
                    out.append("        TableColumn<ObservableList<String>, String> ").append(colVar)
                       .append(" = new TableColumn<>(\"").append(escape(columns.get(i))).append("\");\n");
                    out.append("        ").append(colVar)
                       .append(".setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().size() > ")
                       .append(idx).append(" ? d.getValue().get(").append(idx).append(") : \"\"));\n");
                    out.append("        ").append(var).append(".getColumns().add(").append(colVar).append(");\n");
                }
            }
            case WEB_VIEW -> {
                if (!c.getText().isEmpty()) {
                    out.append("        ").append(var).append(".getEngine().load(\"")
                       .append(escape(c.getText())).append("\");\n");
                }
            }
            case MEDIA_PLAYER -> {
                if (!c.getMediaData().isEmpty()) {
                    out.append("        MediaPlayer ").append(var).append("Player = new MediaPlayer(new Media(\n")
                       .append("                new File(UI.resourceToTempFile(\"/")
                       .append(mediaResource(form, c)).append("\")).toURI().toString()));\n");
                    out.append("        ").append(var).append(".setMediaPlayer(").append(var).append("Player);\n");
                    if (!c.isDisabled()) {
                        out.append("        ").append(var).append("Player.setAutoPlay(true);\n");
                    }
                }
            }
            default -> { }
        }
    }

    /** The jar resource name for a Media component's bytes. */
    public static String mediaResource(FormModel form, FormComponent c) {
        return className(form) + "_" + c.getId() + ".media";
    }

    /** Emits either AnchorPane anchors or plain layoutX/Y, per the component's anchor flags. */
    private static void appendPosition(StringBuilder out, FormModel form, FormComponent c) {
        String var = c.getId();
        if (c.isAnchorLeft()) {
            out.append("        AnchorPane.setLeftAnchor(").append(var).append(", ")
               .append(c.getX()).append(");\n");
        } else if (!c.isAnchorRight()) {
            out.append("        ").append(var).append(".setLayoutX(").append(fmt(c.getX())).append(");\n");
        }
        if (c.isAnchorRight()) {
            out.append("        AnchorPane.setRightAnchor(").append(var).append(", ")
               .append(form.getWidth() - c.getX() - c.getWidth()).append(");\n");
        }
        if (c.isAnchorTop()) {
            out.append("        AnchorPane.setTopAnchor(").append(var).append(", ")
               .append(c.getY()).append(");\n");
        } else if (!c.isAnchorBottom()) {
            out.append("        ").append(var).append(".setLayoutY(").append(fmt(c.getY())).append(");\n");
        }
        if (c.isAnchorBottom()) {
            out.append("        AnchorPane.setBottomAnchor(").append(var).append(", ")
               .append(form.getHeight() - c.getY() - c.getHeight()).append(");\n");
        }
    }

    /** Timers become Timelines: no node, tick code embedded in the KeyFrame. */
    private static void appendTimer(StringBuilder out, FormComponent c) {
        String var = c.getId();
        double interval = c.getValue() <= 0 ? 1000 : c.getValue();
        out.append("        ").append(var).append(" = new Timeline(new KeyFrame(Duration.millis(")
           .append(fmt(interval)).append("), event -> {\n");
        String code = c.getEvents().get("onTick");
        if (code != null && !code.isBlank()) {
            for (String line : code.split("\n", -1)) {
                out.append("            ").append(line.stripTrailing()).append("\n");
            }
        }
        out.append("        }));\n");
        out.append("        ").append(var).append(".setCycleCount(Animation.INDEFINITE);\n");
        if (!c.isDisabled()) {
            out.append("        ").append(var).append(".play();\n");
        }
        out.append("\n");
    }

    private static void appendFormEvents(StringBuilder out, FormModel form) {
        for (EventSpec spec : EventSpec.forForm()) {
            String code = form.getEvents().get(spec.key());
            if (code == null || code.isBlank()) {
                continue;
            }
            out.append("        ").append(spec.setter()).append("(event -> {\n");
            for (String line : code.split("\n", -1)) {
                out.append("            ").append(line.stripTrailing()).append("\n");
            }
            out.append("        });\n");
        }
    }

    private static void appendEvents(StringBuilder out, FormComponent c) {
        for (EventSpec spec : EventSpec.forType(c.getType())) {
            String code = c.getEvents().get(spec.key());
            if (code == null || code.isBlank()) {
                continue;
            }
            String var = c.getId();
            switch (spec.kind()) {
                case SETTER -> out.append("        ").append(var).append(".").append(spec.setter()).append("(event -> {\n");
                case VALUE_LISTENER -> out.append("        ").append(var)
                        .append(".valueProperty().addListener((obs, oldValue, newValue) -> {\n");
                case SELECTION_LISTENER -> out.append("        ").append(var)
                        .append(".getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {\n");
                case TIMER_TICK -> {
                    continue; // embedded by appendTimer, never reaches here
                }
            }
            for (String line : code.split("\n", -1)) {
                out.append("            ").append(line.stripTrailing()).append("\n");
            }
            out.append("        });\n");
        }
    }

    private static String javaTypeFor(FormComponent c) {
        return switch (c.getType()) {
            case BUTTON -> "Button";
            case LABEL -> "Label";
            case TEXT_FIELD -> "TextField";
            case TEXT_AREA -> "TextArea";
            case CHECK_BOX -> "CheckBox";
            case SLIDER -> "Slider";
            case PANEL -> "Pane";
            case COMBO_BOX -> "ComboBox<String>";
            case LIST_VIEW -> "ListView<String>";
            case RADIO_BUTTON -> "RadioButton";
            case PROGRESS_BAR -> "ProgressBar";
            case HYPERLINK -> "Hyperlink";
            case IMAGE_VIEW -> "ImageView";
            case TIMER -> "Timeline";
            case TABLE_VIEW -> "TableView<ObservableList<String>>";
            case WEB_VIEW -> "WebView";
            case MEDIA_PLAYER -> "MediaView";
        };
    }

    private static boolean hasText(FormComponent c) {
        return switch (c.getType()) {
            case BUTTON, LABEL, TEXT_FIELD, TEXT_AREA, CHECK_BOX, RADIO_BUTTON, HYPERLINK -> true;
            case SLIDER, PANEL, COMBO_BOX, LIST_VIEW, PROGRESS_BAR, IMAGE_VIEW, TIMER,
                 TABLE_VIEW, WEB_VIEW, MEDIA_PLAYER -> false;
        };
    }

    private static String fmt(double v) {
        if (v == Math.floor(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
