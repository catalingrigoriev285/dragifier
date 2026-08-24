package dev.dragifier.ui;

import dev.dragifier.model.ComponentType;

import java.util.ArrayList;
import java.util.List;

/** Curated completion entries for the event code editor. */
public final class CompletionCatalog {

    private CompletionCatalog() {}

    private static final List<String> COMMON = List.of(
            "setDisable(true)", "setDisable(false)", "setVisible(true)", "setVisible(false)",
            "setOpacity(0.5)", "requestFocus()");

    public static List<String> methodsFor(ComponentType type) {
        List<String> entries = new ArrayList<>(switch (type) {
            case BUTTON, LABEL, HYPERLINK -> List.of("setText(\"\")", "getText()");
            case CHECK_BOX, RADIO_BUTTON -> List.of(
                    "setText(\"\")", "getText()", "isSelected()", "setSelected(true)");
            case TEXT_FIELD, TEXT_AREA -> List.of(
                    "getText()", "setText(\"\")", "clear()", "setPromptText(\"\")");
            case SLIDER -> List.of("getValue()", "setValue(0)");
            case COMBO_BOX -> List.of(
                    "getValue()", "setValue(\"\")", "getItems().add(\"\")", "getItems().clear()");
            case LIST_VIEW -> List.of(
                    "getItems().add(\"\")", "getItems().clear()",
                    "getSelectionModel().getSelectedItem()");
            case PROGRESS_BAR -> List.of("setProgress(0.5)", "getProgress()");
            case PANEL -> List.of("getChildren()");
            case IMAGE_VIEW -> List.of("setImage(null)");
            case TIMER -> List.of("play()", "pause()", "stop()", "setRate(2)");
            case TABLE_VIEW -> List.of(
                    "getItems().add(UI.row(\"\"))", "getItems().clear()",
                    "getSelectionModel().getSelectedItem()", "getItems().size()");
            case WEB_VIEW -> List.of(
                    "getEngine().load(\"https://\")", "getEngine().reload()", "getEngine().getLocation()");
            case MEDIA_PLAYER -> List.of(
                    "getMediaPlayer().play()", "getMediaPlayer().pause()", "getMediaPlayer().stop()");
            case GROUP_BOX -> List.of("setText(\"\")", "getText()", "setExpanded(true)", "getContent()");
            case SCROLL_PANE -> List.of("setVvalue(0)", "setHvalue(0)", "getContent()");
            case TAB_PANE -> List.of(
                    "getSelectionModel().select(0)", "getSelectionModel().getSelectedIndex()",
                    "getTabs().size()");
            case SPLIT_PANE -> List.of("setDividerPositions(0.5)", "getItems()");
            case STACK_PANEL, GRID_PANE, DOCK_PANEL -> List.of("getChildren()");
        });
        if (type != ComponentType.TIMER) {
            entries.addAll(COMMON);
        }
        return entries;
    }

    public static List<String> uiHelpers() {
        return List.of(
                "alert(\"\")", "confirm(\"\")", "prompt(\"\", \"\")", "copyToClipboard(\"\")",
                "openFileDialog()", "saveFileDialog()", "openLink(\"https://\")", "row(\"\")");
    }

    public static List<String> stageMethods() {
        return List.of("setTitle(\"\")", "close()", "setWidth(400)", "setHeight(300)", "centerOnScreen()");
    }
}
