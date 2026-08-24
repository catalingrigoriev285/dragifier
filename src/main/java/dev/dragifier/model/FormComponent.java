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
    /** Design-time only: locked components can't be moved/resized on the canvas. */
    private boolean locked = false;
    /** Text alignment: "LEFT", "CENTER", "RIGHT", or "" for the type's default. */
    private String alignment = "";
    /** Edge anchoring for resizable forms; left+top are the classic defaults. */
    private boolean anchorLeft = true;
    private boolean anchorTop = true;
    private boolean anchorRight = false;
    private boolean anchorBottom = false;
    /** Newline-separated column names, for Table components. */
    private String columns = "";
    /** Base64 media bytes + original extension, for Media components. */
    private String mediaData = "";
    private String mediaFormat = "";
    /** Id of the containing component, or null when placed directly on the form. */
    private String parentId;
    /**
     * Placement inside the parent container: tab/pane index ("0"), grid cell
     * ("col,row") or dock-panel region ("TOP"); empty for single-area containers.
     */
    private String slot = "";
    /** Edge docking inside the parent's content area (absolute containers only). */
    private Dock dock = Dock.NONE;
    /** "VERTICAL" or "HORIZONTAL", for Splitter and StackPanel. */
    private String orientation = "";
    /** Gap between children, for StackPanel (spacing) and Grid (hgap/vgap). */
    private double spacing = 0;
    /** Grid dimensions (0 = default 2x2). */
    private int gridColumns = 0;
    private int gridRows = 0;
    /** Number of panes of a Splitter (0 = default 2). */
    private int panes = 0;
    /** Comma-separated Splitter divider positions in 0..1. */
    private String dividers = "";
    /** Font family name; empty = the theme default. */
    private String fontFamily = "";
    private boolean bold = false;
    private boolean italic = false;
    /** Custom border; empty color = the type's default look. Width is a CSS insets string (empty = 1). */
    private String borderColor = "";
    private String borderWidth = "";
    private double borderRadius = 0;
    /** CSS insets strings ("8" or "4 8 4 8"); margin only matters inside auto-layout containers. */
    private String padding = "";
    private String margin = "";
    private boolean visible = true;
    /** DEFAULT, HAND, TEXT, CROSSHAIR, MOVE, WAIT, NONE or empty for the default. */
    private String cursor = "";
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

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public String getAlignment() { return alignment == null ? "" : alignment; }
    public void setAlignment(String alignment) { this.alignment = alignment; }

    public boolean isAnchorLeft() { return anchorLeft; }
    public void setAnchorLeft(boolean anchorLeft) { this.anchorLeft = anchorLeft; }

    public boolean isAnchorTop() { return anchorTop; }
    public void setAnchorTop(boolean anchorTop) { this.anchorTop = anchorTop; }

    public boolean isAnchorRight() { return anchorRight; }
    public void setAnchorRight(boolean anchorRight) { this.anchorRight = anchorRight; }

    public boolean isAnchorBottom() { return anchorBottom; }
    public void setAnchorBottom(boolean anchorBottom) { this.anchorBottom = anchorBottom; }

    public String getColumns() { return columns == null ? "" : columns; }
    public void setColumns(String columns) { this.columns = columns; }

    public String getMediaData() { return mediaData == null ? "" : mediaData; }
    public void setMediaData(String mediaData) { this.mediaData = mediaData; }

    public String getMediaFormat() { return mediaFormat == null ? "" : mediaFormat; }
    public void setMediaFormat(String mediaFormat) { this.mediaFormat = mediaFormat; }

    public String getParentId() { return parentId == null || parentId.isEmpty() ? null : parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getSlot() { return slot == null ? "" : slot; }
    public void setSlot(String slot) { this.slot = slot == null ? "" : slot; }

    public Dock getDock() { return dock == null ? Dock.NONE : dock; }
    public void setDock(Dock dock) { this.dock = dock; }

    public String getOrientation() { return orientation == null ? "" : orientation; }
    public void setOrientation(String orientation) { this.orientation = orientation; }

    public double getSpacing() { return spacing; }
    public void setSpacing(double spacing) { this.spacing = spacing; }

    public int getGridColumns() { return gridColumns; }
    public void setGridColumns(int gridColumns) { this.gridColumns = gridColumns; }

    public int getGridRows() { return gridRows; }
    public void setGridRows(int gridRows) { this.gridRows = gridRows; }

    public int getPanes() { return panes; }
    public void setPanes(int panes) { this.panes = panes; }

    public String getDividers() { return dividers == null ? "" : dividers; }
    public void setDividers(String dividers) { this.dividers = dividers; }

    public String getFontFamily() { return fontFamily == null ? "" : fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

    public boolean isBold() { return bold; }
    public void setBold(boolean bold) { this.bold = bold; }

    public boolean isItalic() { return italic; }
    public void setItalic(boolean italic) { this.italic = italic; }

    public String getBorderColor() { return borderColor == null ? "" : borderColor; }
    public void setBorderColor(String borderColor) { this.borderColor = borderColor; }

    public String getBorderWidth() { return borderWidth == null ? "" : borderWidth; }
    public void setBorderWidth(String borderWidth) { this.borderWidth = borderWidth; }

    public double getBorderRadius() { return borderRadius; }
    public void setBorderRadius(double borderRadius) { this.borderRadius = borderRadius; }

    public String getPadding() { return padding == null ? "" : padding; }
    public void setPadding(String padding) { this.padding = padding; }

    public String getMargin() { return margin == null ? "" : margin; }
    public void setMargin(String margin) { this.margin = margin; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public String getCursor() { return cursor == null ? "" : cursor; }
    public void setCursor(String cursor) { this.cursor = cursor; }

    /** Event key → Java handler body. Missing/blank entries mean "no handler". */
    public Map<String, String> getEvents() {
        if (events == null) {
            events = new LinkedHashMap<>();
        }
        return events;
    }
}
