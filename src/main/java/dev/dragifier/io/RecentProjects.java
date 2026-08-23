package dev.dragifier.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/** Recently opened/saved project files, persisted via the Preferences API. */
public final class RecentProjects {

    private static final Preferences PREFS = Preferences.userRoot().node("dev/dragifier");
    private static final String KEY = "recentProjects";
    private static final int MAX = 8;

    private RecentProjects() {}

    /** Existing recent project files, most recent first. */
    public static List<Path> list() {
        List<Path> result = new ArrayList<>();
        for (String entry : PREFS.get(KEY, "").split("\n")) {
            if (!entry.isBlank()) {
                Path p = Path.of(entry);
                if (Files.exists(p)) {
                    result.add(p);
                }
            }
        }
        return result;
    }

    public static void add(Path file) {
        Path abs = file.toAbsolutePath();
        List<Path> current = list();
        current.removeIf(p -> p.equals(abs));
        current.add(0, abs);
        if (current.size() > MAX) {
            current = current.subList(0, MAX);
        }
        StringBuilder sb = new StringBuilder();
        for (Path p : current) {
            sb.append(p).append("\n");
        }
        PREFS.put(KEY, sb.toString());
    }

    public static void clear() {
        PREFS.remove(KEY);
    }
}
