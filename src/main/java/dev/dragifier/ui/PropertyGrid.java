package dev.dragifier.ui;

import dev.dragifier.model.CssInsets;
import dev.dragifier.model.FormComponent;
import dev.dragifier.ui.PropertySpec.Context;
import dev.dragifier.ui.PropertySpec.Editor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Renders a list of {@link PropertySpec}s as collapsible categories (with
 * optional sub-groups) of label/editor rows, supports a text filter, and
 * writes edits back through the {@link Context}.
 */
public class PropertyGrid extends VBox {

    /** A rendered row: the spec, its two nodes and how to reload the editor from the model. */
    private record Row(PropertySpec spec, Node label, Node editor, Runnable load, String groupName) {}

    private static final class Category {
        final String name;
        final HBox header;
        final Label arrow = new Label();
        final VBox body = new VBox(2);
        final GridPane grid = new GridPane();
        final List<Row> rows = new ArrayList<>();
        final Map<String, Label> groupHeaders = new LinkedHashMap<>();

        Category(String name) {
            this.name = name;
            Label title = new Label(name);
            arrow.getStyleClass().add("arrow");
            header = new HBox(6, arrow, title);
            header.setAlignment(Pos.CENTER_LEFT);
            header.getStyleClass().add("property-category");
            grid.setHgap(8);
            grid.setVgap(5);
            grid.setPadding(new Insets(4, 6, 6, 6));
            ColumnConstraints labels = new ColumnConstraints();
            labels.setMinWidth(88);
            ColumnConstraints editors = new ColumnConstraints();
            editors.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().addAll(labels, editors);
            body.getChildren().add(grid);
        }
    }

    private final Set<String> collapsed = new HashSet<>();
    private final List<Category> categories = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private Context context;
    private FormComponent target;
    private String query = "";
    private boolean updating;
    private Consumer<Runnable> applier = Runnable::run;
    private Runnable onRebuild = () -> {};

    public PropertyGrid() {
        setSpacing(2);
    }

    public void setContext(Context context) {
        this.context = context;
    }

    /** Runs a model change (undo checkpoint + change + canvas/tree notification). */
    public void setApplier(Consumer<Runnable> applier) {
        this.applier = applier;
    }

    /** Called after a change that alters which rows apply (the owner re-lists the properties). */
    public void setOnRebuild(Runnable onRebuild) {
        this.onRebuild = onRebuild;
    }

    public void clear() {
        target = null;
        rows.clear();
        categories.clear();
        getChildren().clear();
    }

    /** Rebuilds the grid for {@code target} (null = the form) from the given specs. */
    public void show(FormComponent target, List<PropertySpec> specs) {
        clear();
        this.target = target;
        updating = true;
        Map<String, Category> byName = new LinkedHashMap<>();
        for (PropertySpec spec : specs) {
            if (target != null && !spec.applies().test(target)) {
                continue;
            }
            Category category = byName.computeIfAbsent(spec.category(), Category::new);
            addRow(category, spec);
        }
        for (Category category : byName.values()) {
            categories.add(category);
            category.header.setOnMouseClicked(e -> toggle(category));
            getChildren().addAll(category.header, category.body);
        }
        updating = false;
        applyFilter();
    }

    private void toggle(Category category) {
        if (collapsed.contains(category.name)) {
            collapsed.remove(category.name);
        } else {
            collapsed.add(category.name);
        }
        applyFilter();
    }

    private void addRow(Category category, PropertySpec spec) {
        int gridRow = category.grid.getRowCount();
        if (spec.group() != null && !category.groupHeaders.containsKey(spec.group())) {
            Label groupHeader = new Label(spec.group());
            groupHeader.getStyleClass().add("property-group");
            category.grid.add(groupHeader, 0, gridRow++, 2, 1);
            category.groupHeaders.put(spec.group(), groupHeader);
        }
        Label label = new Label(spec.label());
        label.getStyleClass().add("property-row-label");
        label.setPadding(new Insets(0, 0, 0, spec.group() == null ? 4 : 16));
        Object[] built = buildEditor(spec);
        Node editor = (Node) built[0];
        Runnable load = (Runnable) built[1];
        boolean enabled = target == null || spec.enabled().test(target);
        editor.setDisable(!enabled);
        category.grid.add(label, 0, gridRow);
        category.grid.add(editor, 1, gridRow);
        Row row = new Row(spec, label, editor, load, spec.group());
        category.rows.add(row);
        rows.add(row);
        load.run();
    }

