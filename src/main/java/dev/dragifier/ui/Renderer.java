package dev.dragifier.ui;

import dev.dragifier.model.ComponentType;
import dev.dragifier.model.FormComponent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

/**
 * Turns a {@link FormComponent} into a live JavaFX node and keeps the node in
 * sync with the model. Used by both the design canvas and the preview window,
 * and mirrored by the Java code generator.
 */
public final class Renderer {

    private Renderer() {}

    public static Region createNode(FormComponent c) {
        Region node = switch (c.getType()) {
            case BUTTON -> new Button();
            case LABEL -> new Label();
            case TEXT_FIELD -> new TextField();
            case TEXT_AREA -> new TextArea();
            case CHECK_BOX -> new CheckBox();
            case SLIDER -> new Slider();
            case PANEL -> new Pane();
        };
        apply(node, c);
        return node;
    }

    public static void apply(Region node, FormComponent c) {
        node.setPrefSize(c.getWidth(), c.getHeight());
        node.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        node.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        if (node instanceof Labeled labeled) {
            labeled.setText(c.getText());
        } else if (node instanceof TextInputControl input) {
            input.setText(c.getText());
        }
        node.setStyle(styleFor(c));
    }

    /** CSS style string for a component; shared with the code generator. */
    public static String styleFor(FormComponent c) {
        StringBuilder style = new StringBuilder();
        style.append("-fx-font-size: ").append(c.getFontSize()).append("px;");
        if (!c.getTextColor().isEmpty()) {
            style.append(" -fx-text-fill: ").append(c.getTextColor()).append(";");
        }
        if (!c.getBackground().isEmpty()) {
            style.append(" -fx-background-color: ").append(c.getBackground()).append(";");
        } else if (c.getType() == ComponentType.PANEL) {
            style.append(" -fx-background-color: #f4f4f4; -fx-border-color: #c0c0c0;");
        }
        return style.toString();
    }
}
