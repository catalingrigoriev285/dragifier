package dev.dragifier.ui;

import dev.dragifier.model.ComponentType;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** One place for the per-component-type icons used by the palette and tree. */
public final class Icons {

    private Icons() {}

    public static FontIcon forType(ComponentType type) {
        Feather glyph = switch (type) {
            case BUTTON -> Feather.MOUSE_POINTER;
            case LABEL -> Feather.TYPE;
            case TEXT_FIELD -> Feather.EDIT_3;
            case TEXT_AREA -> Feather.FILE_TEXT;
            case CHECK_BOX -> Feather.CHECK_SQUARE;
            case SLIDER -> Feather.SLIDERS;
            case PANEL -> Feather.LAYOUT;
            case COMBO_BOX -> Feather.CHEVRON_DOWN;
            case LIST_VIEW -> Feather.LIST;
            case RADIO_BUTTON -> Feather.DISC;
            case PROGRESS_BAR -> Feather.ACTIVITY;
            case HYPERLINK -> Feather.LINK;
            case IMAGE_VIEW -> Feather.IMAGE;
            case TIMER -> Feather.CLOCK;
            case TABLE_VIEW -> Feather.COLUMNS;
            case WEB_VIEW -> Feather.GLOBE;
            case MEDIA_PLAYER -> Feather.PLAY_CIRCLE;
            case GROUP_BOX -> Feather.SQUARE;
            case SCROLL_PANE -> Feather.MAXIMIZE;
            case TAB_PANE -> Feather.FOLDER;
            case SPLIT_PANE -> Feather.SIDEBAR;
            case STACK_PANEL -> Feather.LAYERS;
            case GRID_PANE -> Feather.GRID;
            case DOCK_PANEL -> Feather.BOX;
        };
        return new FontIcon(glyph);
    }
}
