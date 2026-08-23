package dev.dragifier;

import dev.dragifier.codegen.JavaCodeGenerator;
import dev.dragifier.model.ComponentType;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import dev.dragifier.runner.AppRunner;

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

        String source = JavaCodeGenerator.generate(model);
        System.out.println(source);

        AppRunner.CompileResult result = AppRunner.compile(model);
        if (result.ok()) {
            System.out.println("SMOKE OK: generated source compiled cleanly.");
        } else {
            System.err.println("SMOKE FAILED:\n" + result.errorDetails());
            System.exit(1);
        }
    }
}
