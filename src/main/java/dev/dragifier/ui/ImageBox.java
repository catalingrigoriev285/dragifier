package dev.dragifier.ui;

import dev.dragifier.model.FormComponent;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * Designer/preview node for Image components: an ImageView stretched to the
 * component bounds, with a placeholder shown while no image is set. The image
 * is decoded from the component's Base64 data and cached until it changes.
 */
public class ImageBox extends StackPane {

    private final ImageView view = new ImageView();
    private final Label placeholder = new Label("Image");
    private String cachedData;

    public ImageBox() {
        view.setPreserveRatio(false);
        placeholder.setStyle("-fx-text-fill: #a0a0a0;");
        getChildren().addAll(placeholder, view);
    }

    void update(FormComponent c) {
        view.setFitWidth(c.getWidth());
        view.setFitHeight(c.getHeight());
        String data = c.getImageData();
        if (!data.equals(cachedData)) {
            cachedData = data;
            if (data.isEmpty()) {
                view.setImage(null);
            } else {
                try {
                    view.setImage(new Image(new ByteArrayInputStream(Base64.getDecoder().decode(data))));
                } catch (IllegalArgumentException ex) {
                    view.setImage(null);
                }
            }
        }
        placeholder.setVisible(view.getImage() == null);
    }
}
