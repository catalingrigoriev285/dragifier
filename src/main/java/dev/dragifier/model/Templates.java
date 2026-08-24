package dev.dragifier.model;

import java.util.List;
import java.util.function.Supplier;

/** Ready-made starter projects for File → New from Template. */
public final class Templates {

    public record Template(String name, Supplier<ProjectModel> factory) {}

    private Templates() {}

    public static List<Template> all() {
        return List.of(
                new Template("Hello World", Templates::helloWorld),
                new Template("Counter", Templates::counter),
                new Template("Login Form", Templates::loginForm),
                new Template("Two Forms", Templates::twoForms),
                new Template("Notepad", Templates::notepad),
                new Template("Calculator", Templates::calculator));
    }

    /** A text editor: docked toolbar (StackPanel of buttons), filling TextArea, status bar. */
    private static ProjectModel notepad() {
        ProjectModel p = ProjectModel.withDefaultForm();
        FormModel f = p.effectiveMain();
        f.setTitle("Notepad");
        f.setWidth(640);
        f.setHeight(480);
        f.setResizable(true);

        // docking is sequential in z-order: toolbar and status strip first, the editor fills the rest
        FormComponent toolbar = f.create(ComponentType.PANEL, 0, 0);           // panel1
        toolbar.setHeight(40);
        toolbar.setDock(Dock.TOP);
        FormComponent status = f.create(ComponentType.LABEL, 0, 0);            // label1
        status.setText("Untitled");
        status.setHeight(24);
        status.setDock(Dock.BOTTOM);
        FormComponent editor = f.create(ComponentType.TEXT_AREA, 0, 0);        // textArea1
        editor.setDock(Dock.FILL);

        FormComponent row = f.create(ComponentType.STACK_PANEL, 4, 4, toolbar, ""); // stackPanel1
        row.setOrientation("HORIZONTAL");
        row.setSpacing(6);
        row.setWidth(632);
        row.setHeight(32);
        row.setAnchorRight(true);

        FormComponent newFile = f.create(ComponentType.BUTTON, 0, 0, row, "");  // button1
        newFile.setText("New");
        newFile.setWidth(72);
        newFile.setHeight(30);
        newFile.getEvents().put("onAction",
                "textArea1.setText(\"\");\n"
                + "label1.setText(\"Untitled\");");

        FormComponent open = f.create(ComponentType.BUTTON, 0, 0, row, "");     // button2
        open.setText("Open…");
        open.setWidth(80);
        open.setHeight(30);
        open.getEvents().put("onAction",
                "String path = UI.openFileDialog();\n"
                + "if (path != null) {\n"
                + "    String text = UI.readFile(path);\n"
                + "    if (text != null) {\n"
                + "        textArea1.setText(text);\n"
                + "        label1.setText(\"File: \" + path);\n"
                + "    } else {\n"
                + "        UI.notifyError(\"Could not read \" + path);\n"
                + "    }\n"
                + "}");

        FormComponent save = f.create(ComponentType.BUTTON, 0, 0, row, "");     // button3
        save.setText("Save");
        save.setWidth(72);
        save.setHeight(30);
        save.getEvents().put("onAction",
                "String path = label1.getText().startsWith(\"File: \")\n"
                + "        ? label1.getText().substring(6) : UI.saveFileDialog();\n"
                + "if (path != null) {\n"
                + "    if (UI.writeFile(path, textArea1.getText())) {\n"
                + "        label1.setText(\"File: \" + path);\n"
                + "        UI.notifySuccess(\"Saved \" + path);\n"
                + "    } else {\n"
                + "        UI.notifyError(\"Could not save \" + path);\n"
                + "    }\n"
                + "}");

        FormComponent saveAs = f.create(ComponentType.BUTTON, 0, 0, row, "");   // button4
        saveAs.setText("Save As…");
        saveAs.setWidth(92);
        saveAs.setHeight(30);
        saveAs.getEvents().put("onAction",
                "String path = UI.saveFileDialog();\n"
                + "if (path != null) {\n"
                + "    if (UI.writeFile(path, textArea1.getText())) {\n"
                + "        label1.setText(\"File: \" + path);\n"
                + "        UI.notifySuccess(\"Saved \" + path);\n"
                + "    } else {\n"
                + "        UI.notifyError(\"Could not save \" + path);\n"
                + "    }\n"
                + "}");
        return p;
    }

