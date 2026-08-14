package com.pokewing.pokeefnpc.brain;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.PokeEFNPCConfig;

import javax.annotation.Nullable;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Talks to a language model running on your own machine.
 *
 * <p>Deliberately built against the <b>OpenAI chat-completions shape</b> rather
 * than any one program's own protocol, because that shape is what every local
 * runner already speaks: Ollama (<code>:11434/v1/chat/completions</code>),
 * llama.cpp's server, LM Studio, text-generation-webui, vLLM. Point the config at
 * whichever you are running and it works, with no key, no account and no traffic
 * leaving the machine.
 *
 * <p>Everything here is <b>blocking and must never be called from the server
 * thread</b>. {@link NpcBrain} owns the thread pool and is the only caller.
 *
 * <p>Failure is expected and cheap: a model that is not running, is still
 * loading, or is too slow simply yields null, and the NPC falls back to its
 * written dialogue. Nothing in the world waits on this.
 */
public final class LlmClient {

    private static final Gson GSON = new Gson();

    /** Consecutive failures before the client stops trying for a while. */
    private static final int FAILURE_LIMIT = 3;
    /** How long the client stays quiet after giving up, in milliseconds. */
    private static final long BACKOFF_MILLIS = 60_000L;

    private static int consecutiveFailures;
    private static long quietUntil;

    private LlmClient() {
    }

    /** One turn in a conversation. */
    public record Message(String role, String content) {
        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }

    /**
     * True when it is worth trying at all. False while the client is backing off
     * from a model that is not answering, so a village full of NPCs does not each
     * spend a socket timeout discovering the same thing.
     */
    public static boolean available() {
        return PokeEFNPCConfig.llmEnabled() && System.currentTimeMillis() >= quietUntil;
    }

    /**
     * Sends a conversation and returns the reply text.
     *
     * @return the model's reply, or null when it could not be reached, answered
     *         with nothing usable, or took longer than the configured timeout
     */
    @Nullable
    public static String complete(List<Message> conversation) {
        if (!available()) {
            return null;
        }
        HttpURLConnection connection = null;
        try {
            connection = open();
            writeRequest(connection, conversation);

            int status = connection.getResponseCode();
            if (status != 200) {
                fail("HTTP " + status);
                return null;
            }
            String body = new String(connection.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            String reply = extractReply(body);
            if (reply == null || reply.isBlank()) {
                fail("empty reply");
                return null;
            }
            succeed();
            return reply.trim();
        } catch (Exception e) {
            fail(e.toString());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static HttpURLConnection open() throws Exception {
        URL url = new URL(PokeEFNPCConfig.llmEndpoint());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        // Some local runners want a bearer token even though they do not check
        // it; sending a placeholder is harmless and saves a support question.
        String key = PokeEFNPCConfig.llmApiKey();
        connection.setRequestProperty("Authorization", "Bearer " + (key.isEmpty() ? "local" : key));
        int timeout = PokeEFNPCConfig.llmTimeoutMillis();
        connection.setConnectTimeout(Math.min(timeout, 5000));
        connection.setReadTimeout(timeout);
        connection.setDoOutput(true);
        return connection;
    }

    private static void writeRequest(HttpURLConnection connection, List<Message> conversation)
            throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("model", PokeEFNPCConfig.llmModel());
        request.addProperty("temperature", PokeEFNPCConfig.llmTemperature());
        // Hard cap on length. A villager who answers in three sentences is in
        // character; one who delivers an essay into a chat line is not.
        request.addProperty("max_tokens", PokeEFNPCConfig.llmMaxTokens());
        request.addProperty("stream", false);

        JsonArray messages = new JsonArray();
        for (Message message : conversation) {
            JsonObject entry = new JsonObject();
            entry.addProperty("role", message.role());
            entry.addProperty("content", message.content());
            messages.add(entry);
        }
        request.add("messages", messages);

        try (OutputStream out = connection.getOutputStream()) {
            out.write(GSON.toJson(request).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Pulls the text out of the response.
     *
     * <p>Handles both the chat-completions shape and the plain-completions shape,
     * because llama.cpp's own <code>/completion</code> endpoint answers with the
     * latter and people do point the config at it.
     */
    @Nullable
    private static String extractReply(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();

        if (root.has("choices")) {
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices.isEmpty()) {
                return null;
            }
            JsonObject first = choices.get(0).getAsJsonObject();
            if (first.has("message")) {
                JsonObject message = first.getAsJsonObject("message");
                return message.has("content") ? message.get("content").getAsString() : null;
            }
            if (first.has("text")) {
                return first.get("text").getAsString();
            }
        }
        // llama.cpp /completion, and Ollama's native /api/generate.
        if (root.has("content")) {
            return root.get("content").getAsString();
        }
        if (root.has("response")) {
            return root.get("response").getAsString();
        }
        return null;
    }

    private static synchronized void fail(String reason) {
        consecutiveFailures++;
        if (consecutiveFailures == 1) {
            PokeEFNPC.LOGGER.warn("PokeEFNPC: local model at {} did not answer ({}). "
                            + "NPCs fall back to written dialogue.",
                    PokeEFNPCConfig.llmEndpoint(), reason);
        }
        if (consecutiveFailures >= FAILURE_LIMIT) {
            quietUntil = System.currentTimeMillis() + BACKOFF_MILLIS;
            consecutiveFailures = 0;
            PokeEFNPC.LOGGER.warn("PokeEFNPC: giving the local model {} seconds to come up.",
                    BACKOFF_MILLIS / 1000L);
        }
    }

    private static synchronized void succeed() {
        if (consecutiveFailures > 0) {
            PokeEFNPC.LOGGER.info("PokeEFNPC: local model is answering again.");
        }
        consecutiveFailures = 0;
    }
}
