package dev.dragifier.ui;

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

    private final GridPane layoutGrid = new GridPane();
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
    private final TextField fontField = new TextField();
    private final ColorPicker textColorPicker = new ColorPicker();
    private final CheckBox customBg = new CheckBox("Custom background");
    private final ColorPicker bgPicker = new ColorPicker(Color.WHITE);
    private final TextArea itemsArea = new TextArea();
    private Label itemsLabel;
    private final TextField valueField = new TextField();
    private Label valueLabel;
    private final javafx.scene.layout.HBox imageButtons = new javafx.scene.layout.HBox(6);
    private Label imageLabel;
    private final TextField tooltipField = new TextField();
    private final CheckBox disabledBox = new CheckBox("Disabled");
    private final javafx.scene.control.ComboBox<String> alignBox = new javafx.scene.control.ComboBox<>();
    private Label alignLabel;

    private final GridPane formGrid = new GridPane();
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
        addRow(layoutGrid, row++, "X", xField);
        addRow(layoutGrid, row++, "Y", yField);
        addRow(layoutGrid, row++, "Width", wField);
        addRow(layoutGrid, row, "Height", hField);

        setupGrid(appearanceGrid);
        row = 0;
        addRow(appearanceGrid, row++, "Text", textField);
        addRow(appearanceGrid, row++, "Font size", fontField);
        addRow(appearanceGrid, row++, "Text color", textColorPicker);
        appearanceGrid.add(customBg, 0, row++, 2, 1);
        addRow(appearanceGrid, row++, "Background", bgPicker);
        alignBox.getItems().addAll("Default", "Left", "Center", "Right");
        alignLabel = addRow(appearanceGrid, row, "Align", alignBox);

        setupGrid(behaviorGrid);
        row = 0;
        addRow(behaviorGrid, row++, "Tooltip", tooltipField);
        behaviorGrid.add(disabledBox, 0, row++, 2, 1);
        itemsArea.setPrefRowCount(3);
        itemsArea.setPrefWidth(110);
        itemsArea.setPromptText("One item per line");
        itemsLabel = addRow(behaviorGrid, row++, "Items", itemsArea);
        valueLabel = addRow(behaviorGrid, row++, "Value %", valueField);
        javafx.scene.control.Button chooseImage = new javafx.scene.control.Button("Choose…");
        javafx.scene.control.Button clearImage = new javafx.scene.control.Button("Clear");
        chooseImage.setOnAction(e -> pickImage());
        clearImage.setOnAction(e -> applyComponent(() -> current.setImageData("")));
        imageButtons.getChildren().addAll(chooseImage, clearImage);
        imageLabel = addRow(behaviorGrid, row, "Image", imageButtons);

        setupGrid(formGrid);
        addRow(formGrid, 0, "Name", nameField);
        addRow(formGrid, 1, "Title", titleField);
        addRow(formGrid, 2, "Width", formWField);
        addRow(formGrid, 3, "Height", formHField);

        for (var section : java.util.List.of(layoutSection, appearanceSection, behaviorSection, formSection)) {
            section.setAnimated(false);
        }
        VBox sections = new VBox(4, layoutSection, appearanceSection, behaviorSection, formSection);
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(sections);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
        getChildren().addAll(header, scroll);

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
        return label;
    }

    private static void setRowVisible(javafx.scene.Node label, javafx.scene.Node control, boolean visible) {
        label.setVisible(visible);
        label.setManaged(visible);
        control.setVisible(visible);
        control.setManaged(visible);
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

    public void showComponent(FormComponent c) {
        current = c;
        updating = true;
        header.setText(c.getType().displayName + " — " + c.getId());
        xField.setText(num(c.getX()));
        yField.setText(num(c.getY()));
        wField.setText(num(c.getWidth()));
        hField.setText(num(c.getHeight()));
        textField.setText(c.getText());
        fontField.setText(num(c.getFontSize()));
        textColorPicker.setValue(parseColor(c.getTextColor(), Color.web("#212121")));
        boolean hasBg = !c.getBackground().isEmpty();
        customBg.setSelected(hasBg);
        bgPicker.setDisable(!hasBg);
        bgPicker.setValue(hasBg ? parseColor(c.getBackground(), Color.WHITE) : Color.WHITE);
        boolean hasItems = c.getType() == ComponentType.COMBO_BOX || c.getType() == ComponentType.LIST_VIEW;
        setRowVisible(itemsLabel, itemsArea, hasItems);
        itemsArea.setText(c.getItems());
        boolean hasValue = c.getType() == ComponentType.PROGRESS_BAR;
        setRowVisible(valueLabel, valueField, hasValue);
        valueField.setText(num(c.getValue()));
        setRowVisible(imageLabel, imageButtons, c.getType() == ComponentType.IMAGE_VIEW);
        tooltipField.setText(c.getTooltip());
        disabledBox.setSelected(c.isDisabled());
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
        onCommit(valueField, () -> applyNumber(valueField, FormComponent::getValue,
                (c, v) -> c.setValue(Math.max(0, Math.min(100, v)))));
        onCommit(tooltipField, () -> {
            if (current != null && !tooltipField.getText().equals(current.getTooltip())) {
                applyComponent(() -> current.setTooltip(tooltipField.getText()));
            }
        });
        disabledBox.setOnAction(e ->
                applyComponent(() -> current.setDisabled(disabledBox.isSelected())));
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
