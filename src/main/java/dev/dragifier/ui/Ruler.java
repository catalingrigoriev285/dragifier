package dev.dragifier.ui;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * A pixel ruler drawn along the design canvas: small ticks every 10 px,
 * medium every 50, labeled every 100. It lives inside the zoom group, so it
 * scales with the canvas and always lines up with form coordinates.
 */
public class Ruler extends javafx.scene.canvas.Canvas {

    public static final double THICKNESS = 22;

    private final boolean horizontal;

    public Ruler(boolean horizontal) {
        this.horizontal = horizontal;
    }

    public void redraw(double length) {
        if (horizontal) {
            setWidth(length);
            setHeight(THICKNESS);
        } else {
            setWidth(THICKNESS);
            setHeight(length);
        }
        GraphicsContext g = getGraphicsContext2D();
        g.clearRect(0, 0, getWidth(), getHeight());
        g.setFill(Color.web("#efefef"));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setStroke(Color.web("#b0b0b0"));
        g.setLineWidth(1);
        g.setFill(Color.web("#707070"));
        g.setFont(Font.font("Consolas", 9));
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.TOP);

        for (int p = 0; p <= (int) length; p += 10) {
            double tick = p % 100 == 0 ? THICKNESS : p % 50 == 0 ? 8 : 4;
            // crisp 1px lines need half-pixel offsets
            double at = p + 0.5;
            if (horizontal) {
                g.strokeLine(at, THICKNESS - tick, at, THICKNESS);
                if (p % 100 == 0 && p > 0) {
                    g.fillText(String.valueOf(p), at + 2, 1);
                }
            } else {
                g.strokeLine(THICKNESS - tick, at, THICKNESS, at);
                if (p % 100 == 0 && p > 0) {
                    g.fillText(String.valueOf(p), 1, at + 2);
                }
            }
        }
    }
}
