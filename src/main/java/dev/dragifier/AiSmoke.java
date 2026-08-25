package dev.dragifier;

import dev.dragifier.ai.AiOps;
import dev.dragifier.ai.AiSession;
import dev.dragifier.ai.FakeTransport;
import dev.dragifier.ai.OpApplier;
import dev.dragifier.ai.ProjectDigest;
import dev.dragifier.ai.PromptBuilder;
import dev.dragifier.ai.ReplyStream;
import dev.dragifier.model.ComponentType;
import dev.dragifier.model.EventSpec;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import dev.dragifier.model.ProjectModel;
import dev.dragifier.model.Templates;
import dev.dragifier.runner.AppRunner;

import java.util.List;

/**
 * Headless check of the AI edit protocol: the system prompt describes the real
 * model, the digest is small and lossless where it matters, and an op list of
 * the shape the assistant is asked for produces a project that actually
 * compiles. Run with {@code gradlew aiSmoke}; exits non-zero on failure.
 *
 * <p>No API key and no network: everything here is the layer between the model's
 * JSON and {@link ProjectModel}, which is where the interesting bugs live.
 */
public final class AiSmoke {

    private AiSmoke() {}

    public static void main(String[] args) throws Exception {
        boolean dump = args.length > 0 && args[0].equals("--print-prompt");
        String prompt = PromptBuilder.systemPrompt();
        if (dump) {
            System.out.println(prompt);
            return;
        }
        checkPromptCoverage(prompt);
        checkPromptExample();
        checkCalculator();
        checkDigest();
        checkDigestStripsBinary();
        checkEnvelopeParsing();
        checkReplyStream();
        checkRefusals();
        try {
            checkOfflineTurn();
        } finally {
            // the FX thread this check starts would otherwise keep the JVM alive for ever
            if (toolkitStarted) {
                javafx.application.Platform.exit();
            }
        }
        System.out.println("AI SMOKE OK: all checks passed.");
    }

    // ------------------------------------------------------------- streaming

    /** The user must see prose stream in, not the JSON envelope carrying it. */
    private static void checkReplyStream() {
        String envelope = "{\"reply\": \"Added a keypad.\\nIt uses UI.eval() \\u2014 nice and short.\","
                + "\"ops\":[{\"op\":\"set\",\"id\":\"a\",\"props\":{\"text\":\"never streamed\"}}]}";
        String expected = "Added a keypad.\nIt uses UI.eval() — nice and short.";

        for (int chunk : new int[]{1, 3, 7, 64, 4096}) {
            StringBuilder seen = new StringBuilder();
            ReplyStream stream = new ReplyStream(seen::append);
            for (int i = 0; i < envelope.length(); i += chunk) {
                stream.append(envelope.substring(i, Math.min(envelope.length(), i + chunk)));
            }
            require(seen.toString().equals(expected),
                    "chunk size " + chunk + " decoded \"" + seen + "\" instead of \"" + expected + "\"");
            require(!seen.toString().contains("never streamed"),
                    "chunk size " + chunk + " leaked op content into the visible reply");
            require(stream.raw().equals(envelope), "chunk size " + chunk + " lost part of the raw reply");
        }

        // a model that answers in prose streams nothing and the caller renders it at the end
        StringBuilder prose = new StringBuilder();
        ReplyStream quiet = new ReplyStream(prose::append);
        quiet.append("I'd use a GridPane for that.");
        require(prose.isEmpty() && !quiet.streamed(), "prose was streamed as if it were a reply field");
        System.out.println("AI SMOKE OK: reply text streams identically at every chunk size, ops never leak.");
    }

    // ---------------------------------------------------------- full turn

