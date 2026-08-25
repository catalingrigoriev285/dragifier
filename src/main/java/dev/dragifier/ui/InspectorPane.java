package dev.dragifier.ui;

import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Properties inspector: a searchable, categorised property grid for the
 * selected component (or the form when nothing is selected). Rows come from
 * {@link ComponentProperties}; edits write through to the model with an undo
 * checkpoint and notify the owner.
 */
public class InspectorPane extends VBox {

    private FormModel model;
    private FormComponent current;

    private Consumer<FormComponent> onComponentEdited = c -> {};
    private Runnable onFormEdited = () -> {};
    private Runnable checkpoint = () -> {};
    private Predicate<String> nameInUse = n -> false;
    private BiPredicate<FormComponent, String> renamer = (c, id) -> false;
    private BiConsumer<FormComponent, String> onEditEvent = (c, key) -> {};

    /** The width a property row needs; the pane opens at this and never shrinks below it. */
    private static final double CONTENT_WIDTH = 254;

    private final Label header = new Label();
    private final TextField searchField = new TextField();
    private final PropertyGrid grid = new PropertyGrid();

    public InspectorPane() {
        setSpacing(8);
        setPadding(new Insets(10));
        // Wide enough for a full property row without a horizontal scrollbar:
        // 20 pane padding + 12 grid padding + 88 label column + 8 hgap
        // + 110 widest editor + ~16 vertical scrollbar.
        setPrefWidth(CONTENT_WIDTH);
        setMinWidth(CONTENT_WIDTH);
        getStyleClass().addAll("side-panel", "inspector");

        header.getStyleClass().add("panel-header");
        searchField.setPromptText("Search properties…");
        searchField.textProperty().addListener((obs, was, is) -> grid.search(is));

        grid.setContext(new PropertySpec.Context() {
            @Override
            public FormComponent component() {
                return current;
            }

            @Override
            public void apply(Runnable change) {
                if (current != null) {
                    applyComponent(change);
                } else {
                    applyForm(change);
                }
            }

            @Override
            public Window window() {
                return getScene() == null ? null : getScene().getWindow();
            }

            @Override
            public boolean rename(FormComponent c, String newId) {
                boolean ok = renamer.test(c, newId);
                if (ok) {
                    header.setText(c.getType().displayName + " — " + c.getId());
                }
                return ok;
            }

            @Override
            public void editEvent(String key) {
                onEditEvent.accept(current, key);
            }
        });
        grid.setApplier(change -> {
            if (current != null) {
                applyComponent(change);
            } else {
                applyForm(change);
            }
        });
        grid.setOnRebuild(() -> {
            if (current != null) {
                showComponent(current);
            } else {
                showForm();
            }
        });

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(header, searchField, scroll);
        showForm();
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

    public void setNameInUse(Predicate<String> nameInUse) {
        this.nameInUse = nameInUse;
    }

    /** Callback that performs the actual component rename (with refactoring); returns success. */
    public void setRenamer(BiPredicate<FormComponent, String> renamer) {
        this.renamer = renamer;
    }

    /** Called with (component or null for the form, event key) when an Events row's button is pressed. */
    public void setOnEditEvent(BiConsumer<FormComponent, String> onEditEvent) {
        this.onEditEvent = onEditEvent;
    }

    /** Focus the Name field for an F2 rename. */
    public void focusIdField() {
        if (current != null) {
            grid.focusRow("id");
        }
    }

    public void showComponent(FormComponent c) {
        current = c;
        header.setText(c.getType().displayName + " — " + c.getId());
        grid.show(c, ComponentProperties.forComponent(model, c));
    }

    public void showMulti(int count) {
        current = null;
        header.setText(count + " components selected");
        grid.clear();
    }

    public void showForm() {
        current = null;
        header.setText("Form");
        if (model != null) {
            grid.show(null, ComponentProperties.forForm(model, nameInUse));
        } else {
            grid.clear();
        }
    }

    /** Called when the canvas moves/resizes the selected component, to keep fields in sync. */
    public void updateGeometry(FormComponent c) {
        if (c == current) {
            grid.refreshValues();
        }
    }

    private void applyComponent(Runnable change) {
        if (current == null) {
            return;
        }
        checkpoint.run();
        change.run();
        onComponentEdited.accept(current);
    }

    private void applyForm(Runnable change) {
        if (model == null) {
            return;
        }
        checkpoint.run();
        change.run();
        onFormEdited.run();
    }
}
