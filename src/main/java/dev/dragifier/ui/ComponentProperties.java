package dev.dragifier.ui;

import dev.dragifier.model.ComponentType;
import dev.dragifier.model.ContainerGeometry;
import dev.dragifier.model.CssInsets;
import dev.dragifier.model.Dock;
import dev.dragifier.model.EventSpec;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import dev.dragifier.ui.PropertySpec.Context;
import dev.dragifier.ui.PropertySpec.Editor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Predicate;

/**
 * The property registry: which rows the inspector shows for a component (or
 * the form), grouped into categories in display order. Every model property
 * lives here once; the old per-type if-chain is replaced by {@code applies}
 * predicates.
 */
public final class ComponentProperties {

    public static final String GENERAL = "General";
    public static final String SIZE = "Size";
    public static final String POSITION = "Position";
    public static final String FONT = "Font";
    public static final String BACKGROUND = "Background";
    public static final String BORDER = "Border";
    public static final String PADDING = "Padding";
    public static final String MARGIN = "Margin";
    public static final String BEHAVIOR = "Behavior";
    public static final String EVENTS = "Events";

    private static final List<String> ALIGN_CHOICES = List.of("Default", "Left", "Center", "Right");
    private static final List<String> ORIENTATIONS = List.of("Horizontal", "Vertical");
    private static final List<String> CURSOR_CHOICES = List.of("Default", "Hand", "Text", "Crosshair", "Move", "Wait", "None");

    private ComponentProperties() {}

