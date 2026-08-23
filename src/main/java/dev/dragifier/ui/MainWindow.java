package dev.dragifier.ui;

import dev.dragifier.codegen.JavaCodeGenerator;
import dev.dragifier.io.ProjectIO;
import dev.dragifier.model.FormModel;
import dev.dragifier.packager.AppPackager;
import dev.dragifier.runner.AppRunner;
import dev.dragifier.undo.UndoManager;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
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
    private final EventEditorPane eventEditor = new EventEditorPane();
    private final ComponentTreePane tree = new ComponentTreePane();
    private final UndoManager undoManager = new UndoManager(() -> ProjectIO.toJson(model));
    private final Label status = new Label("Ready");

    public MainWindow(Stage stage) {
        this.stage = stage;

        canvas.setOnSelectionChanged(sel -> {
            tree.select(sel.isEmpty() ? null : sel.get(0));
            if (sel.size() == 1) {
                inspector.showComponent(sel.get(0));
                eventEditor.showComponent(sel.get(0));
            } else if (sel.isEmpty()) {
                inspector.showForm();
                eventEditor.showNone();
            } else {
                inspector.showMulti(sel.size());
                eventEditor.showNone();
            }
        });
        canvas.setOnOpenEvents(c -> eventEditor.focusCode());
        canvas.setOnCheckpoint(undoManager::checkpoint);
        inspector.setCheckpoint(() -> undoManager.checkpoint(null));
        eventEditor.setCheckpoint(undoManager::checkpoint);
        eventEditor.setOnEdited(this::markDirty);
        tree.setOnPick(canvas::select);
        canvas.setOnGeometryChanged(c -> {
            inspector.updateGeometry(c);
            markDirty();
        });
        canvas.setOnStructureChanged(() -> {
            tree.refresh();
            tree.select(canvas.getSelected());
            markDirty();
        });
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
        VBox left = new VBox(new PalettePane(), tree);
        VBox.setVgrow(tree, javafx.scene.layout.Priority.ALWAYS);
        root.setLeft(left);
        root.setRight(inspector);

        StackPane canvasHolder = new StackPane(canvas);
        canvasHolder.setPadding(new Insets(24));
        canvasHolder.setStyle("-fx-background-color: #e4e4e4;");
        ScrollPane scroll = new ScrollPane(canvasHolder);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);

        SplitPane center = new SplitPane(scroll, eventEditor);
        center.setOrientation(Orientation.VERTICAL);
        center.setDividerPositions(0.72);
        root.setCenter(center);

        status.setPadding(new Insets(4, 10, 4, 10));
        root.setBottom(status);

        stage.setScene(new Scene(root, 1100, 720));
        updateTitle();
    }

    private void bindModel() {
        canvas.setModel(model);
        inspector.setModel(model);
        tree.setModel(model);
        inspector.showForm();
        eventEditor.showNone();
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

        Menu edit = new Menu("Edit");
        edit.getItems().addAll(
                item("Undo", "Shortcut+Z", this::undo),
                item("Redo", "Shortcut+Y", this::redo),
                new SeparatorMenuItem(),
                item("Copy", "Shortcut+C", canvas::copySelected),
                item("Paste", "Shortcut+V", canvas::paste),
                item("Duplicate", "Shortcut+D", canvas::duplicateSelected),
                item("Select All", "Shortcut+A", canvas::selectAll),
                item("Delete", "Delete", canvas::deleteSelected));

        Menu arrange = new Menu("Arrange");
        arrange.getItems().addAll(
                item("Align Left", null, () -> canvas.align(DesignCanvas.AlignOp.LEFT)),
                item("Align Right", null, () -> canvas.align(DesignCanvas.AlignOp.RIGHT)),
                item("Align Top", null, () -> canvas.align(DesignCanvas.AlignOp.TOP)),
                item("Align Bottom", null, () -> canvas.align(DesignCanvas.AlignOp.BOTTOM)),
                new SeparatorMenuItem(),
                item("Center Horizontally", null, () -> canvas.align(DesignCanvas.AlignOp.CENTER_H)),
                item("Center Vertically", null, () -> canvas.align(DesignCanvas.AlignOp.CENTER_V)),
                new SeparatorMenuItem(),
                item("Same Size", null, () -> canvas.align(DesignCanvas.AlignOp.SAME_SIZE)));

        Menu project = new Menu("Project");
        project.getItems().addAll(
                item("Run", "F5", this::run),
                item("Quick Preview", "Shift+F5", this::preview),
                item("Export Java Code…", "Shortcut+E", this::exportCode),
                new SeparatorMenuItem(),
                item("Package App…", null, this::packageApp));

        return new MenuBar(file, edit, arrange, project);
    }

    private ToolBar buildToolBar() {
        Button run = new Button("▶ Run");
        run.setOnAction(e -> run());
        Button preview = new Button("Quick Preview");
        preview.setOnAction(e -> preview());
        Button export = new Button("Export Java");
        export.setOnAction(e -> exportCode());
        return new ToolBar(run, preview, export);
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
        undoManager.clear();
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
            undoManager.clear();
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

    private void packageApp() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose output folder for the packaged app");
        File dir = chooser.showDialog(stage);
        if (dir == null) {
            return;
        }
        AppPackager.packageApp(model, dir.toPath(), status::setText, this::errorText);
    }

    private void run() {
        AppRunner.run(model, status::setText, this::errorText);
    }

    private void undo() {
        String snapshot = undoManager.undo();
        if (snapshot == null) {
            status.setText("Nothing to undo");
            return;
        }
        restoreSnapshot(snapshot);
        status.setText("Undone");
    }

    private void redo() {
        String snapshot = undoManager.redo();
        if (snapshot == null) {
            status.setText("Nothing to redo");
            return;
        }
        restoreSnapshot(snapshot);
        status.setText("Redone");
    }

    private void restoreSnapshot(String json) {
        model = ProjectIO.fromJson(json);
        bindModel();
        markDirty();
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

    private void errorText(String message, String details) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(message);
        TextArea area = new TextArea(details);
        area.setEditable(false);
        area.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");
        alert.getDialogPane().setContent(area);
        alert.setResizable(true);
        alert.showAndWait();
    }
}
