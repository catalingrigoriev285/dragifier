package dev.dragifier.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import dev.dragifier.model.ProjectModel;

import java.util.Map;
import java.util.Set;

/**
 * The project as the assistant sees it: compact JSON in the same vocabulary the
 * op protocol uses, so what it reads and what it writes are one language.
 *
 * <p>This is not an optimisation, it is a requirement. Projects inline images
 * and media as Base64 inside the model, so {@code ProjectIO.toJson} of a project
 * with one screenshot in it is megabytes — enough to blow the context window and
 * bill for it on every single turn. Binary payloads are replaced by a flag here.
 *
 * <p>Properties still at their default are omitted too, which cuts a typical form
 * to about a fifth of its full serialization: Gson writes all ~38 component fields,
 * of which a button sets four or five.
 */
public final class ProjectDigest {

    // HTML escaping would turn every "=" in a handler body into \u003d — unreadable for the model
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** Field values a fresh component starts with; anything equal to these is left out. */
    private static final JsonObject DEFAULTS = GSON.toJsonTree(new FormComponent()).getAsJsonObject();

    /** Base64 payloads: never sent, reported as a flag instead. */
    private static final Map<String, String> BINARY = Map.of(
            "imageData", "hasImage",
            "mediaData", "hasMedia");

    /** Geometry is what most requests are about — always stated, default or not. */
    private static final Set<String> ALWAYS = Set.of("x", "y", "width", "height");

    /** Past this the digest is progressively summarized rather than sent whole. */
    private static final int MAX_CHARS = 60_000;
    private static final int MAX_EVENT_CHARS = 4_000;

    private ProjectDigest() {}

    /** The whole project, degrading to summaries of the inactive forms if it gets too big. */
    public static String of(ProjectModel project, FormModel active) {
        String full = render(project, active, false, Integer.MAX_VALUE);
        if (full.length() <= MAX_CHARS) {
            return full;
        }
        String trimmed = render(project, active, false, MAX_EVENT_CHARS);
        if (trimmed.length() <= MAX_CHARS) {
            return trimmed;
        }
        return render(project, active, true, MAX_EVENT_CHARS);
    }

    private static String render(ProjectModel project, FormModel active,
                                 boolean summarizeOthers, int maxEventChars) {
        JsonObject root = new JsonObject();
        root.addProperty("activeForm", active == null ? "" : active.getName());
        root.addProperty("mainForm", project.getMainForm());
        JsonArray forms = new JsonArray();
        for (FormModel form : project.getForms()) {
            forms.add(summarizeOthers && form != active
                    ? summary(form)
                    : form(form, maxEventChars));
        }
        root.add("forms", forms);
        return GSON.toJson(root);
    }

    private static JsonObject summary(FormModel form) {
        JsonObject out = new JsonObject();
        out.addProperty("name", form.getName());
        out.addProperty("title", form.getTitle());
        out.addProperty("componentCount", form.getComponents().size());
        out.addProperty("note", "not shown in full — ask about this form to see it");
        return out;
    }

    private static JsonObject form(FormModel form, int maxEventChars) {
        JsonObject out = new JsonObject();
        out.addProperty("name", form.getName());
        out.addProperty("title", form.getTitle());
        out.addProperty("width", form.getWidth());
        out.addProperty("height", form.getHeight());
        out.addProperty("resizable", form.isResizable());
        if (!form.getEvents().isEmpty()) {
            out.add("events", events(form.getEvents(), maxEventChars));
        }
        JsonArray components = new JsonArray();
        // walk() is parents-before-children, so the model reads a tree rather than a bag
        for (FormComponent c : form.walk()) {
            components.add(component(c, maxEventChars));
        }
        out.add("components", components);
        return out;
    }

    private static JsonObject component(FormComponent c, int maxEventChars) {
        JsonObject full = GSON.toJsonTree(c).getAsJsonObject();
        JsonObject out = new JsonObject();
        out.addProperty("id", c.getId());
        out.addProperty("type", c.getType() == null ? "" : c.getType().name());
        if (c.getParentId() != null) {
            out.addProperty("parent", c.getParentId());
            out.addProperty("slot", c.getSlot());
        }
        for (Map.Entry<String, JsonElement> entry : full.entrySet()) {
            String key = entry.getKey();
            if (out.has(key) || key.equals("parentId") || key.equals("events") || key.equals("slot")) {
                continue;
            }
            String flag = BINARY.get(key);
            if (flag != null) {
                if (!entry.getValue().getAsString().isEmpty()) {
                    out.addProperty(flag, true);
                }
                continue;
            }
            if (ALWAYS.contains(key) || !entry.getValue().equals(DEFAULTS.get(key))) {
                out.add(key, entry.getValue());
            }
        }
        if (!c.getEvents().isEmpty()) {
            out.add("events", events(c.getEvents(), maxEventChars));
        }
        return out;
    }

    /** Handler bodies go in whole — they are exactly what follow-up turns edit. */
    private static JsonObject events(Map<String, String> events, int maxChars) {
        JsonObject out = new JsonObject();
        for (Map.Entry<String, String> entry : events.entrySet()) {
            String code = entry.getValue();
            out.addProperty(entry.getKey(), code.length() <= maxChars
                    ? code
                    : code.substring(0, maxChars) + "\n/* …truncated… */");
        }
        return out;
    }
}
