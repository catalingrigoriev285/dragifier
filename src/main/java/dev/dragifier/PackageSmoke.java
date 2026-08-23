package dev.dragifier;

import dev.dragifier.model.ComponentType;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
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
        FormModel model = new FormModel();
        model.setTitle("Packaged Demo");
        FormComponent label = model.create(ComponentType.LABEL, 24, 24);
        label.setText("It works!");
        FormComponent button = model.create(ComponentType.BUTTON, 24, 64);
        button.setText("Click");
        button.getEvents().put("onAction", "label1.setText(\"Clicked from a packaged app\");");

        Path dest = args.length > 0
                ? Path.of(args[0])
                : Files.createTempDirectory("dragifier-package-smoke");
        Path exe = AppPackager.packageSync(model, dest, s -> System.out.println("  " + s));

        if (Files.exists(exe)) {
            System.out.println("PACKAGE SMOKE OK: " + exe + " (" + Files.size(exe) + " bytes)");
        } else {
            System.err.println("PACKAGE SMOKE FAILED: launcher not found at " + exe);
            System.exit(1);
        }
    }
}
