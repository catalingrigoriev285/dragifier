package dev.dragifier.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import dev.dragifier.codegen.JavaCodeGenerator;
import dev.dragifier.io.ProjectIO;
import dev.dragifier.io.RecentProjects;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import dev.dragifier.model.ProjectModel;
import dev.dragifier.model.Templates;
import dev.dragifier.packager.AppPackager;
import dev.dragifier.runner.AppRunner;
import dev.dragifier.undo.UndoManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
    private SplitPane designerSplit;
    private SplitPane centerSplit;
    private Node editorHolder;
    private Node bottomHolder;
    private Node canvasScroll;
    /** Hosts whatever the selected top-strip tab shows: the canvas, or a promoted code pane. */
    private final StackPane centerContent = new StackPane();
    private final Button promoteButton = new Button(null, new FontIcon(Feather.MAXIMIZE_2));
    private final Button demoteButton = new Button(null, new FontIcon(Feather.MINIMIZE_2));
    private double savedCenterDivider = 0.72;

    /** The two code panes, each either docked in {@link #bottomTabs} or promoted into {@link #formTabs}. */
    private Tab eventsTab;
    private Tab consoleTab;
    /** Where a promoted code tab sits in the top strip, remembered across tab-strip rebuilds. */
    private final Map<Tab, Integer> promotedIndex = new HashMap<>();

    private ToolBar toolBar;
    private final List<Menu> designerMenus = new ArrayList<>();
    private final List<MenuItem> designerViewItems = new ArrayList<>();

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
            focusCodeTab(eventsTab);
            eventEditor.focusCode();
        });
        canvas.setOnCheckpoint(undoManager::checkpoint);
        inspector.setCheckpoint(() -> undoManager.checkpoint(null));
        eventEditor.setCheckpoint(undoManager::checkpoint);
        eventEditor.setOnEdited(this::markDirty);
        eventEditor.setFormNames(() -> project.getForms().stream().map(FormModel::getName).toList());
        eventEditor.setContextForm(() -> model);
        tree.setOnPick(canvas::select);
        tree.setOnMove(this::applyTreeMove);
        canvas.setOnFormResized(() -> {
            updateRulers();
            if (canvas.getSelected() == null) {
                inspector.showForm();
            }
            markDirty();
        });
        inspector.setOnComponentEdited(c -> {
            canvas.refresh(c);
            tree.refresh();
            tree.select(c);
            markDirty();
        });
        inspector.setOnEditEvent((c, key) -> {
            focusCodeTab(eventsTab);
            if (c == null) {
                eventEditor.showForm(model);
            } else {
                eventEditor.showComponent(c);
            }
            eventEditor.selectEvent(key);
            eventEditor.focusCode();
        });
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
        inspector.setRenamer((c, newId) -> {
            if (!model.canRename(c, newId)) {
                status.setText("Invalid or duplicate id: " + newId);
                return false;
            }
            undoManager.checkpoint(null);
            model.renameComponent(c, newId);
            tree.refresh();
            tree.select(c);
            eventEditor.showComponent(c);
            markDirty();
            status.setText("Renamed to " + newId);
            return true;
        });
        canvas.setOnRenameRequest(inspector::focusIdField);
        canvas.setOnZOrderRequest(this::zOrder);
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

        Node leftPanel = buildLeftPanel();
        Node designerCenter = buildDesignerCenter();
        designerSplit = new SplitPane(leftPanel, designerCenter, inspector);
        designerSplit.setOrientation(Orientation.HORIZONTAL);
        SplitPane.setResizableWithParent(leftPanel, false);
        SplitPane.setResizableWithParent(inspector, false);
        fitSidePanels((Region) leftPanel, inspector);

        toolBar = buildToolBar();
        root.setTop(new VBox(buildMenuBar(), toolBar));
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
            if (tab.getUserData() instanceof FormModel picked) {
                if (picked != model) {
                    model = picked;
                    bindActiveForm();
                }
                showInCenter(canvasScroll);
            } else {
                // a promoted code tab: its pane renders full height under the shared strip
                showInCenter(tab == eventsTab ? eventEditor : console);
            }
            updateCornerButtons();
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
        canvasScroll = scroll;

        // tabs hold no content (centerContent below is the editor), so their computed
        // min height is ~0 - pin it so the tab strip can never be squeezed away
        formTabs.setMinHeight(Region.USE_PREF_SIZE);
        centerContent.getChildren().add(canvasScroll);
        VBox editor = new VBox(formTabs, centerContent);
        VBox.setVgrow(centerContent, Priority.ALWAYS);

        eventsTab = codeTab("Events", Feather.ZAP, eventEditor);
        consoleTab = codeTab("Console", Feather.TERMINAL, console);
        bottomTabs.getTabs().addAll(eventsTab, consoleTab);
        bottomTabs.getSelectionModel().selectedItemProperty()
                .addListener((obs, was, tab) -> updateCornerButtons());

        // maximize/restore toggles overlaid at the right end of each tab strip
        cornerButton(promoteButton, "Maximize the selected code pane (Shortcut+Shift+M)",
                () -> promote(bottomTabs.getSelectionModel().getSelectedItem()));
        cornerButton(demoteButton, "Send the selected code pane back down (Shortcut+Shift+M)",
                () -> demote(formTabs.getSelectionModel().getSelectedItem()));

        editorHolder = new StackPane(editor, demoteButton);
        bottomHolder = new StackPane(bottomTabs, promoteButton);

        centerSplit = new SplitPane(editorHolder, bottomHolder);
        centerSplit.setOrientation(Orientation.VERTICAL);
        centerSplit.setDividerPositions(savedCenterDivider);
        updateCornerButtons();
        return centerSplit;
    }

    private Tab codeTab(String title, Feather glyph, Node pane) {
        Tab tab = new Tab();
        tab.setClosable(false);
        tab.setContent(pane);
        HBox handle = new HBox(6, new FontIcon(glyph), new Label(title));
        handle.setAlignment(Pos.CENTER_LEFT);
        handle.getStyleClass().add("drag-tab");
        tab.setGraphic(handle);
        DraggableTabs.enable(tab, handle, this::onTabsReordered);

        MenuItem toggle = new MenuItem();
        toggle.setOnAction(e -> toggleCodePane(tab));
        ContextMenu menu = new ContextMenu(toggle);
        menu.setOnShowing(e -> toggle.setText(isPromoted(tab) ? "Restore" : "Maximize"));
        tab.setContextMenu(menu);
        return tab;
    }

    private void cornerButton(Button button, String tooltip, Runnable action) {
        button.getStyleClass().addAll("flat", "button-icon", "small", "pane-max-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        button.setOnAction(e -> action.run());
        StackPane.setAlignment(button, Pos.TOP_RIGHT);
        StackPane.setMargin(button, new Insets(3, 6, 0, 0));
    }

    private boolean isPromoted(Tab tab) {
        return tab != null && tab.getTabPane() == formTabs && !(tab.getUserData() instanceof FormModel);
    }

    /** Shows exactly one node in the area under the top tab strip. */
    private void showInCenter(Node node) {
        if (node != null && (centerContent.getChildren().size() != 1
                || centerContent.getChildren().get(0) != node)) {
            centerContent.getChildren().setAll(node);
        }
    }

    /** Moves a code pane up into the shared top strip, where it renders at full height. */
    private void promote(Tab tab) {
        if (tab == null || isPromoted(tab)) {
            return;
        }
        Node pane = tab.getContent();
        tab.setContent(null);
        bottomTabs.getTabs().remove(tab);
        if (bottomTabs.getTabs().isEmpty()) {
            double[] positions = centerSplit.getDividerPositions();
            if (positions.length > 0) {
                savedCenterDivider = positions[0];
            }
            centerSplit.getItems().remove(bottomHolder);
        }
        int index = promotedIndex.getOrDefault(tab, formTabs.getTabs().size());
        formTabs.getTabs().add(Math.max(0, Math.min(index, formTabs.getTabs().size())), tab);
        showInCenter(pane);
        formTabs.getSelectionModel().select(tab);
        updateCornerButtons();
    }

    /** Sends a promoted code pane back down to the docked bottom strip. */
    private void demote(Tab tab) {
        if (!isPromoted(tab)) {
            return;
        }
        promotedIndex.put(tab, formTabs.getTabs().indexOf(tab));
        formTabs.getTabs().remove(tab);
        tab.setContent(tab == eventsTab ? eventEditor : console);
        // keep Events left of Console however the strips were reordered
        bottomTabs.getTabs().add(tab == eventsTab ? 0 : bottomTabs.getTabs().size(), tab);
        if (!centerSplit.getItems().contains(bottomHolder)) {
            centerSplit.getItems().add(bottomHolder);
            centerSplit.setDividerPositions(savedCenterDivider);
        }
        bottomTabs.getSelectionModel().select(tab);
        if (!isPromoted(formTabs.getSelectionModel().getSelectedItem())) {
            showInCenter(canvasScroll);
        }
        updateCornerButtons();
    }

    private void toggleCodePane(Tab tab) {
        if (isPromoted(tab)) {
            demote(tab);
        } else {
            promote(tab);
        }
    }

    /** Shortcut+Shift+M: send the selected promoted pane down, else bring the selected docked one up. */
    private void toggleSelectedCodePane() {
        Tab top = formTabs.getSelectionModel().getSelectedItem();
        if (isPromoted(top)) {
            demote(top);
        } else {
            promote(bottomTabs.getSelectionModel().getSelectedItem());
        }
    }

    /** Each corner button only makes sense while its own strip has a code tab selected. */
    private void updateCornerButtons() {
        boolean canDemote = isPromoted(formTabs.getSelectionModel().getSelectedItem());
        demoteButton.setVisible(canDemote);
        demoteButton.setManaged(canDemote);
        boolean canPromote = bottomTabs.getSelectionModel().getSelectedItem() != null;
        promoteButton.setVisible(canPromote);
        promoteButton.setManaged(canPromote);
    }

    /** After a drag the strip order is the truth: write it back to the project and remember it. */
    private void onTabsReordered() {
        rememberPromotedIndices();
        List<FormModel> stripOrder = formTabs.getTabs().stream()
                .map(Tab::getUserData)
                .filter(FormModel.class::isInstance)
                .map(FormModel.class::cast)
                .toList();
        if (stripOrder.equals(project.getForms())) {
            return;  // only code tabs moved — nothing to record in the project
        }
        undoManager.checkpoint(null);
        for (int i = 0; i < stripOrder.size(); i++) {
            project.moveForm(stripOrder.get(i), i);
        }
        markDirty();
        status.setText("Reordered forms");
    }

    private void rememberPromotedIndices() {
        for (Tab tab : List.of(eventsTab, consoleTab)) {
            if (isPromoted(tab)) {
                promotedIndex.put(tab, formTabs.getTabs().indexOf(tab));
            }
        }
    }

    /** Selects a code pane wherever it currently lives, promoted or docked. */
    private void focusCodeTab(Tab tab) {
        if (isPromoted(tab)) {
            formTabs.getSelectionModel().select(tab);
        } else {
            bottomTabs.getSelectionModel().select(tab);
        }
    }

    /**
     * Opens each side panel at the width its own content needs, once the split has a real width.
     * Left alone a SplitPane splits evenly, and the fixed 0.15/0.82 fractions this used to use
     * clipped the property editors and the component tree behind horizontal scrollbars. The
     * positions are applied on the pulse after layout — SplitPane rewrites them during its own
     * layout pass, so setting them inline is silently discarded.
     */
    private void fitSidePanels(Region leftPanel, Region rightPanel) {
        designerSplit.widthProperty().addListener(new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Number> obs, Number was, Number is) {
                if (is.doubleValue() <= 0) {
                    return;  // still on the welcome page, nothing laid out yet
                }
                designerSplit.widthProperty().removeListener(this);
                Platform.runLater(() -> {
                    double total = designerSplit.getWidth();
                    if (total > 0) {
                        designerSplit.setDividerPositions(
                                leftPanel.getPrefWidth() / total,
                                1 - rightPanel.getPrefWidth() / total);
                    }
                });
            }
        });
    }

    private Node buildLeftPanel() {
        PalettePane palette = new PalettePane();
        SplitPane left = new SplitPane(palette, tree);
        left.setOrientation(Orientation.VERTICAL);
        left.setDividerPositions(0.6);
        // as wide as the hungriest of the two stacked panes, so neither has to scroll sideways
        double width = Math.max(palette.getPrefWidth(), tree.getPrefWidth());
        left.setPrefWidth(width);
        left.setMinWidth(width);
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
        root.setCenter(welcomePane);
        setDesignerChromeEnabled(false);
    }

    private void showDesigner() {
        root.setCenter(designerSplit);
        setDesignerChromeEnabled(true);
    }

    /** On the welcome page there is nothing to act on: hide the toolbar and the designer-only menus. */
    private void setDesignerChromeEnabled(boolean enabled) {
        toolBar.setVisible(enabled);
        toolBar.setManaged(enabled);
        for (Menu menu : designerMenus) {
            menu.setVisible(enabled);
        }
        for (MenuItem menuItem : designerViewItems) {
            menuItem.setVisible(enabled);
        }
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
        SeparatorMenuItem viewSeparator = new SeparatorMenuItem();
        MenuItem maximizeEditor =
                item("Maximize / Restore Code Pane", "Shortcut+Shift+M", this::toggleSelectedCodePane);
        view.getItems().addAll(darkItem, viewSeparator, maximizeEditor);
        designerViewItems.addAll(List.of(viewSeparator, maximizeEditor));

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
                item("Send to Back", "Shortcut+Shift+B", () -> zOrder(false)),
                item("Order…", null, this::showOrderDialog));

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

        designerMenus.addAll(List.of(edit, arrange, project));
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
        Tab wasSelected = formTabs.getSelectionModel().getSelectedItem();
        Tab keepSelected = isPromoted(wasSelected) ? wasSelected : null;
        rememberPromotedIndices();
        List<Tab> promoted = Stream.of(eventsTab, consoleTab).filter(this::isPromoted).toList();
        formTabs.getTabs().clear();
        Tab activeFormTab = null;
        for (FormModel form : project.getForms()) {
            Tab tab = new Tab();
            tab.setUserData(form);
            Label handle = new Label(tabTitle(form));
            handle.getStyleClass().add("drag-tab");
            tab.setGraphic(handle);
            DraggableTabs.enable(tab, handle, this::onTabsReordered);
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
                activeFormTab = tab;
            }
        }
        // put the promoted code tabs back where the user left them (ascending, so inserts don't shift)
        for (Tab tab : promoted.stream()
                .sorted(Comparator.comparingInt(t -> promotedIndex.getOrDefault(t, Integer.MAX_VALUE)))
                .toList()) {
            int index = promotedIndex.getOrDefault(tab, formTabs.getTabs().size());
            formTabs.getTabs().add(Math.max(0, Math.min(index, formTabs.getTabs().size())), tab);
        }
        formTabsUpdating = false;
        Tab select = keepSelected != null ? keepSelected : activeFormTab;
        if (select != null) {
            formTabs.getSelectionModel().select(select);
        }
        updateCornerButtons();
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

    /** Orders the selected component's sibling group (top-level components when nothing is selected). */
    private void showOrderDialog() {
        FormComponent selected = canvas.getSelected();
        var siblings = selected == null ? model.childrenOf(null) : model.siblingsOf(selected);
        OrderDialog.show(stage, siblings).ifPresent(order -> {
            undoManager.checkpoint(null);
            model.reorderSiblings(order);
            canvas.rebuildPreservingSelection();
            tree.refresh();
            tree.select(canvas.getSelected());
            markDirty();
        });
    }

    /** Component tree drag-and-drop: moves {@code moved} under {@code newParent} at {@code index} among its siblings. */
    private void applyTreeMove(FormComponent moved, FormComponent newParent, int index) {
        if (moved == newParent || (newParent != null && model.isAncestor(moved, newParent))) {
            return;
        }
        undoManager.checkpoint(null);
        if (newParent != model.parentOf(moved)) {
            String slot = newParent == null ? "" : switch (newParent.getType().kind) {
                case TABS, SPLIT -> "0";
                case DOCK -> "CENTER";
                case GRID -> "0,0";
                default -> "";
            };
            if (!model.reparent(moved, newParent, slot)) {
                return;
            }
        }
        var siblings = model.childrenOf(newParent);
        siblings.remove(moved);
        siblings.add(Math.max(0, Math.min(siblings.size(), index)), moved);
        model.reorderSiblings(siblings);
        canvas.rebuildPreservingSelection();
        tree.refresh();
        tree.select(moved);
        markDirty();
    }

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
        focusCodeTab(consoleTab);
        AppRunner.run(project,
                s -> {
                    status.setText(s);
                    console.append("[dragifier] " + s);
                },
                this::errorText,
                console::append,
                this::showProblems);
    }

    private void showProblems(AppRunner.CompileResult result) {
        for (AppRunner.CompileError err : result.errors()) {
            console.append("[error] " + err.file() + ":" + err.line() + " " + err.message());
        }
        ProblemsDialog.show(stage, result.errors(), err -> {
            var entry = result.map().resolve(err.file(), err.line());
            if (entry == null) {
                errorText("Compilation failed", result.errorDetails());
                return;
            }
            navigateToError(entry, err);
        });
    }

    private void navigateToError(dev.dragifier.codegen.SourceMap.Entry entry, AppRunner.CompileError err) {
        FormModel form = project.findForm(entry.formName());
        if (form != null && form != model) {
            model = form;
            bindProject();
        }
        FormComponent target = entry.componentId() == null ? null
                : model.getComponents().stream()
                        .filter(c -> c.getId().equals(entry.componentId()))
                        .findFirst().orElse(null);
        canvas.select(target);
        eventEditor.selectEvent(entry.eventKey());
        focusCodeTab(eventsTab);
        long delta = Math.max(0, err.line() - entry.generatedLine());
        eventEditor.focusLine(entry.userLine() + (int) delta);
        status.setText("Error in " + (entry.componentId() == null ? "form" : entry.componentId())
                + " → " + entry.eventKey());
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
