package dev.dragifier.ui;

import dev.dragifier.model.ComponentType;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The form design surface: renders the model's components and lets the user
 * drop new components from the palette, select (single, Ctrl+click, marquee),
 * move, resize, align, nudge, copy/paste and delete them. Smart alignment
 * guides appear while dragging when edges or centers line up.
 */
public class DesignCanvas extends Pane {

    private static final double GRID = 8;
    private static final double GUIDE_SNAP = 6;
    private static final double MIN_SIZE = 16;
    private static final double HANDLE_SIZE = 8;
    /** Matches the Primer accent used by the app theme. */
    private static final Color ACCENT = Color.web("#0969da");

    private boolean snapEnabled = true;

    private enum Dir { NW, N, NE, E, SE, S, SW, W }

    public enum AlignOp { LEFT, RIGHT, TOP, BOTTOM, CENTER_H, CENTER_V, SAME_SIZE }

    private FormModel model;
    private final Map<FormComponent, Pane> wrappers = new LinkedHashMap<>();
    private final LinkedHashSet<FormComponent> selection = new LinkedHashSet<>();
    private final Group handleGroup = new Group();
    private final Line vGuide = new Line();
    private final Line hGuide = new Line();
    private final Rectangle marquee = new Rectangle();

    private Consumer<List<FormComponent>> onSelectionChanged = sel -> {};
    private Consumer<FormComponent> onGeometryChanged = c -> {};
    private Runnable onStructureChanged = () -> {};
    private Consumer<FormComponent> onOpenEvents = c -> {};
    private Consumer<String> checkpoint = tag -> {};

    private List<FormComponent> copied = List.of();

    // move drag state
    private Point2D pressPoint;
    private final Map<FormComponent, Point2D> dragOrigins = new HashMap<>();
    private boolean groupMoved;
    private boolean moveCheckpointed;

    // resize drag state
    private double resizeStartX, resizeStartY;
    private double origX, origY, origW, origH;
    private boolean resizeCheckpointed;

    // marquee state
    private Point2D marqueeAnchor;

