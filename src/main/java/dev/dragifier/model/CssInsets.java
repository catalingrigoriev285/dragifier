package dev.dragifier.model;

/**
 * CSS-style insets as typed in the inspector: one value for all sides
 * ({@code 8}), two for vertical/horizontal ({@code 4 8}) or four for
 * top/right/bottom/left ({@code 1 2 3 4}). Used for padding, margin and
 * border width; rendered as {@code -fx-padding} etc. and as {@code new Insets(...)}.
 */
public final class CssInsets {

    private CssInsets() {}

    /** {top, right, bottom, left}, or null when blank or invalid. */
    public static double[] parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.trim().split("[\\s,]+");
        double[] v = new double[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                v[i] = Double.parseDouble(parts[i]);
                if (v[i] < 0 || Double.isNaN(v[i]) || Double.isInfinite(v[i])) {
                    return null;
                }
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return switch (v.length) {
            case 1 -> new double[]{v[0], v[0], v[0], v[0]};
            case 2 -> new double[]{v[0], v[1], v[0], v[1]};
            case 4 -> v;
            default -> null;
        };
    }

    /** Canonical "top right bottom left" form, or "" when blank/invalid. */
    public static String normalize(String value) {
        double[] v = parse(value);
        if (v == null) {
            return "";
        }
        if (v[0] == v[1] && v[1] == v[2] && v[2] == v[3]) {
            return fmt(v[0]);
        }
        return fmt(v[0]) + " " + fmt(v[1]) + " " + fmt(v[2]) + " " + fmt(v[3]);
    }

    /** True when the text is empty (meaning "unset") or a valid insets value. */
    public static boolean isValid(String value) {
        return value == null || value.isBlank() || parse(value) != null;
    }

    public static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
