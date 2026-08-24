package dev.dragifier.ui;

import dev.dragifier.model.ContainerGeometry;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import javafx.geometry.Insets;
import dev.dragifier.model.ComponentType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.function.Consumer;

/**
 * Properties inspector. Shows the selected component's properties, or the
 * form's own properties when nothing is selected. Edits write through to the model.
 */
public class InspectorPane extends VBox {

    private FormModel model;
    private FormComponent current;
    private boolean updating;

    private Consumer<FormComponent> onComponentEdited = c -> {};
    private Runnable onFormEdited = () -> {};
    private Runnable checkpoint = () -> {};

    private final Label header = new Label();
    private final TextField searchField = new TextField();
    private final java.util.List<javafx.scene.Node[]> searchRows = new java.util.ArrayList<>();

    private final GridPane layoutGrid = new GridPane();
    private final TextField idField = new TextField();
    private java.util.function.BiPredicate<FormComponent, String> renamer = (c, id) -> false;
    private final GridPane appearanceGrid = new GridPane();
    private final GridPane behaviorGrid = new GridPane();
    private final javafx.scene.control.TitledPane layoutSection = new javafx.scene.control.TitledPane("Layout", layoutGrid);
    private final javafx.scene.control.TitledPane appearanceSection = new javafx.scene.control.TitledPane("Appearance", appearanceGrid);
    private final javafx.scene.control.TitledPane behaviorSection = new javafx.scene.control.TitledPane("Behavior", behaviorGrid);
    private final TextField xField = new TextField();
    private final TextField yField = new TextField();
    private final TextField wField = new TextField();
    private final TextField hField = new TextField();
    private final TextField textField = new TextField();
    private Label textLabel;
    private final javafx.scene.layout.HBox folderButtons = new javafx.scene.layout.HBox(6);
    private Label folderLabel;
    private final TextField fontField = new TextField();
    private final ColorPicker textColorPicker = new ColorPicker();
    private final CheckBox customBg = new CheckBox("Custom background");
    private final ColorPicker bgPicker = new ColorPicker(Color.WHITE);
    private final TextArea itemsArea = new TextArea();
    private Label itemsLabel;
    private final TextArea columnsArea = new TextArea();
    private Label columnsLabel;
    private final javafx.scene.layout.HBox mediaButtons = new javafx.scene.layout.HBox(6);
    private Label mediaLabel;
    private final TextField valueField = new TextField();
    private Label valueLabel;
    private final javafx.scene.layout.HBox imageButtons = new javafx.scene.layout.HBox(6);
    private Label imageLabel;
    private final TextField tooltipField = new TextField();
    private final CheckBox disabledBox = new CheckBox("Disabled");
    private final CheckBox lockedBox = new CheckBox("Locked");
    private final javafx.scene.control.ComboBox<String> alignBox = new javafx.scene.control.ComboBox<>();
    private Label alignLabel;

    private final CheckBox anchorL = new CheckBox("L");
    private final CheckBox anchorT = new CheckBox("T");
    private final CheckBox anchorR = new CheckBox("R");
    private final CheckBox anchorB = new CheckBox("B");

    // nesting: parent container and placement inside it
    private final Label parentValue = new Label("Form");
    private final TextField slotField = new TextField();
    private Label slotLabel;
    // container properties
    private final javafx.scene.control.ComboBox<String> orientationBox = new javafx.scene.control.ComboBox<>();
    private Label orientationLabel;
    private final TextField panesField = new TextField();
    private Label panesLabel;
    private final TextField dividersField = new TextField();
    private Label dividersLabel;
    private final TextField spacingField = new TextField();
    private Label spacingLabel;
    private final TextField colsField = new TextField();
    private Label colsLabel;
    private final TextField rowsField = new TextField();
    private Label rowsLabel;
    // placement inside auto-layout parents
    private final TextField cellColField = new TextField();
    private Label cellColLabel;
    private final TextField cellRowField = new TextField();
    private Label cellRowLabel;
    private final javafx.scene.control.ComboBox<String> regionBox = new javafx.scene.control.ComboBox<>();
    private Label regionLabel;
    private final javafx.scene.control.ComboBox<dev.dragifier.model.Dock> dockBox = new javafx.scene.control.ComboBox<>();
    private Label dockLabel;
    private Label xLabel;
    private Label yLabel;
    private Label anchorsLabel;
    private final javafx.scene.layout.HBox anchorsRow = new javafx.scene.layout.HBox(8, anchorL, anchorT, anchorR, anchorB);

