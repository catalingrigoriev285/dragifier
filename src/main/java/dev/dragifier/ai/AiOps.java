package dev.dragifier.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses an assistant reply into prose plus a list of edit operations.
 *
 * <p>The assistant is asked for a single JSON object
 * {@code {"reply": "...", "ops": [...]}}, but models wrap it in markdown fences,
 * put prose around it, or ignore the contract entirely and just answer in
 * words. Parsing is therefore deliberately forgiving: anything that doesn't
 * yield our object shape degrades to a chat-only turn that changes nothing,
 * rather than surfacing as an error.
 */
public final class AiOps {

    /**
     * One assistant turn. {@code structured} is false when no op-protocol object
     * could be found, in which case {@code text} is the whole raw reply and
     * {@code ops} is empty.
     */
    public record Reply(String text, List<JsonObject> ops, boolean structured) {}

    /** Outcome of applying a reply's ops: how many landed, and what was refused. */
    public record ApplyReport(int applied, List<String> warnings) {
        public boolean clean() {
            return warnings.isEmpty();
        }
    }

    /** How many candidate '{' positions to try before giving up on finding the object. */
    private static final int MAX_CANDIDATES = 64;

    private AiOps() {}

    public static Reply parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Reply("", List.of(), false);
        }
        String text = raw.trim();
        String json = extractObject(text);
        if (json == null) {
            return new Reply(text, List.of(), false);
        }
        JsonObject root = asObject(json);
        if (root == null || (!root.has("reply") && !root.has("ops"))) {
            return new Reply(text, List.of(), false);
        }

        List<JsonObject> ops = new ArrayList<>();
        if (root.get("ops") != null && root.get("ops").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("ops")) {
                if (element.isJsonObject()) {
                    ops.add(element.getAsJsonObject());
                }
            }
        }
        return new Reply(replyText(root, text, json), List.copyOf(ops), true);
    }

    private static String replyText(JsonObject root, String whole, String json) {
        JsonElement reply = root.get("reply");
        if (reply != null && reply.isJsonPrimitive()) {
            return reply.getAsString().trim();
        }
        // no "reply" field: whatever the model wrote around the JSON is the message
        return whole.replace(json, "").replace("```json", "").replace("```", "").trim();
    }

    /**
     * The first substring that parses as our protocol object. Tries a balanced
     * brace scan from each '{' in turn (which naturally steps over markdown
     * fences and any prose before the object), then falls back to first-'{'
     * through last-'}' for replies whose braces don't balance cleanly.
     */
    private static String extractObject(String text) {
        int tried = 0;
        for (int i = text.indexOf('{'); i >= 0 && tried < MAX_CANDIDATES; i = text.indexOf('{', i + 1), tried++) {
            String candidate = balancedFrom(text, i);
            if (candidate != null && isProtocol(candidate)) {
                return candidate;
            }
        }
        int first = text.indexOf('{');
        int last = text.lastIndexOf('}');
        if (first >= 0 && last > first) {
            String span = text.substring(first, last + 1);
            if (isProtocol(span)) {
                return span;
            }
        }
        return null;
    }

    /**
     * The balanced {@code {...}} starting at {@code start}, or null when it never
     * closes. String literals are skipped so that braces inside generated Java
     * code — which the {@code code} fields are full of — don't throw off the depth.
     */
    private static String balancedFrom(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}' && --depth == 0) {
                return text.substring(start, i + 1);
            }
        }
        return null;
    }

    private static boolean isProtocol(String json) {
        JsonObject root = asObject(json);
        return root != null && (root.has("reply") || root.has("ops"));
    }

    private static JsonObject asObject(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException ex) {
            return null;  // not JSON, or truncated mid-stream
        }
    }
}
