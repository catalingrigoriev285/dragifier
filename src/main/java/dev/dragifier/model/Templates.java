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
                new Template("Two Forms", Templates::twoForms));
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
