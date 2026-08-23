package dev.dragifier.runner;

import dev.dragifier.codegen.JavaCodeGenerator;
import dev.dragifier.model.FormModel;
import dev.dragifier.model.ProjectModel;
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
import java.util.Map;
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
     * Runs asynchronously. {@code status}, {@code error} and {@code output}
     * (one line of the app's stdout/stderr at a time) are always invoked on
     * the JavaFX application thread.
     */
    public static void run(ProjectModel project, Consumer<String> status,
                           BiConsumer<String, String> error, Consumer<String> output,
                           Consumer<CompileResult> onCompileErrors) {
        Thread thread = new Thread(() -> execute(project,
                s -> Platform.runLater(() -> status.accept(s)),
                (h, d) -> Platform.runLater(() -> error.accept(h, d)),
                line -> Platform.runLater(() -> output.accept(line)),
                result -> Platform.runLater(() -> onCompileErrors.accept(result))),
                "dragifier-runner");
        thread.setDaemon(true);
        thread.start();
    }

    /** One compile error, with the generated file it occurred in. */
    public record CompileError(String file, long line, String message) {}

    /** Result of compiling a project: output dir, launcher class, errors (empty = success), and the source map. */
    public record CompileResult(Path dir, String className, String errorDetails,
                                List<CompileError> errors, dev.dragifier.codegen.SourceMap map) {
        public boolean ok() {
            return errorDetails == null;
        }
    }

    /** Generates all project sources into a temp dir and compiles them with the in-process javac. */
    public static CompileResult compile(ProjectModel project) throws Exception {
        Path dir = Files.createTempDirectory("dragifier-run");
        dev.dragifier.codegen.SourceMap map = new dev.dragifier.codegen.SourceMap();
        List<Path> sourceFiles = new ArrayList<>();
        for (Map.Entry<String, String> entry : JavaCodeGenerator.generateProject(project, map).entrySet()) {
            Path src = dir.resolve(entry.getKey());
            Files.writeString(src, entry.getValue(), StandardCharsets.UTF_8);
            sourceFiles.add(src);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new CompileResult(dir, JavaCodeGenerator.LAUNCHER_CLASS,
                    "No Java compiler available — the IDE must run on a JDK (not a JRE).",
                    List.of(), map);
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean ok;
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            List<String> options = new ArrayList<>(List.of("-d", dir.toString()));
            String modulePath = modulePath();
            if (modulePath != null) {
                options.addAll(List.of("--module-path", modulePath, "--add-modules", "javafx.controls,javafx.web,javafx.media"));
            }
            ok = compiler.getTask(null, fm, diagnostics, options, null,
                    fm.getJavaFileObjectsFromPaths(sourceFiles)).call();
        }
        if (!ok) {
            List<CompileError> errors = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(d -> new CompileError(
                            d.getSource() == null ? "" : d.getSource().getName(),
                            d.getLineNumber(), d.getMessage(null)))
                    .toList();
            String details = errors.stream()
                    .map(e -> e.file() + " line " + e.line() + ": " + e.message())
                    .collect(Collectors.joining("\n"));
            return new CompileResult(dir, JavaCodeGenerator.LAUNCHER_CLASS,
                    details.isEmpty() ? "Unknown compile error" : details, errors, map);
        }
        writeImageResources(project, dir);
        return new CompileResult(dir, JavaCodeGenerator.LAUNCHER_CLASS, null, List.of(), map);
    }

    /** Writes each Image component's bytes next to the classes so getResourceAsStream finds them. */
    private static void writeImageResources(ProjectModel project, Path dir) throws Exception {
        if (project.hasWindowIcon()) {
            Files.write(dir.resolve(JavaCodeGenerator.ICON_RESOURCE),
                    java.util.Base64.getDecoder().decode(project.getIconData()));
        }
        for (FormModel form : project.getForms()) {
            for (var c : form.getComponents()) {
                if (!c.getImageData().isEmpty()) {
                    Files.write(dir.resolve(JavaCodeGenerator.imageResource(form, c)),
                            java.util.Base64.getDecoder().decode(c.getImageData()));
                }
                if (!c.getMediaData().isEmpty()) {
                    Files.write(dir.resolve(JavaCodeGenerator.mediaResource(form, c)),
                            java.util.Base64.getDecoder().decode(c.getMediaData()));
                }
            }
        }
    }

    private static void execute(ProjectModel project, Consumer<String> status,
                                BiConsumer<String, String> error, Consumer<String> output,
                                Consumer<CompileResult> onCompileErrors) {
        try {
            status.accept("Compiling…");
            CompileResult result = compile(project);
            if (!result.ok()) {
                if (result.errors().isEmpty()) {
                    error.accept("Compilation failed", result.errorDetails());
                } else {
                    onCompileErrors.accept(result);
                }
                status.accept("Compilation failed");
                return;
            }
            Path dir = result.dir();
            String cls = result.className();

            status.accept("Running " + cls + "…");
            List<String> cmd = new ArrayList<>();
            cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            String modulePath = modulePath();
            if (usesWeb(project) && modulePath != null) {
                // classpath mode: javafx.web can't resolve as a module on this JDK
                cmd.add("--enable-native-access=ALL-UNNAMED");
                cmd.addAll(List.of("-cp", modulePath + File.pathSeparator + dir, cls));
            } else {
                cmd.add("--enable-native-access=javafx.graphics");
                if (modulePath != null) {
                    cmd.addAll(List.of("--module-path", modulePath, "--add-modules", addModulesFor(project)));
                }
                cmd.addAll(List.of("-cp", dir.toString(), cls));
            }
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder all = new StringBuilder();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    all.append(line).append('\n');
                    output.accept(line);
                }
            }
            int exit = process.waitFor();
            if (exit != 0) {
                error.accept("App exited with code " + exit, all.toString());
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

    public static boolean usesWeb(ProjectModel project) {
        return usesType(project, dev.dragifier.model.ComponentType.WEB_VIEW);
    }

    public static boolean usesMedia(ProjectModel project) {
        return usesType(project, dev.dragifier.model.ComponentType.MEDIA_PLAYER);
    }

    private static boolean usesType(ProjectModel project, dev.dragifier.model.ComponentType type) {
        return project.getForms().stream()
                .flatMap(f -> f.getComponents().stream())
                .anyMatch(c -> c.getType() == type);
    }

    /**
     * JavaFX modules a project needs on the module path. WebView is excluded:
     * javafx.web requires jdk.jsobject, which modern JDKs no longer ship, so
     * WebView projects run with JavaFX on the classpath instead.
     */
    public static String addModulesFor(ProjectModel project) {
        return "javafx.controls" + (usesMedia(project) ? ",javafx.media" : "");
    }

    private static String modulePath() {
        // merge the IDE's module path with any javafx jars left on its classpath
        // (javafx-web/media ride along as plain dependencies), deduped by filename
        var byName = new java.util.LinkedHashMap<String, String>();
        for (String source : new String[]{
                System.getProperty("jdk.module.path", ""),
                System.getProperty("java.class.path", "")}) {
            for (String entry : source.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                if (entry.isBlank()) {
                    continue;
                }
                Path file = Path.of(entry).getFileName();
                if (file != null && file.toString().toLowerCase().startsWith("javafx-")) {
                    byName.putIfAbsent(file.toString(), entry);
                }
            }
        }
        return byName.isEmpty() ? null : String.join(File.pathSeparator, byName.values());
    }
}