    public DesignCanvas() {
        setStyle("-fx-background-color: white; -fx-border-color: #9e9e9e;");
        setFocusTraversable(true);
        handleGroup.setVisible(false);
        createHandles();

        for (Line guide : List.of(vGuide, hGuide)) {
            guide.setStroke(Color.web("#f43f5e"));
            guide.getStrokeDashArray().setAll(4.0, 4.0);
            guide.setMouseTransparent(true);
            guide.setVisible(false);
        }
        marquee.setFill(ACCENT.deriveColor(0, 1, 1, 0.08));
        marquee.setStroke(ACCENT);
        marquee.setMouseTransparent(true);
        marquee.setVisible(false);

        setOnMousePressed(e -> {
            if (e.getTarget() == this) {
                requestFocus();
                marqueeAnchor = new Point2D(e.getX(), e.getY());
                if (!e.isShortcutDown()) {
                    setSelection(List.of());
                }
            }
        });
        setOnMouseDragged(e -> {
            if (marqueeAnchor == null) {
                return;
            }
            double x = Math.min(marqueeAnchor.getX(), e.getX());
            double y = Math.min(marqueeAnchor.getY(), e.getY());
            marquee.setX(x);
            marquee.setY(y);
            marquee.setWidth(Math.abs(e.getX() - marqueeAnchor.getX()));
            marquee.setHeight(Math.abs(e.getY() - marqueeAnchor.getY()));
            marquee.setVisible(true);
            marquee.toFront();
        });
        setOnMouseReleased(e -> {
            if (marqueeAnchor != null && marquee.isVisible()) {
                List<FormComponent> hit = new ArrayList<>();
                if (e.isShortcutDown()) {
                    hit.addAll(selection);
                }
                for (FormComponent c : model.getComponents()) {
                    boolean intersects = c.getX() < marquee.getX() + marquee.getWidth()
                            && c.getX() + c.getWidth() > marquee.getX()
                            && c.getY() < marquee.getY() + marquee.getHeight()
                            && c.getY() + c.getHeight() > marquee.getY();
                    if (intersects && !hit.contains(c)) {
                        hit.add(c);
                    }
                }
                setSelection(hit);
            }
            marquee.setVisible(false);
            marqueeAnchor = null;
        });
        setOnKeyPressed(this::handleKey);

        setOnDragOver(e -> {
            if (e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        setOnDragDropped(e -> {
            ComponentType type = typeFrom(e.getDragboard().hasString() ? e.getDragboard().getString() : null);
            if (type != null && model != null) {
                double x = clamp(snap(e.getX() - type.defaultWidth / 2), 0, Math.max(0, model.getWidth() - type.defaultWidth));
                double y = clamp(snap(e.getY() - type.defaultHeight / 2), 0, Math.max(0, model.getHeight() - type.defaultHeight));
                checkpoint.accept(null);
                FormComponent c = model.create(type, x, y);
                addWrapper(c);
                overlaysToFront();
                select(c);
                onStructureChanged.run();
                e.setDropCompleted(true);
            } else {
                e.setDropCompleted(false);
            }
            e.consume();
        });
    }

    public static String dragString(ComponentType type) {
        return type.name();
    }

    public static ClipboardContent dragContent(ComponentType type) {
        ClipboardContent content = new ClipboardContent();
        content.putString(dragString(type));
        return content;
    }

    private static ComponentType typeFrom(String name) {
        if (name == null) {
            return null;
        }
        try {
            return ComponentType.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public void setOnSelectionChanged(Consumer<List<FormComponent>> onSelectionChanged) { this.onSelectionChanged = onSelectionChanged; }
    public void setOnGeometryChanged(Consumer<FormComponent> onGeometryChanged) { this.onGeometryChanged = onGeometryChanged; }
    public void setOnStructureChanged(Runnable onStructureChanged) { this.onStructureChanged = onStructureChanged; }
    public void setOnOpenEvents(Consumer<FormComponent> onOpenEvents) { this.onOpenEvents = onOpenEvents; }
    public void setOnCheckpoint(Consumer<String> checkpoint) { this.checkpoint = checkpoint; }

    /** The primary (first-selected) component, or null. */
    public FormComponent getSelected() {
        return selection.isEmpty() ? null : selection.iterator().next();
    }

    /** Snapshot of the current selection, in selection order. */
    public List<FormComponent> getSelectionList() {
        return List.copyOf(selection);
    }

    public void setSnapEnabled(boolean snapEnabled) {
        this.snapEnabled = snapEnabled;
    }

    /** Rebuild after external model reordering (z-order), keeping the selection. */
    public void rebuildPreservingSelection() {
        List<FormComponent> kept = getSelectionList();
        rebuild();
        setSelection(kept);
    }

    public void setModel(FormModel model) {
        this.model = model;
        rebuild();
    }

    public void applyFormSize() {
        setPrefSize(model.getWidth(), model.getHeight());
        setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    }

    public void rebuild() {
        getChildren().clear();
        wrappers.clear();
        selection.clear();
        applyFormSize();
        for (FormComponent c : model.getComponents()) {
            addWrapper(c);
        }
        getChildren().addAll(vGuide, hGuide, marquee, handleGroup);
        setSelection(List.of());
    }

    /** Re-applies model state (geometry and visual properties) to a component's node. */
    public void refresh(FormComponent c) {
        Pane wrapper = wrappers.get(c);
        if (wrapper == null) {
            return;
        }
        Region node = (Region) wrapper.getChildren().get(0);
        Renderer.apply(node, c);
        positionWrapper(c, wrapper);
        if (c == getSelected()) {
            layoutHandles();
        }
    }

    /** Single-select (or clear with null). */
    public void select(FormComponent c) {
        setSelection(c == null ? List.of() : List.of(c));
    }

    public void setSelection(List<FormComponent> components) {
        selection.clear();
        for (FormComponent c : components) {
            if (wrappers.containsKey(c)) {
                selection.add(c);
            }
        }
        for (Map.Entry<FormComponent, Pane> entry : wrappers.entrySet()) {
            boolean isSelected = selection.contains(entry.getKey());
            entry.getValue().getStyleClass().removeAll("design-selected");
            if (isSelected) {
                entry.getValue().getStyleClass().add("design-selected");
            }
        }
        handleGroup.setVisible(selection.size() == 1);
        if (selection.size() == 1) {
            layoutHandles();
        }
        overlaysToFront();
        onSelectionChanged.accept(List.copyOf(selection));
    }

    private void toggleSelect(FormComponent c) {
        List<FormComponent> next = new ArrayList<>(selection);
        if (!next.remove(c)) {
            next.add(c);
        }
        setSelection(next);
    }

    private void overlaysToFront() {
        vGuide.toFront();
        hGuide.toFront();
        marquee.toFront();
        handleGroup.toFront();
    }

    public void align(AlignOp op) {
        if (selection.size() < 2) {
            return;
        }
        checkpoint.accept(null);
        FormComponent anchor = getSelected();
        for (FormComponent c : selection) {
            if (c == anchor) {
                continue;
            }
            switch (op) {
                case LEFT -> c.setX(anchor.getX());
                case RIGHT -> c.setX(anchor.getX() + anchor.getWidth() - c.getWidth());
                case TOP -> c.setY(anchor.getY());
                case BOTTOM -> c.setY(anchor.getY() + anchor.getHeight() - c.getHeight());
                case CENTER_H -> c.setX(anchor.getX() + (anchor.getWidth() - c.getWidth()) / 2);
                case CENTER_V -> c.setY(anchor.getY() + (anchor.getHeight() - c.getHeight()) / 2);
                case SAME_SIZE -> {
                    c.setWidth(anchor.getWidth());
                    c.setHeight(anchor.getHeight());
                }
            }
            refresh(c);
        }
        onGeometryChanged.accept(anchor);
    }

    private void addWrapper(FormComponent c) {
        Pane wrapper = new Pane();
        Region node = Renderer.createNode(c);
        node.setMouseTransparent(true);
        wrapper.getChildren().add(node);
        positionWrapper(c, wrapper);

        wrapper.setOnMousePressed(e -> {
            requestFocus();
            if (e.isShortcutDown()) {
                toggleSelect(c);
            } else if (!selection.contains(c)) {
                select(c);
            }
            pressPoint = sceneToLocal(e.getSceneX(), e.getSceneY());
            dragOrigins.clear();
            for (FormComponent s : selection) {
                dragOrigins.put(s, new Point2D(s.getX(), s.getY()));
            }
            groupMoved = false;
            moveCheckpointed = false;
            if (e.getClickCount() == 2) {
                onOpenEvents.accept(c);
            }
            e.consume();
        });
        wrapper.setOnMouseDragged(e -> {
            if (pressPoint == null || !selection.contains(c)) {
                return;
            }
            if (!moveCheckpointed) {
                checkpoint.accept(null);
                moveCheckpointed = true;
            }
            groupMoved = true;
            Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
            moveSelection(c, p.getX() - pressPoint.getX(), p.getY() - pressPoint.getY());
            e.consume();
        });
        wrapper.setOnMouseReleased(e -> {
            vGuide.setVisible(false);
            hGuide.setVisible(false);
            if (!groupMoved && !e.isShortcutDown() && selection.size() > 1) {
                select(c);
            }
            e.consume();
        });

        wrappers.put(c, wrapper);
        getChildren().add(wrapper);
    }

    /** Moves the whole selection by a delta anchored on the dragged component, with guide/grid snapping. */
    private void moveSelection(FormComponent dragged, double dx, double dy) {
        Point2D origin = dragOrigins.get(dragged);
        if (origin == null) {
            return;
        }
        double rawX = origin.getX() + dx;
        double rawY = origin.getY() + dy;

        Double guideX = findGuide(rawX, dragged.getWidth(), true);
        Double guideY = findGuide(rawY, dragged.getHeight(), false);
        double nx = guideX != null ? guideX : snap(rawX);
        double ny = guideY != null ? guideY : snap(rawY);

        // clamp the shared delta so every selected component stays inside the form
        double adx = nx - origin.getX();
        double ady = ny - origin.getY();
        for (FormComponent s : selection) {
            Point2D so = dragOrigins.get(s);
            if (so == null) {
                continue;
            }
            adx = clamp(adx, -so.getX(), Math.max(-so.getX(), model.getWidth() - s.getWidth() - so.getX()));
            ady = clamp(ady, -so.getY(), Math.max(-so.getY(), model.getHeight() - s.getHeight() - so.getY()));
        }
        for (FormComponent s : selection) {
            Point2D so = dragOrigins.get(s);
            if (so == null) {
                continue;
            }
            s.setX(so.getX() + adx);
            s.setY(so.getY() + ady);
            positionWrapper(s, wrappers.get(s));
        }
        if (selection.size() == 1) {
            layoutHandles();
        }
        showGuides(dragged, guideX != null && adx == nx - origin.getX(),
                guideY != null && ady == ny - origin.getY());
        onGeometryChanged.accept(dragged);
    }

    /**
     * Finds the nearest alignment guide for the dragged component's edge/center
     * positions. Returns the snapped coordinate, or null when no guide is close.
     */
    private Double findGuide(double raw, double size, boolean horizontalAxis) {
        List<Double> candidates = new ArrayList<>();
        for (FormComponent other : model.getComponents()) {
            if (selection.contains(other)) {
                continue;
            }
            double pos = horizontalAxis ? other.getX() : other.getY();
            double extent = horizontalAxis ? other.getWidth() : other.getHeight();
            candidates.add(pos);
            candidates.add(pos + extent / 2);
            candidates.add(pos + extent);
        }
        candidates.add((horizontalAxis ? model.getWidth() : model.getHeight()) / 2);

        double[] offsets = {0, size / 2, size};
        Double best = null;
        double bestDist = GUIDE_SNAP + 1;
        for (double cand : candidates) {
            for (double off : offsets) {
                double dist = Math.abs(cand - (raw + off));
                if (dist < bestDist) {
                    bestDist = dist;
                    best = cand - off;
                }
            }
        }
        return best;
    }

    private void showGuides(FormComponent dragged, boolean showV, boolean showH) {
        vGuide.setVisible(showV);
        hGuide.setVisible(showH);
        if (showV) {
            // the guide line sits at whichever dragged edge/center matched
            double x = nearestEdge(dragged.getX(), dragged.getWidth(), true);
            vGuide.setStartX(x);
            vGuide.setEndX(x);
            vGuide.setStartY(0);
            vGuide.setEndY(model.getHeight());
        }
        if (showH) {
            double y = nearestEdge(dragged.getY(), dragged.getHeight(), false);
            hGuide.setStartY(y);
            hGuide.setEndY(y);
            hGuide.setStartX(0);
            hGuide.setEndX(model.getWidth());
        }
        overlaysToFront();
    }

    /** Which of the dragged component's edges/center actually aligned with a candidate. */
    private double nearestEdge(double pos, double size, boolean horizontalAxis) {
        List<Double> candidates = new ArrayList<>();
        for (FormComponent other : model.getComponents()) {
            if (selection.contains(other)) {
                continue;
            }
            double p = horizontalAxis ? other.getX() : other.getY();
            double extent = horizontalAxis ? other.getWidth() : other.getHeight();
            candidates.add(p);
            candidates.add(p + extent / 2);
            candidates.add(p + extent);
        }
        candidates.add((horizontalAxis ? model.getWidth() : model.getHeight()) / 2);
        double bestEdge = pos;
        double bestDist = Double.MAX_VALUE;
        for (double cand : candidates) {
            for (double edge : new double[]{pos, pos + size / 2, pos + size}) {
                double dist = Math.abs(cand - edge);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestEdge = cand;
                }
            }
        }
        return bestEdge;
    }

    private void positionWrapper(FormComponent c, Pane wrapper) {
        wrapper.setLayoutX(c.getX());
        wrapper.setLayoutY(c.getY());
        wrapper.setPrefSize(c.getWidth(), c.getHeight());
        wrapper.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        wrapper.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    }

    private void createHandles() {
        for (Dir dir : Dir.values()) {
            Rectangle handle = new Rectangle(HANDLE_SIZE, HANDLE_SIZE, ACCENT);
            handle.setStroke(Color.WHITE);
            handle.setStrokeWidth(1);
            handle.setCursor(cursorFor(dir));
            handle.setOnMousePressed(e -> {
                FormComponent sel = getSelected();
                if (sel == null) {
                    return;
                }
                Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
                resizeStartX = p.getX();
                resizeStartY = p.getY();
                resizeCheckpointed = false;
                origX = sel.getX();
                origY = sel.getY();
                origW = sel.getWidth();
                origH = sel.getHeight();
                e.consume();
            });
            handle.setOnMouseDragged(e -> {
                if (selection.size() != 1) {
                    return;
                }
                if (!resizeCheckpointed) {
                    checkpoint.accept(null);
                    resizeCheckpointed = true;
                }
                Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
                resizeTo(dir, p.getX() - resizeStartX, p.getY() - resizeStartY);
                e.consume();
            });
            handleGroup.getChildren().add(handle);
        }
    }

    private void resizeTo(Dir dir, double dx, double dy) {
        FormComponent sel = getSelected();
        double nx = origX, ny = origY, nw = origW, nh = origH;
        boolean west = dir == Dir.NW || dir == Dir.W || dir == Dir.SW;
        boolean east = dir == Dir.NE || dir == Dir.E || dir == Dir.SE;
        boolean north = dir == Dir.NW || dir == Dir.N || dir == Dir.NE;
        boolean south = dir == Dir.SW || dir == Dir.S || dir == Dir.SE;

        if (west) {
            nx = snap(origX + dx);
            nx = clamp(nx, 0, origX + origW - MIN_SIZE);
            nw = origX + origW - nx;
        } else if (east) {
            nw = Math.max(MIN_SIZE, snap(origW + dx));
            nw = Math.min(nw, model.getWidth() - origX);
        }
        if (north) {
            ny = snap(origY + dy);
            ny = clamp(ny, 0, origY + origH - MIN_SIZE);
            nh = origY + origH - ny;
        } else if (south) {
            nh = Math.max(MIN_SIZE, snap(origH + dy));
            nh = Math.min(nh, model.getHeight() - origY);
        }

        sel.setX(nx);
        sel.setY(ny);
        sel.setWidth(nw);
        sel.setHeight(nh);
        refresh(sel);
        onGeometryChanged.accept(sel);
    }

    private void layoutHandles() {
        FormComponent sel = getSelected();
        if (sel == null) {
            return;
        }
        double x = sel.getX(), y = sel.getY();
        double w = sel.getWidth(), h = sel.getHeight();
        double half = HANDLE_SIZE / 2;
        int i = 0;
        for (Dir dir : Dir.values()) {
            Rectangle handle = (Rectangle) handleGroup.getChildren().get(i++);
            double hx = switch (dir) {
                case NW, W, SW -> x;
                case N, S -> x + w / 2;
                case NE, E, SE -> x + w;
            };
            double hy = switch (dir) {
                case NW, N, NE -> y;
                case W, E -> y + h / 2;
                case SW, S, SE -> y + h;
            };
            handle.setX(hx - half);
            handle.setY(hy - half);
        }
    }

    public void copySelected() {
        if (!selection.isEmpty()) {
            copied = List.copyOf(selection);
        }
    }

    public void paste() {
        if (copied.isEmpty() || model == null) {
            return;
        }
        checkpoint.accept(null);
        List<FormComponent> pasted = new ArrayList<>();
        for (FormComponent src : copied) {
            FormComponent c = model.duplicate(src);
            addWrapper(c);
            pasted.add(c);
        }
        overlaysToFront();
        setSelection(pasted);
        onStructureChanged.run();
    }

    public void duplicateSelected() {
        if (selection.isEmpty()) {
            return;
        }
        copied = List.copyOf(selection);
        paste();
    }

    public void deleteSelected() {
        if (selection.isEmpty()) {
            return;
        }
        checkpoint.accept(null);
        for (FormComponent c : List.copyOf(selection)) {
            model.remove(c);
            getChildren().remove(wrappers.remove(c));
        }
        setSelection(List.of());
        onStructureChanged.run();
    }

    public void selectAll() {
        setSelection(List.copyOf(model.getComponents()));
    }

    private void handleKey(KeyEvent e) {
        if (e.isShortcutDown()) {
            switch (e.getCode()) {
                case C -> copySelected();
                case V -> paste();
                case D -> duplicateSelected();
                case A -> selectAll();
                default -> { return; }
            }
            e.consume();
            return;
        }
        if (selection.isEmpty()) {
            return;
        }
        double step = e.isShiftDown() ? GRID : 1;
        switch (e.getCode()) {
            case DELETE, BACK_SPACE -> deleteSelected();
            case LEFT -> nudge(-step, 0);
            case RIGHT -> nudge(step, 0);
            case UP -> nudge(0, -step);
            case DOWN -> nudge(0, step);
            default -> { return; }
        }
        e.consume();
    }

    private void nudge(double dx, double dy) {
        FormComponent primary = getSelected();
        checkpoint.accept("nudge:" + primary.getId());
        // clamp the shared delta so the whole selection stays inside the form
        for (FormComponent s : selection) {
            dx = clamp(dx, -s.getX(), Math.max(-s.getX(), model.getWidth() - s.getWidth() - s.getX()));
            dy = clamp(dy, -s.getY(), Math.max(-s.getY(), model.getHeight() - s.getHeight() - s.getY()));
        }
        for (FormComponent s : selection) {
            s.setX(s.getX() + dx);
            s.setY(s.getY() + dy);
            positionWrapper(s, wrappers.get(s));
        }
        if (selection.size() == 1) {
            layoutHandles();
        }
        onGeometryChanged.accept(primary);
    }

    private Cursor cursorFor(Dir dir) {
        return switch (dir) {
            case NW -> Cursor.NW_RESIZE;
            case N -> Cursor.N_RESIZE;
            case NE -> Cursor.NE_RESIZE;
            case E -> Cursor.E_RESIZE;
            case SE -> Cursor.SE_RESIZE;
            case S -> Cursor.S_RESIZE;
            case SW -> Cursor.SW_RESIZE;
            case W -> Cursor.W_RESIZE;
        };
    }

    private double snap(double v) {
        return snapEnabled ? Math.round(v / GRID) * GRID : v;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