    /** Rows for a component, in category order. */
    public static List<PropertySpec> forComponent(FormModel model, FormComponent c) {
        List<PropertySpec> specs = new ArrayList<>();
        ComponentType type = c.getType();
        FormComponent parent = model == null ? null : model.parentOf(c);
        ComponentType.ContainerKind parentKind = parent == null ? ComponentType.ContainerKind.NONE : parent.getType().kind;
        boolean autoLaid = parentKind.isAutoLayout();
        Predicate<FormComponent> docked = x -> !autoLaid && x.getDock() != Dock.NONE;
        Predicate<FormComponent> notDocked = x -> !docked.test(x);
        Predicate<FormComponent> positioned = x -> !autoLaid;

        // ---- General
        specs.add(PropertySpec.of("id", "Name", GENERAL).custom(ctx -> idEditor(ctx)).build());
        String textLabel = switch (type) {
            case GROUP_BOX -> "Title";
            case WEB_VIEW -> "URL";
            case FILE_BROWSER -> "Root folder";
            case COMBO_BOX -> "Prompt";
            default -> "Text";
        };
        specs.add(PropertySpec.of("text", textLabel, GENERAL)
                .applies(x -> type != ComponentType.TIMER)
                .get(c::getText).set((x, v) -> x.setText((String) v)).build());
        specs.add(PropertySpec.of("tooltip", "Tooltip", GENERAL)
                .applies(x -> type != ComponentType.TIMER)
                .get(c::getTooltip).set((x, v) -> x.setTooltip((String) v)).build());
        specs.add(PropertySpec.of("parent", "Parent", GENERAL).editor(Editor.READONLY)
                .get(() -> parent == null ? "Form" : parent.getId() + " (" + parent.getType().displayName + ")").build());

        // ---- Size
        specs.add(PropertySpec.of("width", "Width", SIZE).editor(Editor.NUMBER)
                .enabled(x -> !docked.test(x) || x.getDock() == Dock.LEFT || x.getDock() == Dock.RIGHT)
                .get(c::getWidth).set((x, v) -> x.setWidth(Math.max(16, (Double) v))).build());
        specs.add(PropertySpec.of("height", "Height", SIZE).editor(Editor.NUMBER)
                .enabled(x -> !docked.test(x) || x.getDock() == Dock.TOP || x.getDock() == Dock.BOTTOM)
                .get(c::getHeight).set((x, v) -> x.setHeight(Math.max(16, (Double) v))).build());

        // ---- Position
        specs.add(PropertySpec.of("x", "X", POSITION).editor(Editor.NUMBER).applies(positioned).enabled(notDocked)
                .get(c::getX).set((x, v) -> x.setX((Double) v)).build());
        specs.add(PropertySpec.of("y", "Y", POSITION).editor(Editor.NUMBER).applies(positioned).enabled(notDocked)
                .get(c::getY).set((x, v) -> x.setY((Double) v)).build());
        specs.add(PropertySpec.of("anchors", "Anchors", POSITION).applies(positioned).enabled(notDocked)
                .custom(ctx -> anchorsEditor(ctx)).build());
        specs.add(PropertySpec.of("dock", "Dock", POSITION).editor(Editor.CHOICE).applies(positioned)
                .choices(Arrays.stream(Dock.values()).map(Enum::name).toList())
                .get(() -> c.getDock().name()).set((x, v) -> x.setDock(Dock.valueOf((String) v))).rebuild().build());
        if (parentKind.hasSlots()) {
            specs.add(PropertySpec.of("slot", parent.getType() == ComponentType.TAB_PANE ? "Tab #" : "Pane #", POSITION)
                    .editor(Editor.INTEGER)
                    .get(() -> ContainerGeometry.slotIndex(c, parent) + 1)
                    .set((x, v) -> x.setSlot(String.valueOf(clamp((Integer) v - 1, 0, ContainerGeometry.slotCount(parent) - 1))))
                    .rebuild().build());
        }
        if (parentKind == ComponentType.ContainerKind.GRID) {
            specs.add(PropertySpec.of("gridCol", "Column", POSITION).editor(Editor.INTEGER)
                    .get(() -> ContainerGeometry.gridCell(c, parent)[0] + 1)
                    .set((x, v) -> {
                        int[] cell = ContainerGeometry.gridCell(x, parent);
                        x.setSlot(clamp((Integer) v - 1, 0, ContainerGeometry.gridColumns(parent) - 1) + "," + cell[1]);
                    }).rebuild().build());
            specs.add(PropertySpec.of("gridRow", "Row", POSITION).editor(Editor.INTEGER)
                    .get(() -> ContainerGeometry.gridCell(c, parent)[1] + 1)
                    .set((x, v) -> {
                        int[] cell = ContainerGeometry.gridCell(x, parent);
                        x.setSlot(cell[0] + "," + clamp((Integer) v - 1, 0, ContainerGeometry.gridRows(parent) - 1));
                    }).rebuild().build());
        }
        if (parentKind == ComponentType.ContainerKind.DOCK) {
            specs.add(PropertySpec.of("region", "Region", POSITION).editor(Editor.CHOICE)
                    .choices(ContainerGeometry.DOCK_REGIONS)
                    .get(() -> ContainerGeometry.dockRegion(c)).set((x, v) -> x.setSlot((String) v)).rebuild().build());
        }

        // ---- Font
        boolean hasText = type != ComponentType.TIMER;
        specs.add(PropertySpec.of("fontFamily", "Family", FONT).editor(Editor.FONT_FAMILY).applies(x -> hasText)
                .get(c::getFontFamily).set((x, v) -> x.setFontFamily((String) v)).build());
        specs.add(PropertySpec.of("fontSize", "Size", FONT).editor(Editor.NUMBER).applies(x -> hasText)
                .get(c::getFontSize).set((x, v) -> x.setFontSize(Math.max(6, (Double) v))).build());
        specs.add(PropertySpec.of("bold", "Bold", FONT).editor(Editor.CHECK).applies(x -> hasText)
                .get(c::isBold).set((x, v) -> x.setBold((Boolean) v)).build());
        specs.add(PropertySpec.of("italic", "Italic", FONT).editor(Editor.CHECK).applies(x -> hasText)
                .get(c::isItalic).set((x, v) -> x.setItalic((Boolean) v)).build());
        specs.add(PropertySpec.of("textColor", "Color", FONT).editor(Editor.COLOR).applies(x -> hasText)
                .get(c::getTextColor).set((x, v) -> x.setTextColor((String) v)).build());
        specs.add(PropertySpec.of("alignment", "Align", FONT).editor(Editor.CHOICE).choices(ALIGN_CHOICES)
                .applies(x -> Renderer.supportsAlignment(type))
                .get(() -> switch (c.getAlignment()) {
                    case "LEFT" -> "Left";
                    case "CENTER" -> "Center";
                    case "RIGHT" -> "Right";
                    default -> "Default";
                })
                .set((x, v) -> x.setAlignment(switch ((String) v) {
                    case "Left" -> "LEFT";
                    case "Center" -> "CENTER";
                    case "Right" -> "RIGHT";
                    default -> "";
                })).build());

        // ---- Background / Border / Padding / Margin
        specs.add(PropertySpec.of("background", "Color", BACKGROUND).editor(Editor.COLOR)
                .get(c::getBackground).set((x, v) -> x.setBackground((String) v)).build());
        specs.add(PropertySpec.of("borderColor", "Color", BORDER).editor(Editor.COLOR)
                .get(c::getBorderColor).set((x, v) -> x.setBorderColor((String) v)).rebuild().build());
        specs.add(PropertySpec.of("borderWidth", "Width", BORDER).editor(Editor.INSETS).prompt("1  or  1 0 1 0")
                .enabled(x -> !x.getBorderColor().isEmpty())
                .get(c::getBorderWidth).set((x, v) -> x.setBorderWidth((String) v)).build());
        specs.add(PropertySpec.of("borderRadius", "Radius", BORDER).editor(Editor.NUMBER)
                .enabled(x -> !x.getBorderColor().isEmpty())
                .get(c::getBorderRadius).set((x, v) -> x.setBorderRadius(Math.max(0, (Double) v))).build());
        specs.add(PropertySpec.of("padding", "Padding", PADDING).editor(Editor.INSETS).prompt("8  or  4 8 4 8")
                .applies(x -> type != ComponentType.TIMER)
                .get(c::getPadding).set((x, v) -> x.setPadding((String) v)).build());
        specs.add(PropertySpec.of("margin", "Margin", MARGIN).editor(Editor.INSETS).prompt("8  or  4 8 4 8")
                .applies(x -> autoLaid)
                .get(c::getMargin).set((x, v) -> x.setMargin((String) v)).build());

        // ---- Behavior
        specs.add(PropertySpec.of("enabled", "Enabled", BEHAVIOR).editor(Editor.CHECK)
                .get(() -> !c.isDisabled()).set((x, v) -> x.setDisabled(!(Boolean) v)).build());
        specs.add(PropertySpec.of("visible", "Visible", BEHAVIOR).editor(Editor.CHECK).applies(x -> type != ComponentType.TIMER)
                .get(c::isVisible).set((x, v) -> x.setVisible((Boolean) v)).build());
        specs.add(PropertySpec.of("cursor", "Cursor", BEHAVIOR).editor(Editor.CHOICE).choices(CURSOR_CHOICES)
                .applies(x -> type != ComponentType.TIMER)
                .get(() -> cursorLabel(c.getCursor()))
                .set((x, v) -> x.setCursor("Default".equals(v) ? "" : ((String) v).toUpperCase())).build());
        specs.add(PropertySpec.of("locked", "Locked", BEHAVIOR).editor(Editor.CHECK)
                .get(c::isLocked).set((x, v) -> x.setLocked((Boolean) v)).build());

        // ---- Type-specific (category named after the type)
        String typeCat = type.displayName;
        switch (type) {
            case COMBO_BOX, LIST_VIEW -> specs.add(multiline("items", "Items", typeCat, "One item per line", c));
            case TAB_PANE -> specs.add(multiline("items", "Tabs", typeCat, "One tab title per line", c));
            case FILE_BROWSER -> {
                specs.add(PropertySpec.of("folder", "Folder", typeCat).custom(ctx -> folderEditor(ctx)).build());
                specs.add(multiline("items", "Filters", typeCat, "One extension per line, e.g. txt (empty = all files)", c));
            }
            case TABLE_VIEW -> specs.add(PropertySpec.of("columns", "Columns", typeCat).editor(Editor.MULTILINE)
                    .prompt("One column per line").get(c::getColumns).set((x, v) -> x.setColumns((String) v)).build());
            case PROGRESS_BAR -> specs.add(PropertySpec.of("value", "Value %", typeCat).editor(Editor.NUMBER)
                    .get(c::getValue).set((x, v) -> x.setValue(Math.max(0, Math.min(100, (Double) v)))).build());
            case TIMER -> specs.add(PropertySpec.of("value", "Interval ms", typeCat).editor(Editor.NUMBER)
                    .get(c::getValue).set((x, v) -> x.setValue(Math.max(16, (Double) v))).build());
            case IMAGE_VIEW -> specs.add(PropertySpec.of("image", "Image", typeCat).custom(ctx -> imageEditor(ctx)).build());
            case MEDIA_PLAYER -> specs.add(PropertySpec.of("media", "Media", typeCat).custom(ctx -> mediaEditor(ctx)).build());
            case SPLIT_PANE -> {
                specs.add(orientation(typeCat, c));
                specs.add(PropertySpec.of("panes", "Panes", typeCat).editor(Editor.INTEGER)
                        .get(() -> ContainerGeometry.paneCount(c))
                        .set((x, v) -> x.setPanes(clamp((Integer) v, 2, 8))).build());
                specs.add(PropertySpec.of("dividers", "Dividers", typeCat).prompt("e.g. 0.3, 0.7")
                        .get(c::getDividers).set((x, v) -> x.setDividers((String) v)).build());
            }
            case STACK_PANEL -> {
                specs.add(orientation(typeCat, c));
                specs.add(spacing(typeCat, c));
            }
            case GRID_PANE -> {
                specs.add(spacing(typeCat, c));
                specs.add(PropertySpec.of("gridColumns", "Columns", typeCat).editor(Editor.INTEGER)
                        .get(() -> ContainerGeometry.gridColumns(c))
                        .set((x, v) -> x.setGridColumns(clamp((Integer) v, 1, 12))).build());
                specs.add(PropertySpec.of("gridRows", "Rows", typeCat).editor(Editor.INTEGER)
                        .get(() -> ContainerGeometry.gridRows(c))
                        .set((x, v) -> x.setGridRows(clamp((Integer) v, 1, 12))).build());
            }
            default -> { }
        }

        // ---- Events
        for (EventSpec event : EventSpec.forType(type)) {
            specs.add(eventRow(event, () -> hasCode(c.getEvents().get(event.key()))));
        }
        return specs;
    }

