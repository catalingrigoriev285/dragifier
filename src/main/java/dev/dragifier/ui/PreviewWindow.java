package dev.dragifier.ui;

import dev.dragifier.model.FormModel;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Runs the designed form as a live window, interpreting the model directly. */
public final class PreviewWindow {

    private PreviewWindow() {}

    public static void show(FormModel model, Window owner) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle(model.getTitle() + " — Preview");
        stage.setScene(new Scene(LiveBuilder.build(model)));
        stage.setResizable(model.isResizable());
        stage.show();
    }
}
