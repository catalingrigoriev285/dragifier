package dev.dragifier.ui;

import dev.dragifier.model.ComponentType;
import dev.dragifier.model.ContainerGeometry;
import dev.dragifier.model.Dock;
import dev.dragifier.model.DockLayout;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The form design surface: renders the model's components and lets the user
 * drop new components from the palette (onto the form or into a container),
 * select (single, Ctrl+click, marquee), move, resize, align, nudge, copy/paste
 * and delete them. Dragging a component over a container re-parents it. Smart
 * alignment guides appear while dragging when sibling edges or centers line up.
 *
 * <p>Every component gets a wrapper {@code Pane} holding its live node; the
 * wrapper of a nested component lives inside its parent's content pane, so
 * model coordinates are always relative to the parent's content area.
 */
public class DesignCanvas extends Pane {

    private static final double GRID = 8;
    private static final double GUIDE_SNAP = 6;
    private static final double MIN_SIZE = 16;
    private static final double MIN_FORM_WIDTH = 120;
    private static final double MIN_FORM_HEIGHT = 80;
    private static final double HANDLE_SIZE = 8;
    /** Matches the Primer accent used by the app theme. */
    private static final Color ACCENT = Color.web("#0969da");
    private static final String DROP_TARGET_CLASS = "design-drop-target";

    private boolean snapEnabled = true;

    private enum Dir { NW, N, NE, E, SE, S, SW, W }

    public enum AlignOp { LEFT, RIGHT, TOP, BOTTOM, CENTER_H, CENTER_V, SAME_SIZE }

    /** A container content area under the pointer (null container = the form itself). */
    private record DropTarget(FormComponent container, int slot, Pane pane) {}

    private FormModel model;
    private final Map<FormComponent, Pane> wrappers = new LinkedHashMap<>();
    private final LinkedHashSet<FormComponent> selection = new LinkedHashSet<>();
    private final Group handleGroup = new Group();
    /** Grips on the form's right/bottom edges for resizing the form itself. */
    private final Group formHandleGroup = new Group();
    private final Line vGuide = new Line();
    private final Line hGuide = new Line();
    private final Rectangle marquee = new Rectangle();
    /** Where a child of an auto-layout container would land (insertion line, grid cell or dock region). */
    private final Rectangle slotIndicator = new Rectangle();
    private Pane dropHighlight;
    /** The one context menu allowed open at a time (right-clicks must not stack menus). */
    private javafx.scene.control.ContextMenu openMenu;

    private Consumer<List<FormComponent>> onSelectionChanged = sel -> {};
    private Consumer<FormComponent> onGeometryChanged = c -> {};
    private Runnable onStructureChanged = () -> {};
    private Consumer<FormComponent> onOpenEvents = c -> {};
    private Consumer<String> checkpoint = tag -> {};
    private Consumer<Boolean> onZOrderRequest = front -> {};
    private Runnable onRenameRequest = () -> {};
    private Runnable onFormResized = () -> {};

    // form resize drag state
    private double formStartX, formStartY, formOrigW, formOrigH;
    private boolean formCheckpointed;

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
        createFormHandles();
        // top-level docks follow the form size
        widthProperty().addListener((obs, was, is) -> applyDocksIn(null));
        heightProperty().addListener((obs, was, is) -> applyDocksIn(null));

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
        slotIndicator.setFill(ACCENT.deriveColor(0, 1, 1, 0.25));
        slotIndicator.setStroke(ACCENT);
        slotIndicator.setMouseTransparent(true);
        slotIndicator.setVisible(false);