    /** A pocket calculator: display field plus a 4×5 Grid of keys; UI.eval does the math. */
    private static ProjectModel calculator() {
        ProjectModel p = ProjectModel.withDefaultForm();
        FormModel f = p.effectiveMain();
        f.setTitle("Calculator");
        f.setWidth(260);
        f.setHeight(380);

        FormComponent display = f.create(ComponentType.TEXT_FIELD, 12, 12);    // textField1
        display.setWidth(236);
        display.setHeight(48);
        display.setFontSize(22);
        display.setAlignment("RIGHT");
        display.setAnchorRight(true);

        FormComponent keys = f.create(ComponentType.GRID_PANE, 12, 72);        // gridPane1
        keys.setWidth(236);
        keys.setHeight(296);
        keys.setGridColumns(4);
        keys.setGridRows(5);
        keys.setSpacing(4);
        keys.setAnchorRight(true);
        keys.setAnchorBottom(true);

        String[][] rows = {
                {"C", "⌫", "%", "÷"},
                {"7", "8", "9", "×"},
                {"4", "5", "6", "−"},
                {"1", "2", "3", "+"},
                {"±", "0", ".", "="}};
        for (int r = 0; r < rows.length; r++) {
            for (int col = 0; col < rows[r].length; col++) {
                String text = rows[r][col];
                FormComponent key = f.create(ComponentType.BUTTON, 0, 0, keys, col + "," + r);
                key.setText(text);
                key.setFontSize(16);
                key.getEvents().put("onAction", switch (text) {
                    case "C" -> "textField1.setText(\"\");";
                    case "⌫" -> "String t = textField1.getText();\n"
                            + "if (!t.isEmpty()) {\n"
                            + "    textField1.setText(t.substring(0, t.length() - 1));\n"
                            + "}";
                    case "±" -> "String t = textField1.getText();\n"
                            + "textField1.setText(t.startsWith(\"-\") ? t.substring(1) : \"-\" + t);";
                    case "=" -> "textField1.setText(UI.formatNumber(UI.eval(textField1.getText())));";
                    default -> "textField1.setText(textField1.getText() + \"" + text + "\");";
                });
            }
        }
        return p;
    }

    private static ProjectModel helloWorld() {
        ProjectModel p = ProjectModel.withDefaultForm();
        FormModel f = p.effectiveMain();
        f.setTitle("Hello World");
        f.setWidth(360);
        f.setHeight(220);
        FormComponent ask = f.create(ComponentType.LABEL, 24, 24);
        ask.setText("What's your name?");
        ask.setWidth(200);
        FormComponent name = f.create(ComponentType.TEXT_FIELD, 24, 56);
        name.setWidth(200);
        FormComponent greet = f.create(ComponentType.BUTTON, 24, 100);
        greet.setText("Greet");
        greet.getEvents().put("onAction", "label2.setText(\"Hello, \" + textField1.getText() + \"!\");");
        FormComponent out = f.create(ComponentType.LABEL, 24, 152);
        out.setText("");
        out.setWidth(312);
        out.setFontSize(18);
        return p;
    }

    private static ProjectModel counter() {
        ProjectModel p = ProjectModel.withDefaultForm();
        FormModel f = p.effectiveMain();
        f.setTitle("Counter");
        f.setWidth(280);
        f.setHeight(180);
        FormComponent count = f.create(ComponentType.LABEL, 24, 24);
        count.setText("0");
        count.setWidth(232);
        count.setHeight(56);
        count.setFontSize(32);
        count.setAlignment("CENTER");
        FormComponent minus = f.create(ComponentType.BUTTON, 24, 104);
        minus.setText("−");
        minus.setWidth(108);
        minus.getEvents().put("onAction",
                "label1.setText(String.valueOf(Integer.parseInt(label1.getText()) - 1));");
        FormComponent plus = f.create(ComponentType.BUTTON, 148, 104);
        plus.setText("+");
        plus.setWidth(108);
        plus.getEvents().put("onAction",
                "label1.setText(String.valueOf(Integer.parseInt(label1.getText()) + 1));");
        return p;
    }

    private static ProjectModel loginForm() {
        ProjectModel p = ProjectModel.withDefaultForm();
        FormModel f = p.effectiveMain();
        f.setTitle("Login");
        f.setWidth(320);
        f.setHeight(240);
        FormComponent userLabel = f.create(ComponentType.LABEL, 24, 24);
        userLabel.setText("Username");
        FormComponent user = f.create(ComponentType.TEXT_FIELD, 24, 48);
        user.setWidth(272);
        FormComponent passLabel = f.create(ComponentType.LABEL, 24, 88);
        passLabel.setText("Password");
        FormComponent pass = f.create(ComponentType.TEXT_FIELD, 24, 112);
        pass.setWidth(272);
        FormComponent login = f.create(ComponentType.BUTTON, 24, 156);
        login.setText("Log in");
        login.setWidth(120);
        login.getEvents().put("onAction",
                "if (!textField1.getText().isEmpty() && !textField2.getText().isEmpty()) {\n"
                + "    label3.setText(\"Welcome, \" + textField1.getText() + \"!\");\n"
                + "} else {\n"
                + "    label3.setText(\"Fill in both fields\");\n"
                + "}");
        FormComponent result = f.create(ComponentType.LABEL, 24, 200);
        result.setText("");
        result.setWidth(272);
        result.setTextColor("#067d17");
        return p;
    }

    private static ProjectModel twoForms() {
        ProjectModel p = ProjectModel.withDefaultForm();
        FormModel f = p.effectiveMain();
        f.setTitle("Main Window");
        f.setWidth(320);
        f.setHeight(160);
        FormComponent open = f.create(ComponentType.BUTTON, 24, 24);
        open.setText("Open second form");
        open.setWidth(180);
        open.getEvents().put("onAction", "new Form2().show();");

        FormModel second = p.addForm();
        second.setTitle("Second Window");
        second.setWidth(280);
        second.setHeight(140);
        FormComponent info = second.create(ComponentType.LABEL, 24, 24);
        info.setText("This is Form2");
        info.setFontSize(16);
        return p;
    }
}
