package dev.dragifier;

import dev.dragifier.codegen.JavaCodeGenerator;
import dev.dragifier.model.ComponentType;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import dev.dragifier.io.ProjectIO;
import dev.dragifier.runner.AppRunner;
import dev.dragifier.undo.UndoManager;

/**
 * Headless sanity check for the generate → compile pipeline: builds a small
 * form with an event handler, generates its source and compiles it.
 * Run with {@code gradlew smoke}; exits non-zero if compilation fails.
 */
public final class CodegenSmoke {

    private CodegenSmoke() {}

    public static void main(String[] args) throws Exception {
        FormModel model = new FormModel();
        model.setTitle("Smoke Test");

        FormComponent label = model.create(ComponentType.LABEL, 24, 24);
        label.setText("Waiting…");

        FormComponent field = model.create(ComponentType.TEXT_FIELD, 24, 64);

        FormComponent button = model.create(ComponentType.BUTTON, 24, 108);
        button.setText("Greet");
        button.getEvents().put("onAction",
                "label1.setText(\"Hello, \" + textField1.getText() + \"!\");\n"
                + "stage.setTitle(\"Greeted\");");

        FormComponent slider = model.create(ComponentType.SLIDER, 24, 156);
        slider.getEvents().put("onValueChange",
                "label1.setText(String.valueOf(newValue.intValue()));");

        FormComponent combo = model.create(ComponentType.COMBO_BOX, 24, 196);
        combo.setText("Pick one");
        combo.setItems("Alpha\nBeta \"quoted\"\nGamma");
        combo.getEvents().put("onAction", "label1.setText(comboBox1.getValue());");

        FormComponent list = model.create(ComponentType.LIST_VIEW, 24, 240);
        list.setItems("One\nTwo\nThree");
        list.getEvents().put("onSelect", "label1.setText(newValue);");

        FormComponent progress = model.create(ComponentType.PROGRESS_BAR, 24, 380);
        progress.setValue(42);

        String source = JavaCodeGenerator.generate(model);
        System.out.println(source);

        AppRunner.CompileResult result = AppRunner.compile(model);
        if (result.ok()) {
            System.out.println("SMOKE OK: generated source compiled cleanly.");
        } else {
            System.err.println("SMOKE FAILED:\n" + result.errorDetails());
            System.exit(1);
        }

        checkUndoRedo(model, label);
    }

    private static void checkUndoRedo(FormModel model, FormComponent label) {
        String original = label.getText();
        UndoManager undo = new UndoManager(() -> ProjectIO.toJson(model));

        undo.checkpoint(null);
        label.setText("Changed");
        FormModel undone = ProjectIO.fromJson(undo.undo());
        if (!original.equals(undone.getComponents().get(0).getText())) {
            System.err.println("SMOKE FAILED: undo did not restore label text");
            System.exit(1);
        }
        FormModel redone = ProjectIO.fromJson(undo.redo());
        if (!"Changed".equals(redone.getComponents().get(0).getText())) {
            System.err.println("SMOKE FAILED: redo did not restore label text");
            System.exit(1);
        }

        UndoManager coalescing = new UndoManager(() -> ProjectIO.toJson(model));
        coalescing.checkpoint("typing");
        coalescing.checkpoint("typing");
        coalescing.checkpoint("typing");
        if (coalescing.undo() == null || coalescing.undo() != null) {
            System.err.println("SMOKE FAILED: coalescing should record exactly one step");
            System.exit(1);
        }
        System.out.println("SMOKE OK: undo/redo snapshots behave correctly.");
    }
}
