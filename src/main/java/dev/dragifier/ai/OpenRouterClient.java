package dev.dragifier.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to OpenRouter's OpenAI-compatible chat API over the JDK's own
 * {@link HttpClient} — no new dependency, and Dragifier runs on a JDK anyway
 * because {@code AppRunner} needs javac.
 *
 * <p>Streaming is read on the calling thread from a plain {@link InputStream},
 * which makes cancellation trivial and reliable: {@link #cancel()} closes the
 * stream from another thread and the read loop falls out.
 */
public final class OpenRouterClient implements Transport {

    private static final String BASE = "https://openrouter.ai/api/v1";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** No request timeout on the stream itself — a long generation is not a failure. */
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private volatile InputStream active;
    private volatile boolean cancelled;

    @Override
    public void chat(String model, List<ChatMessage> messages, StreamSink sink) throws Exception {
        cancelled = false;
        HttpResponse<InputStream> response = http.send(
                post("/chat/completions", requestBody(model, messages)),
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException(describe(response.statusCode(), readAll(response.body())));
        }
        try (InputStream body = response.body()) {
            active = body;
            readStream(body, sink);
        } catch (IOException ex) {
            if (!cancelled) {
                throw ex;
            }
        } finally {
            active = null;
        }
    }

    @Override
    public boolean ready() {
        return AiSettings.configured();
    }

    @Override
    public void cancel() {
        cancelled = true;
        InputStream stream = active;
        if (stream != null) {
            try {
                stream.close();  // unblocks the reader
            } catch (IOException ignored) {
                // closing to interrupt; the read loop handles the fallout
            }
        }
    }

    // -------------------------------------------------------------- streaming

    /**
     * Server-sent events: {@code data: {...}} lines terminated by
     * {@code data: [DONE]}. OpenRouter also sends {@code : OPENROUTER PROCESSING}
     * comment lines as keep-alives, which must be skipped or the JSON parser
     * chokes mid-stream.
     */
    private void readStream(InputStream body, StreamSink sink) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (cancelled) {
                return;
            }
            if (line.isBlank() || line.startsWith(":")) {
                continue;
            }
            if (!line.startsWith("data:")) {
                continue;
            }
            String payload = line.substring(5).strip();
            if (payload.equals("[DONE]")) {
                return;
            }
            JsonObject chunk;
            try {
                chunk = JsonParser.parseString(payload).getAsJsonObject();
            } catch (RuntimeException ex) {
                continue;  // a partial or unexpected frame is not worth failing the turn over
            }
            // an upstream failure arrives as HTTP 200 with an error object in the stream
            JsonObject error = chunk.getAsJsonObject("error");
            if (error != null) {
                throw new IOException("The model provider returned an error: "
                        + string(error, "message", payload));
            }
            emit(chunk, sink);
        }
    }

    private void emit(JsonObject chunk, StreamSink sink) {
        JsonArray choices = chunk.getAsJsonArray("choices");
        if (choices != null && !choices.isEmpty() && choices.get(0).isJsonObject()) {
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject delta = choice.getAsJsonObject("delta");
            if (delta != null) {
                String content = string(delta, "content", "");
                if (!content.isEmpty()) {
                    sink.onDelta(content);
                }
                String reasoning = string(delta, "reasoning", "");
                if (!reasoning.isEmpty()) {
                    sink.onReasoning(reasoning);
                }
            }
            String finish = string(choice, "finish_reason", "");
            if (!finish.isEmpty()) {
                sink.onFinish(finish);
            }
        }
        JsonObject usage = chunk.getAsJsonObject("usage");
        if (usage != null) {
            sink.onUsage(new Usage(
                    (int) number(usage, "prompt_tokens"),
                    (int) number(usage, "completion_tokens"),
                    usage.has("cost") && !usage.get("cost").isJsonNull()
                            ? usage.get("cost").getAsDouble() : null));
        }
    }

    private static String requestBody(String model, List<ChatMessage> messages) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.addProperty("stream", true);
        JsonArray list = new JsonArray();
        for (ChatMessage message : messages) {
            JsonObject entry = new JsonObject();
            entry.addProperty("role", message.role());
            entry.addProperty("content", message.content());
            list.add(entry);
        }
        root.add("messages", list);
        // asks for token counts and cost in the final chunk, so no extra call is needed
        JsonObject usage = new JsonObject();
        usage.addProperty("include", true);
        root.add("usage", usage);
        return GSON.toJson(root);
    }

    // ---------------------------------------------------------------- models

    /** The available models, newest listing OpenRouter has. Sorted by name. */
    public List<ModelInfo> listModels() throws Exception {
        HttpResponse<String> response = http.send(get("/models"),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException(describe(response.statusCode(), response.body()));
        }
        JsonArray data = JsonParser.parseString(response.body())
                .getAsJsonObject().getAsJsonArray("data");
        List<ModelInfo> models = new ArrayList<>();
        for (JsonElement element : data) {
            JsonObject entry = element.getAsJsonObject();
            JsonObject pricing = entry.getAsJsonObject("pricing");
            models.add(new ModelInfo(
                    string(entry, "id", ""),
                    string(entry, "name", ""),
                    price(pricing, "prompt"),
                    price(pricing, "completion"),
                    (long) number(entry, "context_length")));
        }
        models.sort((a, b) -> a.label().compareToIgnoreCase(b.label()));
        return models;
    }

    /** Confirms the key works, returning a short description of it. Throws when it doesn't. */
    public String checkKey() throws Exception {
        HttpResponse<String> response = http.send(get("/key"),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException(describe(response.statusCode(), response.body()));
        }
        JsonObject data = JsonParser.parseString(response.body())
                .getAsJsonObject().getAsJsonObject("data");
        if (data == null) {
            return "Key accepted.";
        }
        String label = string(data, "label", "");
        double used = number(data, "usage");
        JsonElement limit = data.get("limit");
        String remaining = limit == null || limit.isJsonNull()
                ? "no spending limit"
                : String.format("$%.2f of $%.2f used", used, limit.getAsDouble());
        return "Key accepted" + (label.isEmpty() ? "" : " (" + label + ")") + " — " + remaining + ".";
    }

    // --------------------------------------------------------------- requests

    private HttpRequest post(String path, String body) {
        return baseRequest(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private HttpRequest get(String path) {
        return baseRequest(path).GET().build();
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder(URI.create(BASE + path))
                .header("Authorization", "Bearer " + AiSettings.apiKey())
                // OpenRouter uses these for attribution on its model leaderboards
                .header("HTTP-Referer", "https://github.com/dragifier/dragifier")
                .header("X-Title", "Dragifier");
    }

    /** Turns an HTTP failure into something worth showing a user, with the key scrubbed out. */
    private static String describe(int status, String body) {
        String message = body;
        try {
            JsonObject error = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error");
            if (error != null) {
                message = string(error, "message", body);
            }
        } catch (RuntimeException ignored) {
            // not JSON — show what came back
        }
        String hint = switch (status) {
            case 401 -> "Check the API key in File → AI Settings.";
            case 402 -> "Your OpenRouter account is out of credit.";
            case 404 -> "That model id does not exist — pick one from the list in AI Settings.";
            case 429 -> "Rate limited; wait a moment and try again.";
            default -> "";
        };
        String detail = redact(message).strip();
        if (detail.length() > 400) {
            detail = detail.substring(0, 400) + "…";
        }
        return "OpenRouter returned " + status + ": " + detail + (hint.isEmpty() ? "" : "\n" + hint);
    }

    /** Never let an API key reach a dialog, the console, or a log. */
    private static String redact(String text) {
        return text == null ? "" : text.replaceAll("sk-or-[A-Za-z0-9\\-_]+", "sk-or-…");
    }

    private static String readAll(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object == null ? null : object.get(key);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive()
                ? fallback : value.getAsString();
    }

    private static double number(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        try {
            return value == null || value.isJsonNull() ? 0 : value.getAsDouble();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /** Prices come back as decimal strings in dollars per token. */
    private static double price(JsonObject pricing, String key) {
        try {
            return Double.parseDouble(string(pricing, key, "0"));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
