package dev.dragifier.model;

import java.util.List;

/**
 * Delphi-style docking inside one content area: siblings are processed in
 * z-order, each docked one takes its edge of the remaining rectangle (keeping
 * its own thickness) and {@code FILL} takes what is left. Geometry is written
 * back into the components, so the designer, the preview and the generated
 * code all see the same rectangles; the generated code then reproduces the
 * behaviour on window resize with plain AnchorPane anchors.
 */
public final class DockLayout {

    private DockLayout() {}

    /** Lays out the docked members of {@code siblings} in a {@code w}×{@code h} area; true when anything moved. */
    public static boolean apply(List<FormComponent> siblings, double w, double h) {
        double x = 0, y = 0, rw = Math.max(0, w), rh = Math.max(0, h);
        boolean changed = false;
        for (FormComponent c : siblings) {
            switch (c.getDock()) {
                case NONE -> { }
                case LEFT -> {
                    changed |= set(c, x, y, c.getWidth(), rh);
                    x += c.getWidth();
                    rw = Math.max(0, rw - c.getWidth());
                }
                case RIGHT -> {
                    changed |= set(c, x + rw - c.getWidth(), y, c.getWidth(), rh);
                    rw = Math.max(0, rw - c.getWidth());
                }
                case TOP -> {
                    changed |= set(c, x, y, rw, c.getHeight());
                    y += c.getHeight();
                    rh = Math.max(0, rh - c.getHeight());
                }
                case BOTTOM -> {
                    changed |= set(c, x, y + rh - c.getHeight(), rw, c.getHeight());
                    rh = Math.max(0, rh - c.getHeight());
                }
                case FILL -> changed |= set(c, x, y, Math.max(16, rw), Math.max(16, rh));
            }
        }
        return changed;
    }

    private static boolean set(FormComponent c, double x, double y, double w, double h) {
        boolean changed = c.getX() != x || c.getY() != y || c.getWidth() != w || c.getHeight() != h;
        c.setX(x);
        c.setY(y);
        c.setWidth(w);
        c.setHeight(h);
        return changed;
    }

    /**
     * Effective {left, top, right, bottom} anchors: a docked component is
     * pinned to its edge and stretched along it, otherwise the user's flags apply.
     */
    public static boolean[] anchors(FormComponent c) {
        return switch (c.getDock()) {
            case NONE -> new boolean[]{c.isAnchorLeft(), c.isAnchorTop(), c.isAnchorRight(), c.isAnchorBottom()};
            case LEFT -> new boolean[]{true, true, false, true};
            case RIGHT -> new boolean[]{false, true, true, true};
            case TOP -> new boolean[]{true, true, true, false};
            case BOTTOM -> new boolean[]{true, false, true, true};
            case FILL -> new boolean[]{true, true, true, true};
        };
    }

    /** Runs docking for every content area of {@code parent} (null = the form) using the model's assumed sizes. */
    public static void applyTo(FormModel form, FormComponent parent) {
        if (parent == null) {
            apply(form.childrenOf(null), form.getWidth(), form.getHeight());
            return;
        }
        if (!parent.getType().kind.isAbsolute()) {
            return;
        }
        int slots = ContainerGeometry.slotCount(parent);
        for (int i = 0; i < slots; i++) {
            final int slot = i;
            List<FormComponent> group = form.childrenOf(parent).stream()
                    .filter(ch -> ContainerGeometry.slotIndex(ch, parent) == slot).toList();
            apply(group, ContainerGeometry.contentWidth(parent, i), ContainerGeometry.contentHeight(parent, i));
        }
    }
}
