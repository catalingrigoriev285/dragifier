package dev.dragifier.ui;

import dev.dragifier.codegen.JavaCodeGenerator;
import dev.dragifier.io.ProjectIO;
import dev.dragifier.model.FormModel;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The main IDE window: menu, toolbar, palette, design canvas and inspector. */
public class MainWindow {

    private final Stage stage;
    private FormModel model = new FormModel();
    private Path currentFile;
    private boolean dirty;

    private final DesignCanvas canvas = new DesignCanvas();
    private final InspectorPane inspector = new InspectorPane();
    private final Label status = new Label("Ready");

    public MainWindow(Stage stage) {
        this.stage = stage;

        canvas.setOnSelect(c -> {
            if (c == null) {
                inspector.showForm();
            } else {
                inspector.showComponent(c);
            }
        });
        canvas.setOnGeometryChanged(c -> {
            inspector.updateGeometry(c);
            markDirty();
        });
        canvas.setOnStructureChanged(this::markDirty);
        inspector.setOnComponentEdited(c -> {
            canvas.refresh(c);
            markDirty();
        });
        inspector.setOnFormEdited(() -> {
            canvas.applyFormSize();
            markDirty();
        });

        bindModel();

        BorderPane root = new BorderPane();
        root.setTop(new VBox(buildMenuBar(), buildToolBar()));
        root.setLeft(new PalettePane());
        root.setRight(inspector);

        StackPane canvasHolder = new StackPane(canvas);
        canvasHolder.setPadding(new Insets(24));
        canvasHolder.setStyle("-fx-background-color: #e4e4e4;");
        ScrollPane scroll = new ScrollPane(canvasHolder);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        root.setCenter(scroll);

        status.setPadding(new Insets(4, 10, 4, 10));
        root.setBottom(status);

        stage.setScene(new Scene(root, 1100, 720));
        updateTitle();
    }

    private void bindModel() {
        canvas.setModel(model);
        inspector.setModel(model);
        inspector.showForm();
    }

    private MenuBar buildMenuBar() {
        Menu file = new Menu("File");
        file.getItems().addAll(
                item("New", "Shortcut+N", this::newProject),
                item("Open…", "Shortcut+O", this::openProject),
                item("Save", "Shortcut+S", this::saveProject),
                item("Save As…", "Shortcut+Shift+S", this::saveProjectAs),
                new SeparatorMenuItem(),
                item("Exit", null, stage::close));

        Menu project = new Menu("Project");
        project.getItems().addAll(
                item("Preview", "F5", this::preview),
                item("Export Java Code…", "Shortcut+E", this::exportCode));

        return new MenuBar(file, project);
    }

    private ToolBar buildToolBar() {
        Button run = new Button("▶ Preview");
        run.setOnAction(e -> preview());
        Button export = new Button("Export Java");
        export.setOnAction(e -> exportCode());
        return new ToolBar(run, export);
    }

    private MenuItem item(String text, String accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(KeyCombination.keyCombination(accelerator));
        }
        item.setOnAction(e -> action.run());
        return item;
    }

    private void newProject() {
        model = new FormModel();
        currentFile = null;
        dirty = false;
        bindModel();
        updateTitle();
        status.setText("New project");
    }

    private void openProject() {
        FileChooser chooser = projectChooser();
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            model = ProjectIO.load(file.toPath());
            currentFile = file.toPath();
            dirty = false;
            bindModel();
            updateTitle();
            status.setText("Opened " + file.getName());
        } catch (Exception ex) {
            error("Could not open project", ex);
        }
    }

    private void saveProject() {
        if (currentFile == null) {
            saveProjectAs();
            return;
        }
        try {
            ProjectIO.save(model, currentFile);
            dirty = false;
            updateTitle();
            status.setText("Saved " + currentFile.getFileName());
        } catch (IOException ex) {
            error("Could not save project", ex);
        }
    }

    private void saveProjectAs() {
        FileChooser chooser = projectChooser();
        chooser.setInitialFileName("project.dragifier");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        currentFile = file.toPath();
        saveProject();
    }

    private void exportCode() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Java Code");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java source (*.java)", "*.java"));
        chooser.setInitialFileName(JavaCodeGenerator.className(model) + ".java");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(), JavaCodeGenerator.generate(model), StandardCharsets.UTF_8);
            status.setText("Exported " + file.getName());
        } catch (IOException ex) {
            error("Could not export code", ex);
        }
    }

    private void preview() {
        PreviewWindow.show(model, stage);
    }

    private FileChooser projectChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Dragifier Project");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Dragifier project (*.dragifier)", "*.dragifier"));
        return chooser;
    }

    private void markDirty() {
        dirty = true;
        updateTitle();
    }

    private void updateTitle() {
        String name = currentFile == null ? "untitled" : currentFile.getFileName().toString();
        stage.setTitle("Dragifier — " + name + (dirty ? " *" : ""));
    }

    private void error(String message, Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(message);
        alert.setContentText(ex.getMessage());
        alert.showAndWait();
    }
}
