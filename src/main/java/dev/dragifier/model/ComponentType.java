package dev.dragifier.model;

/** The kinds of components available in the palette. */
public enum ComponentType {
    BUTTON("Button", "button", 100, 32, "Button", Category.BASIC, ContainerKind.NONE),
    LABEL("Label", "label", 80, 24, "Label", Category.BASIC, ContainerKind.NONE),
    TEXT_FIELD("TextField", "textField", 160, 28, "", Category.BASIC, ContainerKind.NONE),
    TEXT_AREA("TextArea", "textArea", 220, 110, "", Category.BASIC, ContainerKind.NONE),
    CHECK_BOX("CheckBox", "checkBox", 110, 24, "CheckBox", Category.BASIC, ContainerKind.NONE),
    SLIDER("Slider", "slider", 160, 24, "", Category.BASIC, ContainerKind.NONE),
    PANEL("Panel", "panel", 220, 150, "", Category.CONTAINER, ContainerKind.ABSOLUTE),
    COMBO_BOX("ComboBox", "comboBox", 160, 28, "", Category.BASIC, ContainerKind.NONE),
    LIST_VIEW("ListView", "listView", 180, 120, "", Category.BASIC, ContainerKind.NONE),
    RADIO_BUTTON("RadioButton", "radioButton", 110, 24, "RadioButton", Category.BASIC, ContainerKind.NONE),
    PROGRESS_BAR("ProgressBar", "progressBar", 160, 20, "", Category.BASIC, ContainerKind.NONE),
    HYPERLINK("Hyperlink", "hyperlink", 100, 24, "Hyperlink", Category.BASIC, ContainerKind.NONE),
    IMAGE_VIEW("Image", "image", 120, 90, "", Category.BASIC, ContainerKind.NONE),
    TIMER("Timer", "timer", 48, 48, "", Category.OTHER, ContainerKind.NONE),
    TABLE_VIEW("Table", "table", 260, 160, "", Category.OTHER, ContainerKind.NONE),
    WEB_VIEW("WebView", "webView", 280, 180, "https://example.com", Category.OTHER, ContainerKind.NONE),
    MEDIA_PLAYER("Media", "media", 240, 160, "", Category.OTHER, ContainerKind.NONE),
    FILE_BROWSER("FileBrowser", "fileBrowser", 220, 200, "", Category.OTHER, ContainerKind.NONE),
    GROUP_BOX("GroupBox", "groupBox", 220, 150, "Group", Category.CONTAINER, ContainerKind.GROUP),
    SCROLL_PANE("ScrollView", "scrollPane", 220, 150, "", Category.CONTAINER, ContainerKind.SCROLL),
    TAB_PANE("TabControl", "tabPane", 280, 180, "", Category.CONTAINER, ContainerKind.TABS),
    SPLIT_PANE("Splitter", "splitPane", 280, 160, "", Category.CONTAINER, ContainerKind.SPLIT),
    STACK_PANEL("StackPanel", "stackPanel", 200, 160, "", Category.CONTAINER, ContainerKind.STACK),
    GRID_PANE("Grid", "gridPane", 240, 160, "", Category.CONTAINER, ContainerKind.GRID),
    DOCK_PANEL("DockPanel", "dockPanel", 280, 200, "", Category.CONTAINER, ContainerKind.DOCK);

    /** Palette grouping. */
    public enum Category { BASIC, CONTAINER, OTHER }

    /**
     * How a container hosts its children. {@code ABSOLUTE/GROUP/SCROLL/TABS/SPLIT}
     * position children at x/y inside one or more AnchorPane content areas;
     * {@code STACK/GRID/DOCK} lay children out themselves (x/y ignored).
     */
    public enum ContainerKind {
        NONE, ABSOLUTE, GROUP, SCROLL, TABS, SPLIT, STACK, GRID, DOCK;

        public boolean isAutoLayout() {
            return this == STACK || this == GRID || this == DOCK;
        }

        /** True when children are placed at x/y inside AnchorPane content areas. */
        public boolean isAbsolute() {
            return this != NONE && !isAutoLayout();
        }

        /** True when the container has several content areas addressed by a slot index. */
        public boolean hasSlots() {
            return this == TABS || this == SPLIT;
        }
    }

    public final String displayName;
    public final String idPrefix;
    public final double defaultWidth;
    public final double defaultHeight;
    public final String defaultText;
    public final Category category;
    public final ContainerKind kind;

    ComponentType(String displayName, String idPrefix,
                  double defaultWidth, double defaultHeight, String defaultText,
                  Category category, ContainerKind kind) {
        this.displayName = displayName;
        this.idPrefix = idPrefix;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.defaultText = defaultText;
        this.category = category;
        this.kind = kind;
    }

    public boolean isContainer() {
        return kind != ContainerKind.NONE;
    }
}
