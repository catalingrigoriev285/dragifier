package dev.dragifier.undo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * Snapshot-based undo/redo. Each undo step is a full serialized model snapshot
 * taken just before a change. Consecutive checkpoints with the same non-null
 * tag coalesce into one step (e.g. a typing burst or repeated nudges).
 */
public class UndoManager {

    private static final int MAX_DEPTH = 100;

    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    private final Supplier<String> snapshot;
    private String lastTag;

    public UndoManager(Supplier<String> snapshot) {
        this.snapshot = snapshot;
    }

    /** Call before mutating the model. A null tag always records a new step. */
    public void checkpoint(String tag) {
        if (tag != null && tag.equals(lastTag)) {
            return;
        }
        undoStack.push(snapshot.get());
        if (undoStack.size() > MAX_DEPTH) {
            undoStack.removeLast();
        }
        redoStack.clear();
        lastTag = tag;
    }

    /** Returns the snapshot to restore, or null if there is nothing to undo. */
    public String undo() {
        if (undoStack.isEmpty()) {
            return null;
        }
        redoStack.push(snapshot.get());
        lastTag = null;
        return undoStack.pop();
    }

    /** Returns the snapshot to restore, or null if there is nothing to redo. */
    public String redo() {
        if (redoStack.isEmpty()) {
            return null;
        }
        undoStack.push(snapshot.get());
        lastTag = null;
        return redoStack.pop();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        lastTag = null;
    }
}
