package dev.dragifier.model;

import java.util.ArrayList;
import java.util.List;

/**
 * An event a component type supports. {@code key} is the storage key in
 * {@link FormComponent#getEvents()}, {@code hint} shows the lambda signature
 * the user's code body runs inside.
 */
public record EventSpec(String key, String displayName, String hint, Kind kind, String setter) {

    public enum Kind {
        /** Wired via a handler setter, e.g. {@code setOnAction(event -> { ... })}. */
        SETTER,
        /** Wired via {@code valueProperty().addListener((obs, oldValue, newValue) -> { ... })}. */
        VALUE_LISTENER,
        /** Wired via {@code getSelectionModel().selectedItemProperty().addListener(...)}. */
        SELECTION_LISTENER,
        /** Timer tick — the handler body is embedded in the Timeline's KeyFrame. */
        TIMER_TICK,
        /** Wired via a setter taking a {@code Consumer<File>}: {@code setOnFileOpened(file -> { ... })}. */
        FILE_CALLBACK
    }

    private static final String EVENT_HINT = "(event) -> { ... }";
    private static final String CHANGE_HINT = "(obs, oldValue, newValue) -> { ... }";

    private static final EventSpec MOUSE_CLICK =
            new EventSpec("onMouseClicked", "Mouse click", EVENT_HINT, Kind.SETTER, "setOnMouseClicked");
    /** Every visual component gets these in addition to its own events. */
    private static final List<EventSpec> MOUSE_EVENTS = List.of(
            new EventSpec("onMouseEntered", "Mouse enter", EVENT_HINT, Kind.SETTER, "setOnMouseEntered"),
            new EventSpec("onMouseExited", "Mouse leave", EVENT_HINT, Kind.SETTER, "setOnMouseExited"));

    /** Events of the form itself (edited when nothing is selected). */
    public static List<EventSpec> forForm() {
        return List.of(new EventSpec("onShown", "On show", EVENT_HINT, Kind.SETTER, "setOnShown"));
    }

    /** The type's own events first, then a mouse click (unless it already reacts to clicks) and mouse enter/leave. */
    public static List<EventSpec> forType(ComponentType type) {
        List<EventSpec> own = ownEvents(type);
        if (type == ComponentType.TIMER) {
            return own; // not a node
        }
        List<EventSpec> all = new ArrayList<>(own);
        boolean clicks = switch (type) {
            case BUTTON, CHECK_BOX, RADIO_BUTTON, HYPERLINK -> true;
            default -> own.stream().anyMatch(e -> e.key().equals(MOUSE_CLICK.key()));
        };
        if (!clicks) {
            all.add(MOUSE_CLICK);
        }
        all.addAll(MOUSE_EVENTS);
        return List.copyOf(all);
    }

    private static List<EventSpec> ownEvents(ComponentType type) {
        return switch (type) {
            case BUTTON -> List.of(
                    new EventSpec("onAction", "On click", EVENT_HINT, Kind.SETTER, "setOnAction"));
            case CHECK_BOX -> List.of(
                    new EventSpec("onAction", "On toggle", EVENT_HINT, Kind.SETTER, "setOnAction"));
            case TEXT_FIELD -> List.of(
                    new EventSpec("onAction", "On enter", EVENT_HINT, Kind.SETTER, "setOnAction"));
            case LABEL, TEXT_AREA, PANEL -> List.of(
                    new EventSpec("onMouseClicked", "On click", EVENT_HINT, Kind.SETTER, "setOnMouseClicked"));
            case SLIDER -> List.of(
                    new EventSpec("onValueChange", "On value change", CHANGE_HINT, Kind.VALUE_LISTENER, null));
            case COMBO_BOX -> List.of(
                    new EventSpec("onAction", "On select", EVENT_HINT, Kind.SETTER, "setOnAction"));
            case RADIO_BUTTON -> List.of(
                    new EventSpec("onAction", "On toggle", EVENT_HINT, Kind.SETTER, "setOnAction"));
            case HYPERLINK -> List.of(
                    new EventSpec("onAction", "On click", EVENT_HINT, Kind.SETTER, "setOnAction"));
            case LIST_VIEW -> List.of(
                    new EventSpec("onSelect", "On select", CHANGE_HINT, Kind.SELECTION_LISTENER, null));
            case PROGRESS_BAR -> List.of();
            case IMAGE_VIEW -> List.of(
                    new EventSpec("onMouseClicked", "On click", EVENT_HINT, Kind.SETTER, "setOnMouseClicked"));
            case TIMER -> List.of(
                    new EventSpec("onTick", "On tick", EVENT_HINT, Kind.TIMER_TICK, null));
            case TABLE_VIEW -> List.of(
                    new EventSpec("onSelect", "On select", CHANGE_HINT, Kind.SELECTION_LISTENER, null));
            case WEB_VIEW, MEDIA_PLAYER -> List.of();
            case FILE_BROWSER -> List.of(
                    new EventSpec("onFileSelected", "On select", "(file) -> { ... }", Kind.FILE_CALLBACK, "setOnFileSelected"),
                    new EventSpec("onFileOpened", "On open", "(file) -> { ... }", Kind.FILE_CALLBACK, "setOnFileOpened"));
            case GROUP_BOX, SCROLL_PANE, SPLIT_PANE, STACK_PANEL, GRID_PANE, DOCK_PANEL -> List.of(
                    new EventSpec("onMouseClicked", "On click", EVENT_HINT, Kind.SETTER, "setOnMouseClicked"));
            case TAB_PANE -> List.of(
                    new EventSpec("onTabChange", "On tab change", CHANGE_HINT, Kind.SELECTION_LISTENER, null));
        };
    }
}
