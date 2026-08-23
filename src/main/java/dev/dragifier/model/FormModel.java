package dev.dragifier.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The whole form being designed: window properties plus placed components. */
public class FormModel {
    /** Unique form name; becomes the generated Java class name. */
    private String name = "Form1";
    private String title = "My App";
    private double width = 640;
    private double height = 480;
    // not final: Gson assigns it reflectively on load, which the JDK blocks for final fields
    private List<FormComponent> components = new ArrayList<>();
    private Map<String, String> events = new LinkedHashMap<>();

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

    /** Adds a copy of {@code src} with a fresh id, offset slightly and kept inside the form. */
    public FormComponent duplicate(FormComponent src) {
        double nx = Math.min(src.getX() + 16, Math.max(0, width - src.getWidth()));
        double ny = Math.min(src.getY() + 16, Math.max(0, height - src.getHeight()));
        FormComponent c = create(src.getType(), nx, ny);
        c.setWidth(src.getWidth());
        c.setHeight(src.getHeight());
        c.setText(src.getText());
        c.setFontSize(src.getFontSize());
        c.setTextColor(src.getTextColor());
        c.setBackground(src.getBackground());
        c.setItems(src.getItems());
        c.setValue(src.getValue());
        c.setImageData(src.getImageData());
        c.setTooltip(src.getTooltip());
        c.setDisabled(src.isDisabled());
        c.setAlignment(src.getAlignment());
        c.getEvents().putAll(src.getEvents());
        return c;
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

    public String getName() { return name == null || name.isBlank() ? "Form1" : name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public List<FormComponent> getComponents() { return components; }

    /** Form-level event key → Java handler body (e.g. "onShown"). */
    public Map<String, String> getEvents() {
        if (events == null) {
            events = new LinkedHashMap<>();
        }
        return events;
    }
}