    /** Rows for the form itself (nothing selected). */
    public static List<PropertySpec> forForm(FormModel model, Predicate<String> nameInUse) {
        List<PropertySpec> specs = new ArrayList<>();
        specs.add(PropertySpec.of("name", "Name", GENERAL)
                .get(model::getName)
                .set((x, v) -> {
                    StringBuilder sb = new StringBuilder();
                    for (char ch : ((String) v).trim().toCharArray()) {
                        if (Character.isLetterOrDigit(ch)) {
                            sb.append(ch);
                        }
                    }
                    String sanitized = sb.toString();
                    if (!sanitized.isEmpty() && !Character.isDigit(sanitized.charAt(0))
                            && !sanitized.equals(model.getName()) && !nameInUse.test(sanitized)) {
                        model.setName(sanitized);
                    }
                }).build());
        specs.add(PropertySpec.of("title", "Title", GENERAL)
                .get(model::getTitle).set((x, v) -> model.setTitle((String) v)).build());
        specs.add(PropertySpec.of("width", "Width", SIZE).editor(Editor.NUMBER)
                .get(model::getWidth).set((x, v) -> model.setWidth(Math.max(100, (Double) v))).build());
        specs.add(PropertySpec.of("height", "Height", SIZE).editor(Editor.NUMBER)
                .get(model::getHeight).set((x, v) -> model.setHeight(Math.max(100, (Double) v))).build());
        specs.add(PropertySpec.of("resizable", "Resizable window", "Window").editor(Editor.CHECK)
                .get(model::isResizable).set((x, v) -> model.setResizable((Boolean) v)).build());
        for (EventSpec event : EventSpec.forForm()) {
            specs.add(eventRow(event, () -> hasCode(model.getEvents().get(event.key()))));
        }
        return specs;
    }