    /** Re-reads every row's value from the model (after canvas moves, external edits). */
    public void refreshValues() {
        updating = true;
        for (Row row : rows) {
            row.load.run();
            if (target != null) {
                row.editor.setDisable(!row.spec.enabled().test(target));
            }
        }
        updating = false;
    }

    /** Filters rows by label / group / category name; empty shows everything. */
    public void search(String query) {
        this.query = query == null ? "" : query.trim().toLowerCase();
        applyFilter();
    }

    private void applyFilter() {
        boolean filtering = !query.isEmpty();
        for (Category category : categories) {
            boolean anyVisible = false;
            Map<String, Boolean> groupVisible = new LinkedHashMap<>();
            for (Row row : category.rows) {
                boolean match = !filtering
                        || row.spec.label().toLowerCase().contains(query)
                        || category.name.toLowerCase().contains(query)
                        || (row.groupName != null && row.groupName.toLowerCase().contains(query));
                setShown(row.label, match);
                setShown(row.editor, match);
                anyVisible |= match;
                if (row.groupName != null) {
                    groupVisible.merge(row.groupName, match, Boolean::logicalOr);
                }
            }
            for (var entry : category.groupHeaders.entrySet()) {
                setShown(entry.getValue(), groupVisible.getOrDefault(entry.getKey(), false));
            }
            boolean expanded = filtering || !collapsed.contains(category.name);
            setShown(category.header, anyVisible);
            setShown(category.body, anyVisible && expanded);
            category.arrow.setText(expanded ? "▾" : "▸");
        }
    }

    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    /** Focuses the editor of the row with the given key (e.g. "id" for F2 rename). */
    public void focusRow(String key) {
        for (Row row : rows) {
            if (row.spec.key().equals(key)) {
                Node editor = row.editor;
                if (editor instanceof TextField field) {
                    field.requestFocus();
                    field.selectAll();
                } else {
                    editor.requestFocus();
                }
                return;
            }
        }
    }

    // ------------------------------------------------------------- editors

    private void commit(PropertySpec spec, Object value) {
        if (updating || target == null && spec.editor() != Editor.EVENT && context == null) {
            return;
        }
        applier.accept(() -> spec.set().accept(target, value));
        refreshValues();
        if (spec.rebuildAfterSet()) {
            onRebuild.run();
        }
    }

