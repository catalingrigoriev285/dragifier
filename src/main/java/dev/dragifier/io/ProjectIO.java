package dev.dragifier.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dragifier.model.FormModel;
import dev.dragifier.model.ProjectModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saves and loads projects as pretty-printed JSON. */
public final class ProjectIO {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProjectIO() {}

    public static String toJson(ProjectModel project) {
        return GSON.toJson(project);
    }

    public static ProjectModel fromJson(String json) {
        return normalize(GSON.fromJson(json, ProjectModel.class));
    }

    public static void save(ProjectModel project, Path file) throws IOException {
        Files.writeString(file, toJson(project), StandardCharsets.UTF_8);
    }

    /** Loads a project file; legacy single-form files are wrapped into a one-form project. */
    public static ProjectModel load(Path file) throws IOException {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception ex) {
            throw new IOException("File does not contain a Dragifier project: " + file);
        }
        if (root.has("forms")) {
            return fromJson(json);
        }
        FormModel legacy = GSON.fromJson(json, FormModel.class);
        if (legacy == null || legacy.getComponents() == null) {
            throw new IOException("File does not contain a Dragifier project: " + file);
        }
        return ProjectModel.wrapping(legacy);
    }

    private static ProjectModel normalize(ProjectModel project) {
        if (project == null || project.getForms().isEmpty()) {
            return ProjectModel.withDefaultForm();
        }
        int n = 1;
        for (FormModel form : project.getForms()) {
            if (form.getName().isBlank() || project.nameInUse(form.getName(), form)) {
                while (project.nameInUse("Form" + n, form)) {
                    n++;
                }
                form.setName("Form" + n);
            }
        }
        return project;
    }
}