    private final GridPane formGrid = new GridPane();
    private final CheckBox resizableBox = new CheckBox("Resizable window");
    private final javafx.scene.control.TitledPane formSection = new javafx.scene.control.TitledPane("Form", formGrid);
    private final TextField nameField = new TextField();
    private final TextField titleField = new TextField();
    private final TextField formWField = new TextField();
    private final TextField formHField = new TextField();
    private java.util.function.Predicate<String> nameInUse = n -> false;

    public InspectorPane() {
        setSpacing(10);
        setPadding(new Insets(10));
        setPrefWidth(240);
        getStyleClass().addAll("side-panel", "inspector");

        header.getStyleClass().add("panel-header");

        setupGrid(layoutGrid);
        int row = 0;
        addRow(layoutGrid, row++, "Id", idField);
        parentValue.getStyleClass().add("hint-text");
        addRow(layoutGrid, row++, "Parent", parentValue);
        slotLabel = addRow(layoutGrid, row++, "Tab #", slotField);
        cellColLabel = addRow(layoutGrid, row++, "Column", cellColField);
        cellRowLabel = addRow(layoutGrid, row++, "Row", cellRowField);
        regionBox.getItems().addAll(ContainerGeometry.DOCK_REGIONS);
        regionLabel = addRow(layoutGrid, row++, "Region", regionBox);
        dockBox.getItems().addAll(dev.dragifier.model.Dock.values());
        dockLabel = addRow(layoutGrid, row++, "Dock", dockBox);
        xLabel = addRow(layoutGrid, row++, "X", xField);
        yLabel = addRow(layoutGrid, row++, "Y", yField);
        addRow(layoutGrid, row++, "Width", wField);
        addRow(layoutGrid, row++, "Height", hField);
        anchorsLabel = addRow(layoutGrid, row, "Anchors", anchorsRow);

        setupGrid(appearanceGrid);
        row = 0;
        textLabel = addRow(appearanceGrid, row++, "Text", textField);
        addRow(appearanceGrid, row++, "Font size", fontField);
        addRow(appearanceGrid, row++, "Text color", textColorPicker);
        appearanceGrid.add(customBg, 0, row++, 2, 1);
        addRow(appearanceGrid, row++, "Background", bgPicker);
        alignBox.getItems().addAll("Default", "Left", "Center", "Right");
        alignLabel = addRow(appearanceGrid, row, "Align", alignBox);

        setupGrid(behaviorGrid);
        row = 0;
        addRow(behaviorGrid, row++, "Tooltip", tooltipField);
        behaviorGrid.add(new javafx.scene.layout.HBox(12, disabledBox, lockedBox), 0, row++, 2, 1);
        itemsArea.setPrefRowCount(3);
        itemsArea.setPrefWidth(110);
        itemsArea.setPromptText("One item per line");
        itemsLabel = addRow(behaviorGrid, row++, "Items", itemsArea);
        orientationBox.getItems().addAll("Horizontal", "Vertical");
        orientationLabel = addRow(behaviorGrid, row++, "Orientation", orientationBox);
        panesLabel = addRow(behaviorGrid, row++, "Panes", panesField);
        dividersField.setPromptText("e.g. 0.3, 0.7");
        dividersLabel = addRow(behaviorGrid, row++, "Dividers", dividersField);
        spacingLabel = addRow(behaviorGrid, row++, "Spacing", spacingField);
        colsLabel = addRow(behaviorGrid, row++, "Columns", colsField);
        rowsLabel = addRow(behaviorGrid, row++, "Rows", rowsField);
        columnsArea.setPrefRowCount(3);
        columnsArea.setPrefWidth(110);
        columnsArea.setPromptText("One column per line");
        columnsLabel = addRow(behaviorGrid, row++, "Columns", columnsArea);
        valueLabel = addRow(behaviorGrid, row++, "Value %", valueField);
        javafx.scene.control.Button chooseImage = new javafx.scene.control.Button("Choose…");
        javafx.scene.control.Button clearImage = new javafx.scene.control.Button("Clear");
        chooseImage.setOnAction(e -> pickImage());
        clearImage.setOnAction(e -> applyComponent(() -> current.setImageData("")));
        imageButtons.getChildren().addAll(chooseImage, clearImage);
        imageLabel = addRow(behaviorGrid, row++, "Image", imageButtons);
        javafx.scene.control.Button chooseMedia = new javafx.scene.control.Button("Choose…");
        javafx.scene.control.Button clearMedia = new javafx.scene.control.Button("Clear");
        chooseMedia.setOnAction(e -> pickMedia());
        clearMedia.setOnAction(e -> applyComponent(() -> {
            current.setMediaData("");
            current.setMediaFormat("");
        }));
        mediaButtons.getChildren().addAll(chooseMedia, clearMedia);
        mediaLabel = addRow(behaviorGrid, row++, "Media", mediaButtons);
        javafx.scene.control.Button chooseFolder = new javafx.scene.control.Button("Choose…");
        javafx.scene.control.Button clearFolder = new javafx.scene.control.Button("Home");
        chooseFolder.setOnAction(e -> pickFolder());
        clearFolder.setOnAction(e -> {
            applyComponent(() -> current.setText(""));
            textField.setText("");
        });
        folderButtons.getChildren().addAll(chooseFolder, clearFolder);
        folderLabel = addRow(behaviorGrid, row, "Folder", folderButtons);

        setupGrid(formGrid);
        addRow(formGrid, 0, "Name", nameField);
        addRow(formGrid, 1, "Title", titleField);
        addRow(formGrid, 2, "Width", formWField);
        addRow(formGrid, 3, "Height", formHField);
        formGrid.add(resizableBox, 0, 4, 2, 1);

        for (var section : java.util.List.of(layoutSection, appearanceSection, behaviorSection, formSection)) {
            section.setAnimated(false);
        }
        // standalone checkbox rows take part in property search too
        register(customBg, customBg);
        register(disabledBox, disabledBox);
        register(lockedBox, lockedBox);
        register(resizableBox, resizableBox);

        searchField.setPromptText("Search properties…");
        searchField.textProperty().addListener((obs, was, is) -> applyRowVisibility());

        VBox sections = new VBox(4, layoutSection, appearanceSection, behaviorSection, formSection);
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(sections);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
        getChildren().addAll(header, searchField, scroll);

        wireComponentEdits();
        wireFormEdits();
        showForm();
    }

