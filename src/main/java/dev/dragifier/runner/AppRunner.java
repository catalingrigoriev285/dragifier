package dev.dragifier.runner;

import dev.dragifier.codegen.JavaCodeGenerator;
import dev.dragifier.model.FormModel;
import javafx.application.Platform;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Compiles the generated form source with the in-process {@code javac} API and
 * launches the resulting app as a separate Java process, reusing the IDE's own
 * JavaFX module path.
 */
public final class AppRunner {

    private AppRunner() {}

    /**
     * Runs asynchronously. {@code status} and {@code error} are always invoked
     * on the JavaFX application thread.
     */
    public static void run(FormModel model, Consumer<String> status, BiConsumer<String, String> error) {
        Thread thread = new Thread(() -> execute(model,
                s -> Platform.runLater(() -> status.accept(s)),
                (h, d) -> Platform.runLater(() -> error.accept(h, d))),
                "dragifier-runner");
        thread.setDaemon(true);
        thread.start();
    }

    /** Result of compiling a form: output dir, class name, and error details (null when compilation succeeded). */
    public record CompileResult(Path dir, String className, String errorDetails) {
        public boolean ok() {
            return errorDetails == null;
        }
    }

    /** Generates the form's source into a temp dir and compiles it with the in-process javac. */
    public static CompileResult compile(FormModel model) throws Exception {
        Path dir = Files.createTempDirectory("dragifier-run");
        String cls = JavaCodeGenerator.className(model);
        Path src = dir.resolve(cls + ".java");
        Files.writeString(src, JavaCodeGenerator.generate(model), StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new CompileResult(dir, cls, "No Java compiler available — the IDE must run on a JDK (not a JRE).");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean ok;
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            List<String> options = new ArrayList<>(List.of("-d", dir.toString()));
            String modulePath = modulePath();
            if (modulePath != null) {
                options.addAll(List.of("--module-path", modulePath, "--add-modules", "javafx.controls"));
            }
            ok = compiler.getTask(null, fm, diagnostics, options, null,
                    fm.getJavaFileObjects(src)).call();
        }
        if (!ok) {
            String details = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(d -> "line " + d.getLineNumber() + ": " + d.getMessage(null))
                    .collect(Collectors.joining("\n"));
            return new CompileResult(dir, cls, details.isEmpty() ? "Unknown compile error" : details);
        }
        return new CompileResult(dir, cls, null);
    }

    private static void execute(FormModel model, Consumer<String> status, BiConsumer<String, String> error) {
        try {
            status.accept("Compiling…");
            CompileResult result = compile(model);
            if (!result.ok()) {
                error.accept("Compilation failed", result.errorDetails());
                status.accept("Compilation failed");
                return;
            }
            Path dir = result.dir();
            String cls = result.className();

            status.accept("Running " + cls + "…");
            List<String> cmd = new ArrayList<>();
            cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            cmd.add("--enable-native-access=javafx.graphics");
            String modulePath = modulePath();
            if (modulePath != null) {
                cmd.addAll(List.of("--module-path", modulePath, "--add-modules", "javafx.controls"));
            }
            cmd.addAll(List.of("-cp", dir.toString(), cls));
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            if (exit != 0) {
                error.accept("App exited with code " + exit, output);
            }
            status.accept("App finished (exit code " + exit + ")");
        } catch (Exception ex) {
            error.accept("Run failed", String.valueOf(ex));
            status.accept("Run failed");
        }
    }

    /** The IDE's JavaFX jars: module path when present, else javafx entries from the classpath. */
    public static String javafxModulePath() {
        return modulePath();
    }

    private static String modulePath() {
        String mp = System.getProperty("jdk.module.path");
        if (mp != null && !mp.isBlank()) {
            return mp;
        }
        String cp = System.getProperty("java.class.path", "");
        String javafxJars = Arrays.stream(cp.split(File.pathSeparator))
                .filter(entry -> entry.toLowerCase().contains("javafx"))
                .collect(Collectors.joining(File.pathSeparator));
        return javafxJars.isBlank() ? null : javafxJars;
    }
}
