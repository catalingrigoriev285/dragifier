package dev.dragifier.codegen;

import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import dev.dragifier.ui.Renderer;

/** Generates a standalone JavaFX application source file from a form model. */
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
        out.append("    @Override\n");
        out.append("    public void start(Stage stage) {\n");
        out.append("        Pane root = new Pane();\n");
        out.append("        root.setPrefSize(").append(fmt(model.getWidth()))
           .append(", ").append(fmt(model.getHeight())).append(");\n\n");

        for (FormComponent c : model.getComponents()) {
            String var = c.getId();
            String javaType = javaTypeFor(c);
            out.append("        ").append(javaType).append(" ").append(var)
               .append(" = new ").append(javaType).append("();\n");
            if (hasText(c)) {
                out.append("        ").append(var).append(".setText(\"")
                   .append(escape(c.getText())).append("\");\n");
            }
            out.append("        ").append(var).append(".setLayoutX(").append(fmt(c.getX())).append(");\n");
            out.append("        ").append(var).append(".setLayoutY(").append(fmt(c.getY())).append(");\n");
            out.append("        ").append(var).append(".setPrefSize(").append(fmt(c.getWidth()))
               .append(", ").append(fmt(c.getHeight())).append(");\n");
            out.append("        ").append(var).append(".setStyle(\"")
               .append(escape(Renderer.styleFor(c))).append("\");\n");
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

    private static String javaTypeFor(FormComponent c) {
        return switch (c.getType()) {
            case BUTTON -> "Button";
            case LABEL -> "Label";
            case TEXT_FIELD -> "TextField";
            case TEXT_AREA -> "TextArea";
            case CHECK_BOX -> "CheckBox";
            case SLIDER -> "Slider";
            case PANEL -> "Pane";
        };
    }

    private static boolean hasText(FormComponent c) {
        return switch (c.getType()) {
            case BUTTON, LABEL, TEXT_FIELD, TEXT_AREA, CHECK_BOX -> true;
            case SLIDER, PANEL -> false;
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