        setOnMousePressed(e -> {
            hideMenu();
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
                // marquee works on the form level; nested children move with their container
                for (FormComponent c : model.childrenOf(null)) {
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
        setOnContextMenuRequested(e -> {
            if (e.getTarget() == this) {
                showMenu(buildCanvasMenu(), this, e.getScreenX(), e.getScreenY());
            }
            e.consume();
        });

        setOnDragOver(e -> {
            if (e.getDragboard().hasString() && typeFrom(e.getDragboard().getString()) != null) {
                e.acceptTransferModes(TransferMode.COPY);
                DropTarget target = findDropTarget(e.getSceneX(), e.getSceneY(), List.of());
                highlightDropTarget(target == null ? null : target.pane());
                showSlotIndicator(target, target == null ? "" : slotFor(target, e.getSceneX(), e.getSceneY()));
            }
            e.consume();
        });
        setOnDragExited(e -> {
            highlightDropTarget(null);
            slotIndicator.setVisible(false);
        });
        setOnDragDropped(e -> {
            highlightDropTarget(null);
            slotIndicator.setVisible(false);
            ComponentType type = typeFrom(e.getDragboard().hasString() ? e.getDragboard().getString() : null);
            if (type != null && model != null) {
                DropTarget target = findDropTarget(e.getSceneX(), e.getSceneY(), List.of());
                Pane pane = target == null ? this : target.pane();
                Point2D local = pane.sceneToLocal(e.getSceneX(), e.getSceneY());
                double maxW = target == null ? model.getWidth() : pane.getWidth();
                double maxH = target == null ? model.getHeight() : pane.getHeight();
                double x = clamp(snap(local.getX() - type.defaultWidth / 2), 0, Math.max(0, maxW - type.defaultWidth));
                double y = clamp(snap(local.getY() - type.defaultHeight / 2), 0, Math.max(0, maxH - type.defaultHeight));
                String slot = target == null ? "" : slotFor(target, e.getSceneX(), e.getSceneY());
                checkpoint.accept(null);
                FormComponent c = model.create(type, x, y,
                        target == null ? null : target.container(), storedSlot(target, slot));
                if (target != null && target.container().getType().kind == ComponentType.ContainerKind.STACK) {
                    insertAt(c, target.container(), Integer.parseInt(slot));
                }
                rebuild();
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
    public void setOnZOrderRequest(Consumer<Boolean> onZOrderRequest) { this.onZOrderRequest = onZOrderRequest; }
    public void setOnRenameRequest(Runnable onRenameRequest) { this.onRenameRequest = onRenameRequest; }
    /** Fired while the form is resized by dragging its edge grips (model width/height already updated). */
    public void setOnFormResized(Runnable onFormResized) { this.onFormResized = onFormResized; }

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

    /** Rebuild after external model changes (z-order, re-parenting), keeping the selection. */
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
        layoutFormHandles();
        applyDocksIn(null);
    }

    public void rebuild() {
        getChildren().clear();
        wrappers.clear();
        selection.clear();
        dropHighlight = null;
        applyFormSize();
        for (FormComponent c : model.childrenOf(null)) {
            addWrapper(c, this);
        }
        getChildren().addAll(vGuide, hGuide, marquee, slotIndicator, formHandleGroup, handleGroup);
        applyAllDocks();
        setSelection(List.of());
    }

    /**
     * Re-applies model state (geometry and visual properties) to a component's
     * node. Rebuilds the canvas when the component moved to another parent/slot
     * or a container's content areas were recreated (e.g. tab count changed).
     */
    public void refresh(FormComponent c) {
        Pane wrapper = wrappers.get(c);
        if (wrapper == null) {
            return;
        }
        Region node = innerNode(c);
        if (Renderer.needsNewNode(node, c)) {
            rebuildPreservingSelection();
            return;
        }
        List<Pane> panesBefore = Renderer.contentPanes(node);
        Renderer.apply(node, c);
        styleContainerNode(node, c);
        positionWrapper(c, wrapper);
        boolean rehost = wrapper.getParent() != expectedHost(c);
        boolean panesChanged = c.getType().isContainer() && !panesBefore.equals(Renderer.contentPanes(node));
        // auto-layout containers re-place their children from scratch (cell/region/order may have changed)
        if (rehost || panesChanged || c.getType().kind.isAutoLayout()) {
            rebuildPreservingSelection();
            return;
        }
        // its own dock or a sibling's may have changed; containers re-dock their children on size change
        applyDocksIn(model.parentOf(c));
        if (c == getSelected()) {
            handleGroup.setVisible(selection.size() == 1 && resizable(c));
            layoutHandles();
        }
    }

    /** Locked or docked components are not moved by drags/nudges. */
    private static boolean immovable(FormComponent c) {
        return c.isLocked() || c.getDock() != Dock.NONE;
    }

    private static boolean resizable(FormComponent c) {
        return !c.isLocked() && c.getDock() == Dock.NONE;
    }

    /** Re-runs docking for one parent's content areas (null = the form) and moves the docked wrappers. */
    private void applyDocksIn(FormComponent parent) {
        if (model == null || (parent != null && (!parent.getType().kind.isAbsolute() || !wrappers.containsKey(parent)))) {
            return;
        }
        List<Pane> panes = parent == null ? List.of((Pane) this) : Renderer.contentPanes(innerNode(parent));
        List<FormComponent> children = model.childrenOf(parent);
        for (int i = 0; i < panes.size(); i++) {
            List<FormComponent> group = new ArrayList<>();
            boolean anyDocked = false;
            for (FormComponent child : children) {
                if (parent == null || ContainerGeometry.slotIndex(child, parent) == i) {
                    group.add(child);
                    anyDocked |= child.getDock() != Dock.NONE;
                }
            }
            if (!anyDocked) {
                continue;
            }
            Pane pane = panes.get(i);
            double w = parent == null ? model.getWidth()
                    : pane.getWidth() > 0 ? pane.getWidth() : ContainerGeometry.contentWidth(parent, i);
            double h = parent == null ? model.getHeight()
                    : pane.getHeight() > 0 ? pane.getHeight() : ContainerGeometry.contentHeight(parent, i);
            if (DockLayout.apply(group, w, h)) {
                for (FormComponent child : group) {
                    Pane wrapper = wrappers.get(child);
                    if (wrapper != null && child.getDock() != Dock.NONE) {
                        positionWrapper(child, wrapper);
                        Renderer.apply(innerNode(child), child);
                        styleContainerNode(innerNode(child), child);
                    }
                }
                if (selection.size() == 1) {
                    layoutHandles();
                }
            }
        }
    }

    private void applyAllDocks() {
        applyDocksIn(null);
        for (FormComponent c : List.copyOf(wrappers.keySet())) {
            if (c.getType().isContainer()) {
                applyDocksIn(c);
            }
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
        handleGroup.setVisible(selection.size() == 1 && resizable(getSelected()));
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
        slotIndicator.toFront();
        formHandleGroup.toFront();
        handleGroup.toFront();
    }

    /** True when the component's position is decided by an auto-layout parent (StackPanel/Grid/DockPanel). */
    private boolean isAutoLaid(FormComponent c) {
        FormComponent parent = model.parentOf(c);
        return parent != null && parent.getType().kind.isAutoLayout();
    }

    /** A wrapper whose single child always fills it; used for Grid/DockPanel children that stretch with their cell. */
    private static final class FillPane extends Pane {
        @Override
        protected void layoutChildren() {
            for (Node n : getChildren()) {
                n.resizeRelocate(0, 0, getWidth(), getHeight());
            }
        }
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

    // ------------------------------------------------------------- wrappers

    private void addWrapper(FormComponent c, Pane host) {
        FormComponent parent = model.parentOf(c);
        ComponentType.ContainerKind parentKind = parent == null ? ComponentType.ContainerKind.NONE : parent.getType().kind;
        boolean fills = parentKind == ComponentType.ContainerKind.GRID || parentKind == ComponentType.ContainerKind.DOCK;
        Pane wrapper = fills ? new FillPane() : new Pane();
        Region node = Renderer.createNode(c);
        if (fills) {
            node.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }
        boolean container = c.getType().isContainer();
        if (container) {
            // a mouse-transparent container would hide its children from the mouse too;
            // presses on empty content bubble up to this wrapper instead
            styleContainerNode(node, c);
            if (node instanceof SplitPane split) {
                node.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> commitDividers(c, split));
            }
        } else {
            node.setMouseTransparent(true);
        }
        wrapper.getChildren().add(node);
        positionWrapper(c, wrapper);

        wrapper.setOnMousePressed(e -> {
            hideMenu();
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
        wrapper.setOnContextMenuRequested(e -> {
            if (!selection.contains(c)) {
                select(c);
            }
            showMenu(buildComponentMenu(), wrapper, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        wrapper.setOnMouseDragged(e -> {
            if (pressPoint == null || !selection.contains(c) || immovable(c)) {
                return;
            }
            if (!moveCheckpointed) {
                checkpoint.accept(null);
                moveCheckpointed = true;
            }
            groupMoved = true;
            if (!isAutoLaid(c)) {
                Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
                moveSelection(c, p.getX() - pressPoint.getX(), p.getY() - pressPoint.getY());
            }
            DropTarget target = findDropTarget(e.getSceneX(), e.getSceneY(), selection);
            highlightDropTarget(target == null || target.container() == model.parentOf(c) ? null : target.pane());
            // inside an auto-layout container the drag picks a new slot instead of moving pixels
            showSlotIndicator(target, target == null ? "" : slotFor(target, e.getSceneX(), e.getSceneY()));
            e.consume();
        });
        wrapper.setOnMouseReleased(e -> {
            vGuide.setVisible(false);
            hGuide.setVisible(false);
            slotIndicator.setVisible(false);
            highlightDropTarget(null);
            if (groupMoved) {
                finishMove(c, e.getSceneX(), e.getSceneY());
            } else if (!e.isShortcutDown() && selection.size() > 1) {
                select(c);
            }
            e.consume();
        });

        wrappers.put(c, wrapper);
        if (parentKind.isAutoLayout()) {
            Renderer.placeChild(innerNode(parent), parent, wrapper, c);
        } else {
            host.getChildren().add(wrapper);
        }

        if (container) {
            List<Pane> panes = Renderer.contentPanes(node);
            for (Pane pane : panes) {
                // unfilled content areas are not pickable by default; make clicks there
                // reach this container instead of falling through to the canvas
                pane.setPickOnBounds(true);
                // docked children follow the content area's live size
                pane.widthProperty().addListener((obs, was, is) -> applyDocksIn(c));
                pane.heightProperty().addListener((obs, was, is) -> applyDocksIn(c));
            }
            for (FormComponent child : model.childrenOf(c)) {
                int slot = ContainerGeometry.slotIndex(child, c);
                if (slot < panes.size()) {
                    addWrapper(child, panes.get(slot));
                }
            }
            updateScrollContent(c);
        }
    }

    /** Design-time tweaks for container nodes: keep children clickable even when "disabled". */
    private void styleContainerNode(Region node, FormComponent c) {
        if (!c.getType().isContainer()) {
            return;
        }
        node.setDisable(false);
        node.setOpacity(c.isDisabled() ? 0.5 : 1);
        node.setFocusTraversable(false);
        if (node instanceof javafx.scene.layout.GridPane grid) {
            grid.setGridLinesVisible(true); // design-time only; generated code leaves them off
        }
    }

    private Region innerNode(FormComponent c) {
        return (Region) wrappers.get(c).getChildren().get(0);
    }

    /** The pane a component's wrapper is (or should be) hosted in: a parent's content pane or the canvas. */
    private Pane expectedHost(FormComponent c) {
        FormComponent parent = model.parentOf(c);
        if (parent == null || !wrappers.containsKey(parent)) {
            return this;
        }
        List<Pane> panes = Renderer.contentPanes(innerNode(parent));
        int slot = ContainerGeometry.slotIndex(c, parent);
        return slot < panes.size() ? panes.get(slot) : this;
    }

    /** The pane currently hosting the component's wrapper. */
    private Pane hostOf(FormComponent c) {
        Pane wrapper = wrappers.get(c);
        return wrapper == null || !(wrapper.getParent() instanceof Pane p) ? this : p;
    }

    /** Canvas-space origin of the pane hosting {@code c}. */
    private Point2D hostOrigin(FormComponent c) {
        Pane host = hostOf(c);
        return host == this ? Point2D.ZERO : sceneToLocal(host.localToScene(0, 0));
    }

    /** Canvas-space origin of the component itself. */
    private Point2D canvasOrigin(FormComponent c) {
        Pane wrapper = wrappers.get(c);
        return wrapper == null ? new Point2D(c.getX(), c.getY()) : sceneToLocal(wrapper.localToScene(0, 0));
    }

    private double hostWidth(FormComponent c) {
        Pane host = hostOf(c);
        if (host == this) {
            return model.getWidth();
        }
        if (host.getWidth() > 0) {
            return host.getWidth();
        }
        FormComponent parent = model.parentOf(c);
        return parent == null ? model.getWidth() : parent.getWidth();
    }

    private double hostHeight(FormComponent c) {
        Pane host = hostOf(c);
        if (host == this) {
            return model.getHeight();
        }
        if (host.getHeight() > 0) {
            return host.getHeight();
        }
        FormComponent parent = model.parentOf(c);
        return parent == null ? model.getHeight() : parent.getHeight();
    }

    /** A ScrollView's content grows to hold its children so they can be scrolled to. */
    private void updateScrollContent(FormComponent container) {
        if (container.getType().kind != ComponentType.ContainerKind.SCROLL || !wrappers.containsKey(container)) {
            return;
        }
        List<Pane> panes = Renderer.contentPanes(innerNode(container));
        if (panes.isEmpty()) {
            return;
        }
        double w = ContainerGeometry.contentWidth(container, 0);
        double h = ContainerGeometry.contentHeight(container, 0);
        for (FormComponent child : model.childrenOf(container)) {
            w = Math.max(w, child.getX() + child.getWidth());
            h = Math.max(h, child.getY() + child.getHeight());
        }
        panes.get(0).setPrefSize(w, h);
    }

    private void commitDividers(FormComponent c, SplitPane split) {
        StringBuilder sb = new StringBuilder();
        for (double pos : split.getDividerPositions()) {
            if (!sb.isEmpty()) {
                sb.append(",");
            }
            sb.append(Math.round(pos * 1000) / 1000.0);
        }
        String value = sb.toString();
        if (!value.equals(c.getDividers())) {
            checkpoint.accept("dividers:" + c.getId());
            c.setDividers(value);
            onGeometryChanged.accept(c);
        }
    }

    // ------------------------------------------------------------- dropping

    /** True when the node is attached to a scene and none of its ancestors is hidden. */
    private static boolean isShowing(Node node) {
        if (node.getScene() == null) {
            return false;
        }
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The deepest container content area under the given scene point, ignoring
     * {@code excluded} components and anything nested inside them. Null means
     * the form itself.
     */
    private DropTarget findDropTarget(double sceneX, double sceneY, Collection<FormComponent> excluded) {
        DropTarget best = null;
        int bestDepth = -1;
        for (FormComponent c : wrappers.keySet()) {
            if (!c.getType().isContainer() || excluded.contains(c)) {
                continue;
            }
            boolean insideExcluded = false;
            for (FormComponent x : excluded) {
                if (model.isAncestor(x, c)) {
                    insideExcluded = true;
                    break;
                }
            }
            if (insideExcluded) {
                continue;
            }
            List<Pane> panes = Renderer.contentPanes(innerNode(c));
            for (int i = 0; i < panes.size(); i++) {
                Pane pane = panes.get(i);
                if (!isShowing(pane)) {
                    continue;
                }
                Point2D local = pane.sceneToLocal(sceneX, sceneY);
                if (local.getX() >= 0 && local.getY() >= 0
                        && local.getX() <= pane.getWidth() && local.getY() <= pane.getHeight()) {
                    int depth = model.depth(c);
                    if (depth > bestDepth) {
                        bestDepth = depth;
                        best = new DropTarget(c, i, pane);
                    }
                }
            }
        }
        return best;
    }

    private void highlightDropTarget(Pane pane) {
        if (pane == dropHighlight) {
            return;
        }
        if (dropHighlight != null) {
            dropHighlight.getStyleClass().remove(DROP_TARGET_CLASS);
        }
        dropHighlight = pane;
        if (dropHighlight != null) {
            dropHighlight.getStyleClass().add(DROP_TARGET_CLASS);
        }
    }

    /**
     * The placement string for a drop at the given scene point: tab/pane index,
     * StackPanel insertion index (among siblings not being moved), Grid "col,row"
     * or DockPanel region. Empty for single-area containers.
     */
    private String slotFor(DropTarget target, double sceneX, double sceneY) {
        FormComponent container = target.container();
        Pane pane = target.pane();
        Point2D p = pane.sceneToLocal(sceneX, sceneY);
        return switch (container.getType().kind) {
            case TABS, SPLIT -> String.valueOf(target.slot());
            case STACK -> {
                boolean horizontal = "HORIZONTAL".equals(container.getOrientation());
                int index = 0;
                for (FormComponent sib : model.childrenOf(container)) {
                    Pane w = wrappers.get(sib);
                    if (selection.contains(sib) || w == null) {
                        continue;
                    }
                    double mid = horizontal ? w.getLayoutX() + w.getWidth() / 2 : w.getLayoutY() + w.getHeight() / 2;
                    if ((horizontal ? p.getX() : p.getY()) > mid) {
                        index++;
                    }
                }
                yield String.valueOf(index);
            }
            case GRID -> {
                int cols = ContainerGeometry.gridColumns(container);
                int rows = ContainerGeometry.gridRows(container);
                int col = (int) clamp(Math.floor(p.getX() / Math.max(1, pane.getWidth() / cols)), 0, cols - 1);
                int row = (int) clamp(Math.floor(p.getY() / Math.max(1, pane.getHeight() / rows)), 0, rows - 1);
                yield col + "," + row;
            }
            case DOCK -> {
                double w = pane.getWidth(), h = pane.getHeight();
                if (p.getY() < h * 0.25) {
                    yield "TOP";
                } else if (p.getY() > h * 0.75) {
                    yield "BOTTOM";
                } else if (p.getX() < w * 0.25) {
                    yield "LEFT";
                } else if (p.getX() > w * 0.75) {
                    yield "RIGHT";
                }
                yield "CENTER";
            }
            default -> "";
        };
    }

    /** StackPanel order lives in sibling order, not in the slot string. */
    private static String storedSlot(DropTarget target, String slot) {
        return target != null && target.container().getType().kind == ComponentType.ContainerKind.STACK ? "" : slot;
    }

    /** Moves {@code c} to {@code index} among the children of {@code container}. */
    private void insertAt(FormComponent c, FormComponent container, int index) {
        List<FormComponent> siblings = model.childrenOf(container);
        siblings.remove(c);
        siblings.add(Math.max(0, Math.min(siblings.size(), index)), c);
        model.reorderSiblings(siblings);
    }

    /** The component's current placement string in {@code parent}, comparable with {@link #slotFor}. */
    private String currentSlot(FormComponent c, FormComponent parent) {
        if (parent == null) {
            return "";
        }
        return switch (parent.getType().kind) {
            case TABS, SPLIT -> String.valueOf(ContainerGeometry.slotIndex(c, parent));
            case STACK -> {
                int index = 0;
                for (FormComponent sib : model.childrenOf(parent)) {
                    if (sib == c) {
                        break;
                    }
                    if (!selection.contains(sib)) {
                        index++;
                    }
                }
                yield String.valueOf(index);
            }
            case GRID -> {
                int[] cell = ContainerGeometry.gridCell(c, parent);
                yield cell[0] + "," + cell[1];
            }
            case DOCK -> ContainerGeometry.dockRegion(c);
            default -> "";
        };
    }

    /** Paints where a dragged/dropped child would land inside an auto-layout container. */
    private void showSlotIndicator(DropTarget target, String slot) {
        if (target == null || !target.container().getType().kind.isAutoLayout() || slot.isEmpty()) {
            slotIndicator.setVisible(false);
            return;
        }
        FormComponent container = target.container();
        Pane pane = target.pane();
        Point2D origin = sceneToLocal(pane.localToScene(0, 0));
        double w = pane.getWidth(), h = pane.getHeight();
        double x = 0, y = 0, rw = w, rh = h;
        switch (container.getType().kind) {
            case STACK -> {
                boolean horizontal = "HORIZONTAL".equals(container.getOrientation());
                int index = Integer.parseInt(slot);
                double pos = 0;
                int i = 0;
                for (FormComponent sib : model.childrenOf(container)) {
                    Pane sw = wrappers.get(sib);
                    if (selection.contains(sib) || sw == null) {
                        continue;
                    }
                    if (i == index) {
                        pos = horizontal ? sw.getLayoutX() : sw.getLayoutY();
                        break;
                    }
                    pos = horizontal ? sw.getLayoutX() + sw.getWidth() : sw.getLayoutY() + sw.getHeight();
                    i++;
                }
                if (horizontal) {
                    x = pos - 1;
                    rw = 3;
                } else {
                    y = pos - 1;
                    rh = 3;
                }
            }
            case GRID -> {
                String[] parts = slot.split(",");
                int cols = ContainerGeometry.gridColumns(container);
                int rows = ContainerGeometry.gridRows(container);
                double cw = w / cols, ch = h / rows;
                x = Integer.parseInt(parts[0]) * cw;
                y = Integer.parseInt(parts[1]) * ch;
                rw = cw;
                rh = ch;
            }
            case DOCK -> {
                switch (slot) {
                    case "TOP" -> rh = h * 0.25;
                    case "BOTTOM" -> { y = h * 0.75; rh = h * 0.25; }
                    case "LEFT" -> { y = h * 0.25; rw = w * 0.25; rh = h * 0.5; }
                    case "RIGHT" -> { x = w * 0.75; y = h * 0.25; rw = w * 0.25; rh = h * 0.5; }
                    default -> { x = w * 0.25; y = h * 0.25; rw = w * 0.5; rh = h * 0.5; }
                }
            }
            default -> { }
        }
        slotIndicator.setX(origin.getX() + x);
        slotIndicator.setY(origin.getY() + y);
        slotIndicator.setWidth(Math.max(0, rw));
        slotIndicator.setHeight(Math.max(0, rh));
        slotIndicator.setVisible(true);
        overlaysToFront();
    }

    /** After a move drag: re-parent/re-slot into the target under the pointer, or settle inside the current parent. */
    private void finishMove(FormComponent dragged, double sceneX, double sceneY) {
        DropTarget target = findDropTarget(sceneX, sceneY, selection);
        FormComponent currentParent = model.parentOf(dragged);
        FormComponent targetParent = target == null ? null : target.container();
        String newSlot = target == null ? "" : slotFor(target, sceneX, sceneY);
        boolean parentChanged = targetParent != currentParent;
        boolean slotChanged = targetParent != null && !newSlot.equals(currentSlot(dragged, targetParent));

        if (parentChanged || slotChanged) {
            Pane pane = target == null ? this : target.pane();
            boolean absoluteTarget = targetParent == null || !targetParent.getType().kind.isAutoLayout();
            double maxW = target == null ? model.getWidth() : pane.getWidth();
            double maxH = target == null ? model.getHeight() : pane.getHeight();
            for (FormComponent s : selection) {
                if (immovable(s) || (targetParent != null && (s == targetParent || model.isAncestor(s, targetParent)))) {
                    continue;
                }
                Point2D local = pane.sceneToLocal(wrappers.get(s).localToScene(0, 0));
                if (!model.reparent(s, targetParent, storedSlot(target, newSlot))) {
                    continue;
                }
                if (targetParent != null && targetParent.getType().kind == ComponentType.ContainerKind.STACK) {
                    insertAt(s, targetParent, Integer.parseInt(newSlot));
                }
                if (absoluteTarget) {
                    s.setX(clamp(snap(local.getX()), 0, Math.max(0, maxW - s.getWidth())));
                    s.setY(clamp(snap(local.getY()), 0, Math.max(0, maxH - s.getHeight())));
                }
            }
            rebuildPreservingSelection();
            onStructureChanged.run();
        } else {
            for (FormComponent s : selection) {
                if (immovable(s) || isAutoLaid(s)) {
                    continue;
                }
                s.setX(clamp(s.getX(), 0, Math.max(0, hostWidth(s) - s.getWidth())));
                s.setY(clamp(s.getY(), 0, Math.max(0, hostHeight(s) - s.getHeight())));
                positionWrapper(s, wrappers.get(s));
            }
            if (selection.size() == 1) {
                layoutHandles();
            }
        }
        onGeometryChanged.accept(dragged);
    }

    /** Selects the container of the primary selection (no-op at form level). */
    public void selectParent() {
        FormComponent sel = getSelected();
        FormComponent parent = sel == null ? null : model.parentOf(sel);
        if (parent != null) {
            select(parent);
        }
    }

    /** Moves the selected components out of their containers onto the form, keeping their on-screen place. */
    public void moveSelectionToForm() {
        List<FormComponent> nested = selection.stream().filter(s -> s.getParentId() != null).toList();
        if (nested.isEmpty()) {
            return;
        }
        checkpoint.accept(null);
        for (FormComponent s : nested) {
            Point2D origin = canvasOrigin(s);
            model.reparent(s, null, "");
            s.setX(clamp(snap(origin.getX()), 0, Math.max(0, model.getWidth() - s.getWidth())));
            s.setY(clamp(snap(origin.getY()), 0, Math.max(0, model.getHeight() - s.getHeight())));
        }
        rebuildPreservingSelection();
        onStructureChanged.run();
    }

    // --------------------------------------------------------------- moving

    /** Moves the whole selection by a delta anchored on the dragged component, with guide/grid snapping. */
    private void moveSelection(FormComponent dragged, double dx, double dy) {
        Point2D origin = dragOrigins.get(dragged);
        if (origin == null) {
            return;
        }
        double rawX = origin.getX() + dx;
        double rawY = origin.getY() + dy;

        Double guideX = findGuide(dragged, rawX, dragged.getWidth(), true);
        Double guideY = findGuide(dragged, rawY, dragged.getHeight(), false);
        double nx = guideX != null ? guideX : snap(rawX);
        double ny = guideY != null ? guideY : snap(rawY);

        // clamp the shared delta so every selected component stays on the form
        // (not necessarily inside its parent: dragging out re-parents on release)
        double adx = nx - origin.getX();
        double ady = ny - origin.getY();
        for (FormComponent s : selection) {
            Point2D so = dragOrigins.get(s);
            if (so == null || immovable(s)) {
                continue;
            }
            Point2D ho = hostOrigin(s);
            double minDx = -(so.getX() + ho.getX());
            double minDy = -(so.getY() + ho.getY());
            adx = clamp(adx, minDx, Math.max(minDx, model.getWidth() - s.getWidth() - so.getX() - ho.getX()));
            ady = clamp(ady, minDy, Math.max(minDy, model.getHeight() - s.getHeight() - so.getY() - ho.getY()));
        }
        for (FormComponent s : selection) {
            Point2D so = dragOrigins.get(s);
            if (so == null || immovable(s)) {
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

    /** Alignment candidates in the dragged component's parent space: sibling edges/centers plus the parent's center. */
    private List<Double> guideCandidates(FormComponent dragged, boolean horizontalAxis) {
        List<Double> candidates = new ArrayList<>();
        for (FormComponent other : model.siblingsOf(dragged)) {
            if (selection.contains(other)) {
                continue;
            }
            double pos = horizontalAxis ? other.getX() : other.getY();
            double extent = horizontalAxis ? other.getWidth() : other.getHeight();
            candidates.add(pos);
            candidates.add(pos + extent / 2);
            candidates.add(pos + extent);
        }
        candidates.add((horizontalAxis ? hostWidth(dragged) : hostHeight(dragged)) / 2);
        return candidates;
    }

    /**
     * Finds the nearest alignment guide for the dragged component's edge/center
     * positions. Returns the snapped coordinate, or null when no guide is close.
     */
    private Double findGuide(FormComponent dragged, double raw, double size, boolean horizontalAxis) {
        double[] offsets = {0, size / 2, size};
        Double best = null;
        double bestDist = GUIDE_SNAP + 1;
        for (double cand : guideCandidates(dragged, horizontalAxis)) {
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
        Point2D ho = hostOrigin(dragged);
        if (showV) {
            // the guide line sits at whichever dragged edge/center matched
            double x = ho.getX() + nearestEdge(dragged, dragged.getX(), dragged.getWidth(), true);
            vGuide.setStartX(x);
            vGuide.setEndX(x);
            vGuide.setStartY(ho.getY());
            vGuide.setEndY(ho.getY() + hostHeight(dragged));
        }
        if (showH) {
            double y = ho.getY() + nearestEdge(dragged, dragged.getY(), dragged.getHeight(), false);
            hGuide.setStartY(y);
            hGuide.setEndY(y);
            hGuide.setStartX(ho.getX());
            hGuide.setEndX(ho.getX() + hostWidth(dragged));
        }
        overlaysToFront();
    }

    /** Which of the dragged component's edges/center actually aligned with a candidate. */
    private double nearestEdge(FormComponent dragged, double pos, double size, boolean horizontalAxis) {
        double bestEdge = pos;
        double bestDist = Double.MAX_VALUE;
        for (double cand : guideCandidates(dragged, horizontalAxis)) {
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
        FormComponent parent = model.parentOf(c);
        ComponentType.ContainerKind parentKind = parent == null ? ComponentType.ContainerKind.NONE : parent.getType().kind;
        wrapper.setPrefSize(c.getWidth(), c.getHeight());
        switch (parentKind) {
            case GRID, DOCK -> {
                // the cell/region decides the size; the model size is only a preference
                wrapper.setMinSize(0, 0);
                wrapper.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            }
            case STACK -> {
                wrapper.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                wrapper.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            }
            default -> {
                wrapper.setLayoutX(c.getX());
                wrapper.setLayoutY(c.getY());
                wrapper.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                wrapper.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            }
        }
        if (parent != null) {
            updateScrollContent(parent);
        }
    }

    // ------------------------------------------------------------- resizing

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

    /** Grey grips at the form's right/bottom edges; dragging them changes the form size. */
    private void createFormHandles() {
        for (Dir dir : List.of(Dir.E, Dir.S, Dir.SE)) {
            Rectangle handle = new Rectangle(HANDLE_SIZE, HANDLE_SIZE, Color.web("#6e7781"));
            handle.setStroke(Color.WHITE);
            handle.setStrokeWidth(1);
            handle.setCursor(cursorFor(dir));
            handle.setOnMousePressed(e -> {
                hideMenu();
                Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
                formStartX = p.getX();
                formStartY = p.getY();
                formOrigW = model.getWidth();
                formOrigH = model.getHeight();
                formCheckpointed = false;
                e.consume();
            });
            handle.setOnMouseDragged(e -> {
                if (model == null) {
                    return;
                }
                if (!formCheckpointed) {
                    checkpoint.accept(null);
                    formCheckpointed = true;
                }
                Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
                double w = dir == Dir.S ? formOrigW
                        : Math.max(MIN_FORM_WIDTH, snap(formOrigW + p.getX() - formStartX));
                double h = dir == Dir.E ? formOrigH
                        : Math.max(MIN_FORM_HEIGHT, snap(formOrigH + p.getY() - formStartY));
                if (w != model.getWidth() || h != model.getHeight()) {
                    model.setWidth(w);
                    model.setHeight(h);
                    applyFormSize();
                    if (selection.size() == 1) {
                        layoutHandles();
                    }
                    onFormResized.run();
                }
                e.consume();
            });
            formHandleGroup.getChildren().add(handle);
        }
    }

    private void layoutFormHandles() {
        if (model == null || formHandleGroup.getChildren().size() < 3) {
            return;
        }
        double w = model.getWidth(), h = model.getHeight(), half = HANDLE_SIZE / 2;
        Rectangle east = (Rectangle) formHandleGroup.getChildren().get(0);
        Rectangle south = (Rectangle) formHandleGroup.getChildren().get(1);
        Rectangle corner = (Rectangle) formHandleGroup.getChildren().get(2);
        east.setX(w - half);
        east.setY(h / 2 - half);
        south.setX(w / 2 - half);
        south.setY(h - half);
        corner.setX(w - half);
        corner.setY(h - half);
    }

    private void resizeTo(Dir dir, double dx, double dy) {
        FormComponent sel = getSelected();
        double nx = origX, ny = origY, nw = origW, nh = origH;
        boolean west = dir == Dir.NW || dir == Dir.W || dir == Dir.SW;
        boolean east = dir == Dir.NE || dir == Dir.E || dir == Dir.SE;
        boolean north = dir == Dir.NW || dir == Dir.N || dir == Dir.NE;
        boolean south = dir == Dir.SW || dir == Dir.S || dir == Dir.SE;
        double maxW = hostWidth(sel);
        double maxH = hostHeight(sel);

        if (west) {
            nx = snap(origX + dx);
            nx = clamp(nx, 0, origX + origW - MIN_SIZE);
            nw = origX + origW - nx;
        } else if (east) {
            nw = Math.max(MIN_SIZE, snap(origW + dx));
            nw = Math.min(nw, maxW - origX);
        }
        if (north) {
            ny = snap(origY + dy);
            ny = clamp(ny, 0, origY + origH - MIN_SIZE);
            nh = origY + origH - ny;
        } else if (south) {
            nh = Math.max(MIN_SIZE, snap(origH + dy));
            nh = Math.min(nh, maxH - origY);
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
        Point2D origin = canvasOrigin(sel);
        Pane wrapper = wrappers.get(sel);
        boolean autoLaid = isAutoLaid(sel);
        double x = origin.getX(), y = origin.getY();
        // auto-laid children may be stretched by their cell: track the live size
        double w = autoLaid && wrapper.getWidth() > 0 ? wrapper.getWidth() : sel.getWidth();
        double h = autoLaid && wrapper.getHeight() > 0 ? wrapper.getHeight() : sel.getHeight();
        double half = HANDLE_SIZE / 2;
        int i = 0;
        for (Dir dir : Dir.values()) {
            Rectangle handle = (Rectangle) handleGroup.getChildren().get(i++);
            // inside an auto-layout container only the size can be edited, so keep the E/S/SE handles
            handle.setVisible(!autoLaid || dir == Dir.E || dir == Dir.S || dir == Dir.SE);
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

    // ---------------------------------------------------------------- menus

    private javafx.scene.control.ContextMenu buildComponentMenu() {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        boolean allLocked = selection.stream().allMatch(FormComponent::isLocked);
        boolean nested = selection.stream().anyMatch(s -> s.getParentId() != null);
        javafx.scene.control.Menu alignMenu = new javafx.scene.control.Menu("Align");
        alignMenu.setDisable(selection.size() < 2);
        alignMenu.getItems().addAll(
                menuItem("Left", () -> align(AlignOp.LEFT)),
                menuItem("Right", () -> align(AlignOp.RIGHT)),
                menuItem("Top", () -> align(AlignOp.TOP)),
                menuItem("Bottom", () -> align(AlignOp.BOTTOM)),
                menuItem("Center Horizontally", () -> align(AlignOp.CENTER_H)),
                menuItem("Center Vertically", () -> align(AlignOp.CENTER_V)),
                menuItem("Same Size", () -> align(AlignOp.SAME_SIZE)));
        javafx.scene.control.MenuItem selectParent = menuItem("Select Parent", this::selectParent);
        selectParent.setDisable(getSelected() == null || getSelected().getParentId() == null);
        javafx.scene.control.MenuItem toForm = menuItem("Move to Form", this::moveSelectionToForm);
        toForm.setDisable(!nested);
        menu.getItems().addAll(
                menuItem("Cut", () -> { copySelected(); deleteSelected(); }),
                menuItem("Copy", this::copySelected),
                menuItem("Paste", this::paste),
                menuItem("Duplicate", this::duplicateSelected),
                menuItem("Delete", this::deleteSelected),
                new javafx.scene.control.SeparatorMenuItem(),
                menuItem("Bring to Front", () -> onZOrderRequest.accept(true)),
                menuItem("Send to Back", () -> onZOrderRequest.accept(false)),
                new javafx.scene.control.SeparatorMenuItem(),
                selectParent,
                toForm,
                new javafx.scene.control.SeparatorMenuItem(),
                menuItem(allLocked ? "Unlock" : "Lock", this::toggleLockSelected),
                new javafx.scene.control.SeparatorMenuItem(),
                alignMenu);
        return menu;
    }

    private javafx.scene.control.ContextMenu buildCanvasMenu() {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        menu.getItems().addAll(
                menuItem("Paste", this::paste),
                menuItem("Select All", this::selectAll));
        return menu;
    }

    private void showMenu(javafx.scene.control.ContextMenu menu, Node anchor, double screenX, double screenY) {
        hideMenu();
        openMenu = menu;
        menu.setAutoHide(true);
        menu.setOnHidden(e -> {
            if (openMenu == menu) {
                openMenu = null;
            }
        });
        menu.show(anchor, screenX, screenY);
    }

    private void hideMenu() {
        if (openMenu != null) {
            javafx.scene.control.ContextMenu menu = openMenu;
            openMenu = null;
            menu.hide();
        }
    }

    private javafx.scene.control.MenuItem menuItem(String text, Runnable action) {
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    // -------------------------------------------------------------- editing

    /** Locks all selected components, or unlocks them when all are already locked. */
    public void toggleLockSelected() {
        if (selection.isEmpty()) {
            return;
        }
        boolean lock = !selection.stream().allMatch(FormComponent::isLocked);
        checkpoint.accept(null);
        for (FormComponent c : selection) {
            c.setLocked(lock);
        }
        handleGroup.setVisible(selection.size() == 1 && resizable(getSelected()));
        onStructureChanged.run();
        onSelectionChanged.accept(getSelectionList());
    }

    /** The selected components whose ancestors are not selected themselves (subtrees come along on paste). */
    private List<FormComponent> topMostSelection() {
        List<FormComponent> out = new ArrayList<>();
        for (FormComponent c : selection) {
            boolean covered = false;
            for (FormComponent other : selection) {
                if (other != c && model.isAncestor(other, c)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                out.add(c);
            }
        }
        return out;
    }

    public void copySelected() {
        if (!selection.isEmpty()) {
            copied = List.copyOf(topMostSelection());
        }
    }

    public void paste() {
        if (copied.isEmpty() || model == null) {
            return;
        }
        checkpoint.accept(null);
        List<FormComponent> pasted = new ArrayList<>();
        for (FormComponent src : copied) {
            pasted.add(model.duplicate(src));
        }
        rebuild();
        setSelection(pasted);
        onStructureChanged.run();
    }

    public void duplicateSelected() {
        if (selection.isEmpty()) {
            return;
        }
        copied = List.copyOf(topMostSelection());
        paste();
    }

    public void deleteSelected() {
        if (selection.isEmpty()) {
            return;
        }
        checkpoint.accept(null);
        for (FormComponent c : List.copyOf(selection)) {
            model.remove(c);
        }
        rebuild();
        onStructureChanged.run();
    }

    /** Selects every top-level component (nested ones move with their containers). */
    public void selectAll() {
        setSelection(model.childrenOf(null));
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
            case F2 -> onRenameRequest.run();
            case DELETE, BACK_SPACE -> deleteSelected();
            case ESCAPE -> selectParent();
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
        if (selection.stream().allMatch(DesignCanvas::immovable)) {
            return;
        }
        if (isAutoLaid(primary)) {
            nudgeSlot(primary, dx, dy);
            return;
        }
        checkpoint.accept("nudge:" + primary.getId());
        // clamp the shared delta so the whole selection stays inside its parents
        for (FormComponent s : selection) {
            if (immovable(s)) {
                continue;
            }
            dx = clamp(dx, -s.getX(), Math.max(-s.getX(), hostWidth(s) - s.getWidth() - s.getX()));
            dy = clamp(dy, -s.getY(), Math.max(-s.getY(), hostHeight(s) - s.getHeight() - s.getY()));
        }
        for (FormComponent s : selection) {
            if (immovable(s)) {
                continue;
            }
            s.setX(s.getX() + dx);
            s.setY(s.getY() + dy);
            positionWrapper(s, wrappers.get(s));
        }
        if (selection.size() == 1) {
            layoutHandles();
        }
        onGeometryChanged.accept(primary);
    }

    /** Arrow keys inside an auto-layout container move the component to the neighbouring slot. */
    private void nudgeSlot(FormComponent c, double dx, double dy) {
        FormComponent parent = model.parentOf(c);
        if (c.isLocked() || parent == null) {
            return;
        }
        int step = (int) Math.signum(dx != 0 ? dx : dy);
        switch (parent.getType().kind) {
            case STACK -> {
                List<FormComponent> siblings = model.childrenOf(parent);
                int index = siblings.indexOf(c);
                int next = Math.max(0, Math.min(siblings.size() - 1, index + step));
                if (next == index) {
                    return;
                }
                checkpoint.accept("nudge:" + c.getId());
                insertAt(c, parent, next);
            }
            case GRID -> {
                int[] cell = ContainerGeometry.gridCell(c, parent);
                int col = (int) clamp(cell[0] + Math.signum(dx), 0, ContainerGeometry.gridColumns(parent) - 1);
                int row = (int) clamp(cell[1] + Math.signum(dy), 0, ContainerGeometry.gridRows(parent) - 1);
                if (col == cell[0] && row == cell[1]) {
                    return;
                }
                checkpoint.accept("nudge:" + c.getId());
                c.setSlot(col + "," + row);
            }
            default -> {
                return;
            }
        }
        rebuildPreservingSelection();
        onStructureChanged.run();
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
