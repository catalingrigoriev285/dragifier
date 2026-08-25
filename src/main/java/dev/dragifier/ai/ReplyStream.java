package dev.dragifier.ai;

import java.util.function.Consumer;

/**
 * Pulls the human-readable part out of the reply while it is still arriving.
 *
 * <p>The assistant answers with a JSON envelope, so forwarding raw deltas to the
 * chat would show the user {@code {"reply": "Added a 4x5 key} one character at a
 * time. This scans for the {@code "reply"} string and emits its <em>decoded</em>
 * contents as they stream, then goes quiet once the ops start — which is why the
 * prompt insists {@code "reply"} comes first.
 *
 * <p>It never fails: if the model puts the keys the other way round, or answers
 * in prose, nothing is emitted and the caller falls back to rendering the whole
 * reply once the stream ends.
 */
public final class ReplyStream {

    private enum State { SEEKING, IN_STRING, DONE }

    private static final String KEY = "\"reply\"";

    private final StringBuilder raw = new StringBuilder();
    private final Consumer<String> onText;
    private State state = State.SEEKING;
    /** Next index of {@link #raw} to look at. */
    private int scan;

    public ReplyStream(Consumer<String> onText) {
        this.onText = onText;
    }

    public void append(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        raw.append(chunk);
        if (state == State.SEEKING) {
            seek();
        }
        if (state == State.IN_STRING) {
            decode();
        }
    }

    /** Everything received, for the real parse once the stream ends. */
    public String raw() {
        return raw.toString();
    }

    /** True when the reply text was found and streamed, so the caller need not re-render it. */
    public boolean streamed() {
        return state != State.SEEKING;
    }

    private void seek() {
        int key = raw.indexOf(KEY, scan);
        if (key < 0) {
            // the key may straddle two chunks — keep just enough tail to catch it
            scan = Math.max(0, raw.length() - KEY.length());
            return;
        }
        int i = key + KEY.length();
        i = skipSpace(i);
        if (i >= raw.length()) {
            scan = key;  // wait for the colon
            return;
        }
        if (raw.charAt(i) != ':') {
            scan = key + KEY.length();
            return;
        }
        i = skipSpace(i + 1);
        if (i >= raw.length()) {
            scan = key;
            return;
        }
        if (raw.charAt(i) != '"') {
            state = State.DONE;  // not a plain string; nothing to stream
            return;
        }
        scan = i + 1;
        state = State.IN_STRING;
    }

    private int skipSpace(int from) {
        int i = from;
        while (i < raw.length() && Character.isWhitespace(raw.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * Emits whatever is decodable so far, stopping short of a partial escape
     * sequence so a {@code \\u00e9} split across two chunks still comes out right.
     */
    private void decode() {
        StringBuilder out = new StringBuilder();
        int i = scan;
        while (i < raw.length()) {
            char ch = raw.charAt(i);
            if (ch == '"') {
                state = State.DONE;
                i++;
                break;
            }
            if (ch != '\\') {
                out.append(ch);
                i++;
                continue;
            }
            if (i + 1 >= raw.length()) {
                break;  // escape marker with nothing after it yet
            }
            char escape = raw.charAt(i + 1);
            if (escape == 'u') {
                if (i + 5 >= raw.length()) {
                    break;  // wait for all four hex digits
                }
                try {
                    out.append((char) Integer.parseInt(raw.substring(i + 2, i + 6), 16));
                } catch (NumberFormatException ex) {
                    out.append(raw, i, i + 6);  // malformed; show it rather than dropping it
                }
                i += 6;
                continue;
            }
            out.append(switch (escape) {
                case 'n' -> '\n';
                case 't' -> '\t';
                case 'r' -> '\r';
                case 'b' -> '\b';
                case 'f' -> '\f';
                default -> escape;  // \" \\ \/
            });
            i += 2;
        }
        scan = i;
        if (!out.isEmpty()) {
            onText.accept(out.toString());
        }
    }
}
