package dev.dragifier.model;

import java.util.ArrayList;
import java.util.List;

/** The whole form being designed: window properties plus placed components. */
public class FormModel {
    private String title = "My App";
    private double width = 640;
    private double height = 480;
    private final List<FormComponent> components = new ArrayList<>();

    public FormComponent create(ComponentType type, double x, double y) {
        FormComponent c = new FormComponent();
        c.setId(nextId(type));
        c.setType(type);
        c.setX(x);
        c.setY(y);
        c.setWidth(type.defaultWidth);
        c.setHeight(type.defaultHeight);
        c.setText(type.defaultText);
        components.add(c);
        return c;
    }

    public void remove(FormComponent c) {
        components.remove(c);
    }

    private String nextId(ComponentType type) {
        int n = 1;
        while (hasId(type.idPrefix + n)) {
            n++;
        }
        return type.idPrefix + n;
    }

    private boolean hasId(String id) {
        return components.stream().anyMatch(c -> id.equals(c.getId()));
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public List<FormComponent> getComponents() { return components; }
}
