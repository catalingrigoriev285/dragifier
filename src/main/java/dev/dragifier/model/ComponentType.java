package dev.dragifier.model;

/** The kinds of components available in the palette. */
public enum ComponentType {
    BUTTON("Button", "button", 100, 32, "Button"),
    LABEL("Label", "label", 80, 24, "Label"),
    TEXT_FIELD("TextField", "textField", 160, 28, ""),
    TEXT_AREA("TextArea", "textArea", 220, 110, ""),
    CHECK_BOX("CheckBox", "checkBox", 110, 24, "CheckBox"),
    SLIDER("Slider", "slider", 160, 24, ""),
    PANEL("Panel", "panel", 220, 150, "");

    public final String displayName;
    public final String idPrefix;
    public final double defaultWidth;
    public final double defaultHeight;
    public final String defaultText;

    ComponentType(String displayName, String idPrefix,
                  double defaultWidth, double defaultHeight, String defaultText) {
        this.displayName = displayName;
        this.idPrefix = idPrefix;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.defaultText = defaultText;
    }
}