    /** A whole turn — stream, apply, compile, repair — with no key and no network. */
    private static void checkOfflineTurn() throws Exception {
        if (!startToolkit()) {
            System.out.println("AI SMOKE SKIP: no JavaFX toolkit available for the full-turn check.");
            return;
        }
        // the first reply misspells a method; the repair round fixes it
        String broken = "{\"reply\":\"Added a greeter.\",\"ops\":["
                + "{\"op\":\"add\",\"type\":\"LABEL\",\"id\":\"greeting\",\"x\":24,\"y\":24},"
                + "{\"op\":\"add\",\"type\":\"BUTTON\",\"id\":\"greet\",\"x\":24,\"y\":64,"
                + "\"props\":{\"text\":\"Greet\"},"
                + "\"events\":{\"onAction\":\"greeting.setTxt(\\\"Hello\\\");\"}}]}";
        String fixed = "{\"reply\":\"Fixed the method name.\",\"ops\":["
                + "{\"op\":\"event\",\"id\":\"greet\",\"key\":\"onAction\","
                + "\"code\":\"greeting.setText(\\\"Hello\\\");\"}]}";

        FakeTransport transport = new FakeTransport(5, broken, fixed);
        ProjectModel project = ProjectModel.withDefaultForm();
        AiSession session = new AiSession(transport);
        session.setProject(() -> project);
        session.setActiveForm(project::effectiveMain);

        StringBuilder streamed = new StringBuilder();
        int[] checkpoints = {0};
        session.setOnDelta(streamed::append);
        session.setCheckpoint(() -> checkpoints[0]++);
        java.util.concurrent.CompletableFuture<AiSession.Turn> done = new java.util.concurrent.CompletableFuture<>();
        session.setOnTurnEnd(done::complete);
        session.setOnError(message -> done.completeExceptionally(new IllegalStateException(message)));

        javafx.application.Platform.runLater(() -> session.send("make a greeter"));
        AiSession.Turn turn = done.get(120, java.util.concurrent.TimeUnit.SECONDS);

        require(turn.compiled(), "the repaired turn still does not compile: " + turn.errors());
        require(turn.repaired(), "the turn did not report that it went through a repair round");
        require(turn.report().applied() == 3,
                "expected 2 adds plus 1 repair op, got " + turn.report().applied());
        require(checkpoints[0] == 2, "expected one checkpoint per apply, got " + checkpoints[0]);
        require(streamed.toString().contains("Added a greeter."),
                "the reply text was not streamed: \"" + streamed + "\"");
        require(!streamed.toString().contains("\"op\""), "raw ops leaked into the streamed reply");

        FormModel form = project.effectiveMain();
        require(form.findById("greet").getEvents().get("onAction").contains("setText"),
                "the repair did not replace the broken handler");
        require(transport.calls().size() == 2, "expected two model calls, got " + transport.calls().size());
        require(transport.calls().get(0).get(0).role().equals("system"),
                "the system prompt is not the first message");
        String repairTurn = transport.calls().get(1).getLast().content();
        require(repairTurn.contains("greet") && repairTurn.contains("onAction"),
                "the repair prompt does not name the failing component and event: " + repairTurn);
        require(repairTurn.contains("setTxt"), "the repair prompt does not quote the offending line");

        System.out.println("AI SMOKE OK: a full turn streams, applies, compiles, repairs and reports.");
    }

    /** Starting the toolkit lets the session's Platform.runLater hops work headlessly. */
    private static boolean toolkitStarted;

    private static boolean startToolkit() {
        try {
            java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
            javafx.application.Platform.startup(ready::countDown);
            toolkitStarted = ready.await(30, java.util.concurrent.TimeUnit.SECONDS);
            return toolkitStarted;
        } catch (Exception ex) {
            return false;
        }
    }

    // -------------------------------------------------------------- prompt

    /** Every type, event key and settable property the IDE has must reach the model. */
    private static void checkPromptCoverage(String prompt) {
        for (ComponentType type : ComponentType.values()) {
            require(prompt.contains(type.name()), "prompt never mentions " + type.name());
            for (EventSpec spec : EventSpec.forType(type)) {
                require(prompt.contains(spec.key()),
                        "prompt never mentions event " + spec.key() + " of " + type.name());
            }
        }
        for (EventSpec spec : EventSpec.forForm()) {
            require(prompt.contains(spec.key()), "prompt never mentions form event " + spec.key());
        }
        for (String key : OpApplier.patchableKeys()) {
            require(prompt.contains(key), "prompt never mentions property " + key);
        }
        List<String> helpers = PromptBuilder.uiSignatures();
        require(helpers.size() >= 15,
                "only " + helpers.size() + " UI helpers extracted from RuntimeApi.SOURCE");
        require(helpers.stream().anyMatch(line -> line.startsWith("UI.eval(")),
                "UI.eval missing from the extracted helper list");
        require(prompt.contains("no third-party libraries")
                        || prompt.contains("NO third-party libraries"),
                "prompt does not state the no-third-party-libraries rule");
        System.out.println("AI SMOKE OK: prompt covers every type, event and property ("
                + prompt.length() + " chars, ~" + PromptBuilder.estimateTokens(prompt) + " tokens, "
                + helpers.size() + " UI helpers).");
    }

