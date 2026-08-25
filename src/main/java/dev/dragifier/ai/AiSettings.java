package dev.dragifier.ai;

import java.util.List;
import java.util.prefs.Preferences;

/**
 * The assistant's settings, in the same preferences node
 * {@link dev.dragifier.io.RecentProjects} already uses.
 *
 * <p>The API key is stored in plain text — on Windows that is
 * {@code HKCU\Software\JavaSoft\Prefs}, readable by anything running as this
 * user. That is what {@link Preferences} does; the settings dialog says so
 * rather than implying otherwise, and {@code OPENROUTER_API_KEY} in the
 * environment overrides it for anyone who would rather not store it at all.
 */
public final class AiSettings {

    private static final Preferences PREFS = Preferences.userRoot().node("dev/dragifier");

    private static final String KEY_API = "openrouterApiKey";
    private static final String KEY_MODEL = "aiModel";
    private static final String KEY_VERIFY = "aiAutoVerify";

    public static final String ENV_VAR = "OPENROUTER_API_KEY";

    /**
     * Used until the user picks one. The settings dialog fetches the live list,
     * and the field accepts any slug typed by hand, so a slug that has since been
     * retired costs one clear error and a click on Refresh.
     */
    public static final String DEFAULT_MODEL = "anthropic/claude-sonnet-4.5";

    /** Shown when the model list cannot be fetched. Marked as possibly stale in the dialog. */
    public static final List<String> FALLBACK_MODELS = List.of(
            "anthropic/claude-sonnet-4.5",
            "anthropic/claude-opus-4.1",
            "openai/gpt-4.1",
            "google/gemini-2.5-pro",
            "deepseek/deepseek-chat");

    private AiSettings() {}

    /** The environment wins, so a key never has to be written to disk at all. */
    public static String apiKey() {
        String env = System.getenv(ENV_VAR);
        return env != null && !env.isBlank() ? env.strip() : PREFS.get(KEY_API, "");
    }

    public static boolean apiKeyFromEnvironment() {
        String env = System.getenv(ENV_VAR);
        return env != null && !env.isBlank();
    }

    public static void setApiKey(String key) {
        PREFS.put(KEY_API, key == null ? "" : key.strip());
    }

    public static boolean configured() {
        return !apiKey().isEmpty();
    }

    public static String model() {
        String model = PREFS.get(KEY_MODEL, DEFAULT_MODEL);
        return model.isBlank() ? DEFAULT_MODEL : model;
    }

    public static void setModel(String model) {
        PREFS.put(KEY_MODEL, model == null || model.isBlank() ? DEFAULT_MODEL : model.strip());
    }

    /** Whether an assistant turn compiles the project afterwards and repairs what it broke. */
    public static boolean autoVerify() {
        return PREFS.getBoolean(KEY_VERIFY, true);
    }

    public static void setAutoVerify(boolean verify) {
        PREFS.putBoolean(KEY_VERIFY, verify);
    }
}
