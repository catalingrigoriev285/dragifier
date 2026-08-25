package dev.dragifier.ai;

import java.util.List;

/**
 * The seam between the assistant and the network, so everything above it can be
 * exercised without an API key.
 */
public interface Transport {

    /**
     * Streams one completion, blocking until the stream ends. Callbacks fire on
     * the calling thread — the caller is responsible for getting them onto the
     * JavaFX thread.
     */
    void chat(String model, List<ChatMessage> messages, StreamSink sink) throws Exception;

    /** Aborts the stream in flight, if any. Safe to call from another thread. */
    void cancel();

    /**
     * Whether this transport can be used at all. Only the real client needs
     * credentials, so the check lives here rather than in the session — which
     * lets a canned transport run a whole turn without one.
     */
    default boolean ready() {
        return true;
    }

    /** What the caller is told as a completion arrives. */
    interface StreamSink {
        /** A piece of the reply. Called many times per second. */
        void onDelta(String text);

        /** A piece of the model's internal reasoning, when it emits any. Usually discarded. */
        default void onReasoning(String text) {}

        /** Token counts and cost, from the last chunk before the stream closes. */
        default void onUsage(Usage usage) {}

        /**
         * Why the model stopped: "stop" is normal, "length" means it ran out of
         * output room — the most likely failure when building a big form in one turn.
         */
        default void onFinish(String reason) {}
    }

    /** Token counts for one turn, and what OpenRouter charged for it when it says. */
    record Usage(int promptTokens, int completionTokens, Double cost) {
        public static final Usage NONE = new Usage(0, 0, null);

        public boolean known() {
            return promptTokens > 0 || completionTokens > 0;
        }
    }

    /** One entry of OpenRouter's model list. Prices are dollars per token. */
    record ModelInfo(String id, String name, double promptPrice, double completionPrice, long contextLength) {

        /** "name — $3.00/$15.00 per 1M · 200k ctx", for the picker. */
        public String label() {
            String prices = promptPrice <= 0 && completionPrice <= 0
                    ? "free"
                    : String.format("$%.2f/$%.2f per 1M", promptPrice * 1e6, completionPrice * 1e6);
            String context = contextLength <= 0 ? "" : " \u00b7 " + (contextLength / 1000) + "k ctx";
            return (name == null || name.isBlank() ? id : name) + " \u2014 " + prices + context;
        }
    }
}
