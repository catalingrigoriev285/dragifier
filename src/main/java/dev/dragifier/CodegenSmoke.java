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

        // nested containers: children are created with (parent, slot) and use content-relative coordinates
        FormComponent nestedButton = form.create(ComponentType.BUTTON, 10, 10, panel, "");
        nestedButton.setText("In panel");
        FormComponent group = form.create(ComponentType.GROUP_BOX, 700, 24);
        group.setText("Settings");
        FormComponent groupField = form.create(ComponentType.TEXT_FIELD, 10, 10, group, "");
        groupField.setAnchorRight(true);
        FormComponent innerPanel = form.create(ComponentType.PANEL, 10, 50, group, "");
        innerPanel.setWidth(180);
        innerPanel.setHeight(60);
        FormComponent deepLink = form.create(ComponentType.HYPERLINK, 4, 4, innerPanel, "");
        deepLink.getEvents().put("onAction", "textField2.setText(\"deep\");");
        FormComponent scroll = form.create(ComponentType.SCROLL_PANE, 700, 200);
        FormComponent bigLabel = form.create(ComponentType.LABEL, 10, 10, scroll, "");
        bigLabel.setWidth(400);
        bigLabel.setHeight(300);
        FormComponent tabs = form.create(ComponentType.TAB_PANE, 700, 380);
        tabs.setItems("General\nAdvanced \"quoted\"");
        form.create(ComponentType.CHECK_BOX, 10, 10, tabs, "0");
        form.create(ComponentType.SLIDER, 10, 10, tabs, "1");
        tabs.getEvents().put("onTabChange", "label1.setText(newValue.getText());");
        FormComponent split = form.create(ComponentType.SPLIT_PANE, 1000, 24);
        split.setOrientation("VERTICAL");
        split.setPanes(3);
        split.setDividers("0.3, 0.6");
        form.create(ComponentType.LABEL, 4, 4, split, "0");
        FormComponent splitArea = form.create(ComponentType.TEXT_AREA, 4, 4, split, "2");
        splitArea.setAnchorRight(true);
        splitArea.setAnchorBottom(true);

        // auto-layout containers: x/y are ignored, the container lays children out
        FormComponent stack = form.create(ComponentType.STACK_PANEL, 1000, 220);
        for (int i = 0; i < 3; i++) {
            form.create(ComponentType.BUTTON, 0, 0, stack, "").setText("Stack " + i);
        }
        FormComponent hstack = form.create(ComponentType.STACK_PANEL, 1000, 400);
        hstack.setOrientation("HORIZONTAL");
        hstack.setSpacing(10);
        form.create(ComponentType.LABEL, 0, 0, hstack, "");
        form.create(ComponentType.TEXT_FIELD, 0, 0, hstack, "");
        FormComponent grid = form.create(ComponentType.GRID_PANE, 1300, 24);
        grid.setGridColumns(3);
        grid.setGridRows(2);
        form.create(ComponentType.LABEL, 0, 0, grid, "0,0");
        form.create(ComponentType.BUTTON, 0, 0, grid, "1,0");
        form.create(ComponentType.CHECK_BOX, 0, 0, grid, "0,1");
        form.create(ComponentType.IMAGE_VIEW, 0, 0, grid, "2,1");
        form.create(ComponentType.PROGRESS_BAR, 0, 0, grid, "9,9"); // out of range → clamped
        FormComponent dock = form.create(ComponentType.DOCK_PANEL, 1300, 220);
        for (String region : dev.dragifier.model.ContainerGeometry.DOCK_REGIONS) {
            form.create(ComponentType.LABEL, 0, 0, dock, region).setText(region);
        }
        FormComponent dockedPanel = form.create(ComponentType.PANEL, 0, 0, dock, "bogus"); // → CENTER
        form.create(ComponentType.BUTTON, 5, 5, dockedPanel, "");

        // Delphi-style docking: a toolbar strip on top, a sidebar left, an editor filling the rest
        FormComponent shell = form.create(ComponentType.PANEL, 1600, 24);
        shell.setWidth(400);
        shell.setHeight(300);
        // docking is sequential in z-order: top and bottom strips first, then the sidebar, then the fill
        FormComponent toolbar = form.create(ComponentType.PANEL, 0, 0, shell, "");
        toolbar.setHeight(30);
        toolbar.setDock(dev.dragifier.model.Dock.TOP);
        FormComponent statusBar = form.create(ComponentType.LABEL, 0, 0, shell, "");
        statusBar.setDock(dev.dragifier.model.Dock.BOTTOM);
        FormComponent sidebar = form.create(ComponentType.LIST_VIEW, 0, 0, shell, "");
        sidebar.setWidth(100);
        sidebar.setDock(dev.dragifier.model.Dock.LEFT);
        FormComponent editor = form.create(ComponentType.TEXT_AREA, 0, 0, shell, "");
        editor.setDock(dev.dragifier.model.Dock.FILL);

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
        checkNesting(form, group);
        checkDocking(form, shell, toolbar, sidebar, editor, statusBar);

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

    private static void fail(String message) {
        System.err.println("SMOKE FAILED: " + message);
        System.exit(1);
    }

    /** Hierarchy invariants: subtree duplicate/remove, rename of a container, reparent guard, z-order, old JSON. */
    private static void checkNesting(FormModel form, FormComponent group) {
        int before = form.getComponents().size();
        var original = form.subtree(group);
        if (original.size() != 4) {
            fail("expected group subtree of 4, got " + original.size());
        }
        FormComponent copy = form.duplicate(group);
        var copied = form.subtree(copy);
        if (copied.size() != original.size()) {
            fail("duplicate did not copy the subtree (" + copied.size() + ")");
        }
        for (FormComponent d : copied) {
            if (d != copy && !form.isAncestor(copy, d)) {
                fail("copied descendant " + d.getId() + " is not under the copy");
            }
            if (original.contains(d)) {
                fail("duplicate shares a component with the original");
            }
        }
        if (form.getComponents().stream().map(FormComponent::getId).distinct().count() != form.getComponents().size()) {
            fail("duplicate produced clashing ids");
        }
        if (!form.renameComponent(copy, "groupCopy")) {
            fail("could not rename the copied group");
        }
        var kids = form.childrenOf(copy);
        if (kids.isEmpty() || kids.stream().anyMatch(k -> !"groupCopy".equals(k.getParentId()))) {
            fail("renaming a container did not update its children's parentId");
        }
        if (form.reparent(copy, kids.get(0), "")) {
            fail("reparenting a container into its own child was accepted");
        }
        form.toFront(copy);
        var top = form.childrenOf(null);
        if (top.get(top.size() - 1) != copy) {
            fail("toFront did not move the copy to the end of its sibling group");
        }
        if (form.childrenOf(copy).size() != kids.size()) {
            fail("toFront lost the copy's children");
        }
        form.remove(copy);
        if (form.getComponents().size() != before || form.findById("groupCopy") != null) {
            fail("remove did not delete the whole subtree");
        }
        // a pre-nesting project file (no parentId/slot/dock keys) loads with form-level defaults
        FormModel old = new com.google.gson.Gson().fromJson(
                "{\"name\":\"Old\",\"components\":[{\"id\":\"button1\",\"type\":\"BUTTON\","
                + "\"x\":1,\"y\":2,\"width\":50,\"height\":20}]}", FormModel.class);
        FormComponent legacy = old.getComponents().get(0);
        if (legacy.getParentId() != null || legacy.getDock() != dev.dragifier.model.Dock.NONE
                || !legacy.getSlot().isEmpty() || old.childrenOf(null).size() != 1) {
            fail("legacy component did not get nesting defaults");
        }
        System.out.println("SMOKE OK: nesting invariants hold (subtree copy/remove, rename, reparent guard, legacy JSON).");
    }

    /** Sequential docking resolves to the expected rectangles (codegen already ran it while generating). */
    private static void checkDocking(FormModel form, FormComponent shell, FormComponent toolbar,
                                     FormComponent sidebar, FormComponent editor, FormComponent statusBar) {
        dev.dragifier.model.DockLayout.applyTo(form, shell);
        double w = shell.getWidth(), h = shell.getHeight();
        expectRect(toolbar, 0, 0, w, 30);
        expectRect(sidebar, 0, 30, 100, h - 30 - statusBar.getHeight());
        expectRect(statusBar, 0, h - statusBar.getHeight(), w, statusBar.getHeight());
        expectRect(editor, 100, 30, w - 100, h - 30 - statusBar.getHeight());
        boolean[] anchors = dev.dragifier.model.DockLayout.anchors(editor);
        if (!(anchors[0] && anchors[1] && anchors[2] && anchors[3])) {
            fail("FILL dock should anchor all four sides");
        }
        System.out.println("SMOKE OK: docking resolves TOP/LEFT/FILL/BOTTOM to the expected rectangles.");
    }

    private static void expectRect(FormComponent c, double x, double y, double w, double h) {
        if (c.getX() != x || c.getY() != y || c.getWidth() != w || c.getHeight() != h) {
            fail(c.getId() + " docked to " + c.getDock() + " is at (" + c.getX() + "," + c.getY() + ","
                    + c.getWidth() + "," + c.getHeight() + "), expected (" + x + "," + y + "," + w + "," + h + ")");
        }
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