    private static boolean same(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return x.doubleValue() == y.doubleValue();
        }
        return a == null ? b == null : a.equals(b);
    }

    /** Builds {editor node, loader}. */
    private Object[] buildEditor(PropertySpec spec) {
        return switch (spec.editor()) {
            case TEXT -> {
                TextField field = new TextField();
                field.setPromptText(spec.prompt());
                onCommit(field, () -> {
                    String value = field.getText();
                    if (!same(value, spec.get().get())) {
                        commit(spec, value);
                    }
                });
                yield new Object[]{field, (Runnable) () -> field.setText(String.valueOf(spec.get().get()))};
            }
            case MULTILINE -> {
                TextArea area = new TextArea();
                area.setPrefRowCount(3);
                area.setPrefWidth(110);
                area.setPromptText(spec.prompt());
                area.focusedProperty().addListener((obs, was, is) -> {
                    if (!is && !same(area.getText(), spec.get().get())) {
                        commit(spec, area.getText());
                    }
                });
                yield new Object[]{area, (Runnable) () -> area.setText(String.valueOf(spec.get().get()))};
            }
            case NUMBER, INTEGER -> {
                TextField field = new TextField();
                field.setPrefWidth(90);
                Runnable load = () -> field.setText(num(spec.get().get()));
                onCommit(field, () -> {
                    Double parsed = parse(field.getText());
                    if (parsed == null) {
                        load.run();
                        return;
                    }
                    Object value = spec.editor() == Editor.INTEGER ? (Object) (int) Math.round(parsed) : (Object) parsed;
                    if (!same(value, spec.get().get())) {
                        commit(spec, value);
                    } else {
                        load.run();
                    }
                });
                yield new Object[]{field, load};
            }
            case CHECK -> {
                CheckBox box = new CheckBox();
                box.setOnAction(e -> commit(spec, box.isSelected()));
                yield new Object[]{box, (Runnable) () -> box.setSelected(Boolean.TRUE.equals(spec.get().get()))};
            }
            case COLOR -> {
                ColorPicker picker = new ColorPicker();
                picker.setPrefWidth(100);
                Button reset = new Button("Default");
                picker.setOnAction(e -> {
                    if (!updating) {
                        commit(spec, hex(picker.getValue()));
                    }
                });
                reset.setOnAction(e -> commit(spec, ""));
                HBox box = new HBox(4, picker, reset);
                box.setAlignment(Pos.CENTER_LEFT);
                yield new Object[]{box, (Runnable) () -> {
                    String value = String.valueOf(spec.get().get());
                    picker.setValue(value.isEmpty() ? Color.WHITE : parseColor(value));
                    reset.setDisable(value.isEmpty());
                }};
            }
            case CHOICE -> {
                ComboBox<String> combo = new ComboBox<>();
                combo.getItems().addAll(spec.choices());
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.setOnAction(e -> {
                    if (!updating && combo.getValue() != null && !same(combo.getValue(), spec.get().get())) {
                        commit(spec, combo.getValue());
                    }
                });
                yield new Object[]{combo, (Runnable) () -> combo.setValue(String.valueOf(spec.get().get()))};
            }
            case FONT_FAMILY -> {
                ComboBox<String> combo = new ComboBox<>();
                combo.setEditable(true);
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.setPromptText("Default");
                combo.getItems().add("");
                combo.getItems().addAll(Font.getFamilies());
                Runnable commitFamily = () -> {
                    String value = combo.getEditor().getText() == null ? "" : combo.getEditor().getText().trim();
                    if (!updating && !same(value, spec.get().get())) {
                        commit(spec, value);
                    }
                };
                combo.setOnAction(e -> commitFamily.run());
                combo.getEditor().focusedProperty().addListener((obs, was, is) -> {
                    if (!is) {
                        commitFamily.run();
                    }
                });
                yield new Object[]{combo, (Runnable) () -> {
                    String value = String.valueOf(spec.get().get());
                    combo.setValue(value);
                    combo.getEditor().setText(value);
                }};
            }
            case INSETS -> {
                TextField field = new TextField();
                field.setPrefWidth(90);
                field.setPromptText(spec.prompt());
                Runnable load = () -> field.setText(String.valueOf(spec.get().get()));
                onCommit(field, () -> {
                    String text = field.getText().trim();
                    if (!CssInsets.isValid(text)) {
                        load.run(); // keep the last good value
                        return;
                    }
                    String value = text.isEmpty() ? "" : CssInsets.normalize(text);
                    if (!same(value, spec.get().get())) {
                        commit(spec, value);
                    } else {
                        load.run();
                    }
                });
                yield new Object[]{field, load};
            }
            case READONLY -> {
                Label value = new Label();
                value.getStyleClass().add("hint-text");
                yield new Object[]{value, (Runnable) () -> value.setText(String.valueOf(spec.get().get()))};
            }
            case CUSTOM -> {
                Node node = spec.custom().apply(context);
                yield new Object[]{node, (Runnable) () -> { }};
            }
            case EVENT -> {
                Label status = new Label();
                status.getStyleClass().add("property-event-status");
                Button edit = new Button("Edit…");
                edit.setOnAction(e -> {
                    if (context != null) {
                        context.editEvent(spec.key());
                    }
                });
                HBox box = new HBox(6, status, edit);
                box.setAlignment(Pos.CENTER_LEFT);
                yield new Object[]{box, (Runnable) () -> {
                    boolean has = Boolean.TRUE.equals(spec.get().get());
                    status.setText(has ? "✓" : "–");
                    edit.setText(has ? "Edit…" : "Add…");
                }};
            }
        };
    }

    private static void onCommit(TextField field, Runnable action) {
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

    private static String num(Object value) {
        if (value instanceof Number n) {
            double v = n.doubleValue();
            return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
        }
        return String.valueOf(value);
    }

    private static Color parseColor(String s) {
        try {
            return Color.web(s);
        } catch (Exception ex) {
            return Color.WHITE;
        }
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}
