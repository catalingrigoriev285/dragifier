package dev.dragifier.ui;

import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Runs the designed form as a live window, interpreting the model directly. */
public final class PreviewWindow {

    private PreviewWindow() {}

    public static void show(FormModel model, Window owner) {
        Pane root = new Pane();
        root.setPrefSize(model.getWidth(), model.getHeight());
        root.setStyle("-fx-background-color: white;");
        for (FormComponent c : model.getComponents()) {
            if (c.getType() == dev.dragifier.model.ComponentType.TIMER) {
                continue; // design-time only; quick preview does not run timers
            }
            Region node = Renderer.createNode(c);
            node.setLayoutX(c.getX());
            node.setLayoutY(c.getY());
            root.getChildren().add(node);
        }
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle(model.getTitle() + " — Preview");
        stage.setScene(new Scene(root));
        stage.setResizable(false);
        stage.show();
    }
}