    /** The example the prompt tells the model to copy has to be one that works. */
    private static void checkPromptExample() throws Exception {
        AiOps.Reply reply = AiOps.parse(PromptBuilder.calculatorExample());
        require(reply.structured(), "the prompt's worked example does not parse as a reply");
        require(!reply.text().isEmpty(), "the prompt's worked example has no reply text");

        ProjectModel project = ProjectModel.withDefaultForm();
        AiOps.ApplyReport report = OpApplier.apply(project, project.effectiveMain(), reply.ops());
        require(report.clean(), "the prompt's worked example warns: " + report.warnings());
        require(report.applied() == reply.ops().size(),
                "only " + report.applied() + " of " + reply.ops().size() + " example ops applied");
        compiles(project, "the prompt's worked example");
        System.out.println("AI SMOKE OK: the prompt's worked example applies and compiles.");
    }

    // ----------------------------------------------------------- op applier

    /** The headline case: "make me a calculator" as the assistant would express it. */
    private static void checkCalculator() throws Exception {
        ProjectModel project = ProjectModel.withDefaultForm();
        FormModel form = project.effectiveMain();
        AiOps.Reply reply = AiOps.parse(calculatorOps());
        require(reply.structured(), "the canned calculator reply does not parse");

        AiOps.ApplyReport report = OpApplier.apply(project, form, reply.ops());
        require(report.clean(), "calculator warns: " + report.warnings());

        require(form.getComponents().size() == 22,
                "expected 22 components, got " + form.getComponents().size());
        require(form.getTitle().equals("Calculator"), "form title not applied");
        FormComponent display = form.findById("display");
        require(display != null && display.getFontSize() == 22 && display.getAlignment().equals("RIGHT"),
                "display properties did not survive the merge patch");
        FormComponent keypad = form.findById("keypad");
        require(keypad != null && form.childrenOf(keypad).size() == 20,
                "keypad should hold 20 keys, holds "
                        + (keypad == null ? "no keypad" : form.childrenOf(keypad).size()));
        FormComponent equals = form.findById("keyEquals");
        require(equals != null && equals.getSlot().equals("3,4"), "= key is not in cell 3,4");
        require(equals.getEvents().get("onAction").contains("UI.eval"), "= key has no handler");

        compiles(project, "the calculator");
        System.out.println("AI SMOKE OK: a calculator built from ops has 22 components and compiles.");
    }

    /** Bad input has to be refused loudly and change nothing else. */
    private static void checkRefusals() {
        ProjectModel project = ProjectModel.withDefaultForm();
        FormModel form = project.effectiveMain();
        FormComponent label = form.create(ComponentType.LABEL, 10, 10);

        AiOps.ApplyReport report = apply(project, form, """
                {"reply":"x","ops":[
                 {"op":"add","type":"SUPERBUTTON","id":"nope","x":0,"y":0},
                 {"op":"add","type":"BUTTON","id":"event","x":0,"y":0},
                 {"op":"add","type":"BUTTON","id":"9lives","x":0,"y":0},
                 {"op":"event","id":"%s","key":"onAction","code":"x();"},
                 {"op":"set","id":"%s","props":{"fontSizes":40}},
                 {"op":"set","id":"%s","props":{"type":"BUTTON"}},
                 {"op":"set","id":"ghost","props":{"width":10}},
                 {"op":"deleteForm","name":"%s"},
                 {"op":"levitate","id":"%s"}
                ]}""".formatted(label.getId(), label.getId(), label.getId(),
                form.getName(), label.getId()));

        expect(report, "unknown component type", "unknown type not refused");
        expect(report, "reserved", "the id \"event\" was not refused");
        expect(report, "9lives", "an id starting with a digit was not refused");
        expect(report, "has no event", "onAction on a Label was not refused");
        expect(report, "unknown property", "a misspelled property was not refused");
        expect(report, "dedicated op", "setting type through props was not refused");
        expect(report, "no component \"ghost\"", "an unknown id was not refused");
        expect(report, "only form", "deleting the last form was not refused");
        expect(report, "unknown op", "an unknown op was not refused");

        // the Label survived every one of those, and no bad handler was stored
        require(form.findById(label.getId()) != null, "the label was lost");
        require(label.getEvents().isEmpty(),
                "an event key a Label does not support was stored anyway: " + label.getEvents());
        require(label.getFontSize() == 13, "a refused patch changed the font size");
        require(label.getType() == ComponentType.LABEL, "a refused patch changed the type");
        require(project.getForms().size() == 1, "form count changed");

        // ids that clash fall back to the generated one, and the components still exist
        require(form.getComponents().size() == 3,
                "expected the label plus two renamed buttons, got " + form.getComponents().size());
        System.out.println("AI SMOKE OK: invalid ids, types, properties, events and ops are all refused.");
    }

