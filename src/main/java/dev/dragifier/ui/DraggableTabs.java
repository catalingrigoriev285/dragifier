package dev.dragifier.ui;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;
import javafx.scene.SnapshotParameters;

/**
 * Drag-and-drop reordering of tab headers inside a single {@link TabPane}.
 *
 * <p>{@code Tab} is not a {@code Node}, so the gesture hangs off the tab's graphic — callers pass
 * the graphic they installed as the drag handle. Drags between two tab panes are rejected.
 */
final class DraggableTabs {

    /** The tab being dragged; the gesture is modal so one static is enough. */
    private static Tab dragged;

    private DraggableTabs() {
    }

    /**
     * Makes {@code tab} draggable by its {@code handle} and a drop target for its siblings.
     * {@code onReordered} runs after a successful drop, once the tab is in its new place.
     */
    static void enable(Tab tab, Node handle, Runnable onReordered) {
        handle.setOnDragDetected(e -> {
            Dragboard board = handle.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(tab.getId() == null ? "tab" : tab.getId());  // a drag needs non-empty content
            board.setContent(content);
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            board.setDragView(handle.snapshot(params, null));
            dragged = tab;
            e.consume();
        });

        handle.setOnDragOver(e -> {
            if (canDropOn(tab)) {
                e.acceptTransferModes(TransferMode.MOVE);
                if (!handle.getStyleClass().contains("drop-target")) {
                    handle.getStyleClass().add("drop-target");
                }
            }
            e.consume();
        });

        handle.setOnDragExited(e -> {
            handle.getStyleClass().remove("drop-target");
            e.consume();
        });

        handle.setOnDragDropped(e -> {
            handle.getStyleClass().remove("drop-target");
            if (!canDropOn(tab)) {
                e.setDropCompleted(false);
                e.consume();
                return;
            }
            TabPane pane = tab.getTabPane();
            int target = pane.getTabs().indexOf(tab);
            if (e.getX() > handle.getBoundsInLocal().getWidth() / 2) {
                target++;
            }
            int from = pane.getTabs().indexOf(dragged);
            if (from < target) {
                target--;  // removing the dragged tab shifts everything after it left
            }
            pane.getTabs().remove(dragged);
            pane.getTabs().add(Math.max(0, Math.min(target, pane.getTabs().size())), dragged);
            pane.getSelectionModel().select(dragged);
            e.setDropCompleted(true);
            e.consume();
            onReordered.run();
        });

        handle.setOnDragDone(e -> {
            handle.getStyleClass().remove("drop-target");
            dragged = null;
            e.consume();
        });
    }

    private static boolean canDropOn(Tab tab) {
        return dragged != null && dragged != tab && dragged.getTabPane() == tab.getTabPane();
    }
}
