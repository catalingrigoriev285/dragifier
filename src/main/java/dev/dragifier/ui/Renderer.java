package dev.dragifier.ui;

import dev.dragifier.model.ComponentType;
import dev.dragifier.model.FormComponent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

import java.util.List;

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
            case COMBO_BOX -> new ComboBox<String>();
            case LIST_VIEW -> new ListView<String>();
            case RADIO_BUTTON -> new RadioButton();
            case PROGRESS_BAR -> new ProgressBar(0);
            case HYPERLINK -> new Hyperlink();
            case IMAGE_VIEW -> new ImageBox();
        };
        apply(node, c);
        return node;
    }

    @SuppressWarnings("unchecked")
    public static void apply(Region node, FormComponent c) {
        node.setPrefSize(c.getWidth(), c.getHeight());
        node.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        node.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        if (node instanceof Labeled labeled) {
            labeled.setText(c.getText());
        } else if (node instanceof TextInputControl input) {
            input.setText(c.getText());
        } else if (node instanceof ComboBox<?> combo) {
            ((ComboBox<String>) combo).getItems().setAll(itemList(c));
            ((ComboBox<String>) combo).setPromptText(c.getText());
        } else if (node instanceof ListView<?> list) {
            ((ListView<String>) list).getItems().setAll(itemList(c));
        } else if (node instanceof ProgressBar bar) {
            bar.setProgress(c.getValue() / 100.0);
        } else if (node instanceof ImageBox box) {
            box.update(c);
        }
        if (node instanceof Labeled labeled) {
            labeled.setAlignment(posFor(c));
        } else if (node instanceof TextField textField) {
            textField.setAlignment(posFor(c));
        }
        node.setDisable(c.isDisabled());
        applyTooltip(node, c.getTooltip());
        node.setStyle(styleFor(c));
    }

    /** True for types whose text alignment can be set. */
    public static boolean supportsAlignment(ComponentType type) {
        return switch (type) {
            case BUTTON, LABEL, CHECK_BOX, RADIO_BUTTON, HYPERLINK, TEXT_FIELD -> true;
            default -> false;
        };
    }

    private static javafx.geometry.Pos posFor(FormComponent c) {
        return switch (c.getAlignment()) {
            case "LEFT" -> javafx.geometry.Pos.CENTER_LEFT;
            case "CENTER" -> javafx.geometry.Pos.CENTER;
            case "RIGHT" -> javafx.geometry.Pos.CENTER_RIGHT;
            default -> c.getType() == ComponentType.BUTTON
                    ? javafx.geometry.Pos.CENTER : javafx.geometry.Pos.CENTER_LEFT;
        };
    }

    private static void applyTooltip(Region node, String text) {
        javafx.scene.control.Tooltip existing =
                (javafx.scene.control.Tooltip) node.getProperties().get("dragifier.tooltip");
        if (text.isEmpty()) {
            if (existing != null) {
                javafx.scene.control.Tooltip.uninstall(node, existing);
                node.getProperties().remove("dragifier.tooltip");
            }
        } else if (existing == null) {
            javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(text);
            javafx.scene.control.Tooltip.install(node, tooltip);
            node.getProperties().put("dragifier.tooltip", tooltip);
        } else {
            existing.setText(text);
        }
    }

    /** Non-blank lines of the component's items text. */
    public static List<String> itemList(FormComponent c) {
        return c.getItems().lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
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
        } else if (c.getType() == ComponentType.IMAGE_VIEW && c.getImageData().isEmpty()) {
            style.append(" -fx-border-color: #c0c0c0; -fx-border-style: dashed;");
        }
        return style.toString();
    }
}
