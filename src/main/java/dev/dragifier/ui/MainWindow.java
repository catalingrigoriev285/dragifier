package dev.dragifier.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import dev.dragifier.codegen.JavaCodeGenerator;
import dev.dragifier.io.ProjectIO;
import dev.dragifier.io.RecentProjects;
import dev.dragifier.model.FormModel;
import dev.dragifier.model.ProjectModel;
import dev.dragifier.model.Templates;
import dev.dragifier.packager.AppPackager;
import dev.dragifier.runner.AppRunner;
import dev.dragifier.undo.UndoManager;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The main IDE window: menus, icon toolbar, form tabs, palette, design canvas, inspector, events/console. */
public class MainWindow {

    private final Stage stage;
    private ProjectModel project = ProjectModel.withDefaultForm();
    private FormModel model = project.effectiveMain();
    private Path currentFile;
    private boolean dirty;
    private boolean darkTheme;

    private final DesignCanvas canvas = new DesignCanvas();
    private final InspectorPane inspector = new InspectorPane();
    private final EventEditorPane eventEditor = new EventEditorPane();
    private final ConsolePane console = new ConsolePane();
    private final ComponentTreePane tree = new ComponentTreePane();
    private final UndoManager undoManager = new UndoManager(() -> ProjectIO.toJson(project));

    private final TabPane formTabs = new TabPane();
    private boolean formTabsUpdating;
    private final TabPane bottomTabs = new TabPane();
    private final Ruler hRuler = new Ruler(true);
    private final Ruler vRuler = new Ruler(false);
    private final GridPane rulerGrid = new GridPane();
    private final Label status = new Label("Ready");
    private final Label cursorPos = new Label("");

    private final BorderPane root = new BorderPane();
    private WelcomePane welcomePane;
    private Node designerCenter;
    private Node leftPanel;

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
        canvas.setOnOpenEvents(c -> {
            bottomTabs.getSelectionModel().select(0);
            eventEditor.focusCode();
        });
        canvas.setOnCheckpoint(undoManager::checkpoint);
        inspector.setCheckpoint(() -> undoManager.checkpoint(null));
        eventEditor.setCheckpoint(undoManager::checkpoint);
        eventEditor.setOnEdited(this::markDirty);
        eventEditor.setFormNames(() -> project.getForms().stream().map(FormModel::getName).toList());
        eventEditor.setContextForm(() -> model);
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
        inspector.setNameInUse(name -> project.nameInUse(name, model));
        inspector.setOnFormEdited(() -> {
            canvas.applyFormSize();
            updateRulers();
            if (project.findForm(project.getMainForm()) == null) {
                project.setMainForm(model.getName());
            }
            refreshFormTabs();
            markDirty();
        });

        welcomePane = new WelcomePane(
                () -> { newProject(); showDesigner(); },
                this::openProject,
                template -> loadTemplate(template),
                this::openPath);

        designerCenter = buildDesignerCenter();
        leftPanel = buildLeftPanel();

        root.setTop(new VBox(buildMenuBar(), buildToolBar()));
        root.setRight(inspector);
        buildStatusBar();

