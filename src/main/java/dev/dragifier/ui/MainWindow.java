package dev.dragifier.ui;

import dev.dragifier.codegen.JavaCodeGenerator;
import dev.dragifier.io.ProjectIO;
import dev.dragifier.model.FormModel;
import dev.dragifier.model.ProjectModel;
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
    private ProjectModel project = ProjectModel.withDefaultForm();
    private FormModel model = project.effectiveMain();
    private Path currentFile;
    private boolean dirty;
    private final javafx.scene.control.ComboBox<FormModel> formBox = new javafx.scene.control.ComboBox<>();
    private boolean formBoxUpdating;

    private final DesignCanvas canvas = new DesignCanvas();
    private final InspectorPane inspector = new InspectorPane();
    private final EventEditorPane eventEditor = new EventEditorPane();
    private final ComponentTreePane tree = new ComponentTreePane();
    private final UndoManager undoManager = new UndoManager(() -> ProjectIO.toJson(project));
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
                eventEditor.showForm(model);
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
        inspector.setNameInUse(name -> project.nameInUse(name, model));
        inspector.setOnFormEdited(() -> {
            canvas.applyFormSize();
            if (project.findForm(project.getMainForm()) == null) {
                project.setMainForm(model.getName());
            }
            refreshFormBox();
            markDirty();
        });

        bindProject();

        BorderPane root = new BorderPane();
        root.setTop(new VBox(buildMenuBar(), buildToolBar()));
        VBox left = new VBox(new PalettePane(), tree);
        VBox.setVgrow(tree, javafx.scene.layout.Priority.ALWAYS);
        root.setLeft(left);
        root.setRight(inspector);

        // Group so the canvas's zoom transform is included in layout bounds
        StackPane canvasHolder = new StackPane(new javafx.scene.Group(canvas));
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

        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(getClass().getResource("/code-highlight.css").toExternalForm());
        stage.setScene(scene);
        updateTitle();
    }

    private void bindProject() {
        refreshFormBox();
        bindActiveForm();
    }

    private void bindActiveForm() {
        canvas.setModel(model);
        inspector.setModel(model);
        tree.setModel(model);
        inspector.showForm();
        eventEditor.showForm(model);
    }

    private void refreshFormBox() {
        formBoxUpdating = true;
        formBox.getItems().setAll(project.getForms());
        formBox.getSelectionModel().select(model);
        formBoxUpdating = false;
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
                item("Package App…", null, () -> packageTo(AppPackager.OutputType.APP_IMAGE)),
                item("Package Installer (.exe)…", null, () -> packageTo(AppPackager.OutputType.INSTALLER)));

        return new MenuBar(file, edit, arrange, project);
    }

    private ToolBar buildToolBar() {
        Button run = new Button("▶ Run");
        run.setOnAction(e -> run());
        Button preview = new Button("Quick Preview");
        preview.setOnAction(e -> preview());
        Button export = new Button("Export Java");
        export.setOnAction(e -> exportCode());

        formBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(FormModel f) {
                return f == null ? "" : f.getName() + (f.getName().equals(project.getMainForm()) ? " ★" : "");
            }
            @Override public FormModel fromString(String s) {
                return null;
            }
        });
        formBox.setOnAction(e -> {
            if (formBoxUpdating) {
                return;
            }
            FormModel picked = formBox.getValue();
            if (picked != null && picked != model) {
                model = picked;
                bindActiveForm();
            }
        });
        Button addForm = new Button("+ Form");
        addForm.setOnAction(e -> {
            undoManager.checkpoint(null);
            model = project.addForm();
            bindProject();
            markDirty();
            status.setText("Added " + model.getName());
        });
        Button removeForm = new Button("− Form");
        removeForm.setOnAction(e -> {
            undoManager.checkpoint(null);
            if (project.removeForm(model)) {
                model = project.effectiveMain();
                bindProject();
                markDirty();
            } else {
                status.setText("A project needs at least one form");
            }
        });
        Button setMain = new Button("Set Main");
        setMain.setOnAction(e -> {
            undoManager.checkpoint(null);
            project.setMainForm(model.getName());
            refreshFormBox();
            markDirty();
            status.setText(model.getName() + " is now the startup form");
        });

        javafx.scene.control.ComboBox<Integer> zoomBox = new javafx.scene.control.ComboBox<>();
        zoomBox.getItems().addAll(50, 75, 100, 125, 150, 200);
        zoomBox.setValue(100);
        zoomBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Integer v) {
                return v == null ? "" : v + "%";
            }
            @Override public Integer fromString(String s) {
                return null;
            }
        });
        zoomBox.setOnAction(e -> {
            Integer percent = zoomBox.getValue();
            if (percent != null) {
                double z = percent / 100.0;
                canvas.getTransforms().setAll(new javafx.scene.transform.Scale(z, z));
            }
        });

        return new ToolBar(run, preview, export,
                new javafx.scene.control.Separator(),
                new Label("Form:"), formBox, addForm, removeForm, setMain,
                new javafx.scene.control.Separator(),
                new Label("Zoom:"), zoomBox);
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
        project = ProjectModel.withDefaultForm();
        model = project.effectiveMain();
        currentFile = null;
        dirty = false;
        undoManager.clear();
        bindProject();
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
            project = ProjectIO.load(file.toPath());
            model = project.effectiveMain();
            currentFile = file.toPath();
            dirty = false;
            undoManager.clear();
            bindProject();
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
            ProjectIO.save(project, currentFile);
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
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Export Java Code (one file per form plus Main)");
        File dir = chooser.showDialog(stage);
        if (dir == null) {
            return;
        }
        try {
            var sources = JavaCodeGenerator.generateProject(project);
            for (var entry : sources.entrySet()) {
                Files.writeString(dir.toPath().resolve(entry.getKey()), entry.getValue(), StandardCharsets.UTF_8);
            }
            status.setText("Exported " + sources.size() + " files to " + dir.getName());
        } catch (IOException ex) {
            error("Could not export code", ex);
        }
    }

    private void preview() {
        PreviewWindow.show(model, stage);
    }

    private void packageTo(AppPackager.OutputType type) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(type == AppPackager.OutputType.INSTALLER
                ? "Choose output folder for the installer"
                : "Choose output folder for the packaged app");
        File dir = chooser.showDialog(stage);
        if (dir == null) {
            return;
        }
        AppPackager.packageApp(project, dir.toPath(), type, status::setText, this::errorText,
                exe -> offerOpenFolder(exe));
    }

    private void offerOpenFolder(Path exe) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Packaging finished");
        alert.setHeaderText("Packaged successfully");
        alert.setContentText(exe + "\n\nOpen the output folder?");
        alert.showAndWait().ifPresent(button -> {
            if (button == javafx.scene.control.ButtonType.OK) {
                try {
                    new ProcessBuilder("explorer.exe", "/select,", exe.toString()).start();
                } catch (IOException ignored) {
                    // opening Explorer is best-effort
                }
            }
        });
    }

    private void run() {
        AppRunner.run(project, status::setText, this::errorText);
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
        String activeName = model.getName();
        project = ProjectIO.fromJson(json);
        FormModel active = project.findForm(activeName);
        model = active != null ? active : project.effectiveMain();
        bindProject();
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
