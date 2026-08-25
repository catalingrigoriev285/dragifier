package dev.dragifier.ai;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.dragifier.codegen.FileBrowserApi;
import dev.dragifier.codegen.JavaCodeGenerator;
import dev.dragifier.codegen.RuntimeApi;
import dev.dragifier.model.ComponentType;
import dev.dragifier.model.ContainerGeometry;
import dev.dragifier.model.EventSpec;
import dev.dragifier.model.FormComponent;
import dev.dragifier.model.FormModel;
import dev.dragifier.model.ProjectModel;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies a list of assistant edit operations to a project.
 *
 * <p>Nothing here trusts the model. Every id, type, event key and slot is
 * checked against the real model before it is written, and anything refused
 * becomes a warning the chat shows rather than a silent no-op — most of all
 * event keys, which {@link dev.dragifier.codegen.JavaCodeGenerator} looks up
 * per type and simply drops when they don't match, so an unchecked bad key
 * would produce an app that compiles cleanly and does nothing.
 *
 * <p>Properties are applied as a JSON merge patch in the very vocabulary the
 * model was shown ({@link dev.dragifier.io.ProjectIO}'s field names), so adding
 * a field to {@link FormComponent} extends the AI's reach automatically — no
 * per-field table here to fall out of date the way {@code FormModel.copyOne}
 * and {@code ComponentProperties.forComponent} do.
 */
public final class OpApplier {

    private static final Gson GSON = new Gson();

    /** Fields with a dedicated op; refused inside a {@code props} patch. */
    private static final Set<String> RESERVED = Set.of("id", "type", "parentId", "events", "slot");

    /**
     * Base64 payloads. The assistant never sees these (the digest replaces them
     * with a flag), so it has nothing to write back — and letting it try would
     * mean a model inventing megabytes of Base64 into the project file.
     */
    private static final Set<String> BINARY = Set.of("imageData", "mediaData", "mediaFormat");

    /** Form names that would collide with a class the generator always emits. */
    private static final Set<String> RESERVED_FORM_NAMES = Set.of(
            JavaCodeGenerator.MAIN_CLASS,
            JavaCodeGenerator.LAUNCHER_CLASS,
            stem(RuntimeApi.FILE_NAME),
            stem(FileBrowserApi.FILE_NAME));

    /** Settable component property names, computed once from a fresh FormComponent. */
    private static final Set<String> PATCHABLE = buildPatchable();

    /**
     * Names the generated handler lambdas already bind, from {@link EventSpec}'s
     * signatures. A component called {@code event} is legal Java — the lambda
     * parameter simply shadows the field — but its own handler could then never
     * reach it, which is exactly the code the assistant writes. Refuse the name
     * instead. ({@code stage}, {@code root} and {@code UI} are already blocked by
     * {@link FormModel#canRename}.)
     */
    private static final Set<String> SHADOWED_IDS =
            Set.of("event", "file", "obs", "oldValue", "newValue");

    private static final double MIN_FORM_SIZE = 120;
    private static final double MAX_FORM_SIZE = 4000;

    private final ProjectModel project;
    private final FormModel activeForm;
    private final List<String> warnings = new ArrayList<>();
    /** Id the model asked for → id it actually got, so later ops still resolve. */
    private final Map<String, String> idRemap = new HashMap<>();
    private int applied;

    private OpApplier(ProjectModel project, FormModel activeForm) {
        this.project = project;
        this.activeForm = activeForm;
    }

    /** Applies every op it can, collecting warnings for the rest. Never throws. */
    public static AiOps.ApplyReport apply(ProjectModel project, FormModel activeForm, List<JsonObject> ops) {
        OpApplier applier = new OpApplier(project, activeForm);
        for (JsonObject op : ops) {
            try {
                applier.one(op);
            } catch (RuntimeException ex) {
                applier.warn("could not apply " + str(op, "op", "?") + ": " + ex);
            }
        }
        return new AiOps.ApplyReport(applier.applied, List.copyOf(applier.warnings));
    }

    /**
     * Component property names a {@code props} patch may set — every serialized
     * {@link FormComponent} field except the reserved ones, in declaration order.
     * Derived from the model itself, so the system prompt and the applier can
     * never disagree about what is settable.
     */
    public static Set<String> patchableKeys() {
        return PATCHABLE;
    }

    private static Set<String> buildPatchable() {
        // a fresh component serializes every field that has a non-null default,
        // which is exactly the settable set once the reserved names are removed
        Set<String> keys = new LinkedHashSet<>(
                GSON.toJsonTree(new FormComponent()).getAsJsonObject().keySet());
        keys.removeAll(RESERVED);
        keys.removeAll(BINARY);
        return Collections.unmodifiableSet(keys);
    }

    /** Form property names a {@code setForm} patch may set. */
    public static List<String> formKeys() {
        return List.of("title", "width", "height", "resizable", "name");
    }

    // ------------------------------------------------------------ dispatch

    private void one(JsonObject op) {
        String kind = str(op, "op", "");
        FormModel form = targetForm(op);
        switch (kind) {
            case "setForm" -> setForm(form, object(op, "props"));
            case "add" -> add(form, op);
            case "set" -> set(form, op);
            case "event" -> event(form, op);
            case "rename" -> rename(form, op);
            case "delete" -> delete(form, op);
            case "move" -> move(form, op);
            case "addForm" -> addForm(op);
            case "deleteForm" -> deleteForm(op);
            case "setMainForm" -> setMainForm(op);
            case "" -> warn("an op had no \"op\" field and was ignored");
            default -> warn("unknown op \"" + kind + "\" ignored");
        }
    }

    private FormModel targetForm(JsonObject op) {
        String name = str(op, "form", "");
        if (name.isEmpty()) {
            return activeForm;
        }
        FormModel form = project.findForm(name);
        if (form == null) {
            warn("no form named \"" + name + "\" — used \"" + activeForm.getName() + "\" instead");
            return activeForm;
        }
        return form;
    }

    // --------------------------------------------------------------- forms

    private void setForm(FormModel form, JsonObject props) {
        if (props == null || props.size() == 0) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : props.entrySet()) {
            JsonElement value = entry.getValue();
            switch (entry.getKey()) {
                case "title" -> form.setTitle(value.getAsString());
                case "width" -> form.setWidth(clampFormSize(value.getAsDouble()));
                case "height" -> form.setHeight(clampFormSize(value.getAsDouble()));
                case "resizable" -> form.setResizable(value.getAsBoolean());
                case "name" -> renameForm(form, value.getAsString());
                default -> warn("unknown form property \"" + entry.getKey() + "\" ignored");
            }
        }
        applied++;
    }

    private void renameForm(FormModel form, String newName) {
        String old = form.getName();
        if (newName.equals(old)) {
            return;
        }
        if (!validFormName(newName) || project.nameInUse(newName, form)) {
            warn("form name \"" + newName + "\" is invalid or already used — kept \"" + old + "\"");
            return;
        }
        form.setName(newName);
        if (old.equals(project.getMainForm())) {
            project.setMainForm(newName);
        }
    }

    private void addForm(JsonObject op) {
        FormModel form = project.addForm();
        String name = str(op, "name", "");
        if (!name.isEmpty()) {
            if (!validFormName(name) || project.nameInUse(name, form)) {
                warn("form name \"" + name + "\" is invalid or already used — created \""
                        + form.getName() + "\" instead");
            } else {
                form.setName(name);
                form.setTitle(name);
            }
        }
        setForm(form, object(op, "props"));
        applied++;
    }

    private void deleteForm(JsonObject op) {
        String name = str(op, "name", "");
        FormModel form = project.findForm(name);
        if (form == null) {
            warn("no form named \"" + name + "\" to delete");
            return;
        }
        if (!project.removeForm(form)) {
            warn("\"" + name + "\" is the only form — a project needs at least one");
            return;
        }
        applied++;
    }

    private void setMainForm(JsonObject op) {
        String name = str(op, "name", "");
        if (project.findForm(name) == null) {
            warn("no form named \"" + name + "\" to make the main form");
            return;
        }
        project.setMainForm(name);
        applied++;
    }

    /** A form name becomes a generated class name, so it must be a usable, unclaimed identifier. */
    private static boolean validFormName(String name) {
        if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))
                || RESERVED_FORM_NAMES.contains(name)) {
            return false;
        }
        return name.chars().allMatch(Character::isJavaIdentifierPart);
    }

    // ---------------------------------------------------------- components

    private void add(FormModel form, JsonObject op) {
        String typeName = str(op, "type", "");
        ComponentType type = componentType(typeName);
        if (type == null) {
            warn("unknown component type \"" + typeName + "\" — nothing added");
            return;
        }
        FormComponent parent = container(form, op, typeName);
        String slot = normalizeSlot(parent, str(op, "slot", ""));
        FormComponent c = form.create(type, number(op, "x", 0), number(op, "y", 0), parent, slot);
        claimId(form, c, str(op, "id", ""));
        patch(c, object(op, "props"));
        JsonObject events = object(op, "events");
        if (events != null) {
            for (Map.Entry<String, JsonElement> entry : events.entrySet()) {
                putEvent(c, entry.getKey(), asText(entry.getValue()));
            }
        }
        applied++;
    }

    private void set(FormModel form, JsonObject op) {
        FormComponent c = require(form, str(op, "id", ""), "set");
        if (c == null) {
            return;
        }
        patch(c, object(op, "props"));
        applied++;
    }

    private void rename(FormModel form, JsonObject op) {
        String from = str(op, "id", "");
        FormComponent c = require(form, from, "rename");
        if (c == null) {
            return;
        }
        String newId = str(op, "newId", "");
        String old = c.getId();
        // renameComponent also rewrites references to the old id in this form's event code
        if (SHADOWED_IDS.contains(newId) || !form.renameComponent(c, newId)) {
            warn("cannot rename \"" + old + "\" to \"" + newId + "\" — invalid, reserved or already used");
            return;
        }
        idRemap.put(old, newId);
        applied++;
    }

    private void delete(FormModel form, JsonObject op) {
        FormComponent c = require(form, str(op, "id", ""), "delete");
        if (c == null) {
            return;
        }
        form.remove(c);  // takes the whole subtree
        applied++;
    }

    private void move(FormModel form, JsonObject op) {
        FormComponent c = require(form, str(op, "id", ""), "move");
        if (c == null) {
            return;
        }
        // an absent "parent" key means "keep the parent, just change the slot"
        FormComponent parent = op.has("parent") ? container(form, op, c.getId()) : form.parentOf(c);
        String slot = op.has("slot") ? normalizeSlot(parent, str(op, "slot", "")) : c.getSlot();
        if (!form.reparent(c, parent, slot)) {
            warn("cannot move \"" + c.getId() + "\" there — it would nest inside itself");
            return;
        }
        applied++;
    }

    /** The container an op names via {@code parent}, or null for "directly on the form". */
    private FormComponent container(FormModel form, JsonObject op, String what) {
        String parentId = str(op, "parent", "");
        if (parentId.isEmpty()) {
            return null;
        }
        FormComponent parent = resolve(form, parentId);
        if (parent == null) {
            warn("no component \"" + parentId + "\" to hold " + what + " — placed on the form");
            return null;
        }
        if (!parent.getType().isContainer()) {
            warn("\"" + parentId + "\" is a " + parent.getType().displayName
                    + ", not a container — " + what + " placed on the form");
            return null;
        }
        return parent;
    }

    /** Takes the model's chosen id when it is legal, otherwise keeps the generated one. */
    private void claimId(FormModel form, FormComponent c, String requested) {
        if (requested.isEmpty() || requested.equals(c.getId())) {
            return;
        }
        if (form.canRename(c, requested) && !SHADOWED_IDS.contains(requested)) {
            c.setId(requested);  // brand new: nothing references the generated id yet
            return;
        }
        idRemap.put(requested, c.getId());
        warn("id \"" + requested + "\" is invalid, reserved or already used — kept \"" + c.getId() + "\"");
    }

    private FormComponent require(FormModel form, String id, String what) {
        FormComponent c = resolve(form, id);
        if (c == null) {
            warn("no component \"" + id + "\" on " + form.getName() + " to " + what);
        }
        return c;
    }

    private FormComponent resolve(FormModel form, String id) {
        if (id.isEmpty()) {
            return null;
        }
        FormComponent c = form.findById(id);
        if (c != null) {
            return c;
        }
        String remapped = idRemap.get(id);
        return remapped == null ? null : form.findById(remapped);
    }

    // ----------------------------------------------------------- properties

    /**
     * Merges {@code props} into the component through its JSON form, so the keys
     * are exactly the ones the model saw in the project it was given.
     */
    private void patch(FormComponent c, JsonObject props) {
        if (props == null || props.size() == 0) {
            return;
        }
        JsonObject merged = GSON.toJsonTree(c).getAsJsonObject();
        boolean any = false;
        for (Map.Entry<String, JsonElement> entry : props.entrySet()) {
            String key = entry.getKey();
            if (RESERVED.contains(key)) {
                warn("\"" + key + "\" cannot be set through props — use the dedicated op");
                continue;
            }
            if (BINARY.contains(key)) {
                warn("images and media can only be set in the inspector, not by the assistant");
                continue;
            }
            if (!PATCHABLE.contains(key)) {
                warn("unknown property \"" + key + "\" on " + c.getId() + " ignored");
                continue;
            }
            merged.add(key, entry.getValue());
            any = true;
        }
        if (!any) {
            return;
        }
        FormComponent patched;
        try {
            patched = GSON.fromJson(merged, FormComponent.class);
        } catch (RuntimeException ex) {
            warn("bad property value on " + c.getId() + " — left unchanged");
            return;
        }
        // an unknown enum name deserializes to null and would NPE deep in codegen
        if (patched == null || patched.getType() == null || patched.getDock() == null) {
            warn("invalid enum value on " + c.getId() + " — left unchanged");
            return;
        }
        copyFields(patched, c);
    }

    /**
     * Copies every field of the patched copy back onto the live component, so
     * references held elsewhere stay valid. Reflection rather than a setter list
     * is the point: a new {@link FormComponent} field is covered for free.
     */
    private static void copyFields(FormComponent from, FormComponent to) {
        for (Field field : FormComponent.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                field.setAccessible(true);
                field.set(to, field.get(from));
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("cannot copy field " + field.getName(), ex);
            }
        }
    }

    // --------------------------------------------------------------- events

    private void event(FormModel form, JsonObject op) {
        String key = str(op, "key", "");
        if (key.isEmpty()) {
            warn("an event op had no \"key\" and was ignored");
            return;
        }
        String code = op.has("code") ? asText(op.get("code")) : "";
        String id = str(op, "id", "");
        if (id.isEmpty()) {
            if (!hasKey(EventSpec.forForm(), key)) {
                warn("a form has no event \"" + key + "\" — code dropped; valid: "
                        + keyList(EventSpec.forForm()));
                return;
            }
            store(form.getEvents(), key, code);
            applied++;
            return;
        }
        FormComponent c = require(form, id, "attach \"" + key + "\" to");
        if (c != null && putEvent(c, key, code)) {
            applied++;
        }
    }

    /**
     * Stores one handler after checking the key really belongs to the type.
     * This check is load-bearing: {@code JavaCodeGenerator.appendEvents} iterates
     * {@code EventSpec.forType} and looks each key up, so a handler filed under a
     * key the type doesn't have is dropped at generation time with no compile
     * error — the app builds fine and the control silently does nothing.
     */
    private boolean putEvent(FormComponent c, String key, String code) {
        List<EventSpec> specs = EventSpec.forType(c.getType());
        if (!hasKey(specs, key)) {
            warn("a " + c.getType().displayName + " has no event \"" + key + "\" — code for "
                    + c.getId() + " dropped; valid: " + keyList(specs));
            return false;
        }
        store(c.getEvents(), key, code);
        return true;
    }

    private static void store(Map<String, String> events, String key, String code) {
        if (code.isBlank()) {
            events.remove(key);
        } else {
            events.put(key, code.strip());
        }
    }

    private static boolean hasKey(List<EventSpec> specs, String key) {
        return specs.stream().anyMatch(spec -> spec.key().equals(key));
    }

    private static String keyList(List<EventSpec> specs) {
        return String.join(", ", specs.stream().map(EventSpec::key).toList());
    }

    // ---------------------------------------------------------------- slots

    /**
     * Normalizes a slot string to what the parent's container kind actually
     * addresses, clamping out-of-range values rather than refusing them.
     */
    private String normalizeSlot(FormComponent parent, String slot) {
        if (parent == null) {
            return "";
        }
        return switch (parent.getType().kind) {
            case TABS, SPLIT -> {
                int max = ContainerGeometry.slotCount(parent) - 1;
                yield String.valueOf(clamp(index(slot, parent, 0), 0, max));
            }
            case GRID -> {
                String[] parts = slot.split(",");
                int col = clamp(index(parts.length > 0 ? parts[0] : "", parent, 0),
                        0, ContainerGeometry.gridColumns(parent) - 1);
                int row = clamp(index(parts.length > 1 ? parts[1] : "", parent, 0),
                        0, ContainerGeometry.gridRows(parent) - 1);
                yield col + "," + row;
            }
            case DOCK -> {
                String region = slot.trim().toUpperCase();
                if (!ContainerGeometry.DOCK_REGIONS.contains(region)) {
                    if (!slot.isBlank()) {
                        warn("\"" + slot + "\" is not a DockPanel region — used CENTER; valid: "
                                + String.join(", ", ContainerGeometry.DOCK_REGIONS));
                    }
                    yield "CENTER";
                }
                yield region;
            }
            default -> "";
        };
    }

    private int index(String text, FormComponent parent, int fallback) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ex) {
            warn("\"" + trimmed + "\" is not a slot number for " + parent.getId() + " — used " + fallback);
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampFormSize(double value) {
        return Math.max(MIN_FORM_SIZE, Math.min(MAX_FORM_SIZE, value));
    }

    // --------------------------------------------------------------- helpers

    private static ComponentType componentType(String name) {
        for (ComponentType type : ComponentType.values()) {
            if (type.name().equalsIgnoreCase(name) || type.displayName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }

    private static String str(JsonObject op, String key, String fallback) {
        JsonElement value = op.get(key);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive()
                ? fallback : value.getAsString();
    }

    private static double number(JsonObject op, String key, double fallback) {
        JsonElement value = op.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return value.getAsDouble();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static JsonObject object(JsonObject op, String key) {
        JsonElement value = op.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String asText(JsonElement value) {
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static String stem(String fileName) {
        return fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    private void warn(String message) {
        warnings.add(message);
    }
}
