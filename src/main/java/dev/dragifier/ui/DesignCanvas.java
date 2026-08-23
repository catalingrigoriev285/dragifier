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
import javafx.scene.shape.Rectangle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The form design surface: renders the model's components, and lets the user
 * drop new components from the palette, select, move, resize, nudge and delete them.
 */
public class DesignCanvas extends Pane {

    private static final double GRID = 8;
    private static final double MIN_SIZE = 16;
    private static final double HANDLE_SIZE = 8;

    private enum Dir { NW, N, NE, E, SE, S, SW, W }

    private FormModel model;
    private final Map<FormComponent, Pane> wrappers = new LinkedHashMap<>();
    private FormComponent selected;
    private final Group handleGroup = new Group();

    private Consumer<FormComponent> onSelect = c -> {};
    private Consumer<FormComponent> onGeometryChanged = c -> {};
    private Runnable onStructureChanged = () -> {};
    private Consumer<FormComponent> onOpenEvents = c -> {};

    private double dragOffsetX;
    private double dragOffsetY;

    // resize drag state
    private double resizeStartX, resizeStartY;
    private double origX, origY, origW, origH;

    public DesignCanvas() {
        setStyle("-fx-background-color: white; -fx-border-color: #9e9e9e;");
        setFocusTraversable(true);
        handleGroup.setVisible(false);
        createHandles();

        setOnMousePressed(e -> {
            if (e.getTarget() == this) {
                select(null);
            }
            requestFocus();
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
                FormComponent c = model.create(type, x, y);
                addWrapper(c);
                handleGroup.toFront();
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

    public void setOnSelect(Consumer<FormComponent> onSelect) { this.onSelect = onSelect; }
    public void setOnGeometryChanged(Consumer<FormComponent> onGeometryChanged) { this.onGeometryChanged = onGeometryChanged; }
    public void setOnStructureChanged(Runnable onStructureChanged) { this.onStructureChanged = onStructureChanged; }
    public void setOnOpenEvents(Consumer<FormComponent> onOpenEvents) { this.onOpenEvents = onOpenEvents; }

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
        selected = null;
        applyFormSize();
        for (FormComponent c : model.getComponents()) {
            addWrapper(c);
        }
        getChildren().add(handleGroup);
        select(null);
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
        if (c == selected) {
            layoutHandles();
        }
    }

    public void select(FormComponent c) {
        selected = c;
        for (Map.Entry<FormComponent, Pane> entry : wrappers.entrySet()) {
            boolean isSelected = entry.getKey() == c;
            entry.getValue().setStyle(isSelected
                    ? "-fx-border-color: #3b82f6; -fx-border-width: 1;"
                    : "");
        }
        handleGroup.setVisible(c != null);
        if (c != null) {
            layoutHandles();
            handleGroup.toFront();
        }
        onSelect.accept(c);
    }

    private void addWrapper(FormComponent c) {
        Pane wrapper = new Pane();
        Region node = Renderer.createNode(c);
        node.setMouseTransparent(true);
        wrapper.getChildren().add(node);
        positionWrapper(c, wrapper);

        wrapper.setOnMousePressed(e -> {
            select(c);
            dragOffsetX = e.getX();
            dragOffsetY = e.getY();
            requestFocus();
            if (e.getClickCount() == 2) {
                onOpenEvents.accept(c);
            }
            e.consume();
        });
        wrapper.setOnMouseDragged(e -> {
            Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
            double nx = clamp(snap(p.getX() - dragOffsetX), 0, Math.max(0, model.getWidth() - c.getWidth()));
            double ny = clamp(snap(p.getY() - dragOffsetY), 0, Math.max(0, model.getHeight() - c.getHeight()));
            c.setX(nx);
            c.setY(ny);
            positionWrapper(c, wrapper);
            layoutHandles();
            onGeometryChanged.accept(c);
            e.consume();
        });

        wrappers.put(c, wrapper);
        getChildren().add(wrapper);
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
            Rectangle handle = new Rectangle(HANDLE_SIZE, HANDLE_SIZE, Color.web("#3b82f6"));
            handle.setStroke(Color.WHITE);
            handle.setStrokeWidth(1);
            handle.setCursor(cursorFor(dir));
            handle.setOnMousePressed(e -> {
                Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
                resizeStartX = p.getX();
                resizeStartY = p.getY();
                origX = selected.getX();
                origY = selected.getY();
                origW = selected.getWidth();
                origH = selected.getHeight();
                e.consume();
            });
            handle.setOnMouseDragged(e -> {
                if (selected == null) {
                    return;
                }
                Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
                resizeTo(dir, p.getX() - resizeStartX, p.getY() - resizeStartY);
                e.consume();
            });
            handleGroup.getChildren().add(handle);
        }
    }

    private void resizeTo(Dir dir, double dx, double dy) {
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

        selected.setX(nx);
        selected.setY(ny);
        selected.setWidth(nw);
        selected.setHeight(nh);
        refresh(selected);
        onGeometryChanged.accept(selected);
    }

    private void layoutHandles() {
        if (selected == null) {
            return;
        }
        double x = selected.getX(), y = selected.getY();
        double w = selected.getWidth(), h = selected.getHeight();
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

    private void handleKey(KeyEvent e) {
        if (selected == null) {
            return;
        }
        double step = e.isShiftDown() ? GRID : 1;
        switch (e.getCode()) {
            case DELETE, BACK_SPACE -> {
                model.remove(selected);
                Pane wrapper = wrappers.remove(selected);
                getChildren().remove(wrapper);
                select(null);
                onStructureChanged.run();
            }
            case LEFT -> nudge(-step, 0);
            case RIGHT -> nudge(step, 0);
            case UP -> nudge(0, -step);
            case DOWN -> nudge(0, step);
            default -> { return; }
        }
        e.consume();
    }

    private void nudge(double dx, double dy) {
        selected.setX(clamp(selected.getX() + dx, 0, Math.max(0, model.getWidth() - selected.getWidth())));
        selected.setY(clamp(selected.getY() + dy, 0, Math.max(0, model.getHeight() - selected.getHeight())));
        refresh(selected);
        onGeometryChanged.accept(selected);
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

    private static double snap(double v) {
        return Math.round(v / GRID) * GRID;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