    // --------------------------------------------------------------- digest

    private static void checkDigest() {
        ProjectModel project = Templates.all().stream()
                .filter(t -> t.name().equals("Notepad")).findFirst().orElseThrow()
                .factory().get();
        FormModel form = project.effectiveMain();
        String digest = ProjectDigest.of(project, form);

        for (FormComponent c : form.getComponents()) {
            require(digest.contains("\"" + c.getId() + "\""), c.getId() + " missing from the digest");
            for (String code : c.getEvents().values()) {
                // handler bodies go in whole — follow-up turns edit them
                String probe = code.lines().findFirst().orElse("").strip();
                require(probe.isEmpty() || digest.contains(escape(probe)),
                        "handler code for " + c.getId() + " missing from the digest");
            }
        }
        require(digest.contains("\"activeForm\":\"" + form.getName() + "\""),
                "digest does not name the active form");
        // full serialization writes every field of every component; the digest should be far smaller
        String full = dev.dragifier.io.ProjectIO.toJson(project);
        require(digest.length() * 2 < full.length(),
                "digest (" + digest.length() + ") is not meaningfully smaller than the project JSON ("
                        + full.length() + ")");
        System.out.println("AI SMOKE OK: digest keeps ids and handler code, "
                + full.length() + " chars -> " + digest.length() + ".");
    }

    /** The wallet check: an inlined image must never reach the prompt. */
    private static void checkDigestStripsBinary() {
        ProjectModel project = ProjectModel.withDefaultForm();
        FormModel form = project.effectiveMain();
        FormComponent image = form.create(ComponentType.IMAGE_VIEW, 10, 10);
        String base64 = "A".repeat(2_000_000);
        image.setImageData(base64);
        project.setIconData(base64);

        String digest = ProjectDigest.of(project, form);
        require(!digest.contains("AAAAAAAAAA"), "Base64 image data leaked into the digest");
        require(digest.contains("\"hasImage\":true"), "the digest does not report that an image is set");
        require(digest.length() < 20_000, "digest is " + digest.length() + " chars with one image");
        System.out.println("AI SMOKE OK: a 2 MB image becomes a flag, digest stays "
                + digest.length() + " chars.");
    }

    // ------------------------------------------------------------- parsing

    private static void checkEnvelopeParsing() {
        String ops = "\"ops\":[{\"op\":\"set\",\"id\":\"a\",\"props\":{\"width\":10}}]";
        String bare = "{\"reply\":\"hi\"," + ops + "}";

        for (String variant : List.of(
                bare,
                "```json\n" + bare + "\n```",
                "```\n" + bare + "\n```",
                "Sure, here you go:\n\n" + bare + "\n\nLet me know!",
                "  \n" + bare)) {
            AiOps.Reply reply = AiOps.parse(variant);
            require(reply.structured(), "failed to parse: " + shorten(variant));
            require(reply.text().equals("hi"), "wrong reply text from: " + shorten(variant));
            require(reply.ops().size() == 1, "wrong op count from: " + shorten(variant));
        }

        // braces inside handler code must not confuse the scanner
        AiOps.Reply braces = AiOps.parse("{\"reply\":\"ok\",\"ops\":[{\"op\":\"event\",\"id\":\"b\","
                + "\"key\":\"onAction\",\"code\":\"if (x) { y(); } else { z(\\\"}\\\"); }\"}]}");
        require(braces.structured() && braces.ops().size() == 1,
                "braces inside a code string broke the scanner");

        // prose-only replies are a chat turn, not an error
        AiOps.Reply prose = AiOps.parse("I'd use a GridPane for that. Want me to build it?");
        require(!prose.structured() && prose.ops().isEmpty(), "prose was misread as ops");
        require(prose.text().startsWith("I'd use"), "prose reply text was lost");

        // a truncated stream must not throw
        AiOps.Reply cut = AiOps.parse("{\"reply\":\"hi\",\"ops\":[{\"op\":\"add\",\"type\":\"BUT");
        require(!cut.structured(), "a truncated reply was treated as valid");
        System.out.println("AI SMOKE OK: fenced, prose-wrapped, brace-heavy and truncated replies all parse safely.");
    }

