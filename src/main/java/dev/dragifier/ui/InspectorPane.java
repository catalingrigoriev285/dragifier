package dev.dragifier.ui;

import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
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

    private final GridPane componentGrid = new GridPane();
    private final TextField xField = new TextField();
    private final TextField yField = new TextField();
    private final TextField wField = new TextField();
    private final TextField hField = new TextField();
    private final TextField textField = new TextField();
    private final TextField fontField = new TextField();
    private final ColorPicker textColorPicker = new ColorPicker();
    private final CheckBox customBg = new CheckBox("Custom background");
    private final ColorPicker bgPicker = new ColorPicker(Color.WHITE);

    private final GridPane formGrid = new GridPane();
    private final TextField titleField = new TextField();
    private final TextField formWField = new TextField();
    private final TextField formHField = new TextField();

    public InspectorPane() {
        setSpacing(10);
        setPadding(new Insets(10));
        setPrefWidth(220);
        setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #d0d0d0; -fx-border-width: 0 0 0 1;");

        header.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        setupGrid(componentGrid);
        int row = 0;
        addRow(componentGrid, row++, "X", xField);
        addRow(componentGrid, row++, "Y", yField);
        addRow(componentGrid, row++, "Width", wField);
        addRow(componentGrid, row++, "Height", hField);
        addRow(componentGrid, row++, "Text", textField);
        addRow(componentGrid, row++, "Font size", fontField);
        addRow(componentGrid, row++, "Text color", textColorPicker);
        componentGrid.add(customBg, 0, row++, 2, 1);
        addRow(componentGrid, row, "Background", bgPicker);

        setupGrid(formGrid);
        addRow(formGrid, 0, "Title", titleField);
        addRow(formGrid, 1, "Width", formWField);
        addRow(formGrid, 2, "Height", formHField);

        getChildren().addAll(header, componentGrid, formGrid);

        wireComponentEdits();
        wireFormEdits();
        showForm();
    }

    private void setupGrid(GridPane grid) {
        grid.setHgap(8);
        grid.setVgap(6);
    }

    private void addRow(GridPane grid, int row, String labelText, javafx.scene.Node control) {
        Label label = new Label(labelText);
        label.setMinWidth(64);
        grid.add(label, 0, row);
        grid.add(control, 1, row);
        if (control instanceof TextField field) {
            field.setPrefWidth(110);
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
        componentGrid.setVisible(true);
        componentGrid.setManaged(true);
        formGrid.setVisible(false);
        formGrid.setManaged(false);
        updating = false;
    }

    public void showForm() {
        current = null;
        updating = true;
        header.setText("Form");
        if (model != null) {
            titleField.setText(model.getTitle());
            formWField.setText(num(model.getWidth()));
            formHField.setText(num(model.getHeight()));
        }
        componentGrid.setVisible(false);
        componentGrid.setManaged(false);
        formGrid.setVisible(true);
        formGrid.setManaged(true);
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
