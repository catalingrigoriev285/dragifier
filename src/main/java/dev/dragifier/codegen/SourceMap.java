package dev.dragifier.codegen;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps lines of generated Java source back to the user-authored event code
 * they came from, so compile errors can jump to the right form/component/event.
 */
public class SourceMap {

    /**
     * One user-code line: it landed at {@code generatedLine} (1-based) of
     * {@code className}.java, and is line {@code userLine} (0-based) of the
     * event {@code eventKey} on {@code componentId} (null = form-level event)
     * of form {@code formName}.
     */
    public record Entry(String className, int generatedLine, String formName,
                        String componentId, String eventKey, int userLine) {}

    private final List<Entry> entries = new ArrayList<>();

    void add(Entry entry) {
        entries.add(entry);
    }

    /**
     * The nearest user-code line at or above {@code line} in the given file
     * (name accepted with or without path/.java), or null when the error is
     * outside any user code block.
     */
    public Entry resolve(String fileName, long line) {
        String cls = fileName == null ? "" : fileName;
        int slash = Math.max(cls.lastIndexOf('/'), cls.lastIndexOf('\\'));
        if (slash >= 0) {
            cls = cls.substring(slash + 1);
        }
        if (cls.endsWith(".java")) {
            cls = cls.substring(0, cls.length() - 5);
        }
        Entry best = null;
        for (Entry entry : entries) {
            if (entry.className().equals(cls) && entry.generatedLine() <= line
                    && (best == null || entry.generatedLine() > best.generatedLine())) {
                best = entry;
            }
        }
        return best;
    }
}
