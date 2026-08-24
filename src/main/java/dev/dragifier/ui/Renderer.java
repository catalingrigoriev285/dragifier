package dev.dragifier.ui;

import dev.dragifier.model.ComponentType;
import dev.dragifier.model.ContainerGeometry;
import dev.dragifier.model.FormComponent;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link FormComponent} into a live JavaFX node and keeps the node in
 * sync with the model. Used by both the design canvas and the preview window,
 * and mirrored by the Java code generator. Containers expose their content
 * areas through {@link #contentPanes(Region)} so children can be nested.
 */
public final class Renderer {

    private Renderer() {}

    public static Region createNode(FormComponent c) {
        Region node = switch (c.getType()) {
            case BUTTON -> new Button();
            case LABEL -> new Label();
            case TEXT_FIELD -> new TextField();
            case TEXT_AREA -> new TextArea();
            case CHECK_BOX -> new CheckBox();
            case SLIDER -> new Slider();
            case PANEL -> new AnchorPane();
            case COMBO_BOX -> new ComboBox<String>();
            case LIST_VIEW -> new ListView<String>();
            case RADIO_BUTTON -> new RadioButton();
            case PROGRESS_BAR -> new ProgressBar(0);
            case HYPERLINK -> new Hyperlink();
            case IMAGE_VIEW -> new ImageBox();
            case TIMER -> badge(org.kordamp.ikonli.feather.Feather.CLOCK);
            case TABLE_VIEW -> new javafx.scene.control.TableView<javafx.collections.ObservableList<String>>();
            case WEB_VIEW -> badge(org.kordamp.ikonli.feather.Feather.GLOBE);
            case MEDIA_PLAYER -> badge(org.kordamp.ikonli.feather.Feather.PLAY_CIRCLE);
            case GROUP_BOX -> {
                TitledPane pane = new TitledPane("", new AnchorPane());
                pane.setCollapsible(false);
                pane.setAnimated(false);
                yield pane;
            }
            case SCROLL_PANE -> new ScrollPane(new AnchorPane());
            case TAB_PANE -> {
                TabPane tabs = new TabPane();
                tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
                yield tabs;
            }
            case SPLIT_PANE -> new SplitPane();
            case STACK_PANEL -> "HORIZONTAL".equals(c.getOrientation()) ? new HBox() : new VBox();
            case GRID_PANE -> new GridPane();
            case DOCK_PANEL -> new BorderPane();
        };
        apply(node, c);
        return node;
    }

    /** True when the live node no longer matches the component (e.g. StackPanel orientation flipped). */
    public static boolean needsNewNode(Region node, FormComponent c) {
        if (c.getType() == ComponentType.STACK_PANEL) {
            boolean horizontal = "HORIZONTAL".equals(c.getOrientation());
            return horizontal ? !(node instanceof HBox) : !(node instanceof VBox);
        }
        return false;
    }

    /**
     * Adds a child node into an auto-layout container at the child's slot:
     * StackPanel appends in sibling order, Grid uses the "col,row" cell,
     * DockPanel the named region. Mirrored by the code generator.
     */
    public static void placeChild(Region containerNode, FormComponent container, Node childNode, FormComponent child) {
        switch (container.getType().kind) {
            case GRID -> {
                int[] cell = ContainerGeometry.gridCell(child, container);
                GridPane.setConstraints(childNode, cell[0], cell[1]);
                GridPane.setHgrow(childNode, Priority.ALWAYS);
                GridPane.setVgrow(childNode, Priority.ALWAYS);
                ((GridPane) containerNode).getChildren().add(childNode);
            }
            case DOCK -> {
                BorderPane border = (BorderPane) containerNode;
                switch (ContainerGeometry.dockRegion(child)) {
                    case "TOP" -> border.setTop(childNode);
                    case "LEFT" -> border.setLeft(childNode);
                    case "RIGHT" -> border.setRight(childNode);
                    case "BOTTOM" -> border.setBottom(childNode);
                    default -> border.setCenter(childNode);
                }
            }
            default -> ((Pane) containerNode).getChildren().add(childNode);
        }
    }

    private static Label badge(org.kordamp.ikonli.feather.Feather glyph) {
        Label badge = new Label("", new org.kordamp.ikonli.javafx.FontIcon(glyph));
        badge.setAlignment(javafx.geometry.Pos.CENTER);
        return badge;
    }

    @SuppressWarnings("unchecked")
    public static void apply(Region node, FormComponent c) {
        node.setPrefSize(c.getWidth(), c.getHeight());
        node.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        node.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        if (node instanceof Labeled labeled) {
            labeled.setText(c.getText());
        } else if (node instanceof TextInputControl input) {
            input.setText(c.getText());
        } else if (node instanceof ComboBox<?> combo) {
            ((ComboBox<String>) combo).getItems().setAll(itemList(c));
            ((ComboBox<String>) combo).setPromptText(c.getText());
        } else if (node instanceof ListView<?> list) {
            ((ListView<String>) list).getItems().setAll(itemList(c));
        } else if (node instanceof ProgressBar bar) {
            bar.setProgress(c.getValue() / 100.0);
        } else if (node instanceof ImageBox box) {
            box.update(c);
        } else if (node instanceof javafx.scene.control.TableView<?> table) {
            var typed = (javafx.scene.control.TableView<javafx.collections.ObservableList<String>>) table;
            typed.getColumns().clear();
            for (String name : lines(c.getColumns())) {
                typed.getColumns().add(new javafx.scene.control.TableColumn<>(name));
            }
        } else if (node instanceof TabPane tabs) {
            syncTabs(tabs, c);
        } else if (node instanceof SplitPane split) {
            syncSplit(split, c);
        } else if (node instanceof ScrollPane scroll) {
            // the content must at least fill the viewport so children near the edge stay reachable
            if (scroll.getContent() instanceof Region content) {
                content.setMinSize(ContainerGeometry.contentWidth(c, 0), ContainerGeometry.contentHeight(c, 0));
            }
        } else if (node instanceof VBox vbox) {
            vbox.setSpacing(c.getSpacing());
        } else if (node instanceof HBox hbox) {
            hbox.setSpacing(c.getSpacing());
        } else if (node instanceof GridPane grid) {
            syncGrid(grid, c);
        }
        if (node instanceof Labeled labeled && !(node instanceof TitledPane)) {
            labeled.setAlignment(posFor(c));
        } else if (node instanceof TextField textField) {
            textField.setAlignment(posFor(c));
        }
        node.setDisable(c.isDisabled());
        applyTooltip(node, c.getTooltip());
        node.setStyle(styleFor(c));
    }

    /** Tab titles of a TabControl (one per non-blank line of {@code items}; at least one). */
    public static List<String> tabTitles(FormComponent c) {
        List<String> titles = lines(c.getItems());
        return titles.isEmpty() ? List.of("Tab 1") : titles;
    }

    private static void syncTabs(TabPane tabs, FormComponent c) {
        List<String> titles = tabTitles(c);
        if (tabs.getTabs().size() != titles.size()) {
            // count changed: rebuild pages (the canvas re-hosts children afterwards)
            List<Tab> fresh = new ArrayList<>();
            for (String title : titles) {
                Tab tab = new Tab(title, new AnchorPane());
                tab.setClosable(false);
                fresh.add(tab);
            }
            tabs.getTabs().setAll(fresh);
        } else {
            for (int i = 0; i < titles.size(); i++) {
                tabs.getTabs().get(i).setText(titles.get(i));
            }
        }
    }

    /** Equal percent columns/rows, so cells share the container evenly (mirrored by the code generator). */
    private static void syncGrid(GridPane grid, FormComponent c) {
        grid.setHgap(c.getSpacing());
        grid.setVgap(c.getSpacing());
        int cols = ContainerGeometry.gridColumns(c);
        int rows = ContainerGeometry.gridRows(c);
        List<ColumnConstraints> columns = new ArrayList<>();
        for (int i = 0; i < cols; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / cols);
            columns.add(cc);
        }
        grid.getColumnConstraints().setAll(columns);
        List<RowConstraints> rowList = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            RowConstraints rc = new RowConstraints();
            rc.setPercentHeight(100.0 / rows);
            rowList.add(rc);
        }
        grid.getRowConstraints().setAll(rowList);
    }

    private static void syncSplit(SplitPane split, FormComponent c) {
        split.setOrientation("VERTICAL".equals(c.getOrientation()) ? Orientation.VERTICAL : Orientation.HORIZONTAL);
        int panes = ContainerGeometry.paneCount(c);
        if (split.getItems().size() != panes) {
            List<Node> fresh = new ArrayList<>();
            for (int i = 0; i < panes; i++) {
                fresh.add(new AnchorPane());
            }
            split.getItems().setAll(fresh);
        }
        split.setDividerPositions(ContainerGeometry.dividerPositions(c));
    }

    /**
     * The content areas of a container node, indexed by slot (tab/pane index).
     * Single-area containers return one pane; non-containers return nothing.
     */
    public static List<Pane> contentPanes(Region node) {
        if (node instanceof TitledPane titled) {
            return titled.getContent() instanceof Pane p ? List.of(p) : List.of();
        }
        if (node instanceof ScrollPane scroll) {
            return scroll.getContent() instanceof Pane p ? List.of(p) : List.of();
        }
        if (node instanceof TabPane tabs) {
            List<Pane> panes = new ArrayList<>();
            for (Tab tab : tabs.getTabs()) {
                panes.add((Pane) tab.getContent());
            }
            return panes;
        }
        if (node instanceof SplitPane split) {
            List<Pane> panes = new ArrayList<>();
            for (Node item : split.getItems()) {
                panes.add((Pane) item);
            }
            return panes;
        }
        if (node instanceof AnchorPane || node instanceof VBox || node instanceof HBox
                || node instanceof GridPane || node instanceof BorderPane) {
            return List.of((Pane) node);
        }
        return List.of();
    }

    /** True for types whose text alignment can be set. */
    public static boolean supportsAlignment(ComponentType type) {
        return switch (type) {
            case BUTTON, LABEL, CHECK_BOX, RADIO_BUTTON, HYPERLINK, TEXT_FIELD -> true;
            default -> false;
        };
    }

    private static javafx.geometry.Pos posFor(FormComponent c) {
        return switch (c.getAlignment()) {
            case "LEFT" -> javafx.geometry.Pos.CENTER_LEFT;
            case "CENTER" -> javafx.geometry.Pos.CENTER;
            case "RIGHT" -> javafx.geometry.Pos.CENTER_RIGHT;
            default -> switch (c.getType()) {
                case BUTTON, TIMER, MEDIA_PLAYER -> javafx.geometry.Pos.CENTER;
                case WEB_VIEW -> javafx.geometry.Pos.CENTER; // badge shows the URL centered
                default -> javafx.geometry.Pos.CENTER_LEFT;
            };
        };
    }

    private static void applyTooltip(Region node, String text) {
        javafx.scene.control.Tooltip existing =
                (javafx.scene.control.Tooltip) node.getProperties().get("dragifier.tooltip");
        if (text.isEmpty()) {
            if (existing != null) {
                javafx.scene.control.Tooltip.uninstall(node, existing);
                node.getProperties().remove("dragifier.tooltip");
            }
        } else if (existing == null) {
            javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(text);
            javafx.scene.control.Tooltip.install(node, tooltip);
            node.getProperties().put("dragifier.tooltip", tooltip);
        } else {
            existing.setText(text);
        }
    }

    /** Non-blank lines of the component's items text. */
    public static List<String> itemList(FormComponent c) {
        return lines(c.getItems());
    }

    /** Non-blank trimmed lines of a multi-line property value. */
    public static List<String> lines(String value) {
        return value.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** CSS style string for a component; shared with the code generator. */
    public static String styleFor(FormComponent c) {
        StringBuilder style = new StringBuilder();
        style.append("-fx-font-size: ").append(c.getFontSize()).append("px;");
        if (!c.getTextColor().isEmpty()) {
            style.append(" -fx-text-fill: ").append(c.getTextColor()).append(";");
        }
        if (!c.getBackground().isEmpty()) {
            style.append(" -fx-background-color: ").append(c.getBackground()).append(";");
        } else if (c.getType() == ComponentType.PANEL) {
            style.append(" -fx-background-color: #f4f4f4; -fx-border-color: #c0c0c0;");
        } else if (c.getType() == ComponentType.SPLIT_PANE || c.getType() == ComponentType.SCROLL_PANE) {
            style.append(" -fx-border-color: #c0c0c0;");
        } else if (c.getType() == ComponentType.IMAGE_VIEW && c.getImageData().isEmpty()) {
            style.append(" -fx-border-color: #c0c0c0; -fx-border-style: dashed;");
        } else if (c.getType() == ComponentType.TIMER
                || c.getType() == ComponentType.WEB_VIEW
                || c.getType() == ComponentType.MEDIA_PLAYER) {
            style.append(" -fx-border-color: #c0c0c0; -fx-border-style: dashed; -fx-background-color: #f4f4f422;");
        }
        return style.toString();
    }
}
