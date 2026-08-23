package dev.dragifier;

import dev.dragifier.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

/** Entry point for the Dragifier IDE. */
public class DragifierApp extends Application {

    @Override
    public void start(Stage stage) {
        new MainWindow(stage);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
