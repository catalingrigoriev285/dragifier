package dev.dragifier.codegen;

import dev.dragifier.model.EventSpec;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import dev.dragifier.ui.Renderer;

/**
 * Generates a standalone JavaFX application source file from a form model.
 * Components are emitted as fields so event handler code can reference any
 * component on the form by its id.
 */
public final class JavaCodeGenerator {

    private JavaCodeGenerator() {}

    public static String className(FormModel model) {
        StringBuilder sb = new StringBuilder();
        for (char ch : model.getTitle().toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                sb.append(ch);
            }
        }
        if (sb.isEmpty() || Character.isDigit(sb.charAt(0))) {
            return "MainForm";
        }
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        return sb.toString();
    }

    public static String generate(FormModel model) {
        String cls = className(model);
        StringBuilder out = new StringBuilder();
        out.append("import javafx.application.Application;\n");
        out.append("import javafx.scene.Scene;\n");
        out.append("import javafx.scene.control.*;\n");
        out.append("import javafx.scene.layout.Pane;\n");
        out.append("import javafx.stage.Stage;\n\n");
        out.append("public class ").append(cls).append(" extends Application {\n\n");
        out.append("    private Stage stage;\n");
        for (FormComponent c : model.getComponents()) {
            out.append("    private ").append(javaTypeFor(c)).append(" ").append(c.getId()).append(";\n");
        }
        out.append("\n");
        out.append("    @Override\n");
        out.append("    public void start(Stage primaryStage) {\n");
        out.append("        this.stage = primaryStage;\n");
        out.append("        Pane root = new Pane();\n");
        out.append("        root.setPrefSize(").append(fmt(model.getWidth()))
           .append(", ").append(fmt(model.getHeight())).append(");\n\n");

        for (FormComponent c : model.getComponents()) {
            String var = c.getId();
            out.append("        ").append(var).append(" = new ").append(javaTypeFor(c)).append("();\n");
            if (hasText(c)) {
                out.append("        ").append(var).append(".setText(\"")
                   .append(escape(c.getText())).append("\");\n");
            }
            appendTypeSpecific(out, c);
            out.append("        ").append(var).append(".setLayoutX(").append(fmt(c.getX())).append(");\n");
            out.append("        ").append(var).append(".setLayoutY(").append(fmt(c.getY())).append(");\n");
            out.append("        ").append(var).append(".setPrefSize(").append(fmt(c.getWidth()))
               .append(", ").append(fmt(c.getHeight())).append(");\n");
            out.append("        ").append(var).append(".setStyle(\"")
               .append(escape(Renderer.styleFor(c))).append("\");\n");
            appendEvents(out, c);
            out.append("        root.getChildren().add(").append(var).append(");\n\n");
        }

        out.append("        stage.setTitle(\"").append(escape(model.getTitle())).append("\");\n");
        out.append("        stage.setScene(new Scene(root));\n");
        out.append("        stage.setResizable(false);\n");
        out.append("        stage.show();\n");
        out.append("    }\n\n");
        out.append("    public static void main(String[] args) {\n");
        out.append("        launch(args);\n");
        out.append("    }\n");
        out.append("}\n");
        return out.toString();
    }

    private static void appendTypeSpecific(StringBuilder out, FormComponent c) {
        String var = c.getId();
        switch (c.getType()) {
            case COMBO_BOX, LIST_VIEW -> {
                var items = dev.dragifier.ui.Renderer.itemList(c);
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
                if (c.getType() == dev.dragifier.model.ComponentType.COMBO_BOX && !c.getText().isEmpty()) {
                    out.append("        ").append(var).append(".setPromptText(\"")
                       .append(escape(c.getText())).append("\");\n");
                }
            }
            case PROGRESS_BAR -> out.append("        ").append(var).append(".setProgress(")
                    .append(c.getValue() / 100.0).append(");\n");
            default -> { }
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
        };
    }

    private static boolean hasText(FormComponent c) {
        return switch (c.getType()) {
            case BUTTON, LABEL, TEXT_FIELD, TEXT_AREA, CHECK_BOX, RADIO_BUTTON, HYPERLINK -> true;
            case SLIDER, PANEL, COMBO_BOX, LIST_VIEW, PROGRESS_BAR -> false;
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
