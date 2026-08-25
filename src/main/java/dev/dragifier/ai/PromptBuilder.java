package dev.dragifier.ai;

import dev.dragifier.codegen.JavaCodeGenerator;
import dev.dragifier.codegen.RuntimeApi;
import dev.dragifier.model.ComponentType;
import dev.dragifier.model.ContainerGeometry;
import dev.dragifier.model.Dock;
import dev.dragifier.model.EventSpec;
import dev.dragifier.ui.CompletionCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the assistant's system prompt from the running model rather than from a
 * frozen copy of it: the component catalog comes from {@link ComponentType},
 * the event keys from {@link EventSpec}, the settable properties from
 * {@link OpApplier#patchableKeys()}, the helper API from {@link RuntimeApi}'s
 * own source, and the per-type method hints from the same
 * {@link CompletionCatalog} the code editor's autocomplete uses.
 *
 * <p>The point is that adding a component type, an event or a property updates
 * the prompt with no edit here — the usual way an LLM feature rots is a
 * hand-written prompt drifting away from the code it describes.
 */
public final class PromptBuilder {

    private PromptBuilder() {}

    /** Roughly four characters per token — enough to spot the prompt growing out of hand. */
    public static int estimateTokens(String text) {
        return text.length() / 4;
    }

    public static String systemPrompt() {
        StringBuilder out = new StringBuilder();
        contract(out);
        ops(out);
        components(out);
        properties(out);
        layout(out);
        eventCode(out);
        helpers(out);
        example(out);
        return out.toString();
    }

    // ------------------------------------------------------------- contract

    private static void contract(StringBuilder out) {
        out.append("""
                You are the assistant built into Dragifier, a visual IDE for building JavaFX \
                desktop apps. The user describes what they want; you build it by editing their \
                project — both the interface and the code behind it.

                # How to reply

                Reply with ONE JSON object and nothing else, "reply" first:

                {"reply": "<a sentence or two for the user>", "ops": [ ...edits... ]}

                - "reply" is prose for a human. Never put JSON or code in it.
                - "ops" is a list of edits applied in order. Use [] when you are only answering \
                a question or need to ask one.
                - Do not wrap the object in a markdown fence.
                - Build the whole thing in one turn. Don't ask permission first, and don't stop \
                halfway to describe what you would do next.

                Before the ops you are given the current project as JSON. Property names in that \
                JSON and property names in your ops are the same vocabulary.

                """);
    }

    private static void ops(StringBuilder out) {
        out.append("# Ops\n\n").append("""
                {"op":"add", "type":"BUTTON", "id":"save", "parent":"<id>", "slot":"", \
                "x":0, "y":0, "props":{...}, "events":{"onAction":"..."}}
                    New component. You choose "id" — it becomes the Java field name, so make it descriptive and refer to it from handler code. "parent"/"slot" omitted means directly on the form.
                {"op":"set", "id":"save", "props":{"width":120, "text":"Save"}}
                    Change properties. Only the listed ones change.
                {"op":"event", "id":"save", "key":"onAction", "code":"..."}
                    Set a handler body. Omit "id" for a form event. Empty "code" removes it.
                {"op":"rename", "id":"button1", "newId":"save"}
                    Rename; references to it in handler code are updated too.
                {"op":"delete", "id":"panel1"}
                    Delete a component and everything inside it.
                {"op":"move", "id":"save", "parent":"grid1", "slot":"2,3"}
                    Reparent. Omit "parent" to keep it and only change the slot.
                {"op":"setForm", "props":{"title":"Calculator", "width":260, "height":380, \
                "resizable":false, "name":"Form1"}}
                {"op":"addForm", "name":"Settings", "props":{...}}
                {"op":"deleteForm", "name":"Settings"}
                {"op":"setMainForm", "name":"Form1"}

                Any op may carry "form":"<name>" to target a form other than the active one.
                Ops apply in order, and order matters — see the layout rules.

                """);
    }

    // ------------------------------------------------------------ catalog

    private static void components(StringBuilder out) {
        out.append("# Components\n\n")
           .append("name (palette label) default WxH -> JavaFX class; then the events it "
                   + "supports and the methods its handler code can call.\n\n");
        for (ComponentType.Category category : ComponentType.Category.values()) {
            out.append("## ").append(category.name().charAt(0))
               .append(category.name().substring(1).toLowerCase()).append('\n');
            for (ComponentType type : ComponentType.values()) {
                if (type.category == category) {
                    componentEntry(out, type);
                }
            }
            out.append('\n');
        }
    }

    private static void componentEntry(StringBuilder out, ComponentType type) {
        out.append(type.name()).append(" (").append(type.displayName).append(") ")
           .append((int) type.defaultWidth).append('x').append((int) type.defaultHeight)
           .append(" -> ").append(JavaCodeGenerator.javaTypeFor(type));
        if (type.isContainer()) {
            out.append("  CONTAINER, slot = ").append(slotFormat(type));
        }
        out.append('\n');
        List<String> keys = EventSpec.forType(type).stream().map(EventSpec::key).toList();
        if (!keys.isEmpty()) {
            out.append("    events: ").append(String.join(", ", keys)).append('\n');
        }
        // the same hints the code editor's autocomplete offers, so prompt and IDE agree
        out.append("    code:   ").append(String.join(", ", CompletionCatalog.methodsFor(type)))
           .append('\n');
    }

    private static String slotFormat(ComponentType type) {
        return switch (type.kind) {
            case TABS -> "\"0\", \"1\", … (tab index, 0-based; tabs come from the items property)";
            case SPLIT -> "\"0\", \"1\", … (pane index, 0-based; count comes from the panes property)";
            case GRID -> "\"col,row\" (both 0-based)";
            case DOCK -> "one of " + String.join(", ", ContainerGeometry.DOCK_REGIONS);
            default -> "\"\" (one content area)";
        };
    }

    // ---------------------------------------------------------- properties

    /**
     * Notes for properties whose meaning or value format isn't obvious. Every
     * patchable key is listed whether or not it has a note, so a newly added
     * field shows up here un-annotated instead of silently disappearing.
     */
    private static Map<String, String> propertyNotes() {
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("x", "left edge inside the parent's content area (ignored in auto-layout containers)");
        notes.put("y", "top edge inside the parent's content area (ignored in auto-layout containers)");
        notes.put("text", "the caption; for WebView it's the URL, for ComboBox the prompt text");
        notes.put("textColor", "\"#RRGGBB\"");
        notes.put("background", "\"#RRGGBB\", or \"\" for the theme default");
        notes.put("items", "newline-separated entries — ComboBox/ListView options, TabControl tab titles");
        notes.put("columns", "newline-separated column names, for Table");
        notes.put("value", "ProgressBar percent 0-100; Timer interval in milliseconds");
        notes.put("alignment", "LEFT, CENTER, RIGHT, or \"\"");
        notes.put("anchorLeft", "keep this edge a fixed distance from the parent's when the window resizes");
        notes.put("dock", "stick to an edge of the parent and stretch along it: "
                + names(Dock.values()) + " — see the layout rules");
        notes.put("orientation", "HORIZONTAL or VERTICAL — Splitter and StackPanel");
        notes.put("spacing", "gap between children — StackPanel spacing, Grid hgap/vgap");
        notes.put("gridColumns", "Grid size (0 = 2)");
        notes.put("gridRows", "Grid size (0 = 2)");
        notes.put("panes", "number of Splitter panes (0 = 2)");
        notes.put("dividers", "comma-separated Splitter divider positions in 0..1");
        notes.put("borderWidth", "CSS insets: \"1\" or \"1 2 1 2\"");
        notes.put("padding", "CSS insets: \"8\" or \"4 8 4 8\"");
        notes.put("margin", "CSS insets; only has an effect inside an auto-layout container");
        notes.put("cursor", "DEFAULT, HAND, TEXT, CROSSHAIR, MOVE, WAIT, NONE, or \"\"");
        notes.put("locked", "design-time only — stops the user dragging it on the canvas");
        return notes;
    }

    private static void properties(StringBuilder out) {
        out.append("# Properties\n\nUsable in \"props\". Anything not listed here is not settable.\n\n");
        Map<String, String> notes = propertyNotes();
        for (String key : OpApplier.patchableKeys()) {
            out.append("  ").append(key);
            String note = notes.get(key);
            if (note != null) {
                out.append(" — ").append(note);
            }
            out.append('\n');
        }
        out.append("""

                Not settable through "props" — each has its own op: id (rename), type (delete \
                then add), parent and slot (move), events (event). Images and media are set by \
                the user in the inspector; you never see them and cannot change them.

                Form properties for setForm and addForm: """)
           .append(String.join(", ", OpApplier.formKeys()))
           .append(". A form's name becomes a Java class name.\n\n");
    }

    // -------------------------------------------------------------- layout

    private static void layout(StringBuilder out) {
        out.append("# Layout\n\n").append("""
                - x/y/width/height are pixels, measured inside the parent's content area — not \
                the whole window and not the parent's outer bounds.
                - Lay things out on a grid of 8px where you can, and leave a margin of 12-16px \
                around the edges of a form. Give buttons at least 32px of height and controls \
                enough width for their text.
                - PANEL, GROUP_BOX, SCROLL_PANE, TAB_PANE and SPLIT_PANE position children at \
                x/y. STACK_PANEL, GRID_PANE and DOCK_PANEL lay children out themselves, so x/y \
                are ignored there — use slot, spacing and margin instead.
                - For a keypad or a form of evenly sized controls, a GRID_PANE is much better \
                than placing each button by hand.
                - Components are listed in z-order, and ops apply in order, so a component you \
                add later sits on top of one you added earlier.
                - dock is resolved in that same order: each docked component takes its edge of \
                what is left, and FILL takes the remainder. So add the toolbar (dock TOP) and \
                the status bar (dock BOTTOM) before the editor (dock FILL).
                - On a resizable form, use the anchor properties so things follow the window. \
                A control anchored left+right stretches; anchored right only, it moves with the \
                right edge.

                """);
    }

    // ---------------------------------------------------------- event code

    private static void eventCode(StringBuilder out) {
        out.append("# Handler code\n\n").append("""
                A handler body is plain Java statements pasted straight into a lambda. So:

                - Statements only. No method or class declarations, no import statements.
                - Every component id is a field: a component you named "display" is used as \
                display.setText("x"). "stage" is this form's own window.
                - Open another form with: new Form2().show();
                - Available: JavaFX, the JDK (java.util is imported, everything else needs its \
                full name like java.time.LocalDate), and the UI helper below. There are NO \
                third-party libraries — no Apache Commons, no Gson, no Guava. Code that imports \
                one will not compile.
                - Variables already in scope depend on the event:
                """);
        for (Map.Entry<String, List<String>> entry : eventsByHint().entrySet()) {
            out.append("    ").append(entry.getKey()).append("  —  ")
               .append(String.join(", ", entry.getValue())).append('\n');
        }
        out.append("""
                - Keep handlers short. When several controls share logic, repeat the couple of \
                lines rather than trying to declare a shared method — there is nowhere to put one.

                """);
    }

    /** Groups every event key by the lambda signature its body runs inside. */
    private static Map<String, List<String>> eventsByHint() {
        Map<String, List<String>> byHint = new LinkedHashMap<>();
        List<EventSpec> all = new ArrayList<>(EventSpec.forForm());
        for (ComponentType type : ComponentType.values()) {
            all.addAll(EventSpec.forType(type));
        }
        for (EventSpec spec : all) {
            List<String> keys = byHint.computeIfAbsent(spec.hint(), h -> new ArrayList<>());
            if (!keys.contains(spec.key())) {
                keys.add(spec.key());
            }
        }
        return byHint;
    }

    // ------------------------------------------------------------- helpers

    private static final Pattern SIGNATURE = Pattern.compile(
            "public static ([\\w.<>\\[\\]]+) (\\w+)\\(([^)]*)\\)");
    private static final Pattern DOC_LINE = Pattern.compile("/\\*\\* (.+?) \\*/");

    private static void helpers(StringBuilder out) {
        out.append("# The UI helper\n\nCall these from any handler, e.g. UI.alert(\"Saved\").\n\n");
        for (String line : uiSignatures()) {
            out.append("  ").append(line).append('\n');
        }
        out.append('\n');
    }

    /**
     * Pulls the public signatures straight out of the {@code UI.java} source that
     * every generated project gets, with their one-line javadoc where there is one.
     * Reading the real source beats a curated list that would need remembering.
     */
    public static List<String> uiSignatures() {
        List<String> lines = new ArrayList<>();
        String doc = null;
        for (String raw : RuntimeApi.SOURCE.lines().toList()) {
            String line = raw.strip();
            Matcher docMatch = DOC_LINE.matcher(line);
            if (docMatch.find()) {
                doc = docMatch.group(1);
                continue;
            }
            Matcher signature = SIGNATURE.matcher(line);
            if (signature.find()) {
                lines.add("UI." + signature.group(2) + "(" + signature.group(3) + ") -> "
                        + signature.group(1) + (doc == null ? "" : "   // " + doc));
                doc = null;
            } else if (!line.isEmpty() && !line.startsWith("*") && !line.startsWith("//")) {
                doc = null;  // the javadoc belonged to something else
            }
        }
        return lines;
    }

    // ------------------------------------------------------------- example

    private static void example(StringBuilder out) {
        out.append("# Worked example\n\n")
           .append("\"make me a calculator\" — note the grid, the descriptive ids, and the "
                   + "handlers referring to the display by its id:\n\n")
           .append(CALCULATOR_EXAMPLE)
           .append("\nThe real answer places all twenty keys, not three.\n");
    }

    /**
     * The example reply shown in the prompt. Exposed so {@code AiSmoke} can apply
     * it and compile the result — the example a model copies from should be one
     * the build proves works, not one someone typed and hoped about.
     */
    public static String calculatorExample() {
        return CALCULATOR_EXAMPLE;
    }

    private static final String CALCULATOR_EXAMPLE = """
                {"reply": "Built a calculator: a right-aligned display and a 4x5 keypad wired \
                to the expression evaluator.",
                 "ops": [
                  {"op":"setForm","props":{"title":"Calculator","width":260,"height":380}},
                  {"op":"add","type":"TEXT_FIELD","id":"display","x":12,"y":12,
                   "props":{"width":236,"height":48,"fontSize":22,"alignment":"RIGHT","anchorRight":true}},
                  {"op":"add","type":"GRID_PANE","id":"keypad","x":12,"y":72,
                   "props":{"width":236,"height":296,"gridColumns":4,"gridRows":5,"spacing":4,
                            "anchorRight":true,"anchorBottom":true}},
                  {"op":"add","type":"BUTTON","id":"keyClear","parent":"keypad","slot":"0,0",
                   "props":{"text":"C","fontSize":16},
                   "events":{"onAction":"display.setText(\\"\\");"}},
                  {"op":"add","type":"BUTTON","id":"key7","parent":"keypad","slot":"0,1",
                   "props":{"text":"7","fontSize":16},
                   "events":{"onAction":"display.setText(display.getText() + \\"7\\");"}},
                  {"op":"add","type":"BUTTON","id":"keyEquals","parent":"keypad","slot":"3,4",
                   "props":{"text":"=","fontSize":16},
                   "events":{"onAction":"display.setText(UI.formatNumber(UI.eval(display.getText())));"}}
                 ]}
                """;

    private static String names(Enum<?>[] values) {
        return String.join(", ", java.util.Arrays.stream(values).map(Enum::name).toList());
    }
}