    // --------------------------------------------------------------- fixture

    /** A full calculator as an assistant would emit it — the same shape as the prompt example. */
    private static String calculatorOps() {
        StringBuilder out = new StringBuilder();
        out.append("{\"reply\":\"Built a calculator.\",\"ops\":[")
           .append("{\"op\":\"setForm\",\"props\":{\"title\":\"Calculator\",\"width\":260,\"height\":380}},")
           .append("{\"op\":\"add\",\"type\":\"TEXT_FIELD\",\"id\":\"display\",\"x\":12,\"y\":12,")
           .append("\"props\":{\"width\":236,\"height\":48,\"fontSize\":22,\"alignment\":\"RIGHT\",")
           .append("\"anchorRight\":true}},")
           .append("{\"op\":\"add\",\"type\":\"GRID_PANE\",\"id\":\"keypad\",\"x\":12,\"y\":72,")
           .append("\"props\":{\"width\":236,\"height\":296,\"gridColumns\":4,\"gridRows\":5,")
           .append("\"spacing\":4,\"anchorRight\":true,\"anchorBottom\":true}}");

        String[][] rows = {
                {"C", "back", "%", "/"},
                {"7", "8", "9", "*"},
                {"4", "5", "6", "-"},
                {"1", "2", "3", "+"},
                {"sign", "0", ".", "="}};
        int n = 0;
        for (int r = 0; r < rows.length; r++) {
            for (int col = 0; col < rows[r].length; col++) {
                String text = rows[r][col];
                String id = switch (text) {
                    case "C" -> "keyClear";
                    case "back" -> "keyBack";
                    case "sign" -> "keySign";
                    case "=" -> "keyEquals";
                    default -> "key" + (n);
                };
                String code = switch (text) {
                    case "C" -> "display.setText(\\\"\\\");";
                    case "back" -> "String t = display.getText();\\nif (!t.isEmpty()) "
                            + "{ display.setText(t.substring(0, t.length() - 1)); }";
                    case "sign" -> "String t = display.getText();\\ndisplay.setText("
                            + "t.startsWith(\\\"-\\\") ? t.substring(1) : \\\"-\\\" + t);";
                    case "=" -> "display.setText(UI.formatNumber(UI.eval(display.getText())));";
                    default -> "display.setText(display.getText() + \\\"" + text + "\\\");";
                };
                String label = switch (text) {
                    case "back" -> "<-";
                    case "sign" -> "+/-";
                    default -> text;
                };
                out.append(",{\"op\":\"add\",\"type\":\"BUTTON\",\"id\":\"").append(id)
                   .append("\",\"parent\":\"keypad\",\"slot\":\"").append(col).append(',').append(r)
                   .append("\",\"props\":{\"text\":\"").append(label).append("\",\"fontSize\":16},")
                   .append("\"events\":{\"onAction\":\"").append(code).append("\"}}");
                n++;
            }
        }
        return out.append("]}").toString();
    }

    // --------------------------------------------------------------- helpers

    private static AiOps.ApplyReport apply(ProjectModel project, FormModel form, String reply) {
        return OpApplier.apply(project, form, AiOps.parse(reply).ops());
    }

    private static void expect(AiOps.ApplyReport report, String fragment, String message) {
        require(report.warnings().stream().anyMatch(w -> w.contains(fragment)),
                message + " (warnings: " + report.warnings() + ")");
    }

    private static void compiles(ProjectModel project, String what) throws Exception {
        AppRunner.CompileResult result = AppRunner.compile(project);
        require(result.ok(), what + " does not compile:\n" + result.errorDetails());
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String shorten(String text) {
        String flat = text.replace('\n', ' ');
        return flat.length() <= 60 ? flat : flat.substring(0, 60) + "…";
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            System.err.println("AI SMOKE FAILED: " + message);
            System.exit(1);
        }
    }
}
