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
    /** Newline-separated list entries, for ComboBox/ListView. */
    private String items = "";
    /** Percent 0–100, for ProgressBar. */
    private double value = 0;
    /** Base64-encoded image bytes, for Image components. Kept in the project file. */
    private String imageData = "";
    private String tooltip = "";
    private boolean disabled = false;
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

    public String getItems() { return items == null ? "" : items; }
    public void setItems(String items) { this.items = items; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public String getImageData() { return imageData == null ? "" : imageData; }
    public void setImageData(String imageData) { this.imageData = imageData; }

    public String getTooltip() { return tooltip == null ? "" : tooltip; }
    public void setTooltip(String tooltip) { this.tooltip = tooltip; }

    public boolean isDisabled() { return disabled; }
    public void setDisabled(boolean disabled) { this.disabled = disabled; }

    /** Event key → Java handler body. Missing/blank entries mean "no handler". */
    public Map<String, String> getEvents() {
        if (events == null) {
            events = new LinkedHashMap<>();
        }
        return events;
    }
}
