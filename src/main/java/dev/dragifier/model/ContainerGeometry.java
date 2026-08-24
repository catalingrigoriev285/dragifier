package dev.dragifier.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The content-area size of a container as assumed by the code generator and the
 * live preview (the designer measures the real panes instead). Only the
 * chrome that JavaFX adds around the content (borders, headers, dividers) is
 * subtracted; the numbers are close approximations of the default theme.
 */
public final class ContainerGeometry {

    /** TitledPane title bar height. */
    public static final double GROUP_HEADER = 26;
    /** TabPane tab header strip height. */
    public static final double TAB_HEADER = 30;
    /** SplitPane divider thickness. */
    public static final double DIVIDER = 6;
    /** Border of ScrollPane / TitledPane / TabPane. */
    public static final double BORDER = 1;

    private ContainerGeometry() {}

    /** Number of pages of a TabControl (at least 1). */
    public static int tabCount(FormComponent c) {
        return Math.max(1, (int) c.getItems().lines().map(String::trim).filter(s -> !s.isEmpty()).count());
    }

    /** Number of panes of a Splitter (at least 2). */
    public static int paneCount(FormComponent c) {
        return Math.max(2, c.getPanes());
    }

    /** Number of content areas the container exposes for the given kind. */
    public static int slotCount(FormComponent c) {
        return switch (c.getType().kind) {
            case TABS -> tabCount(c);
            case SPLIT -> paneCount(c);
            default -> 1;
        };
    }

    /** Divider positions (0..1) of a Splitter, filled in evenly when unset/short. */
    public static double[] dividerPositions(FormComponent c) {
        int panes = paneCount(c);
        double[] positions = new double[panes - 1];
        List<Double> parsed = new ArrayList<>();
        for (String part : c.getDividers().split(",")) {
            try {
                parsed.add(Math.max(0, Math.min(1, Double.parseDouble(part.trim()))));
            } catch (NumberFormatException ignored) {
                // skip garbage
            }
        }
        for (int i = 0; i < positions.length; i++) {
            positions[i] = i < parsed.size() ? parsed.get(i) : (i + 1) / (double) panes;
        }
        return positions;
    }

    /** Width of the content area at {@code slot} of container {@code c}. */
    public static double contentWidth(FormComponent c, int slot) {
        double w = c.getWidth();
        return switch (c.getType().kind) {
            case ABSOLUTE -> w;
            case GROUP, SCROLL, TABS -> Math.max(0, w - 2 * BORDER);
            case SPLIT -> "VERTICAL".equals(c.getOrientation()) ? w : Math.max(0, splitExtent(c, slot, w));
            default -> w;
        };
    }

    /** Height of the content area at {@code slot} of container {@code c}. */
    public static double contentHeight(FormComponent c, int slot) {
        double h = c.getHeight();
        return switch (c.getType().kind) {
            case ABSOLUTE -> h;
            case GROUP -> Math.max(0, h - GROUP_HEADER - 2 * BORDER);
            case SCROLL -> Math.max(0, h - 2 * BORDER);
            case TABS -> Math.max(0, h - TAB_HEADER - 2 * BORDER);
            case SPLIT -> "VERTICAL".equals(c.getOrientation()) ? Math.max(0, splitExtent(c, slot, h)) : h;
            default -> h;
        };
    }

    private static double splitExtent(FormComponent c, int slot, double total) {
        double[] pos = dividerPositions(c);
        int panes = pos.length + 1;
        int i = Math.max(0, Math.min(panes - 1, slot));
        double start = i == 0 ? 0 : pos[i - 1];
        double end = i == panes - 1 ? 1 : pos[i];
        double dividerShare = DIVIDER * pos.length / (double) panes;
        return (end - start) * total - dividerShare;
    }

    /** Regions of a DockPanel (BorderPane), in slot-string form. */
    public static final List<String> DOCK_REGIONS = List.of("TOP", "LEFT", "CENTER", "RIGHT", "BOTTOM");

    /** Column count of a Grid (default 2). */
    public static int gridColumns(FormComponent c) {
        return Math.max(1, c.getGridColumns() <= 0 ? 2 : c.getGridColumns());
    }

    /** Row count of a Grid (default 2). */
    public static int gridRows(FormComponent c) {
        return Math.max(1, c.getGridRows() <= 0 ? 2 : c.getGridRows());
    }

    /** The {col, row} a Grid child occupies, parsed from "col,row" and clamped to the grid. */
    public static int[] gridCell(FormComponent child, FormComponent grid) {
        int col = 0, row = 0;
        String[] parts = child.getSlot().split(",");
        try {
            if (parts.length >= 1) {
                col = Integer.parseInt(parts[0].trim());
            }
            if (parts.length >= 2) {
                row = Integer.parseInt(parts[1].trim());
            }
        } catch (NumberFormatException ignored) {
            // unset/garbage → top-left cell
        }
        return new int[]{
                Math.max(0, Math.min(gridColumns(grid) - 1, col)),
                Math.max(0, Math.min(gridRows(grid) - 1, row))};
    }

    /** The DockPanel region a child occupies (CENTER when unset/invalid). */
    public static String dockRegion(FormComponent child) {
        String region = child.getSlot().trim().toUpperCase();
        return DOCK_REGIONS.contains(region) ? region : "CENTER";
    }

    /** The slot index a child occupies, parsed from its slot string (0 when unset/invalid). */
    public static int slotIndex(FormComponent child, FormComponent container) {
        int max = slotCount(container) - 1;
        try {
            return Math.max(0, Math.min(max, Integer.parseInt(child.getSlot().trim())));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
