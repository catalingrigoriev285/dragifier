package dev.dragifier.packager;

import dev.dragifier.model.FormModel;
import dev.dragifier.runner.AppRunner;
import javafx.application.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Packages the designed form into a self-contained app-image (launcher exe plus
 * bundled runtime) with jpackage, reusing the IDE's own JavaFX modules.
 */
public final class AppPackager {

    private AppPackager() {}

    /** Async wrapper for the UI; callbacks run on the JavaFX application thread. */
    public static void packageApp(FormModel model, Path destDir,
                                  Consumer<String> status, BiConsumer<String, String> error) {
        Thread thread = new Thread(() -> {
            try {
                Path exe = packageSync(model, destDir,
                        s -> Platform.runLater(() -> status.accept(s)));
                Platform.runLater(() -> status.accept("Packaged: " + exe));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    error.accept("Packaging failed", ex.getMessage() == null ? String.valueOf(ex) : ex.getMessage());
                    status.accept("Packaging failed");
                });
            }
        }, "dragifier-packager");
        thread.setDaemon(true);
        thread.start();
    }

    /** Compiles, jars and jpackages the form. Returns the path of the launcher exe. */
    public static Path packageSync(FormModel model, Path destDir, Consumer<String> status) throws Exception {
        Path jpackage = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "jpackage.exe" : "jpackage");
        if (!Files.exists(jpackage)) {
            throw new IOException("jpackage not found at " + jpackage + " — a full JDK is required.");
        }

        status.accept("Compiling…");
        AppRunner.CompileResult compiled = AppRunner.compile(model);
        if (!compiled.ok()) {
            throw new IOException("Compilation failed:\n" + compiled.errorDetails());
        }
        String cls = compiled.className();

        status.accept("Creating jar…");
        Path inputDir = createJar(compiled.dir(), cls);

        status.accept("Packaging with jpackage (this takes a minute)…");
        List<String> cmd = new ArrayList<>(List.of(
                jpackage.toString(),
                "--type", "app-image",
                "--name", cls,
                "--input", inputDir.toString(),
                "--main-jar", "app.jar",
                "--main-class", cls,
                "--dest", destDir.toString(),
                "--java-options", "--enable-native-access=javafx.graphics"));
        String modulePath = AppRunner.javafxModulePath();
        if (modulePath != null) {
            cmd.addAll(List.of("--module-path", modulePath, "--add-modules", "javafx.controls"));
        }
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException("jpackage exited with code " + exit + ":\n" + output);
        }
        return destDir.resolve(cls).resolve(cls + ".exe");
    }

    /** Jars the compiled classes into {@code <classesDir>/jar-input/app.jar} and returns the input dir. */
    private static Path createJar(Path classesDir, String mainClass) throws IOException {
        Path inputDir = Files.createDirectory(classesDir.resolve("jar-input"));
        Path jar = inputDir.resolve("app.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest);
             Stream<Path> files = Files.walk(classesDir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                if (Files.isRegularFile(p) && p.toString().endsWith(".class")) {
                    out.putNextEntry(new ZipEntry(classesDir.relativize(p).toString().replace('\\', '/')));
                    out.write(Files.readAllBytes(p));
                    out.closeEntry();
                }
            }
        }
        return inputDir;
    }
}
