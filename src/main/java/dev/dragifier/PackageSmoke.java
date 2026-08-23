package dev.dragifier;

import dev.dragifier.model.ComponentType;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import dev.dragifier.model.ProjectModel;
import dev.dragifier.packager.AppPackager;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Headless check of the packaging pipeline: builds a small form and runs the
 * full compile → jar → jpackage flow. Pass the destination directory as the
 * first argument (default: a temp dir). Exits non-zero on failure.
 */
public final class PackageSmoke {

    private PackageSmoke() {}

    public static void main(String[] args) throws Exception {
        ProjectModel project = ProjectModel.withDefaultForm();
        FormModel form = project.effectiveMain();
        form.setTitle("Packaged Demo");
        FormComponent label = form.create(ComponentType.LABEL, 24, 24);
        label.setText("It works!");
        FormComponent button = form.create(ComponentType.BUTTON, 24, 64);
        button.setText("Click");
        button.getEvents().put("onAction", "label1.setText(\"Clicked from a packaged app\");");

        boolean withWeb = java.util.Arrays.asList(args).contains("--with-web");
        if (withWeb) {
            FormComponent web = form.create(ComponentType.WEB_VIEW, 24, 110);
            web.setText("https://example.com");
            System.out.println("  (classpath-mode packaging: project includes a WebView)");
        }

        Path dest = args.length > 0
                ? Path.of(args[0])
                : Files.createTempDirectory("dragifier-package-smoke");
        Path exe = AppPackager.packageSync(project, dest, AppPackager.OutputType.APP_IMAGE,
                s -> System.out.println("  " + s));

        if (Files.exists(exe)) {
            System.out.println("PACKAGE SMOKE OK: " + exe + " (" + Files.size(exe) + " bytes)");
        } else {
            System.err.println("PACKAGE SMOKE FAILED: launcher not found at " + exe);
            System.exit(1);
        }

        // informational: try the installer type; succeeds only where WiX is installed
        try {
            Path installer = AppPackager.packageSync(project, dest, AppPackager.OutputType.INSTALLER,
                    s -> System.out.println("  " + s));
            System.out.println("INSTALLER OK: " + installer);
        } catch (Exception ex) {
            String msg = String.valueOf(ex.getMessage());
            System.out.println("INSTALLER SKIPPED: " + msg.lines().reduce((a, b) -> b).orElse(msg));
        }
    }
}
