package dev.dragifier.ai;

import dev.dragifier.codegen.SourceMap;
import dev.dragifier.io.ProjectIO;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import dev.dragifier.model.ProjectModel;
import dev.dragifier.runner.AppRunner;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs one assistant turn end to end: stream the reply, apply the ops, check the
 * result compiles, and give it one chance to fix what it broke.
 *
 * <p>Three threading rules hold this together, and each of them is a real bug if
 * broken. The model is only ever read and written on the JavaFX thread, because
 * the user keeps designing while a turn is in flight. The compile runs against a
 * <em>deep copy</em>, because generating code calls {@code DockLayout.applyTo},
 * which writes geometry back into the components it is given. And streaming
 * deltas are coalesced to one UI update per pulse instead of one per token,
 * because a fast model emits hundreds a second.
 */
public final class AiSession {

    /** How the turn ended, for the chat pane to render. */
    public record Turn(String reply, AiOps.ApplyReport report,
                       List<AppRunner.CompileError> errors, Transport.Usage usage,
                       boolean repaired, String note) {

        public boolean compiled() {
            return errors.isEmpty();
        }
    }

    /**
     * Errors quoted back to the model in a repair round. There is exactly one such
     * round per user action — it is a straight line through {@link #runTurn}, not a
     * loop, so it cannot run away.
     */
    private static final int MAX_REPAIR_ERRORS = 12;
    private static final int MAX_HISTORY = 12;

    private final Transport transport;
    private final List<ChatMessage> history = new ArrayList<>();

    private Supplier<ProjectModel> project = () -> null;
    private Supplier<FormModel> activeForm = () -> null;
    private Runnable checkpoint = () -> {};
    private Runnable onApplied = () -> {};
    private Consumer<String> onStatus = s -> {};
    private Consumer<String> onDelta = s -> {};
    private Consumer<Turn> onTurnEnd = t -> {};
    private Consumer<String> onError = s -> {};

    private volatile boolean busy;
    private volatile boolean cancelled;

    private final StringBuilder pending = new StringBuilder();
    private final AtomicBoolean drainScheduled = new AtomicBoolean();

    public AiSession(Transport transport) {
        this.transport = transport;
    }

    // ---------------------------------------------------------------- wiring

    public void setProject(Supplier<ProjectModel> project) {
        this.project = project;
    }

    public void setActiveForm(Supplier<FormModel> activeForm) {
        this.activeForm = activeForm;
    }

    /** Called on the JavaFX thread immediately before the ops are applied. */
    public void setCheckpoint(Runnable checkpoint) {
        this.checkpoint = checkpoint;
    }

    /** Called on the JavaFX thread after the ops land, to rebind the panes. */
    public void setOnApplied(Runnable onApplied) {
        this.onApplied = onApplied;
    }

    public void setOnStatus(Consumer<String> onStatus) {
        this.onStatus = onStatus;
    }

    public void setOnDelta(Consumer<String> onDelta) {
        this.onDelta = onDelta;
    }

    public void setOnTurnEnd(Consumer<Turn> onTurnEnd) {
        this.onTurnEnd = onTurnEnd;
    }

    public void setOnError(Consumer<String> onError) {
        this.onError = onError;
    }

    public boolean isBusy() {
        return busy;
    }

    /** Forgets the conversation; the next turn starts fresh. */
    public void clearHistory() {
        history.clear();
    }

    // ------------------------------------------------------------------ send

    /** Starts a turn. Call on the JavaFX thread. */
    public void send(String userText) {
        send(userText, null);
    }

