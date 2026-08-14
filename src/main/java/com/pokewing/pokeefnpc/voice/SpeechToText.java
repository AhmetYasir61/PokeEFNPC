package com.pokewing.pokeefnpc.voice;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.PokeEFNPCConfig;

import javax.annotation.Nullable;
import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Turns a captured utterance into text, using a recogniser on your own machine.
 *
 * <p>Speaks the <b>multipart file upload</b> that both of the free options
 * understand: {@code whisper.cpp}'s bundled server (<code>/inference</code>) and
 * anything exposing the OpenAI-shaped <code>/v1/audio/transcriptions</code>
 * endpoint, which covers faster-whisper's server and most local wrappers. The
 * same request satisfies both, so the config is one URL.
 *
 * <p>Audio is downsampled to 16 kHz before sending. Whisper resamples internally
 * anyway, and sending a third of the bytes over a loopback socket is free.
 *
 * <p>Blocking. Called only from the voice worker pool.
 */
public final class SpeechToText {

    private static final String BOUNDARY = "----PokeEFNPCAudioBoundary";

    private static boolean warned;

    private SpeechToText() {
    }

    public static boolean configured() {
        return PokeEFNPCConfig.sttEnabled()
                && !PokeEFNPCConfig.sttEndpoint().isBlank();
    }

    /**
     * @param samples   mono 16-bit PCM at {@link AudioTools#VOICE_RATE}
     * @return the recognised text, or null when nothing usable came back
     */
    @Nullable
    public static String transcribe(short[] samples) {
        if (!configured() || samples.length == 0) {
            return null;
        }
        HttpURLConnection connection = null;
        try {
            short[] speech = AudioTools.resample(samples, AudioTools.VOICE_RATE,
                    AudioTools.SPEECH_RATE);
            byte[] wav = AudioTools.toWav(speech, AudioTools.SPEECH_RATE);

            connection = open();
            writeMultipart(connection, wav);

            int status = connection.getResponseCode();
            if (status != 200) {
                warnOnce("HTTP " + status);
                return null;
            }
            String body = new String(connection.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            String text = extract(body);
            if (text == null) {
                return null;
            }
            text = text.trim();
            // Whisper emits bracketed noise labels on silence — "[BLANK_AUDIO]",
            // "(wind blowing)". Those are not something a villager should answer.
            if (text.isEmpty() || text.startsWith("[") || text.startsWith("(")
                    || text.length() < PokeEFNPCConfig.sttMinCharacters()) {
                return null;
            }
            return text;
        } catch (Exception e) {
            warnOnce(e.toString());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static HttpURLConnection open() throws Exception {
        URL url = new URL(PokeEFNPCConfig.sttEndpoint());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
        connection.setRequestProperty("Authorization", "Bearer local");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(PokeEFNPCConfig.sttTimeoutMillis());
        connection.setDoOutput(true);
        return connection;
    }

    private static void writeMultipart(HttpURLConnection connection, byte[] wav) throws Exception {
        try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
            field(out, "response_format", "json");
            field(out, "temperature", "0");
            // Both fields are ignored by whichever server does not want them,
            // which is what lets one request shape serve both APIs.
            field(out, "model", PokeEFNPCConfig.sttModel());
            String language = PokeEFNPCConfig.sttLanguage();
            if (!language.isBlank() && !language.equalsIgnoreCase("auto")) {
                field(out, "language", language);
            }

            out.writeBytes("--" + BOUNDARY + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; "
                    + "filename=\"utterance.wav\"\r\n");
            out.writeBytes("Content-Type: audio/wav\r\n\r\n");
            out.write(wav);
            out.writeBytes("\r\n--" + BOUNDARY + "--\r\n");
        }
    }

    private static void field(DataOutputStream out, String name, String value) throws Exception {
        out.writeBytes("--" + BOUNDARY + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.writeBytes("\r\n");
    }

    @Nullable
    private static String extract(String body) {
        String trimmed = body.trim();
        // whisper.cpp with response_format=text answers with bare text.
        if (!trimmed.startsWith("{")) {
            return trimmed;
        }
        JsonElement parsed = JsonParser.parseString(trimmed);
        if (!parsed.isJsonObject()) {
            return null;
        }
        JsonObject root = parsed.getAsJsonObject();
        if (root.has("text")) {
            return root.get("text").getAsString();
        }
        // Some servers wrap it one level deeper.
        if (root.has("transcription")) {
            return root.get("transcription").getAsString();
        }
        return null;
    }

    private static synchronized void warnOnce(String reason) {
        if (!warned) {
            warned = true;
            PokeEFNPC.LOGGER.warn("PokeEFNPC: speech recognition at {} is not answering ({}). "
                            + "Voice input is off until it is; typed chat still works.",
                    PokeEFNPCConfig.sttEndpoint(), reason);
        }
    }
}
