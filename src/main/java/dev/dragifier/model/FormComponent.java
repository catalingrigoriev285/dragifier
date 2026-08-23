package dev.dragifier.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** A single component placed on a form. Plain data object, serialized to JSON as-is. */
public class FormComponent {
    private String id;
    private ComponentType type;
    private double x;
    private double y;
    private double width;
    private double height;
    private String text = "";
    private double fontSize = 13;
    private String textColor = "#212121";
    private String background = "";
    private Map<String, String> events = new LinkedHashMap<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public ComponentType getType() { return type; }
    public void setType(ComponentType type) { this.type = type; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public double getFontSize() { return fontSize; }
    public void setFontSize(double fontSize) { this.fontSize = fontSize; }

    public String getTextColor() { return textColor; }
    public void setTextColor(String textColor) { this.textColor = textColor; }

    public String getBackground() { return background; }
    public void setBackground(String background) { this.background = background; }

    /** Event key → Java handler body. Missing/blank entries mean "no handler". */
    public Map<String, String> getEvents() {
        if (events == null) {
            events = new LinkedHashMap<>();
        }
        return events;
    }
}