    /**
     * Starts a turn with an extra instruction ahead of the user's words — used by
     * "Fix with AI" and the events editor's "Ask AI" to scope what may change.
     */
    public void send(String userText, String scope) {
        if (busy || userText == null || userText.isBlank()) {
            return;
        }
        if (!transport.ready()) {
            onError.accept("No OpenRouter API key yet — set one in File → AI Settings.");
            return;
        }
        busy = true;
        cancelled = false;
        String turnText = scope == null || scope.isBlank() ? userText : scope + "\n\n" + userText;
        String digest = ProjectDigest.of(project.get(), activeForm.get());
        List<ChatMessage> messages = messages(digest, turnText);

        Thread worker = new Thread(() -> runTurn(userText, messages), "dragifier-ai");
        worker.setDaemon(true);
        worker.start();
    }

    /** Aborts the turn in flight. Nothing is applied if it had not been applied yet. */
    public void stop() {
        if (!busy) {
            return;
        }
        cancelled = true;
        transport.cancel();
    }

    /**
     * The full message list. Only the newest user turn carries the project — the
     * stored history holds bare text, so old copies of the project can never pile
     * up in the context.
     */
    private List<ChatMessage> messages(String digest, String turnText) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(PromptBuilder.systemPrompt()));
        int from = Math.max(0, history.size() - MAX_HISTORY);
        messages.addAll(history.subList(from, history.size()));
        messages.add(ChatMessage.user("The project right now:\n" + digest + "\n\n" + turnText));
        return messages;
    }

    // ------------------------------------------------------------- the turn

    private void runTurn(String userText, List<ChatMessage> messages) {
        try {
            status("Thinking…");
            Completion first = stream(messages);
            if (cancelled) {
                finishCancelled();
                return;
            }
            AiOps.Reply reply = AiOps.parse(first.raw());
            String note = lengthWarning(first.finishReason());

            if (!reply.structured() || reply.ops().isEmpty()) {
                // a question, an explanation, or a model that ignored the contract
                remember(userText, reply.text());
                finish(new Turn(reply.text(), new AiOps.ApplyReport(0, List.of()),
                        List.of(), first.usage(), false, note));
                return;
            }

            AiOps.ApplyReport report = applyOnFxThread(reply.ops());
            if (!AiSettings.autoVerify()) {
                remember(userText, reply.text());
                finish(new Turn(reply.text(), report, List.of(), first.usage(), false, note));
                return;
            }

            status("Checking that it compiles…");
            AppRunner.CompileResult result = compileCopy();
            if (result.ok() || cancelled) {
                remember(userText, reply.text());
                finish(new Turn(reply.text(), report, List.of(), first.usage(), false, note));
                return;
            }

            repair(userText, messages, first, reply, report, result, note);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    /** One corrective round, then report whatever is left rather than looping. */
    private void repair(String userText, List<ChatMessage> messages, Completion first,
                        AiOps.Reply reply, AiOps.ApplyReport report,
                        AppRunner.CompileResult result, String note) throws Exception {
        int errorCount = result.errors().size();
        status("Fixing " + errorCount + (errorCount == 1 ? " error…" : " errors…"));

        List<ChatMessage> repairMessages = new ArrayList<>(messages);
        repairMessages.add(ChatMessage.assistant(first.raw()));
        repairMessages.add(ChatMessage.user(compileErrorReport(result)));

        Completion second = stream(repairMessages);
        if (cancelled) {
            finishCancelled();
            return;
        }
        AiOps.Reply fix = AiOps.parse(second.raw());
        AiOps.ApplyReport merged = report;
        if (fix.structured() && !fix.ops().isEmpty()) {
            AiOps.ApplyReport fixReport = applyOnFxThread(fix.ops());
            merged = new AiOps.ApplyReport(report.applied() + fixReport.applied(),
                    concat(report.warnings(), fixReport.warnings()));
            result = compileCopy();
        }

        remember(userText, reply.text());
        Transport.Usage total = add(first.usage(), second.usage());
        String text = fix.structured() && !fix.text().isEmpty()
                ? reply.text() + "\n\n" + fix.text()
                : reply.text();
        finish(new Turn(text, merged, result.ok() ? List.of() : result.errors(), total, true, note));
    }

    /** Streams one completion, coalescing the deltas onto the JavaFX thread. */
    private Completion stream(List<ChatMessage> messages) throws Exception {
        StringBuilder rawAll = new StringBuilder();
        ReplyStream replyStream = new ReplyStream(this::pushDelta);
        String[] finish = {""};
        Transport.Usage[] usage = {Transport.Usage.NONE};
        boolean[] announcedOps = {false};

        transport.chat(AiSettings.model(), messages, new Transport.StreamSink() {
            @Override
            public void onDelta(String text) {
                rawAll.append(text);
                replyStream.append(text);
                // exactly one status change per turn: status() hops to the FX thread,
                // so calling it per delta would be the flood pushDelta exists to avoid
                if (!announcedOps[0] && replyStream.finished()) {
                    announcedOps[0] = true;
                    status("Building it…");
                }
            }

            @Override
            public void onUsage(Transport.Usage reported) {
                usage[0] = reported;
            }

            @Override
            public void onFinish(String reason) {
                finish[0] = reason;
            }
        });
        drainNow();
        return new Completion(rawAll.toString(), finish[0], usage[0]);
    }

    private record Completion(String raw, String finishReason, Transport.Usage usage) {}

    // ---------------------------------------------------------------- model

    /**
     * Takes the undo checkpoint and applies the ops, both on the JavaFX thread.
     * The checkpoint goes here rather than at the start of the turn so that a
     * cancelled turn leaves no empty undo step, and so undo returns to the state
     * as it was when the edit actually landed.
     */
    private AiOps.ApplyReport applyOnFxThread(List<com.google.gson.JsonObject> ops) {
        return onFx(() -> {
            checkpoint.run();
            AiOps.ApplyReport report = OpApplier.apply(project.get(), activeForm.get(), ops);
            onApplied.run();
            return report;
        });
    }

    /**
     * Compiles a copy, never the live project: {@code JavaCodeGenerator} runs
     * {@code DockLayout.applyTo}, which rewrites x/y/width/height of docked
     * components, and iterating the component list off-thread while the user
     * drags something would race besides.
     */
    private AppRunner.CompileResult compileCopy() throws Exception {
        ProjectModel copy = onFx(() -> ProjectIO.fromJson(ProjectIO.toJson(project.get())));
        return AppRunner.compile(copy);
    }

    // --------------------------------------------------------------- repair

    /**
     * Points the model at the mistakes in the coordinates it understands — form,
     * component, event and the line of <em>its</em> code — rather than dumping a
     * 1,500-line generated file at it. Used both by the automatic repair round
     * and by the Problems dialog's "Fix with AI". Safe to call from the JavaFX
     * thread.
     */
    public String compileErrorReport(AppRunner.CompileResult result) {
        ProjectModel copy = onFx(() -> ProjectIO.fromJson(ProjectIO.toJson(project.get())));
        StringBuilder out = new StringBuilder();
        out.append("Those changes were applied, but the project no longer compiles. ")
           .append("Reply with ops that fix ONLY these errors. Do not rebuild the rest of the app.\n\n");

        int shown = 0;
        for (AppRunner.CompileError error : result.errors()) {
            if (shown++ >= MAX_REPAIR_ERRORS) {
                out.append("\n(").append(result.errors().size() - MAX_REPAIR_ERRORS)
                   .append(" more errors not shown.)\n");
                break;
            }
            SourceMap.Entry entry = result.map().resolve(error.file(), error.line());
            if (entry == null) {
                out.append(error.file()).append(" line ").append(error.line()).append(":\n  ")
                   .append(firstLine(error.message())).append('\n');
                continue;
            }
            int line = entry.userLine() + (int) Math.max(0, error.line() - entry.generatedLine());
            String who = entry.componentId() == null ? "the form" : entry.componentId();
            out.append(entry.formName()).append(" / ").append(who).append(" / ")
               .append(entry.eventKey()).append(", line ").append(line + 1).append(":\n  ")
               .append(firstLine(error.message())).append('\n');
            String source = handlerLine(copy, entry, line);
            if (!source.isBlank()) {
                out.append("  > ").append(source.strip()).append('\n');
            }
        }
        return out.toString();
    }

    private static String handlerLine(ProjectModel project, SourceMap.Entry entry, int line) {
        FormModel form = project.findForm(entry.formName());
        if (form == null) {
            return "";
        }
        String code = entry.componentId() == null
                ? form.getEvents().get(entry.eventKey())
                : componentCode(form, entry);
        if (code == null) {
            return "";
        }
        return code.lines().skip(line).findFirst().orElse("");
    }

    private static String componentCode(FormModel form, SourceMap.Entry entry) {
        FormComponent c = form.findById(entry.componentId());
        return c == null ? null : c.getEvents().get(entry.eventKey());
    }

    // -------------------------------------------------------------- plumbing

    /** One UI update per pulse, however fast the tokens arrive. */
    private void pushDelta(String text) {
        synchronized (pending) {
            pending.append(text);
        }
        if (drainScheduled.compareAndSet(false, true)) {
            Platform.runLater(this::drain);
        }
    }

    private void drain() {
        drainScheduled.set(false);
        String chunk;
        synchronized (pending) {
            chunk = pending.toString();
            pending.setLength(0);
        }
        if (!chunk.isEmpty()) {
            onDelta.accept(chunk);
        }
    }

    /** Flushes anything still buffered before the turn is reported as finished. */
    private void drainNow() {
        onFx(() -> {
            drain();
            return null;
        });
    }

    private void status(String message) {
        Platform.runLater(() -> onStatus.accept(message));
    }

    private void finish(Turn turn) {
        busy = false;
        Platform.runLater(() -> onTurnEnd.accept(turn));
    }

    private void finishCancelled() {
        busy = false;
        Platform.runLater(() -> {
            onStatus.accept("Stopped");
            onTurnEnd.accept(new Turn("", new AiOps.ApplyReport(0, List.of()), List.of(),
                    Transport.Usage.NONE, false, "Stopped before any changes were made."));
        });
    }

    private void fail(Exception ex) {
        busy = false;
        String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
        Platform.runLater(() -> {
            onStatus.accept("Failed");
            onError.accept(message);
        });
    }

    private void remember(String userText, String assistantRaw) {
        history.add(ChatMessage.user(userText));
        history.add(ChatMessage.assistant(assistantRaw));
    }

    /**
     * Runs on the JavaFX thread and waits. Running inline when already there
     * matters — otherwise a call from the FX thread would deadlock on itself.
     */
    private static <T> T onFx(Supplier<T> work) {
        if (Platform.isFxApplicationThread()) {
            return work.get();
        }
        FutureTask<T> task = new FutureTask<>(work::get);
        Platform.runLater(task);
        try {
            return task.get();
        } catch (Exception ex) {
            throw new IllegalStateException("failed on the JavaFX thread", ex);
        }
    }

    /** Running out of output room mid-app is the likeliest real failure; name it plainly. */
    private static String lengthWarning(String finishReason) {
        return "length".equals(finishReason)
                ? "The model ran out of room before finishing. Ask for one part at a time, "
                        + "or pick a model with a longer output limit."
                : "";
    }

    private static Transport.Usage add(Transport.Usage a, Transport.Usage b) {
        Double cost = a.cost() == null && b.cost() == null ? null
                : (a.cost() == null ? 0 : a.cost()) + (b.cost() == null ? 0 : b.cost());
        return new Transport.Usage(a.promptTokens() + b.promptTokens(),
                a.completionTokens() + b.completionTokens(), cost);
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return List.copyOf(all);
    }

    private static String firstLine(String message) {
        return message == null ? "" : message.lines().findFirst().orElse("");
    }
}
