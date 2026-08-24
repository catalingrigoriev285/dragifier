package dev.dragifier.ui;

import dev.dragifier.model.ComponentType;
import dev.dragifier.model.ContainerGeometry;
import dev.dragifier.model.DockLayout;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

import java.util.List;

/**
 * Builds a live JavaFX scene graph from a form the same way the generated code
 * does (AnchorPane root, anchors per component, children nested in container
 * content areas or laid out by StackPanel/Grid/DockPanel), so the quick
 * preview behaves like the real app.
 */
public final class LiveBuilder {

    private LiveBuilder() {}

    public static AnchorPane build(FormModel form) {
        AnchorPane root = new AnchorPane();
        root.setPrefSize(form.getWidth(), form.getHeight());
        root.setStyle("-fx-background-color: white;");
        DockLayout.applyTo(form, null);
        for (FormComponent c : form.childrenOf(null)) {
            addTo(form, c, null, root, root, form.getWidth(), form.getHeight());
        }
        return root;
    }

    private static void addTo(FormModel form, FormComponent c, FormComponent parent, Region parentNode,
                              Pane target, double parentW, double parentH) {
        if (c.getType() == ComponentType.TIMER) {
            return; // design-time only; quick preview does not run timers
        }
        Region node = Renderer.createNode(c);
        // generated code sets only the pref size; let the layout stretch anchored nodes the same way
        node.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        node.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        if (parent != null && parent.getType().kind.isAutoLayout()) {
            applyLayoutSizing(node, parent);
            Renderer.placeChild(parentNode, parent, node, c);
        } else {
            position(node, c, parentW, parentH);
            target.getChildren().add(node);
        }
        if (!c.getType().isContainer()) {
            return;
        }
        List<Pane> panes = Renderer.contentPanes(node);
        DockLayout.applyTo(form, c);
        double extentW = 0, extentH = 0;
        for (FormComponent child : form.childrenOf(c)) {
            int slot = ContainerGeometry.slotIndex(child, c);
            if (slot >= panes.size()) {
                continue;
            }
            addTo(form, child, c, node, panes.get(slot),
                    ContainerGeometry.contentWidth(c, slot), ContainerGeometry.contentHeight(c, slot));
            extentW = Math.max(extentW, child.getX() + child.getWidth());
            extentH = Math.max(extentH, child.getY() + child.getHeight());
        }
        if (c.getType().kind == ComponentType.ContainerKind.SCROLL && !panes.isEmpty()) {
            panes.get(0).setPrefSize(
                    Math.max(ContainerGeometry.contentWidth(c, 0), extentW),
                    Math.max(ContainerGeometry.contentHeight(c, 0), extentH));
        }
    }

    /** Mirrors the size constraints the code generator emits for children of auto-layout containers. */
    public static void applyLayoutSizing(Region node, FormComponent parent) {
        switch (parent.getType().kind) {
            case STACK -> node.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            case GRID, DOCK -> {
                node.setMinSize(0, 0);
                node.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            }
            default -> { }
        }
    }

    /** Mirrors {@code JavaCodeGenerator.appendPosition}: anchors per flag, else plain layout coordinates. */
    public static void position(Region node, FormComponent c, double parentW, double parentH) {
        boolean[] a = DockLayout.anchors(c); // left, top, right, bottom
        if (a[0]) {
            AnchorPane.setLeftAnchor(node, c.getX());
        } else if (!a[2]) {
            node.setLayoutX(c.getX());
        }
        if (a[2]) {
            AnchorPane.setRightAnchor(node, parentW - c.getX() - c.getWidth());
        }
        if (a[1]) {
            AnchorPane.setTopAnchor(node, c.getY());
        } else if (!a[3]) {
            node.setLayoutY(c.getY());
        }
        if (a[3]) {
            AnchorPane.setBottomAnchor(node, parentH - c.getY() - c.getHeight());
        }
    }
}
