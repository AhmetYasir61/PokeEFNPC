package com.pokewing.pokeefnpc.voice;

import com.google.gson.JsonObject;
import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.PokeEFNPCConfig;
import com.pokewing.pokeefnpc.npc.NpcRole;

import javax.annotation.Nullable;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Gives an NPC a voice, using a synthesiser on your own machine.
 *
 * <p>Two request shapes cover the free options. Piper's HTTP server takes the
 * line as a plain text body and answers with a WAV; anything OpenAI-shaped
 * (<code>/v1/audio/speech</code>) takes JSON. Which one is used is a config
 * switch, so no wrapper script is needed either way.
 *
 * <p>The returned audio is resampled to the 48 kHz Simple Voice Chat needs and
 * pitched per character, so a village does not sound like forty copies of one
 * narrator — see {@link #pitchFor}.
 *
 * <p>Blocking. Called only from the voice worker pool.
 */
public final class TextToSpeech {

    private static boolean warned;

    private TextToSpeech() {
    }

    public static boolean configured() {
        return PokeEFNPCConfig.ttsEnabled() && !PokeEFNPCConfig.ttsEndpoint().isBlank();
    }

    /**
     * Speaks a line.
     *
     * @return mono 16-bit PCM at {@link AudioTools#VOICE_RATE}, or null on failure
     */
    @Nullable
    public static short[] synthesise(String text, NpcRole role, UUID speaker) {
        if (!configured() || text == null || text.isBlank()) {
            return null;
        }
        HttpURLConnection connection = null;
        try {
            connection = open();
            writeBody(connection, text);

            int status = connection.getResponseCode();
            if (status != 200) {
                warnOnce("HTTP " + status);
                return null;
            }
            byte[] wav = connection.getInputStream().readAllBytes();
            AudioTools.Wav decoded = AudioTools.readWav(wav);

            // Pitch is applied as a rate change: resampling to a rate other than
            // the file's own shifts the pitch and the speed together, which is
            // exactly the "different person" effect wanted here and costs nothing
            // beyond the resample that has to happen anyway.
            float pitch = pitchFor(role, speaker);
            int effectiveRate = Math.round(decoded.sampleRate() * pitch);

            short[] samples = AudioTools.resample(decoded.samples(), effectiveRate,
                    AudioTools.VOICE_RATE);
            return AudioTools.amplify(samples, (float) PokeEFNPCConfig.ttsGain());
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
        URL url = new URL(PokeEFNPCConfig.ttsEndpoint());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type",
                PokeEFNPCConfig.ttsJsonBody() ? "application/json" : "text/plain; charset=utf-8");
        connection.setRequestProperty("Authorization", "Bearer local");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(PokeEFNPCConfig.ttsTimeoutMillis());
        connection.setDoOutput(true);
        return connection;
    }

    private static void writeBody(HttpURLConnection connection, String text) throws Exception {
        byte[] body;
        if (PokeEFNPCConfig.ttsJsonBody()) {
            JsonObject request = new JsonObject();
            // The line is sent under BOTH names on purpose. Piper's /synthesize
            // reads "text"; OpenAI-shaped endpoints read "input". Each ignores
            // the other's key, so one body satisfies both and the config needs
            // no extra switch to say which flavour of server is listening.
            request.addProperty("text", text);
            request.addProperty("input", text);
            request.addProperty("model", PokeEFNPCConfig.ttsModel());
            request.addProperty("response_format", "wav");
            // Piper treats "voice" as a model id and falls back with a warning
            // when it does not name a voice it has loaded — and with a single
            // -m model there is nothing to choose. So an empty setting means
            // "do not ask", rather than asking for a voice that cannot exist.
            String voice = PokeEFNPCConfig.ttsVoice();
            if (!voice.isBlank()) {
                request.addProperty("voice", voice);
            }
            body = request.toString().getBytes(StandardCharsets.UTF_8);
        } else {
            body = text.getBytes(StandardCharsets.UTF_8);
        }
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body);
        }
    }

    /**
     * A per-character pitch multiplier.
     *
     * <p>Derived from the role and then jittered by the individual's own id, so
     * every guard sounds broadly like a guard and no two guards sound identical —
     * and, importantly, the same NPC sounds the same every time, because the
     * jitter comes from a stable id rather than a random number.
     */
    public static float pitchFor(NpcRole role, UUID speaker) {
        float base = switch (role) {
            case CAPTAIN, KNIGHT, EXECUTIONER, BANDIT, GUILD_MASTER -> 0.88F;
            case SOLDIER, GUARD, NIGHT_WATCH, BLACKSMITH, MERCENARY, LUMBERJACK -> 0.93F;
            case ELDER, SEER, PRIEST -> 0.90F;
            case HEALER, TAILOR, BAKER, SCRIBE, GUILD_CLERK -> 1.08F;
            case BARD, BEGGAR -> 1.12F;
            default -> 1.0F;
        };
        // +/-4%, stable per character.
        float jitter = (Math.floorMod(speaker.hashCode(), 81) - 40) / 1000.0F;
        return Math.max(0.75F, Math.min(1.3F, base + jitter));
    }

    private static synchronized void warnOnce(String reason) {
        if (!warned) {
            warned = true;
            PokeEFNPC.LOGGER.warn("PokeEFNPC: speech synthesis at {} is not answering ({}). "
                            + "NPC replies stay in text.",
                    PokeEFNPCConfig.ttsEndpoint(), reason);
        }
    }
}