    private void showSections(boolean component, boolean form) {
        for (var section : java.util.List.of(layoutSection, appearanceSection, behaviorSection)) {
            section.setVisible(component);
            section.setManaged(component);
        }
        formSection.setVisible(form);
        formSection.setManaged(form);
    }

    private void setupGrid(GridPane grid) {
        grid.setHgap(8);
        grid.setVgap(6);
    }

    private Label addRow(GridPane grid, int row, String labelText, javafx.scene.Node control) {
        Label label = new Label(labelText);
        label.setMinWidth(64);
        grid.add(label, 0, row);
        grid.add(control, 1, row);
        if (control instanceof TextField field) {
            field.setPrefWidth(110);
        }
        register(label, control);
        return label;
    }

    private void register(javafx.scene.Node labelNode, javafx.scene.Node control) {
        labelNode.getProperties().put("rowBase", Boolean.TRUE);
        searchRows.add(new javafx.scene.Node[]{labelNode, control});
    }

    /** Sets a row's base (per-type) visibility; the search filter composes on top. */
    private void setRowVisible(javafx.scene.Node label, javafx.scene.Node control, boolean visible) {
        label.getProperties().put("rowBase", visible);
        applyRowVisibility();
    }

    private void applyRowVisibility() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        for (javafx.scene.Node[] row : searchRows) {
            boolean base = !Boolean.FALSE.equals(row[0].getProperties().get("rowBase"));
            String text = row[0] instanceof javafx.scene.control.Labeled labeled ? labeled.getText() : "";
            boolean visible = base && (q.isEmpty() || text.toLowerCase().contains(q));
            for (javafx.scene.Node node : row) {
                node.setVisible(visible);
                node.setManaged(visible);
            }
        }
    }

    public void setModel(FormModel model) {
        this.model = model;
        showForm();
    }

    public void setOnComponentEdited(Consumer<FormComponent> onComponentEdited) {
        this.onComponentEdited = onComponentEdited;
    }

    public void setOnFormEdited(Runnable onFormEdited) {
        this.onFormEdited = onFormEdited;
    }

    public void setCheckpoint(Runnable checkpoint) {
        this.checkpoint = checkpoint;
    }

    public void setNameInUse(java.util.function.Predicate<String> nameInUse) {
        this.nameInUse = nameInUse;
    }

    /** Callback that performs the actual component rename (with refactoring); returns success. */
    public void setRenamer(java.util.function.BiPredicate<FormComponent, String> renamer) {
        this.renamer = renamer;
    }

    /** Focus the Id field for an F2 rename. */
    public void focusIdField() {
        if (current != null) {
            idField.requestFocus();
            idField.selectAll();
        }
    }

    public void showComponent(FormComponent c) {
        current = c;
        updating = true;
        header.setText(c.getType().displayName + " — " + c.getId());
        idField.setText(c.getId());
        xField.setText(num(c.getX()));
        yField.setText(num(c.getY()));
        wField.setText(num(c.getWidth()));
        hField.setText(num(c.getHeight()));
        anchorL.setSelected(c.isAnchorLeft());
        anchorT.setSelected(c.isAnchorTop());
        anchorR.setSelected(c.isAnchorRight());
        anchorB.setSelected(c.isAnchorBottom());
        textField.setText(c.getText());
        fontField.setText(num(c.getFontSize()));
        textColorPicker.setValue(parseColor(c.getTextColor(), Color.web("#212121")));
        boolean hasBg = !c.getBackground().isEmpty();
        customBg.setSelected(hasBg);
        bgPicker.setDisable(!hasBg);
        bgPicker.setValue(hasBg ? parseColor(c.getBackground(), Color.WHITE) : Color.WHITE);
        FormComponent parent = model == null ? null : model.parentOf(c);
        parentValue.setText(parent == null ? "Form" : parent.getId() + " (" + parent.getType().displayName + ")");
        boolean slotted = parent != null && parent.getType().kind.hasSlots();
        slotLabel.setText(parent != null && parent.getType() == ComponentType.TAB_PANE ? "Tab #" : "Pane #");
        setRowVisible(slotLabel, slotField, slotted);
        slotField.setText(slotted ? String.valueOf(ContainerGeometry.slotIndex(c, parent) + 1) : "");
        boolean browser = c.getType() == ComponentType.FILE_BROWSER;
        boolean hasItems = c.getType() == ComponentType.COMBO_BOX || c.getType() == ComponentType.LIST_VIEW
                || c.getType() == ComponentType.TAB_PANE || browser;
        itemsLabel.setText(c.getType() == ComponentType.TAB_PANE ? "Tabs" : browser ? "Filters" : "Items");
        itemsArea.setPromptText(c.getType() == ComponentType.TAB_PANE ? "One tab title per line"
                : browser ? "One extension per line, e.g. txt (empty = all files)" : "One item per line");
        textLabel.setText(browser ? "Root folder" : c.getType() == ComponentType.WEB_VIEW ? "URL" : "Text");
        setRowVisible(folderLabel, folderButtons, browser);
        setRowVisible(itemsLabel, itemsArea, hasItems);
        itemsArea.setText(c.getItems());
        boolean split = c.getType() == ComponentType.SPLIT_PANE;
        boolean stack = c.getType() == ComponentType.STACK_PANEL;
        boolean grid = c.getType() == ComponentType.GRID_PANE;
        setRowVisible(orientationLabel, orientationBox, split || stack);
        orientationBox.setValue("VERTICAL".equals(c.getOrientation()) ? "Vertical" : "Horizontal");
        setRowVisible(panesLabel, panesField, split);
        panesField.setText(String.valueOf(ContainerGeometry.paneCount(c)));
        setRowVisible(dividersLabel, dividersField, split);
        dividersField.setText(c.getDividers());
        setRowVisible(spacingLabel, spacingField, stack || grid);
        spacingField.setText(num(c.getSpacing()));
        setRowVisible(colsLabel, colsField, grid);
        colsField.setText(String.valueOf(ContainerGeometry.gridColumns(c)));
        setRowVisible(rowsLabel, rowsField, grid);
        rowsField.setText(String.valueOf(ContainerGeometry.gridRows(c)));
        boolean inGrid = parent != null && parent.getType() == ComponentType.GRID_PANE;
        boolean inDock = parent != null && parent.getType() == ComponentType.DOCK_PANEL;
        boolean autoLaid = parent != null && parent.getType().kind.isAutoLayout();
        setRowVisible(cellColLabel, cellColField, inGrid);
        setRowVisible(cellRowLabel, cellRowField, inGrid);
        if (inGrid) {
            int[] cell = ContainerGeometry.gridCell(c, parent);
            cellColField.setText(String.valueOf(cell[0] + 1));
            cellRowField.setText(String.valueOf(cell[1] + 1));
        }
        setRowVisible(regionLabel, regionBox, inDock);
        regionBox.setValue(inDock ? ContainerGeometry.dockRegion(c) : "CENTER");
        // an auto-layout parent decides position and anchoring
        setRowVisible(xLabel, xField, !autoLaid);
        setRowVisible(yLabel, yField, !autoLaid);
        setRowVisible(anchorsLabel, anchorsRow, !autoLaid);
        setRowVisible(dockLabel, dockBox, !autoLaid);
        dev.dragifier.model.Dock dock = c.getDock();
        dockBox.setValue(dock);
        // a docked component's rectangle comes from the dock, except its own thickness
        boolean docked = !autoLaid && dock != dev.dragifier.model.Dock.NONE;
        boolean horizontalDock = dock == dev.dragifier.model.Dock.LEFT || dock == dev.dragifier.model.Dock.RIGHT;
        boolean verticalDock = dock == dev.dragifier.model.Dock.TOP || dock == dev.dragifier.model.Dock.BOTTOM;
        xField.setDisable(docked);
        yField.setDisable(docked);
        wField.setDisable(docked && !horizontalDock);
        hField.setDisable(docked && !verticalDock);
        anchorsRow.setDisable(docked);
        setRowVisible(columnsLabel, columnsArea, c.getType() == ComponentType.TABLE_VIEW);
        columnsArea.setText(c.getColumns());
        setRowVisible(mediaLabel, mediaButtons, c.getType() == ComponentType.MEDIA_PLAYER);
        boolean hasValue = c.getType() == ComponentType.PROGRESS_BAR || c.getType() == ComponentType.TIMER;
        valueLabel.setText(c.getType() == ComponentType.TIMER ? "Interval ms" : "Value %");
        setRowVisible(valueLabel, valueField, hasValue);
        valueField.setText(num(c.getValue()));
        setRowVisible(imageLabel, imageButtons, c.getType() == ComponentType.IMAGE_VIEW);
        tooltipField.setText(c.getTooltip());
        disabledBox.setSelected(c.isDisabled());
        lockedBox.setSelected(c.isLocked());
        setRowVisible(alignLabel, alignBox, Renderer.supportsAlignment(c.getType()));
        alignBox.setValue(switch (c.getAlignment()) {
            case "LEFT" -> "Left";
            case "CENTER" -> "Center";
            case "RIGHT" -> "Right";
            default -> "Default";
        });
        showSections(true, false);
        updating = false;
    }

    public void showMulti(int count) {
        current = null;
        updating = true;
        header.setText(count + " components selected");
        showSections(false, false);
        updating = false;
    }

    public void showForm() {
        current = null;
        updating = true;
        header.setText("Form");
        if (model != null) {
            nameField.setText(model.getName());
            titleField.setText(model.getTitle());
            formWField.setText(num(model.getWidth()));
            formHField.setText(num(model.getHeight()));
            resizableBox.setSelected(model.isResizable());
        }
        showSections(false, true);
        updating = false;
    }

    /** Called when the canvas moves/resizes the selected component, to keep fields in sync. */
    public void updateGeometry(FormComponent c) {
        if (c != current) {
            return;
        }
        updating = true;
        xField.setText(num(c.getX()));
        yField.setText(num(c.getY()));
        wField.setText(num(c.getWidth()));
        hField.setText(num(c.getHeight()));
        updating = false;
    }

    private void wireComponentEdits() {
        onCommit(idField, () -> {
            if (current == null || updating) {
                return;
            }
            String raw = idField.getText().trim();
            if (raw.equals(current.getId())) {
                return;
            }
            if (renamer.test(current, raw)) {
                header.setText(current.getType().displayName + " — " + current.getId());
                idField.setText(current.getId());
            } else {
                idField.setText(current.getId());
            }
        });
        onCommit(xField, () -> applyNumber(xField, FormComponent::getX, (c, v) -> c.setX(v)));
        onCommit(yField, () -> applyNumber(yField, FormComponent::getY, (c, v) -> c.setY(v)));
        onCommit(wField, () -> applyNumber(wField, FormComponent::getWidth, (c, v) -> c.setWidth(Math.max(16, v))));
        onCommit(hField, () -> applyNumber(hField, FormComponent::getHeight, (c, v) -> c.setHeight(Math.max(16, v))));
        onCommit(textField, () -> {
            if (current != null && !textField.getText().equals(current.getText())) {
                applyComponent(() -> current.setText(textField.getText()));
            }
        });
        onCommit(fontField, () -> applyNumber(fontField, FormComponent::getFontSize, (c, v) -> c.setFontSize(Math.max(6, v))));
        itemsArea.focusedProperty().addListener((obs, was, is) -> {
            if (!is && current != null && !itemsArea.getText().equals(current.getItems())) {
                applyComponent(() -> current.setItems(itemsArea.getText()));
            }
        });
        columnsArea.focusedProperty().addListener((obs, was, is) -> {
            if (!is && current != null && !columnsArea.getText().equals(current.getColumns())) {
                applyComponent(() -> current.setColumns(columnsArea.getText()));
            }
        });
        onCommit(valueField, () -> applyNumber(valueField, FormComponent::getValue,
                (c, v) -> c.setValue(c.getType() == ComponentType.TIMER
                        ? Math.max(16, v)
                        : Math.max(0, Math.min(100, v)))));
        onCommit(tooltipField, () -> {
            if (current != null && !tooltipField.getText().equals(current.getTooltip())) {
                applyComponent(() -> current.setTooltip(tooltipField.getText()));
            }
        });
        onCommit(slotField, () -> {
            if (current == null || updating || model == null) {
                return;
            }
            FormComponent parent = model.parentOf(current);
            if (parent == null || !parent.getType().kind.hasSlots()) {
                return;
            }
            int currentSlot = ContainerGeometry.slotIndex(current, parent);
            Double v = parse(slotField.getText());
            int idx = v == null ? currentSlot
                    : (int) Math.max(0, Math.min(ContainerGeometry.slotCount(parent) - 1, v.intValue() - 1));
            if (idx != currentSlot) {
                String slot = String.valueOf(idx);
                applyComponent(() -> current.setSlot(slot));
            }
            slotField.setText(String.valueOf(idx + 1));
        });
        orientationBox.setOnAction(e -> {
            String value = "Vertical".equals(orientationBox.getValue()) ? "VERTICAL" : "HORIZONTAL";
            if (current != null && !value.equals(current.getOrientation())) {
                applyComponent(() -> current.setOrientation(value));
            }
        });
        onCommit(panesField, () -> applyNumber(panesField, c -> ContainerGeometry.paneCount(c),
                (c, v) -> c.setPanes((int) Math.max(2, Math.min(8, v)))));
        onCommit(dividersField, () -> {
            if (current != null && !dividersField.getText().equals(current.getDividers())) {
                applyComponent(() -> current.setDividers(dividersField.getText()));
            }
        });
        onCommit(spacingField, () -> applyNumber(spacingField, FormComponent::getSpacing,
                (c, v) -> c.setSpacing(Math.max(0, v))));
        onCommit(colsField, () -> applyNumber(colsField, c -> ContainerGeometry.gridColumns(c),
                (c, v) -> c.setGridColumns((int) Math.max(1, Math.min(12, v)))));
        onCommit(rowsField, () -> applyNumber(rowsField, c -> ContainerGeometry.gridRows(c),
                (c, v) -> c.setGridRows((int) Math.max(1, Math.min(12, v)))));
        dockBox.setOnAction(e -> {
            dev.dragifier.model.Dock dock = dockBox.getValue();
            if (current != null && dock != null && dock != current.getDock()) {
                applyComponent(() -> current.setDock(dock));
                if (current != null) {
                    showComponent(current); // re-evaluate which geometry fields stay editable
                }
            }
        });
        onCommit(cellColField, () -> applyCell(0, cellColField));
        onCommit(cellRowField, () -> applyCell(1, cellRowField));
        regionBox.setOnAction(e -> {
            String region = regionBox.getValue();
            if (current == null || model == null || region == null) {
                return;
            }
            FormComponent parent = model.parentOf(current);
            if (parent != null && parent.getType() == ComponentType.DOCK_PANEL
                    && !region.equals(ContainerGeometry.dockRegion(current))) {
                applyComponent(() -> current.setSlot(region));
            }
        });
        disabledBox.setOnAction(e ->
                applyComponent(() -> current.setDisabled(disabledBox.isSelected())));
        lockedBox.setOnAction(e ->
                applyComponent(() -> current.setLocked(lockedBox.isSelected())));
        anchorL.setOnAction(e -> applyComponent(() -> current.setAnchorLeft(anchorL.isSelected())));
        anchorT.setOnAction(e -> applyComponent(() -> current.setAnchorTop(anchorT.isSelected())));
        anchorR.setOnAction(e -> applyComponent(() -> current.setAnchorRight(anchorR.isSelected())));
        anchorB.setOnAction(e -> applyComponent(() -> current.setAnchorBottom(anchorB.isSelected())));
        alignBox.setOnAction(e -> {
            String picked = alignBox.getValue();
            if (picked == null) {
                return;
            }
            String value = switch (picked) {
                case "Left" -> "LEFT";
                case "Center" -> "CENTER";
                case "Right" -> "RIGHT";
                default -> "";
            };
            if (current != null && !value.equals(current.getAlignment())) {
                applyComponent(() -> current.setAlignment(value));
            }
        });
        textColorPicker.setOnAction(e ->
                applyComponent(() -> current.setTextColor(hex(textColorPicker.getValue()))));
        customBg.setOnAction(e -> {
            bgPicker.setDisable(!customBg.isSelected());
            applyComponent(() -> current.setBackground(
                    customBg.isSelected() ? hex(bgPicker.getValue()) : ""));
        });
        bgPicker.setOnAction(e -> {
            if (customBg.isSelected()) {
                applyComponent(() -> current.setBackground(hex(bgPicker.getValue())));
            }
        });
    }

    private void wireFormEdits() {
        onCommit(nameField, () -> {
            if (model == null) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (char ch : nameField.getText().trim().toCharArray()) {
                if (Character.isLetterOrDigit(ch)) {
                    sb.append(ch);
                }
            }
            String sanitized = sb.toString();
            if (sanitized.equals(model.getName())) {
                nameField.setText(sanitized);
                return;
            }
            if (sanitized.isEmpty() || Character.isDigit(sanitized.charAt(0)) || nameInUse.test(sanitized)) {
                nameField.setText(model.getName());
                return;
            }
            applyForm(() -> model.setName(sanitized));
            nameField.setText(sanitized);
        });
        onCommit(titleField, () -> {
            if (model != null && !titleField.getText().equals(model.getTitle())) {
                applyForm(() -> model.setTitle(titleField.getText()));
            }
        });
        onCommit(formWField, () -> {
            Double v = parse(formWField.getText());
            if (model != null && v != null && Math.max(100, v) != model.getWidth()) {
                applyForm(() -> model.setWidth(Math.max(100, v)));
            }
        });
        onCommit(formHField, () -> {
            Double v = parse(formHField.getText());
            if (model != null && v != null && Math.max(100, v) != model.getHeight()) {
                applyForm(() -> model.setHeight(Math.max(100, v)));
            }
        });
        resizableBox.setOnAction(e ->
                applyForm(() -> model.setResizable(resizableBox.isSelected())));
    }

    /** Writes one axis (0 = column, 1 = row) of a Grid child's cell, 1-based in the UI. */
    private void applyCell(int axis, TextField field) {
        if (current == null || updating || model == null) {
            return;
        }
        FormComponent parent = model.parentOf(current);
        if (parent == null || parent.getType() != ComponentType.GRID_PANE) {
            return;
        }
        int[] cell = ContainerGeometry.gridCell(current, parent);
        Double v = parse(field.getText());
        int max = (axis == 0 ? ContainerGeometry.gridColumns(parent) : ContainerGeometry.gridRows(parent)) - 1;
        int value = v == null ? cell[axis] : (int) Math.max(0, Math.min(max, v.intValue() - 1));
        if (value != cell[axis]) {
            cell[axis] = value;
            String slot = cell[0] + "," + cell[1];
            applyComponent(() -> current.setSlot(slot));
        }
        field.setText(String.valueOf(value + 1));
    }

    private void applyNumber(TextField field,
                             java.util.function.ToDoubleFunction<FormComponent> getter,
                             java.util.function.ObjDoubleConsumer<FormComponent> setter) {
        if (current == null) {
            return;
        }
        Double v = parse(field.getText());
        if (v == null || v == getter.applyAsDouble(current)) {
            return;
        }
        applyComponent(() -> setter.accept(current, v));
    }

    private void pickImage() {
        if (current == null) {
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Choose Image");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(
                "Images (*.png, *.jpg, *.gif, *.bmp)", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        java.io.File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            String encoded = java.util.Base64.getEncoder().encodeToString(bytes);
            applyComponent(() -> current.setImageData(encoded));
        } catch (java.io.IOException ex) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR, "Could not read image: " + ex.getMessage());
            alert.showAndWait();
        }
    }

    private void pickFolder() {
        if (current == null) {
            return;
        }
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Choose Root Folder");
        java.io.File dir = chooser.showDialog(getScene().getWindow());
        if (dir != null) {
            String path = dir.getAbsolutePath();
            applyComponent(() -> current.setText(path));
            textField.setText(path);
        }
    }

    private void pickMedia() {
        if (current == null) {
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Choose Media File");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(
                "Media (*.mp3, *.mp4, *.wav, *.m4a, *.aiff)", "*.mp3", "*.mp4", "*.wav", "*.m4a", "*.aiff"));
        java.io.File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            if (file.length() > 10L * 1024 * 1024) {
                new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                        "Media files are limited to 10 MB (they are stored inside the project file).")
                        .showAndWait();
                return;
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            String encoded = java.util.Base64.getEncoder().encodeToString(bytes);
            String name = file.getName().toLowerCase();
            String format = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
            applyComponent(() -> {
                current.setMediaData(encoded);
                current.setMediaFormat(format);
            });
        } catch (java.io.IOException ex) {
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                    "Could not read media file: " + ex.getMessage()).showAndWait();
        }
    }

    private void applyComponent(Runnable change) {
        if (updating || current == null) {
            return;
        }
        checkpoint.run();
        change.run();
        onComponentEdited.accept(current);
    }

    private void applyForm(Runnable change) {
        if (updating || model == null) {
            return;
        }
        checkpoint.run();
        change.run();
        onFormEdited.run();
    }

    private void onCommit(TextField field, Runnable action) {
        field.setOnAction(e -> action.run());
        field.focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                action.run();
            }
        });
    }

    private static Double parse(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException | NullPointerException ex) {
            return null;
        }
    }

    private static String num(double v) {
        if (v == Math.floor(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    private static Color parseColor(String s, Color fallback) {
        try {
            return Color.web(s);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}