    // ------------------------------------------------------------- helpers

    private static PropertySpec eventRow(EventSpec event, java.util.function.BooleanSupplier hasCode) {
        return PropertySpec.of(event.key(), event.displayName(), EVENTS).editor(Editor.EVENT)
                .get(hasCode::getAsBoolean).build();
    }

    private static boolean hasCode(String code) {
        return code != null && !code.isBlank();
    }

    private static PropertySpec multiline(String key, String label, String category, String prompt, FormComponent c) {
        return PropertySpec.of(key, label, category).editor(Editor.MULTILINE).prompt(prompt)
                .get(c::getItems).set((x, v) -> x.setItems((String) v)).build();
    }

    private static PropertySpec orientation(String category, FormComponent c) {
        return PropertySpec.of("orientation", "Orientation", category).editor(Editor.CHOICE).choices(ORIENTATIONS)
                .get(() -> "VERTICAL".equals(c.getOrientation()) ? "Vertical" : "Horizontal")
                .set((x, v) -> x.setOrientation("Vertical".equals(v) ? "VERTICAL" : "HORIZONTAL")).build();
    }

    private static PropertySpec spacing(String category, FormComponent c) {
        return PropertySpec.of("spacing", "Spacing", category).editor(Editor.NUMBER)
                .get(c::getSpacing).set((x, v) -> x.setSpacing(Math.max(0, (Double) v))).build();
    }

