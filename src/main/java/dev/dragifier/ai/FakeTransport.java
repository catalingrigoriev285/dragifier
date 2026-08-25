package dev.dragifier.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Transport} that replays canned replies instead of calling OpenRouter,
 * so {@link AiSession} — streaming, applying, compiling, repairing — can be
 * verified by {@code gradlew aiSmoke} with no API key and no network.
 *
 * <p>Replies are handed out in order, one per {@code chat} call, which is what
 * lets a test drive a first turn and its repair round.
 */
public final class FakeTransport implements Transport {

    private final List<String> replies;
    private final List<List<ChatMessage>> seen = new ArrayList<>();
    private final int chunkSize;
    private volatile boolean cancelled;

    /** @param chunkSize how many characters each streamed delta carries */
    public FakeTransport(int chunkSize, String... replies) {
        this.chunkSize = Math.max(1, chunkSize);
        this.replies = List.of(replies);
    }

    /** The message lists this was called with, for asserting what the prompt contained. */
    public List<List<ChatMessage>> calls() {
        return seen;
    }

    @Override
    public void chat(String model, List<ChatMessage> messages, StreamSink sink) {
        cancelled = false;
        seen.add(List.copyOf(messages));
        String reply = replies.get(Math.min(seen.size() - 1, replies.size() - 1));
        for (int i = 0; i < reply.length() && !cancelled; i += chunkSize) {
            sink.onDelta(reply.substring(i, Math.min(reply.length(), i + chunkSize)));
        }
        sink.onFinish("stop");
        sink.onUsage(new Usage(1000, 500, 0.0031));
    }

    @Override
    public void cancel() {
        cancelled = true;
    }
}
