package dev.dragifier.codegen;

import dev.dragifier.model.ComponentType;
import dev.dragifier.model.ContainerGeometry;
import dev.dragifier.model.CssInsets;
import dev.dragifier.model.DockLayout;
import dev.dragifier.model.EventSpec;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import dev.dragifier.model.ProjectModel;
import dev.dragifier.ui.Renderer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates Java sources from a project: one {@code Stage} subclass per form
 * (named after the form, components as fields so event handlers can reference
 * them by id) plus a {@code Main} application class that opens the main form.
 * Opening another form from user code is plain Java: {@code new Form2().show();}
 */
public final class JavaCodeGenerator {

    public static final String MAIN_CLASS = "Main";
    /** Entry point that does not extend Application, so classpath launches work too. */
    public static final String LAUNCHER_CLASS = "Launcher";
    public static final String ICON_RESOURCE = "app_icon.png";

    private JavaCodeGenerator() {}

    /** The form's generated class name, derived from its name. */
    public static String className(FormModel form) {
        StringBuilder sb = new StringBuilder();
        for (char ch : form.getName().toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                sb.append(ch);
            }
        }
        if (sb.isEmpty() || Character.isDigit(sb.charAt(0))) {
            return "Form";
        }
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        return sb.toString();
    }

    /** The jar resource name for an Image component's bytes. */
    public static String imageResource(FormModel form, FormComponent c) {
        return className(form) + "_" + c.getId() + ".img";
    }

    /** All sources for the project, filename → content. */
    public static Map<String, String> generateProject(ProjectModel project) {
        return generateProject(project, new SourceMap());
    }

    /** As above, also recording where each user code line lands in {@code map}. */
    public static Map<String, String> generateProject(ProjectModel project, SourceMap map) {
        Map<String, String> sources = new LinkedHashMap<>();
        for (FormModel form : project.getForms()) {
            sources.put(className(form) + ".java", formSource(project, form, map));
        }
        sources.put(MAIN_CLASS + ".java", mainSource(project));
        sources.put(LAUNCHER_CLASS + ".java",
                "public class " + LAUNCHER_CLASS + " {\n"
                + "    public static void main(String[] args) {\n"
                + "        javafx.application.Application.launch(" + MAIN_CLASS + ".class, args);\n"
                + "    }\n"
                + "}\n");
        sources.put(RuntimeApi.FILE_NAME, RuntimeApi.SOURCE);
        if (usesType(project, ComponentType.FILE_BROWSER)) {
            sources.put(FileBrowserApi.FILE_NAME, FileBrowserApi.SOURCE);
        }
        return sources;
    }

    private static boolean usesType(ProjectModel project, ComponentType type) {
        return project.getForms().stream()
                .flatMap(f -> f.getComponents().stream())
                .anyMatch(c -> c.getType() == type);
    }

    private static String mainSource(ProjectModel project) {
        return "import javafx.application.Application;\n"
                + "import javafx.stage.Stage;\n\n"
                + "public class " + MAIN_CLASS + " extends Application {\n\n"
                + "    @Override\n"
                + "    public void start(Stage primaryStage) {\n"
                + "        new " + className(project.effectiveMain()) + "().show();\n"
                + "    }\n\n"
                + "    public static void main(String[] args) {\n"
                + "        launch(args);\n"
                + "    }\n"
                + "}\n";
    }

    private static String formSource(ProjectModel project, FormModel form, SourceMap map) {
        String cls = className(form);
        StringBuilder out = new StringBuilder();
        out.append("import javafx.animation.Animation;\n");
        out.append("import javafx.animation.KeyFrame;\n");
        out.append("import javafx.animation.Timeline;\n");
        out.append("import javafx.beans.property.ReadOnlyStringWrapper;\n");
        out.append("import javafx.collections.ObservableList;\n");
        out.append("import javafx.geometry.Insets;\n");
        out.append("import javafx.geometry.Orientation;\n");
        out.append("import javafx.geometry.Pos;\n");
        out.append("import javafx.scene.Cursor;\n");
        out.append("import javafx.scene.Scene;\n");
        out.append("import javafx.scene.control.*;\n");
        out.append("import javafx.scene.image.Image;\n");
        out.append("import javafx.scene.image.ImageView;\n");
        out.append("import javafx.scene.layout.*;\n");
        out.append("import javafx.scene.media.Media;\n");
        out.append("import javafx.scene.media.MediaPlayer;\n");
        out.append("import javafx.scene.media.MediaView;\n");
        out.append("import javafx.scene.web.WebView;\n");
        out.append("import javafx.stage.Stage;\n");
        out.append("import javafx.util.Duration;\n");
        out.append("import java.io.File;\n\n");
        out.append("public class ").append(cls).append(" extends Stage {\n\n");
        out.append("    private final Stage stage = this;\n");
        for (FormComponent c : form.getComponents()) {
            out.append("    private ").append(javaTypeFor(c)).append(" ").append(c.getId()).append(";\n");
        }
        out.append("\n");
        out.append("    public ").append(cls).append("() {\n");
        out.append("        AnchorPane root = new AnchorPane();\n");
        out.append("        root.setPrefSize(").append(fmt(form.getWidth()))
           .append(", ").append(fmt(form.getHeight())).append(");\n\n");

        DockLayout.applyTo(form, null);
        for (FormComponent c : form.childrenOf(null)) {
            emitComponent(out, form, c, null, "root", form.getWidth(), form.getHeight(), cls, map);
        }

        appendFormEvents(out, form, cls, map);
        out.append("        setResizable(").append(form.isResizable()).append(");\n");
        if (project.hasWindowIcon()) {
            out.append("        getIcons().add(new Image(").append(cls)
               .append(".class.getResourceAsStream(\"/").append(ICON_RESOURCE).append("\")));\n");
        }
        out.append("        setTitle(\"").append(escape(form.getTitle())).append("\");\n");
        out.append("        setScene(new Scene(root));\n");
        out.append("    }\n");
        out.append("}\n");
        return out.toString();
    }

    /**
     * Emits construction, properties, events and the add-to-parent statement for
     * one component, then recurses into its children (each into the content
     * area named by its slot) so nested layouts come out parent-first.
     */
    private static void emitComponent(StringBuilder out, FormModel form, FormComponent c, FormComponent parent,
                                      String target, double parentW, double parentH, String cls, SourceMap map) {
        String var = c.getId();
        if (c.getType() == ComponentType.TIMER) {
            appendTimer(out, form, c, cls, map);
            return;
        }
        boolean autoLaid = parent != null && parent.getType().kind.isAutoLayout();
        boolean isRegion = c.getType() != ComponentType.IMAGE_VIEW && c.getType() != ComponentType.MEDIA_PLAYER;
        out.append("        ").append(var).append(" = new ").append(javaTypeFor(c)).append("();\n");
        if (hasText(c)) {
            out.append("        ").append(var).append(".setText(\"")
               .append(escape(c.getText())).append("\");\n");
        }
        appendTypeSpecific(out, form, c, cls);
        if (!autoLaid) {
            appendPosition(out, parentW, parentH, c);
        }
        if (!isRegion) {
            out.append("        ").append(var).append(".setFitWidth(").append(fmt(c.getWidth())).append(");\n");
            out.append("        ").append(var).append(".setFitHeight(").append(fmt(c.getHeight())).append(");\n");
        } else {
            out.append("        ").append(var).append(".setPrefSize(").append(fmt(c.getWidth()))
               .append(", ").append(fmt(c.getHeight())).append(");\n");
            out.append("        ").append(var).append(".setStyle(\"")
               .append(escape(Renderer.styleFor(c))).append("\");\n");
        }
        if (autoLaid && isRegion) {
            // same constraints LiveBuilder.applyLayoutSizing applies in the preview
            switch (parent.getType().kind) {
                case STACK -> out.append("        ").append(var)
                        .append(".setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);\n");
                case GRID, DOCK -> {
                    out.append("        ").append(var).append(".setMinSize(0, 0);\n");
                    out.append("        ").append(var).append(".setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);\n");
                }
                default -> { }
            }
        }
        if (c.isDisabled()) {
            out.append("        ").append(var).append(".setDisable(true);\n");
        }
        if (!c.isVisible()) {
            out.append("        ").append(var).append(".setVisible(false);\n");
        }
        if (Renderer.cursorFor(c.getCursor()) != null && !"DEFAULT".equals(c.getCursor())) {
            out.append("        ").append(var).append(".setCursor(Cursor.").append(c.getCursor()).append(");\n");
        }
        if (autoLaid) {
            double[] m = CssInsets.parse(c.getMargin());
            String host = switch (parent.getType().kind) {
                case GRID -> "GridPane";
                case DOCK -> "BorderPane";
                case STACK -> javaTypeFor(parent);
                default -> null;
            };
            if (m != null && host != null) {
                out.append("        ").append(host).append(".setMargin(").append(var).append(", new Insets(")
                   .append(dbl(m[0])).append(", ").append(dbl(m[1])).append(", ")
                   .append(dbl(m[2])).append(", ").append(dbl(m[3])).append("));\n");
            }
        }
        if (!c.getAlignment().isEmpty() && Renderer.supportsAlignment(c.getType())) {
            String pos = switch (c.getAlignment()) {
                case "CENTER" -> "Pos.CENTER";
                case "RIGHT" -> "Pos.CENTER_RIGHT";
                default -> "Pos.CENTER_LEFT";
            };
            out.append("        ").append(var).append(".setAlignment(").append(pos).append(");\n");
        }
        if (!c.getTooltip().isEmpty()) {
            boolean isControl = c.getType() != ComponentType.PANEL && c.getType() != ComponentType.IMAGE_VIEW;
            if (isControl) {
                out.append("        ").append(var).append(".setTooltip(new Tooltip(\"")
                   .append(escape(c.getTooltip())).append("\"));\n");
            } else {
                out.append("        Tooltip.install(").append(var).append(", new Tooltip(\"")
                   .append(escape(c.getTooltip())).append("\"));\n");
            }
        }
        appendEvents(out, form, c, cls, map);
        if (autoLaid && parent.getType().kind == ComponentType.ContainerKind.GRID) {
            int[] cell = ContainerGeometry.gridCell(c, parent);
            out.append("        GridPane.setHgrow(").append(var).append(", Priority.ALWAYS);\n");
            out.append("        GridPane.setVgrow(").append(var).append(", Priority.ALWAYS);\n");
            out.append("        ").append(target).append(".add(").append(var).append(", ")
               .append(cell[0]).append(", ").append(cell[1]).append(");\n\n");
        } else if (autoLaid && parent.getType().kind == ComponentType.ContainerKind.DOCK) {
            String region = ContainerGeometry.dockRegion(c);
            String setter = region.charAt(0) + region.substring(1).toLowerCase();
            out.append("        ").append(target).append(".set").append(setter).append("(").append(var).append(");\n\n");
        } else {
            out.append("        ").append(target).append(".getChildren().add(").append(var).append(");\n\n");
        }

        if (c.getType().isContainer()) {
            DockLayout.applyTo(form, c);
            for (FormComponent child : form.childrenOf(c)) {
                int slot = ContainerGeometry.slotIndex(child, c);
                emitComponent(out, form, child, c, childTarget(c, slot),
                        ContainerGeometry.contentWidth(c, slot), ContainerGeometry.contentHeight(c, slot), cls, map);
            }
        }
    }

    /** The generated variable holding the content area children of {@code c} are added to. */
    private static String childTarget(FormComponent c, int slot) {
        String var = c.getId();
        return switch (c.getType()) {
            case GROUP_BOX, SCROLL_PANE -> var + "Content";
            case TAB_PANE -> var + "Tab" + slot + "Content";
            case SPLIT_PANE -> var + "Item" + slot;
            default -> var;
        };
    }

    private static void appendTypeSpecific(StringBuilder out, FormModel form, FormComponent c, String cls) {
        String var = c.getId();
        switch (c.getType()) {
            case GROUP_BOX -> {
                out.append("        AnchorPane ").append(var).append("Content = new AnchorPane();\n");
                out.append("        ").append(var).append(".setContent(").append(var).append("Content);\n");
                out.append("        ").append(var).append(".setCollapsible(false);\n");
            }
            case SCROLL_PANE -> {
                double w = ContainerGeometry.contentWidth(c, 0);
                double h = ContainerGeometry.contentHeight(c, 0);
                for (FormComponent child : form.childrenOf(c)) {
                    w = Math.max(w, child.getX() + child.getWidth());
                    h = Math.max(h, child.getY() + child.getHeight());
                }
                out.append("        AnchorPane ").append(var).append("Content = new AnchorPane();\n");
                out.append("        ").append(var).append("Content.setPrefSize(")
                   .append(fmt(w)).append(", ").append(fmt(h)).append(");\n");
                out.append("        ").append(var).append(".setContent(").append(var).append("Content);\n");
            }
            case TAB_PANE -> {
                var titles = Renderer.tabTitles(c);
                for (int i = 0; i < titles.size(); i++) {
                    String tabVar = var + "Tab" + i;
                    out.append("        AnchorPane ").append(tabVar).append("Content = new AnchorPane();\n");
                    out.append("        Tab ").append(tabVar).append(" = new Tab(\"")
                       .append(escape(titles.get(i))).append("\", ").append(tabVar).append("Content);\n");
                    out.append("        ").append(tabVar).append(".setClosable(false);\n");
                    out.append("        ").append(var).append(".getTabs().add(").append(tabVar).append(");\n");
                }
            }
            case FILE_BROWSER -> {
                if (!c.getText().isBlank()) {
                    out.append("        ").append(var).append(".setRoot(\"").append(escape(c.getText())).append("\");\n");
                }
                var filters = Renderer.lines(c.getItems());
                if (!filters.isEmpty()) {
                    out.append("        ").append(var).append(".setFilters(");
                    for (int i = 0; i < filters.size(); i++) {
                        if (i > 0) {
                            out.append(", ");
                        }
                        out.append("\"").append(escape(filters.get(i))).append("\"");
                    }
                    out.append(");\n");
                }
            }
            case STACK_PANEL -> out.append("        ").append(var).append(".setSpacing(")
                    .append(fmt(c.getSpacing())).append(");\n");
            case GRID_PANE -> {
                out.append("        ").append(var).append(".setHgap(").append(fmt(c.getSpacing())).append(");\n");
                out.append("        ").append(var).append(".setVgap(").append(fmt(c.getSpacing())).append(");\n");
                int cols = ContainerGeometry.gridColumns(c);
                int rows = ContainerGeometry.gridRows(c);
                for (int i = 0; i < cols; i++) {
                    out.append("        ").append(var).append(".getColumnConstraints().add(new ColumnConstraints());\n");
                    out.append("        ").append(var).append(".getColumnConstraints().get(").append(i)
                       .append(").setPercentWidth(").append(100.0 / cols).append(");\n");
                }
                for (int i = 0; i < rows; i++) {
                    out.append("        ").append(var).append(".getRowConstraints().add(new RowConstraints());\n");
                    out.append("        ").append(var).append(".getRowConstraints().get(").append(i)
                       .append(").setPercentHeight(").append(100.0 / rows).append(");\n");
                }
            }
            case SPLIT_PANE -> {
                out.append("        ").append(var).append(".setOrientation(Orientation.")
                   .append("VERTICAL".equals(c.getOrientation()) ? "VERTICAL" : "HORIZONTAL").append(");\n");
                int panes = ContainerGeometry.paneCount(c);
                for (int i = 0; i < panes; i++) {
                    out.append("        AnchorPane ").append(var).append("Item").append(i).append(" = new AnchorPane();\n");
                    out.append("        ").append(var).append(".getItems().add(").append(var).append("Item").append(i).append(");\n");
                }
                double[] positions = ContainerGeometry.dividerPositions(c);
                out.append("        ").append(var).append(".setDividerPositions(");
                for (int i = 0; i < positions.length; i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    out.append(positions[i]);
                }
                out.append(");\n");
            }
            case IMAGE_VIEW -> {
                if (!c.getImageData().isEmpty()) {
                    out.append("        ").append(var).append(".setImage(new Image(")
                       .append(cls).append(".class.getResourceAsStream(\"/")
                       .append(imageResource(form, c)).append("\")));\n");
                }
            }
            case COMBO_BOX, LIST_VIEW -> {
                var items = Renderer.itemList(c);
                if (!items.isEmpty()) {
                    out.append("        ").append(var).append(".getItems().addAll(");
                    for (int i = 0; i < items.size(); i++) {
                        if (i > 0) {
                            out.append(", ");
                        }
                        out.append("\"").append(escape(items.get(i))).append("\"");
                    }
                    out.append(");\n");
                }
                if (c.getType() == ComponentType.COMBO_BOX && !c.getText().isEmpty()) {
                    out.append("        ").append(var).append(".setPromptText(\"")
                       .append(escape(c.getText())).append("\");\n");
                }
            }
            case PROGRESS_BAR -> out.append("        ").append(var).append(".setProgress(")
                    .append(c.getValue() / 100.0).append(");\n");
            case TABLE_VIEW -> {
                var columns = Renderer.lines(c.getColumns());
                for (int i = 0; i < columns.size(); i++) {
                    String colVar = var + "Col" + i;
                    final int idx = i;
                    out.append("        TableColumn<ObservableList<String>, String> ").append(colVar)
                       .append(" = new TableColumn<>(\"").append(escape(columns.get(i))).append("\");\n");
                    out.append("        ").append(colVar)
                       .append(".setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().size() > ")
                       .append(idx).append(" ? d.getValue().get(").append(idx).append(") : \"\"));\n");
                    out.append("        ").append(var).append(".getColumns().add(").append(colVar).append(");\n");
                }
            }
            case WEB_VIEW -> {
                if (!c.getText().isEmpty()) {
                    out.append("        ").append(var).append(".getEngine().load(\"")
                       .append(escape(c.getText())).append("\");\n");
                }
            }
            case MEDIA_PLAYER -> {
                if (!c.getMediaData().isEmpty()) {
                    out.append("        MediaPlayer ").append(var).append("Player = new MediaPlayer(new Media(\n")
                       .append("                new File(UI.resourceToTempFile(\"/")
                       .append(mediaResource(form, c)).append("\")).toURI().toString()));\n");
                    out.append("        ").append(var).append(".setMediaPlayer(").append(var).append("Player);\n");
                    if (!c.isDisabled()) {
                        out.append("        ").append(var).append("Player.setAutoPlay(true);\n");
                    }
                }
            }
            default -> { }
        }
    }

    /** The jar resource name for a Media component's bytes. */
    public static String mediaResource(FormModel form, FormComponent c) {
        return className(form) + "_" + c.getId() + ".media";
    }

    /**
     * Emits either AnchorPane anchors or plain layoutX/Y, per the component's
     * anchor flags; right/bottom offsets are relative to the parent's content size.
     */
    private static void appendPosition(StringBuilder out, double parentW, double parentH, FormComponent c) {
        String var = c.getId();
        boolean[] a = DockLayout.anchors(c); // left, top, right, bottom (docking overrides the flags)
        if (a[0]) {
            out.append("        AnchorPane.setLeftAnchor(").append(var).append(", ")
               .append(dbl(c.getX())).append(");\n");
        } else if (!a[2]) {
            out.append("        ").append(var).append(".setLayoutX(").append(fmt(c.getX())).append(");\n");
        }
        if (a[2]) {
            out.append("        AnchorPane.setRightAnchor(").append(var).append(", ")
               .append(dbl(parentW - c.getX() - c.getWidth())).append(");\n");
        }
        if (a[1]) {
            out.append("        AnchorPane.setTopAnchor(").append(var).append(", ")
               .append(dbl(c.getY())).append(");\n");
        } else if (!a[3]) {
            out.append("        ").append(var).append(".setLayoutY(").append(fmt(c.getY())).append(");\n");
        }
        if (a[3]) {
            out.append("        AnchorPane.setBottomAnchor(").append(var).append(", ")
               .append(dbl(parentH - c.getY() - c.getHeight())).append(");\n");
        }
    }

    /** Timers become Timelines: no node, tick code embedded in the KeyFrame. */
    private static void appendTimer(StringBuilder out, FormModel form, FormComponent c,
                                    String cls, SourceMap map) {
        String var = c.getId();
        double interval = c.getValue() <= 0 ? 1000 : c.getValue();
        out.append("        ").append(var).append(" = new Timeline(new KeyFrame(Duration.millis(")
           .append(fmt(interval)).append("), event -> {\n");
        String code = c.getEvents().get("onTick");
        if (code != null && !code.isBlank()) {
            appendUserCode(out, code, form, c.getId(), "onTick", cls, map);
        }
        out.append("        }));\n");
        out.append("        ").append(var).append(".setCycleCount(Animation.INDEFINITE);\n");
        if (!c.isDisabled()) {
            out.append("        ").append(var).append(".play();\n");
        }
        out.append("\n");
    }

    private static void appendFormEvents(StringBuilder out, FormModel form, String cls, SourceMap map) {
        for (EventSpec spec : EventSpec.forForm()) {
            String code = form.getEvents().get(spec.key());
            if (code == null || code.isBlank()) {
                continue;
            }
            out.append("        ").append(spec.setter()).append("(event -> {\n");
            appendUserCode(out, code, form, null, spec.key(), cls, map);
            out.append("        });\n");
        }
    }

    /** Appends the user's code lines, recording each one's landing spot in the map. */
    private static void appendUserCode(StringBuilder out, String code, FormModel form,
                                       String componentId, String eventKey, String cls, SourceMap map) {
        int generatedLine = 1;
        for (int i = 0; i < out.length(); i++) {
            if (out.charAt(i) == '\n') {
                generatedLine++;
            }
        }
        int userLine = 0;
        for (String line : code.split("\n", -1)) {
            map.add(new SourceMap.Entry(cls, generatedLine, form.getName(), componentId, eventKey, userLine));
            out.append("            ").append(line.stripTrailing()).append("\n");
            generatedLine++;
            userLine++;
        }
    }

    private static void appendEvents(StringBuilder out, FormModel form, FormComponent c,
                                     String cls, SourceMap map) {
        for (EventSpec spec : EventSpec.forType(c.getType())) {
            String code = c.getEvents().get(spec.key());
            if (code == null || code.isBlank()) {
                continue;
            }
            String var = c.getId();
            switch (spec.kind()) {
                case SETTER -> out.append("        ").append(var).append(".").append(spec.setter()).append("(event -> {\n");
                case VALUE_LISTENER -> out.append("        ").append(var)
                        .append(".valueProperty().addListener((obs, oldValue, newValue) -> {\n");
                case SELECTION_LISTENER -> out.append("        ").append(var)
                        .append(".getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {\n");
                case TIMER_TICK -> {
                    continue; // embedded by appendTimer, never reaches here
                }
                case FILE_CALLBACK -> out.append("        ").append(var).append(".").append(spec.setter()).append("(file -> {\n");
            }
            appendUserCode(out, code, form, c.getId(), spec.key(), cls, map);
            out.append("        });\n");
        }
    }

    private static String javaTypeFor(FormComponent c) {
        return switch (c.getType()) {
            case BUTTON -> "Button";
            case LABEL -> "Label";
            case TEXT_FIELD -> "TextField";
            case TEXT_AREA -> "TextArea";
            case CHECK_BOX -> "CheckBox";
            case SLIDER -> "Slider";
            case PANEL -> "AnchorPane";
            case GROUP_BOX -> "TitledPane";
            case SCROLL_PANE -> "ScrollPane";
            case TAB_PANE -> "TabPane";
            case SPLIT_PANE -> "SplitPane";
            case STACK_PANEL -> "HORIZONTAL".equals(c.getOrientation()) ? "HBox" : "VBox";
            case GRID_PANE -> "GridPane";
            case DOCK_PANEL -> "BorderPane";
            case COMBO_BOX -> "ComboBox<String>";
            case LIST_VIEW -> "ListView<String>";
            case RADIO_BUTTON -> "RadioButton";
            case PROGRESS_BAR -> "ProgressBar";
            case HYPERLINK -> "Hyperlink";
            case IMAGE_VIEW -> "ImageView";
            case TIMER -> "Timeline";
            case TABLE_VIEW -> "TableView<ObservableList<String>>";
            case WEB_VIEW -> "WebView";
            case MEDIA_PLAYER -> "MediaView";
            case FILE_BROWSER -> "FileBrowser";
        };
    }

    private static boolean hasText(FormComponent c) {
        return switch (c.getType()) {
            case BUTTON, LABEL, TEXT_FIELD, TEXT_AREA, CHECK_BOX, RADIO_BUTTON, HYPERLINK, GROUP_BOX -> true;
            case SLIDER, PANEL, COMBO_BOX, LIST_VIEW, PROGRESS_BAR, IMAGE_VIEW, TIMER,
                 TABLE_VIEW, WEB_VIEW, MEDIA_PLAYER, SCROLL_PANE, TAB_PANE, SPLIT_PANE,
                 STACK_PANEL, GRID_PANE, DOCK_PANEL, FILE_BROWSER -> false;
        };
    }

    private static String fmt(double v) {
        if (v == Math.floor(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    /** A {@code double} literal (always with a fraction part) for boxed-Double parameters such as anchors. */
    private static String dbl(double v) {
        return v == Math.floor(v) ? (long) v + ".0" : String.valueOf(v);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
