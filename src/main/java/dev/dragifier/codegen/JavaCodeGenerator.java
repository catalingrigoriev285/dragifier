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
            sources.put(className(form) + ".java", formSource(form));
        }
        sources.put(MAIN_CLASS + ".java", mainSource(project));
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

    private static String formSource(FormModel form) {
        String cls = className(form);
        StringBuilder out = new StringBuilder();
        out.append("import javafx.scene.Scene;\n");
        out.append("import javafx.scene.control.*;\n");
        out.append("import javafx.scene.image.Image;\n");
        out.append("import javafx.scene.image.ImageView;\n");
        out.append("import javafx.scene.layout.Pane;\n");
        out.append("import javafx.stage.Stage;\n\n");
        out.append("public class ").append(cls).append(" extends Stage {\n\n");
        out.append("    private final Stage stage = this;\n");
        for (FormComponent c : form.getComponents()) {
            out.append("    private ").append(javaTypeFor(c)).append(" ").append(c.getId()).append(";\n");
        }
        out.append("\n");
        out.append("    public ").append(cls).append("() {\n");
        out.append("        Pane root = new Pane();\n");
        out.append("        root.setPrefSize(").append(fmt(form.getWidth()))
           .append(", ").append(fmt(form.getHeight())).append(");\n\n");

        for (FormComponent c : form.getComponents()) {
            String var = c.getId();
            out.append("        ").append(var).append(" = new ").append(javaTypeFor(c)).append("();\n");
            if (hasText(c)) {
                out.append("        ").append(var).append(".setText(\"")
                   .append(escape(c.getText())).append("\");\n");
            }
            appendTypeSpecific(out, form, c, cls);
            out.append("        ").append(var).append(".setLayoutX(").append(fmt(c.getX())).append(");\n");
            out.append("        ").append(var).append(".setLayoutY(").append(fmt(c.getY())).append(");\n");
            if (c.getType() == ComponentType.IMAGE_VIEW) {
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
        out.append("        setTitle(\"").append(escape(form.getTitle())).append("\");\n");
        out.append("        setScene(new Scene(root));\n");
        out.append("        setResizable(false);\n");
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
            default -> { }
        }
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
        };
    }

    private static boolean hasText(FormComponent c) {
        return switch (c.getType()) {
            case BUTTON, LABEL, TEXT_FIELD, TEXT_AREA, CHECK_BOX, RADIO_BUTTON, HYPERLINK -> true;
            case SLIDER, PANEL, COMBO_BOX, LIST_VIEW, PROGRESS_BAR, IMAGE_VIEW -> false;
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
