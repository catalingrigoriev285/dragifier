package dev.dragifier.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.dragifier.model.FormModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saves and loads projects as pretty-printed JSON. */
public final class ProjectIO {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProjectIO() {}

    public static void save(FormModel model, Path file) throws IOException {
        Files.writeString(file, GSON.toJson(model), StandardCharsets.UTF_8);
    }

    public static String toJson(FormModel model) {
        return GSON.toJson(model);
    }

    public static FormModel fromJson(String json) {
        return GSON.fromJson(json, FormModel.class);
    }

    public static FormModel load(Path file) throws IOException {
        FormModel model = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), FormModel.class);
        if (model == null) {
            throw new IOException("File does not contain a Dragifier project: " + file);
        }
        return model;
    }
}