        Scene scene = new Scene(root, 1200, 780);
        scene.getStylesheets().add(getClass().getResource("/dragifier.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("/code-highlight.css").toExternalForm());
        stage.setScene(scene);

        bindProject();
        showWelcome();
        updateTitle();
    }

    // ---------------------------------------------------------------- layout

    private Node buildDesignerCenter() {
        formTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        formTabs.getSelectionModel().selectedItemProperty().addListener((obs, was, tab) -> {
            if (formTabsUpdating || tab == null) {
                return;
            }
            FormModel picked = (FormModel) tab.getUserData();
            if (picked != null && picked != model) {
                model = picked;
                bindActiveForm();
            }
        });

        Region corner = new Region();
        corner.setMinSize(Ruler.THICKNESS, Ruler.THICKNESS);
        corner.getStyleClass().add("ruler-corner");
        rulerGrid.add(corner, 0, 0);
        rulerGrid.add(hRuler, 1, 0);
        rulerGrid.add(vRuler, 0, 1);
        rulerGrid.add(canvas, 1, 1);

        canvas.addEventHandler(MouseEvent.MOUSE_MOVED,
                e -> cursorPos.setText((int) e.getX() + ", " + (int) e.getY()));
        canvas.addEventHandler(MouseEvent.MOUSE_EXITED, e -> cursorPos.setText(""));

        StackPane canvasHolder = new StackPane(new Group(rulerGrid));
        canvasHolder.setPadding(new Insets(24));
        canvasHolder.getStyleClass().add("canvas-surround");
        ScrollPane scroll = new ScrollPane(canvasHolder);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);

        VBox editorArea = new VBox(formTabs, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Tab eventsTab = new Tab("Events", eventEditor);
        eventsTab.setClosable(false);
        eventsTab.setGraphic(new FontIcon(Feather.ZAP));
        Tab consoleTab = new Tab("Console", console);
        consoleTab.setClosable(false);
        consoleTab.setGraphic(new FontIcon(Feather.TERMINAL));
        bottomTabs.getTabs().addAll(eventsTab, consoleTab);

        SplitPane center = new SplitPane(editorArea, bottomTabs);
        center.setOrientation(Orientation.VERTICAL);
        center.setDividerPositions(0.72);
        return center;
    }

    private Node buildLeftPanel() {
        SplitPane left = new SplitPane(new PalettePane(), tree);
        left.setOrientation(Orientation.VERTICAL);
        left.setDividerPositions(0.6);
        left.setPrefWidth(180);
        left.setMaxWidth(230);
        return left;
    }

    private void buildStatusBar() {
        status.setPadding(new Insets(4, 10, 4, 10));
        cursorPos.setPadding(new Insets(4, 10, 4, 10));
        cursorPos.getStyleClass().add("cursor-pos");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(status, spacer, cursorPos);
        bar.getStyleClass().add("status-bar");
        root.setBottom(bar);
    }

    private void showWelcome() {
        welcomePane.refreshRecents();
        root.setLeft(null);
        root.setRight(null);
        root.setCenter(welcomePane);
    }

    private void showDesigner() {
        root.setLeft(leftPanel);
        root.setRight(inspector);
        root.setCenter(designerCenter);
    }

    private boolean designerShown() {
        return root.getCenter() == designerCenter;
    }

    // ------------------------------------------------------------- menus/bar

    private MenuBar buildMenuBar() {
        Menu file = new Menu("File");
        Menu openRecent = new Menu("Open Recent");
        openRecent.setOnShowing(e -> rebuildRecentMenu(openRecent));
        rebuildRecentMenu(openRecent);
        file.getItems().addAll(
                item("New", "Shortcut+N", () -> { newProject(); showDesigner(); }),
                item("New from Template…", "Shortcut+Shift+N", this::newFromTemplate),
                item("Open…", "Shortcut+O", this::openProject),
                openRecent,
                item("Save", "Shortcut+S", this::saveProject),
                item("Save As…", "Shortcut+Shift+S", this::saveProjectAs),
                new SeparatorMenuItem(),
                item("Welcome", null, this::showWelcome),
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

        Menu view = new Menu("View");
        CheckMenuItem darkItem = new CheckMenuItem("Dark Theme");
        darkItem.setOnAction(e -> setDarkTheme(darkItem.isSelected()));
        view.getItems().add(darkItem);

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
                item("Same Size", null, () -> canvas.align(DesignCanvas.AlignOp.SAME_SIZE)),
                new SeparatorMenuItem(),
                item("Bring to Front", "Shortcut+Shift+F", () -> zOrder(true)),
                item("Send to Back", "Shortcut+Shift+B", () -> zOrder(false)));

        Menu project = new Menu("Project");
        project.getItems().addAll(
                item("Run", "F5", this::run),
                item("Quick Preview", "Shift+F5", this::preview),
                item("Export Java Code…", "Shortcut+E", this::exportCode),
                new SeparatorMenuItem(),
                item("New Form", null, this::addForm),
                item("Set Active Form as Main", null, this::setActiveFormAsMain),
                new SeparatorMenuItem(),
                item("Set App Icon…", null, this::setAppIcon),
                item("Clear App Icon", null, this::clearAppIcon),
                new SeparatorMenuItem(),
                item("Package App…", null, () -> packageTo(AppPackager.OutputType.APP_IMAGE)),
                item("Package Installer (.exe)…", null, () -> packageTo(AppPackager.OutputType.INSTALLER)));

        return new MenuBar(file, edit, view, arrange, project);
    }

    private void rebuildRecentMenu(Menu openRecent) {
        openRecent.getItems().clear();
        var recents = RecentProjects.list();
        if (recents.isEmpty()) {
            MenuItem none = new MenuItem("No recent projects");
            none.setDisable(true);
            openRecent.getItems().add(none);
            return;
        }
        for (Path path : recents) {
            MenuItem entry = new MenuItem(path.getFileName().toString());
            entry.setOnAction(e -> openPath(path));
            openRecent.getItems().add(entry);
        }
        openRecent.getItems().add(new SeparatorMenuItem());
        MenuItem clear = new MenuItem("Clear Recently Opened");
        clear.setOnAction(e -> RecentProjects.clear());
        openRecent.getItems().add(clear);
    }

    private ToolBar buildToolBar() {
        Button run = iconButton(Feather.PLAY, "Run (F5)");
        run.setOnAction(e -> run());
        Button preview = iconButton(Feather.EYE, "Quick Preview (Shift+F5)");
        preview.setOnAction(e -> preview());
        Button export = iconButton(Feather.CODE, "Export Java Code");
        export.setOnAction(e -> exportCode());
        Button pack = iconButton(Feather.PACKAGE, "Package App");
        pack.setOnAction(e -> packageTo(AppPackager.OutputType.APP_IMAGE));

        Button addForm = iconButton(Feather.PLUS, "New Form");
        addForm.setOnAction(e -> addForm());
        Button setMain = iconButton(Feather.STAR, "Set Active Form as Main");
        setMain.setOnAction(e -> setActiveFormAsMain());

        ToggleButton snap = new ToggleButton(null, new FontIcon(Feather.GRID));
        snap.setSelected(true);
        snap.setTooltip(new Tooltip("Snap to grid"));
        snap.setOnAction(e -> canvas.setSnapEnabled(snap.isSelected()));

        ComboBox<Integer> zoomBox = new ComboBox<>();
        zoomBox.getItems().addAll(50, 75, 100, 125, 150, 200);
        zoomBox.setValue(100);
        zoomBox.setConverter(new StringConverter<>() {
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
                rulerGrid.getTransforms().setAll(new Scale(z, z));
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new ToolBar(run, preview, export, pack,
                new Separator(), addForm, setMain,
                new Separator(), snap,
                spacer, new Label("Zoom:"), zoomBox);
    }

    private Button iconButton(Feather glyph, String tooltip) {
        Button button = new Button(null, new FontIcon(glyph));
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    private MenuItem item(String text, String accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(KeyCombination.keyCombination(accelerator));
        }
        item.setOnAction(e -> action.run());
        return item;
    }

    private void setDarkTheme(boolean dark) {
        darkTheme = dark;
        Application.setUserAgentStylesheet(dark
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        var rootClasses = stage.getScene().getRoot().getStyleClass();
        rootClasses.remove("dark");
        if (dark) {
            rootClasses.add("dark");
        }
    }

    // ------------------------------------------------------------ form tabs

    private void bindProject() {
        refreshFormTabs();
        bindActiveForm();
    }

    private void bindActiveForm() {
        canvas.setModel(model);
        inspector.setModel(model);
        tree.setModel(model);
        inspector.showForm();
        eventEditor.showForm(model);
        updateRulers();
    }

    private void refreshFormTabs() {
        formTabsUpdating = true;
        formTabs.getTabs().clear();
        for (FormModel form : project.getForms()) {
            Tab tab = new Tab(tabTitle(form));
            tab.setUserData(form);
            tab.setOnCloseRequest(e -> {
                e.consume();
                deleteForm(form);
            });
            MenuItem setMain = new MenuItem("Set as Main");
            setMain.setOnAction(e -> {
                undoManager.checkpoint(null);
                project.setMainForm(form.getName());
                refreshFormTabs();
                markDirty();
            });
            tab.setContextMenu(new ContextMenu(setMain));
            formTabs.getTabs().add(tab);
            if (form == model) {
                formTabs.getSelectionModel().select(tab);
            }
        }
        formTabsUpdating = false;
    }

    private String tabTitle(FormModel form) {
        return form.getName() + (form.getName().equals(project.getMainForm()) ? " ★" : "");
    }

    private void addForm() {
        undoManager.checkpoint(null);
        model = project.addForm();
        bindProject();
        markDirty();
        status.setText("Added " + model.getName());
    }

    private void deleteForm(FormModel form) {
        if (project.getForms().size() <= 1) {
            status.setText("A project needs at least one form");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete form \"" + form.getName() + "\" and everything on it?");
        confirm.setHeaderText("Delete form");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        undoManager.checkpoint(null);
        project.removeForm(form);
        if (model == form) {
            model = project.effectiveMain();
        }
        bindProject();
        markDirty();
    }

    private void setActiveFormAsMain() {
        undoManager.checkpoint(null);
        project.setMainForm(model.getName());
        refreshFormTabs();
        markDirty();
        status.setText(model.getName() + " is now the startup form");
    }

    private void updateRulers() {
        hRuler.redraw(model.getWidth());
        vRuler.redraw(model.getHeight());
    }

    // -------------------------------------------------------------- z-order

    private void zOrder(boolean front) {
        var selected = canvas.getSelectionList();
        if (selected.isEmpty()) {
            return;
        }
        undoManager.checkpoint(null);
        for (var c : selected) {
            if (front) {
                model.toFront(c);
            } else {
                model.toBack(c);
            }
        }
        canvas.rebuildPreservingSelection();
        tree.refresh();
        tree.select(canvas.getSelected());
        markDirty();
    }

    // ------------------------------------------------------------- projects

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

    private void newFromTemplate() {
        var templates = Templates.all();
        ChoiceDialog<String> dialog = new ChoiceDialog<>(
                templates.get(0).name(), templates.stream().map(Templates.Template::name).toList());
        dialog.setTitle("New from Template");
        dialog.setHeaderText("Choose a starter template");
        dialog.setContentText("Template:");
        dialog.showAndWait().ifPresent(name -> templates.stream()
                .filter(t -> t.name().equals(name)).findFirst()
                .ifPresent(this::loadTemplate));
    }

    private void loadTemplate(Templates.Template template) {
        project = template.factory().get();
        model = project.effectiveMain();
        currentFile = null;
        dirty = false;
        undoManager.clear();
        bindProject();
        showDesigner();
        updateTitle();
        status.setText("Created from template: " + template.name());
    }

    private void openProject() {
        FileChooser chooser = projectChooser();
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            openPath(file.toPath());
        }
    }

    private void openPath(Path path) {
        try {
            project = ProjectIO.load(path);
            model = project.effectiveMain();
            currentFile = path;
            dirty = false;
            undoManager.clear();
            RecentProjects.add(path);
            bindProject();
            showDesigner();
            updateTitle();
            status.setText("Opened " + path.getFileName());
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
            RecentProjects.add(currentFile);
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

    private FileChooser projectChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Dragifier Project");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Dragifier project (*.dragifier)", "*.dragifier"));
        return chooser;
    }

    // ------------------------------------------------------- run/export/package

    private void run() {
        console.clear();
        bottomTabs.getSelectionModel().select(1);
        AppRunner.run(project,
                s -> {
                    status.setText(s);
                    console.append("[dragifier] " + s);
                },
                this::errorText,
                console::append);
    }

    private void preview() {
        PreviewWindow.show(model, stage);
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

    private void setAppIcon() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose App Icon");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Icon (*.png for window icon, *.ico for exe icon)", "*.png", "*.ico"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            undoManager.checkpoint(null);
            project.setIconData(java.util.Base64.getEncoder().encodeToString(bytes));
            project.setIconFormat(file.getName().toLowerCase().endsWith(".ico") ? "ico" : "png");
            markDirty();
            status.setText("App icon set (" + project.getIconFormat() + "): " + file.getName());
        } catch (IOException ex) {
            error("Could not read icon", ex);
        }
    }

    private void clearAppIcon() {
        if (project.getIconData().isEmpty()) {
            return;
        }
        undoManager.checkpoint(null);
        project.setIconData("");
        project.setIconFormat("");
        markDirty();
        status.setText("App icon cleared");
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
                this::offerOpenFolder);
    }

    private void offerOpenFolder(Path exe) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Packaging finished");
        alert.setHeaderText("Packaged successfully");
        alert.setContentText(exe + "\n\nOpen the output folder?");
        alert.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                try {
                    new ProcessBuilder("explorer.exe", "/select,", exe.toString()).start();
                } catch (IOException ignored) {
                    // opening Explorer is best-effort
                }
            }
        });
    }

    // ------------------------------------------------------------ undo/misc

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
        area.getStyleClass().add("console-area");
        alert.getDialogPane().setContent(area);
        alert.setResizable(true);
        alert.showAndWait();
    }
}
