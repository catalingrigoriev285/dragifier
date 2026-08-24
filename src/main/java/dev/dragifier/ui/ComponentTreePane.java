package dev.dragifier.ui;

import dev.dragifier.model.ContainerGeometry;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The component hierarchy: the form as root, containers expandable, children
 * nested. Selection is kept in sync with the canvas. Rows can be dragged onto
 * the upper half of a row to go before it, onto the lower half of a container
 * to move inside it, onto the lower half of a leaf to go after it, or onto
 * the form row to move to the top level.
 */
public class ComponentTreePane extends VBox {

    private static final String DRAG_PREFIX = "dragifier-tree:";

    /** (moved, new parent or null for the form, index among the new siblings excluding {@code moved}). */
    public interface MoveHandler {
        void move(FormComponent moved, FormComponent newParent, int index);
    }

    private final TreeView<FormComponent> tree = new TreeView<>();
    private final TreeItem<FormComponent> rootItem = new TreeItem<>(null);
    /** Ids the user collapsed, so refresh() keeps them collapsed. */
    private final Set<String> collapsed = new HashSet<>();
    private FormModel model;
    private boolean updating;
    private Consumer<FormComponent> onPick = c -> {};
    private MoveHandler onMove = (moved, parent, index) -> {};

    public ComponentTreePane() {
        setSpacing(6);
        setPadding(new Insets(10));
        getStyleClass().add("side-panel");

        Label title = new Label("Components");
        title.getStyleClass().add("panel-header");

        tree.setRoot(rootItem);
        tree.setShowRoot(true);
        rootItem.setExpanded(true);
        tree.setCellFactory(v -> new ComponentCell());
        tree.getSelectionModel().selectedItemProperty().addListener((obs, was, item) -> {
            if (!updating && item != null) {
                // the canvas answers by calling select() back; do that outside the TreeView's own
                // selection-change dispatch, which must not be re-entered
                FormComponent picked = item.getValue(); // null = the form row → clears the canvas selection
                javafx.application.Platform.runLater(() -> onPick.accept(picked));
            }
        });
        VBox.setVgrow(tree, Priority.ALWAYS);

        getChildren().addAll(title, tree);
    }

    public void setOnPick(Consumer<FormComponent> onPick) {
        this.onPick = onPick;
    }

    public void setOnMove(MoveHandler onMove) {
        this.onMove = onMove;
    }

    public void setModel(FormModel model) {
        this.model = model;
        collapsed.clear();
        refresh();
    }

    public void refresh() {
        updating = true;
        rootItem.getChildren().setAll(model == null ? List.of() : buildItems(null));
        updating = false;
    }

    private List<TreeItem<FormComponent>> buildItems(FormComponent parent) {
        List<TreeItem<FormComponent>> items = new ArrayList<>();
        for (FormComponent child : model.childrenOf(parent)) {
            TreeItem<FormComponent> item = new TreeItem<>(child);
            item.getChildren().setAll(buildItems(child));
            item.setExpanded(!collapsed.contains(child.getId()));
            item.expandedProperty().addListener((obs, was, expanded) -> {
                if (expanded) {
                    collapsed.remove(child.getId());
                } else {
                    collapsed.add(child.getId());
                }
            });
            items.add(item);
        }
        return items;
    }

    private TreeItem<FormComponent> find(TreeItem<FormComponent> from, FormComponent c) {
        if (from.getValue() == c) {
            return from;
        }
        for (TreeItem<FormComponent> child : from.getChildren()) {
            TreeItem<FormComponent> hit = find(child, c);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    public void select(FormComponent c) {
        updating = true;
        TreeItem<FormComponent> selected = tree.getSelectionModel().getSelectedItem();
        if (c == null) {
            // keep the form row highlighted when it was what the user clicked
            if (selected != null && selected != rootItem) {
                tree.getSelectionModel().clearSelection();
            }
        } else {
            TreeItem<FormComponent> item = find(rootItem, c);
            if (item == selected) {
                updating = false;
                return;
            }
            if (item != null) {
                for (TreeItem<FormComponent> p = item.getParent(); p != null; p = p.getParent()) {
                    p.setExpanded(true);
                }
                tree.getSelectionModel().select(item);
                int row = tree.getRow(item);
                if (row >= 0) {
                    tree.scrollTo(row);
                }
            } else {
                tree.getSelectionModel().clearSelection();
            }
        }
        updating = false;
    }

    private String slotSuffix(FormComponent c) {
        FormComponent parent = model == null ? null : model.parentOf(c);
        if (parent == null) {
            return "";
        }
        return switch (parent.getType().kind) {
            case TABS -> "  [tab " + (ContainerGeometry.slotIndex(c, parent) + 1) + "]";
            case SPLIT -> "  [pane " + (ContainerGeometry.slotIndex(c, parent) + 1) + "]";
            case GRID -> {
                int[] cell = ContainerGeometry.gridCell(c, parent);
                yield "  [" + (cell[0] + 1) + "," + (cell[1] + 1) + "]";
            }
            case DOCK -> "  [" + ContainerGeometry.dockRegion(c) + "]";
            default -> "";
        };
    }

    private final class ComponentCell extends TreeCell<FormComponent> {

        ComponentCell() {
            setOnDragDetected(e -> {
                if (getItem() != null) {
                    var db = startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(DRAG_PREFIX + getItem().getId());
                    db.setContent(content);
                    e.consume();
                }
            });
            setOnDragOver(e -> {
                if (!isEmpty() && e.getDragboard().hasString()
                        && e.getDragboard().getString().startsWith(DRAG_PREFIX)) {
                    e.acceptTransferModes(TransferMode.MOVE);
                }
                e.consume();
            });
            setOnDragDropped(e -> {
                String payload = e.getDragboard().hasString() ? e.getDragboard().getString() : "";
                if (payload.startsWith(DRAG_PREFIX) && model != null && !isEmpty()) {
                    FormComponent moved = model.findById(payload.substring(DRAG_PREFIX.length()));
                    if (moved != null) {
                        drop(moved, getItem(), e.getY() < getHeight() / 2);
                    }
                    e.setDropCompleted(true);
                }
                e.consume();
            });
        }

        private void drop(FormComponent moved, FormComponent target, boolean upperHalf) {
            if (target == null) {
                // the form row: move to the top level, last
                List<FormComponent> top = model.childrenOf(null);
                top.remove(moved);
                onMove.move(moved, null, top.size());
                return;
            }
            if (target == moved || model.isAncestor(moved, target)) {
                return;
            }
            if (target.getType().isContainer() && !upperHalf) {
                List<FormComponent> inside = model.childrenOf(target);
                inside.remove(moved);
                onMove.move(moved, target, inside.size());
                return;
            }
            FormComponent parent = model.parentOf(target);
            List<FormComponent> siblings = model.childrenOf(parent);
            siblings.remove(moved);
            int index = siblings.indexOf(target) + (upperHalf ? 0 : 1);
            onMove.move(moved, parent, index);
        }

        @Override
        protected void updateItem(FormComponent item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else if (item == null) {
                setText((model == null ? "Form" : model.getName()) + "  (form)");
                setGraphic(new FontIcon(Feather.LAYOUT));
            } else {
                setText(item.getId() + " - " + item.getType().displayName + slotSuffix(item)
                        + (item.isLocked() ? "  (locked)" : ""));
                setGraphic(Icons.forType(item.getType()));
            }
        }
    }
}
