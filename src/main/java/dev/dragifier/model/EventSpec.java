package dev.dragifier.model;

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
        SELECTION_LISTENER
    }

    public static List<EventSpec> forType(ComponentType type) {
        return switch (type) {
            case BUTTON -> List.of(
                    new EventSpec("onAction", "On click", "(event) -> { ... }", Kind.SETTER, "setOnAction"));
            case CHECK_BOX -> List.of(
                    new EventSpec("onAction", "On toggle", "(event) -> { ... }", Kind.SETTER, "setOnAction"));
            case TEXT_FIELD -> List.of(
                    new EventSpec("onAction", "On enter", "(event) -> { ... }", Kind.SETTER, "setOnAction"));
            case LABEL, TEXT_AREA, PANEL -> List.of(
                    new EventSpec("onMouseClicked", "On click", "(event) -> { ... }", Kind.SETTER, "setOnMouseClicked"));
            case SLIDER -> List.of(
                    new EventSpec("onValueChange", "On value change",
                            "(obs, oldValue, newValue) -> { ... }", Kind.VALUE_LISTENER, null));
            case COMBO_BOX -> List.of(
                    new EventSpec("onAction", "On select", "(event) -> { ... }", Kind.SETTER, "setOnAction"));
            case RADIO_BUTTON -> List.of(
                    new EventSpec("onAction", "On toggle", "(event) -> { ... }", Kind.SETTER, "setOnAction"));
            case HYPERLINK -> List.of(
                    new EventSpec("onAction", "On click", "(event) -> { ... }", Kind.SETTER, "setOnAction"));
            case LIST_VIEW -> List.of(
                    new EventSpec("onSelect", "On select",
                            "(obs, oldValue, newValue) -> { ... }", Kind.SELECTION_LISTENER, null));
            case PROGRESS_BAR -> List.of();
            case IMAGE_VIEW -> List.of(
                    new EventSpec("onMouseClicked", "On click", "(event) -> { ... }", Kind.SETTER, "setOnMouseClicked"));
        };
    }
}