    private static String cursorLabel(String cursor) {
        for (String choice : CURSOR_CHOICES) {
            if (choice.equalsIgnoreCase(cursor)) {
                return choice;
            }
        }
        return "Default";
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** True when the CSS-insets text can be stored (empty clears it). */
    public static boolean validInsets(String text) {
        return CssInsets.isValid(text);
    }

    // ------------------------------------------------------ custom editors

    private static Node idEditor(Context ctx) {
        FormComponent c = ctx.component();
        TextField field = new TextField(c.getId());
        field.setPrefWidth(110);
        Runnable commit = () -> {
            String raw = field.getText().trim();
            if (raw.equals(c.getId())) {
                return;
            }
            if (!ctx.rename(c, raw)) {
                field.setText(c.getId());
            }
        };
        field.setOnAction(e -> commit.run());
        field.focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                commit.run();
            }
        });
        field.setId("property-id-field");
        return field;
    }

    private static Node anchorsEditor(Context ctx) {
        FormComponent c = ctx.component();
        CheckBox l = new CheckBox("L");
        CheckBox t = new CheckBox("T");
        CheckBox r = new CheckBox("R");
        CheckBox b = new CheckBox("B");
        l.setSelected(c.isAnchorLeft());
        t.setSelected(c.isAnchorTop());
        r.setSelected(c.isAnchorRight());
        b.setSelected(c.isAnchorBottom());
        l.setOnAction(e -> ctx.apply(() -> c.setAnchorLeft(l.isSelected())));
        t.setOnAction(e -> ctx.apply(() -> c.setAnchorTop(t.isSelected())));
        r.setOnAction(e -> ctx.apply(() -> c.setAnchorRight(r.isSelected())));
        b.setOnAction(e -> ctx.apply(() -> c.setAnchorBottom(b.isSelected())));
        return new HBox(8, l, t, r, b);
    }

    private static Node imageEditor(Context ctx) {
        FormComponent c = ctx.component();
        Button choose = new Button("Choose…");
        Button clear = new Button("Clear");
        choose.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose Image");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Images (*.png, *.jpg, *.gif, *.bmp)", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
            File file = chooser.showOpenDialog(ctx.window());
            if (file == null) {
                return;
            }
            try {
                String encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));
                ctx.apply(() -> c.setImageData(encoded));
            } catch (IOException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not read image: " + ex.getMessage()).showAndWait();
            }
        });
        clear.setOnAction(e -> ctx.apply(() -> c.setImageData("")));
        return new HBox(6, choose, clear);
    }

    private static Node mediaEditor(Context ctx) {
        FormComponent c = ctx.component();
        Button choose = new Button("Choose…");
        Button clear = new Button("Clear");
        choose.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose Media File");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Media (*.mp3, *.mp4, *.wav, *.m4a, *.aiff)", "*.mp3", "*.mp4", "*.wav", "*.m4a", "*.aiff"));
            File file = chooser.showOpenDialog(ctx.window());
            if (file == null) {
                return;
            }
            if (file.length() > 10L * 1024 * 1024) {
                new Alert(Alert.AlertType.ERROR,
                        "Media files are limited to 10 MB (they are stored inside the project file).").showAndWait();
                return;
            }
            try {
                String encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));
                String name = file.getName().toLowerCase();
                String format = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
                ctx.apply(() -> {
                    c.setMediaData(encoded);
                    c.setMediaFormat(format);
                });
            } catch (IOException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not read media file: " + ex.getMessage()).showAndWait();
            }
        });
        clear.setOnAction(e -> ctx.apply(() -> {
            c.setMediaData("");
            c.setMediaFormat("");
        }));
        return new HBox(6, choose, clear);
    }

    private static Node folderEditor(Context ctx) {
        FormComponent c = ctx.component();
        Button choose = new Button("Choose…");
        Button home = new Button("Home");
        choose.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Choose Root Folder");
            File dir = chooser.showDialog(ctx.window());
            if (dir != null) {
                String path = dir.getAbsolutePath();
                ctx.apply(() -> c.setText(path));
            }
        });
        home.setOnAction(e -> ctx.apply(() -> c.setText("")));
        return new HBox(6, choose, home);
    }
}
