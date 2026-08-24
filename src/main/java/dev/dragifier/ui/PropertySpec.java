package dev.dragifier.ui;

import dev.dragifier.model.FormComponent;
import javafx.scene.Node;
import javafx.stage.Window;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * One row of the property inspector: what it is called, where it sits
 * (category / optional sub-group), how it is edited and how it reads from and
 * writes to the model. {@link ComponentProperties} builds the list for a
 * component; {@link PropertyGrid} renders it.
 */
public record PropertySpec(
        String key,
        String label,
        String category,
        String group,
        Editor editor,
        List<String> choices,
        String prompt,
        Predicate<FormComponent> applies,
        Predicate<FormComponent> enabled,
        Supplier<Object> get,
        BiConsumer<FormComponent, Object> set,
        Function<Context, Node> custom,
        boolean rebuildAfterSet) {

    public enum Editor {
        /** One-line text. */
        TEXT,
        /** Multi-line text (items, columns). */
        MULTILINE,
        /** Decimal number. */
        NUMBER,
        /** Whole number. */
        INTEGER,
        /** Boolean checkbox. */
        CHECK,
        /** Colour picker with a "Default" button; the value is a hex string or "" for default. */
        COLOR,
        /** One of {@link #choices}; the value is the choice string. */
        CHOICE,
        /** Editable combo of installed font families; "" = default. */
        FONT_FAMILY,
        /** CSS-style insets: "8" or "4 8 4 8". */
        INSETS,
        /** Plain label. */
        READONLY,
        /** A node built by {@link #custom}. */
        CUSTOM,
        /** An event row: status + Edit button; {@link #get} answers whether code exists. */
        EVENT
    }

    /** What custom editors and the grid need from the inspector. */
    public interface Context {
        /** The component being edited (null for the form). */
        FormComponent component();

        /** Runs a model change with an undo checkpoint and notifies the canvas. */
        void apply(Runnable change);

        Window window();

        /** Renames the component (with refactoring); returns success. */
        boolean rename(FormComponent c, String newId);

        /** Jumps to the event editor for the given event key. */
        void editEvent(String key);
    }

    public static Builder of(String key, String label, String category) {
        return new Builder(key, label, category);
    }

    /** Fluent construction; defaults: TEXT editor, always applies, always enabled. */
    public static final class Builder {
        private final String key;
        private final String label;
        private final String category;
        private String group;
        private Editor editor = Editor.TEXT;
        private List<String> choices = List.of();
        private String prompt = "";
        private Predicate<FormComponent> applies = c -> true;
        private Predicate<FormComponent> enabled = c -> true;
        private Supplier<Object> get = () -> "";
        private BiConsumer<FormComponent, Object> set = (c, v) -> { };
        private Function<Context, Node> custom;
        private boolean rebuild;

        private Builder(String key, String label, String category) {
            this.key = key;
            this.label = label;
            this.category = category;
        }

        public Builder group(String group) { this.group = group; return this; }
        public Builder editor(Editor editor) { this.editor = editor; return this; }
        public Builder choices(List<String> choices) { this.choices = choices; return this; }
        public Builder prompt(String prompt) { this.prompt = prompt; return this; }
        public Builder applies(Predicate<FormComponent> applies) { this.applies = applies; return this; }
        public Builder enabled(Predicate<FormComponent> enabled) { this.enabled = enabled; return this; }
        public Builder get(Supplier<Object> get) { this.get = get; return this; }
        public Builder set(BiConsumer<FormComponent, Object> set) { this.set = set; return this; }
        public Builder custom(Function<Context, Node> custom) { this.custom = custom; this.editor = Editor.CUSTOM; return this; }
        /** Re-lists the properties after a change (the change alters which rows apply). */
        public Builder rebuild() { this.rebuild = true; return this; }

        public PropertySpec build() {
            return new PropertySpec(key, label, category, group, editor, choices, prompt,
                    applies, enabled, get, set, custom, rebuild);
        }
    }
}
