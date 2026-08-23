package dev.dragifier;

import dev.dragifier.codegen.JavaCodeGenerator;
import dev.dragifier.io.ProjectIO;
import dev.dragifier.model.ComponentType;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import dev.dragifier.model.ProjectModel;
import dev.dragifier.runner.AppRunner;
import dev.dragifier.undo.UndoManager;

/**
 * Headless sanity check for the generate → compile pipeline: builds a
 * two-form project exercising every component family, generates its sources
 * and compiles them. Run with {@code gradlew smoke}; exits non-zero on failure.
 */
public final class CodegenSmoke {

    private CodegenSmoke() {}

    public static void main(String[] args) throws Exception {
        ProjectModel project = ProjectModel.withDefaultForm();
        FormModel form = project.effectiveMain();
        form.setTitle("Smoke Test");

        FormComponent label = form.create(ComponentType.LABEL, 24, 24);
        label.setText("Waiting…");

        FormComponent field = form.create(ComponentType.TEXT_FIELD, 24, 64);

        FormComponent button = form.create(ComponentType.BUTTON, 24, 108);
        button.setText("Greet");
        button.getEvents().put("onAction",
                "label1.setText(\"Hello, \" + textField1.getText() + \"!\");\n"
                + "stage.setTitle(\"Greeted\");");

        FormComponent slider = form.create(ComponentType.SLIDER, 24, 156);
        slider.getEvents().put("onValueChange",
                "label1.setText(String.valueOf(newValue.intValue()));");

        FormComponent combo = form.create(ComponentType.COMBO_BOX, 24, 196);
        combo.setText("Pick one");
        combo.setItems("Alpha\nBeta \"quoted\"\nGamma");
        combo.getEvents().put("onAction", "label1.setText(comboBox1.getValue());");

        FormComponent list = form.create(ComponentType.LIST_VIEW, 24, 240);
        list.setItems("One\nTwo\nThree");
        list.getEvents().put("onSelect", "label1.setText(newValue);");

        FormComponent progress = form.create(ComponentType.PROGRESS_BAR, 24, 380);
        progress.setValue(42);

        FormComponent image = form.create(ComponentType.IMAGE_VIEW, 220, 24);
        // 1x1 transparent PNG
        image.setImageData("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
        image.getEvents().put("onMouseClicked", "label1.setText(\"Image clicked\");");

        // second form, opened from the first with plain Java
        FormModel second = project.addForm();
        second.setTitle("Second Form");
        FormComponent info = second.create(ComponentType.LABEL, 24, 24);
        info.setText("This is Form2");
        FormComponent open = form.create(ComponentType.BUTTON, 24, 420);
        open.setText("Open Form2");
        open.getEvents().put("onAction", "new Form2().show();");

        for (var entry : JavaCodeGenerator.generateProject(project).entrySet()) {
            System.out.println("===== " + entry.getKey() + " =====");
            System.out.println(entry.getValue());
        }

        AppRunner.CompileResult result = AppRunner.compile(project);
        if (result.ok()) {
            System.out.println("SMOKE OK: generated sources compiled cleanly (2 forms + Main).");
        } else {
            System.err.println("SMOKE FAILED:\n" + result.errorDetails());
            System.exit(1);
        }
        if (!java.nio.file.Files.exists(result.dir().resolve(JavaCodeGenerator.imageResource(form, image)))) {
            System.err.println("SMOKE FAILED: image resource was not written to the build dir");
            System.exit(1);
        }
        System.out.println("SMOKE OK: image resource written next to classes.");

        checkUndoRedo(project, label);
    }

    private static void checkUndoRedo(ProjectModel project, FormComponent label) {
        String original = label.getText();
        UndoManager undo = new UndoManager(() -> ProjectIO.toJson(project));

        undo.checkpoint(null);
        label.setText("Changed");
        ProjectModel undone = ProjectIO.fromJson(undo.undo());
        if (!original.equals(undone.getForms().get(0).getComponents().get(0).getText())) {
            System.err.println("SMOKE FAILED: undo did not restore label text");
            System.exit(1);
        }
        ProjectModel redone = ProjectIO.fromJson(undo.redo());
        if (!"Changed".equals(redone.getForms().get(0).getComponents().get(0).getText())) {
            System.err.println("SMOKE FAILED: redo did not restore label text");
            System.exit(1);
        }

        UndoManager coalescing = new UndoManager(() -> ProjectIO.toJson(project));
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
