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

        button.setTooltip("Greets the user");
        slider.setDisabled(true);
        FormComponent panel = form.create(ComponentType.PANEL, 220, 140);
        panel.setTooltip("A panel tooltip (Tooltip.install path)");
        form.getEvents().put("onShown", "label1.setText(\"Form shown\");");

        FormComponent timer = form.create(ComponentType.TIMER, 300, 380);
        timer.setValue(500);
        timer.getEvents().put("onTick",
                "label1.setText(String.valueOf(System.nanoTime()));");

        FormComponent apiButton = form.create(ComponentType.BUTTON, 150, 420);
        apiButton.setText("API demo");
        apiButton.getEvents().put("onAction",
                "if (UI.confirm(\"Continue?\")) {\n"
                + "    String name = UI.prompt(\"Your name:\", \"\");\n"
                + "    UI.alert(\"Hello, \" + name);\n"
                + "    UI.copyToClipboard(name);\n"
                + "}");

        FormComponent table = form.create(ComponentType.TABLE_VIEW, 420, 24);
        table.setColumns("Name\nAge");
        table.getEvents().put("onSelect", "label1.setText(String.valueOf(newValue));");

        FormComponent web = form.create(ComponentType.WEB_VIEW, 420, 200);
        web.setText("https://example.com");

        FormComponent media = form.create(ComponentType.MEDIA_PLAYER, 420, 400);
        media.setMediaData("AAAA"); // fake payload, only the resource pipeline is checked
        media.setMediaFormat("mp3");

        form.setResizable(true);
        FormComponent anchored = form.create(ComponentType.TEXT_AREA, 24, 460);
        anchored.setAnchorRight(true);
        anchored.setAnchorBottom(true);

        // second form, opened from the first with plain Java
        FormModel second = project.addForm();
        second.setTitle("Second Form");
        FormComponent info = second.create(ComponentType.LABEL, 24, 24);
        info.setText("This is Form2");
        FormComponent open = form.create(ComponentType.BUTTON, 24, 420);
        open.setText("Open Form2");
        open.getEvents().put("onAction", "new Form2().show();");

        project.setIconData(image.getImageData());
        project.setIconFormat("png");

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
        if (!java.nio.file.Files.exists(result.dir().resolve(JavaCodeGenerator.ICON_RESOURCE))) {
            System.err.println("SMOKE FAILED: window icon resource was not written");
            System.exit(1);
        }
        System.out.println("SMOKE OK: window icon resource written.");
        if (!java.nio.file.Files.exists(result.dir().resolve(JavaCodeGenerator.mediaResource(form, media)))) {
            System.err.println("SMOKE FAILED: media resource was not written");
            System.exit(1);
        }
        System.out.println("SMOKE OK: media resource written.");

        checkUndoRedo(project, label);
        checkRename(project, form, button);
        checkSourceMap(project, button);

        for (var template : dev.dragifier.model.Templates.all()) {
            AppRunner.CompileResult tr = AppRunner.compile(template.factory().get());
            if (!tr.ok()) {
                System.err.println("SMOKE FAILED: template '" + template.name()
                        + "' does not compile:\n" + tr.errorDetails());
                System.exit(1);
            }
        }
        System.out.println("SMOKE OK: all " + dev.dragifier.model.Templates.all().size()
                + " templates compile.");
    }

    private static void checkRename(ProjectModel project, FormModel form, FormComponent button) throws Exception {
        if (form.renameComponent(button, "1bad") || form.renameComponent(button, "label1")) {
            System.err.println("SMOKE FAILED: invalid/duplicate rename was accepted");
            System.exit(1);
        }
        if (!form.renameComponent(button, "greetButton")) {
            System.err.println("SMOKE FAILED: valid rename rejected");
            System.exit(1);
        }
        // the button's own handler references other ids, and other code may reference it;
        // verify the project still compiles after the refactor
        AppRunner.CompileResult renamed = AppRunner.compile(project);
        if (!renamed.ok()) {
            System.err.println("SMOKE FAILED: project does not compile after rename:\n" + renamed.errorDetails());
            System.exit(1);
        }
        form.renameComponent(button, "button1");
        System.out.println("SMOKE OK: component rename refactors and still compiles.");
    }

    private static void checkSourceMap(ProjectModel project, FormComponent button) throws Exception {
        String original = button.getEvents().get("onAction");
        int brokenUserLine = (int) original.lines().count(); // the appended line's 0-based index
        button.getEvents().put("onAction", original + "\nthis is not java");
        AppRunner.CompileResult broken = AppRunner.compile(project);
        button.getEvents().put("onAction", original);
        if (broken.ok() || broken.errors().isEmpty()) {
            System.err.println("SMOKE FAILED: broken event code compiled anyway");
            System.exit(1);
        }
        AppRunner.CompileError first = broken.errors().get(0);
        var entry = broken.map().resolve(first.file(), first.line());
        if (entry == null || !button.getId().equals(entry.componentId())
                || !"onAction".equals(entry.eventKey())) {
            System.err.println("SMOKE FAILED: source map did not resolve to " + button.getId()
                    + ".onAction (got " + entry + ")");
            System.exit(1);
        }
        long userLine = entry.userLine() + Math.max(0, first.line() - entry.generatedLine());
        if (userLine != brokenUserLine) {
            System.err.println("SMOKE FAILED: source map resolved to user line " + userLine
                    + " instead of " + brokenUserLine);
            System.exit(1);
        }
        System.out.println("SMOKE OK: compile errors map back to the exact event line.");
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
