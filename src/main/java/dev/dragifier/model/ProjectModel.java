package dev.dragifier.model;

import java.util.ArrayList;
import java.util.List;

/** A Dragifier project: one or more forms, one of which is the main (startup) form. */
public class ProjectModel {

    private List<FormModel> forms = new ArrayList<>();
    private String mainForm = "";

    public static ProjectModel withDefaultForm() {
        ProjectModel p = new ProjectModel();
        FormModel form = new FormModel();
        form.setName("Form1");
        p.forms.add(form);
        p.mainForm = "Form1";
        return p;
    }

    /** Wraps a legacy single-form project file. */
    public static ProjectModel wrapping(FormModel form) {
        ProjectModel p = new ProjectModel();
        if (form.getName().isBlank()) {
            form.setName("Form1");
        }
        p.forms.add(form);
        p.mainForm = form.getName();
        return p;
    }

    public List<FormModel> getForms() {
        if (forms == null) {
            forms = new ArrayList<>();
        }
        return forms;
    }

    public FormModel findForm(String name) {
        return getForms().stream().filter(f -> f.getName().equals(name)).findFirst().orElse(null);
    }

    public boolean nameInUse(String name, FormModel except) {
        return getForms().stream().anyMatch(f -> f != except && f.getName().equals(name));
    }

    public FormModel addForm() {
        int n = 1;
        while (nameInUse("Form" + n, null)) {
            n++;
        }
        FormModel form = new FormModel();
        form.setName("Form" + n);
        form.setTitle("Form " + n);
        getForms().add(form);
        return form;
    }

    /** Removes a form; refuses to remove the last one. */
    public boolean removeForm(FormModel form) {
        if (getForms().size() <= 1) {
            return false;
        }
        getForms().remove(form);
        if (form.getName().equals(mainForm)) {
            mainForm = getForms().get(0).getName();
        }
        return true;
    }

    public String getMainForm() { return mainForm; }
    public void setMainForm(String mainForm) { this.mainForm = mainForm; }

    /** The startup form: the named main form, or the first form as a fallback. */
    public FormModel effectiveMain() {
        FormModel m = findForm(mainForm);
        return m != null ? m : getForms().get(0);
    }
}
