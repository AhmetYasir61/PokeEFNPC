package com.pokewing.pokeefnpc.voice;

import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.PokeEFNPCConfig;
import com.pokewing.pokeefnpc.npc.NpcEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The wall between the rest of the mod and Simple Voice Chat.
 *
 * <p><b>No class outside the {@code voice} package may name a Simple Voice Chat
 * type.</b> Everything goes through this facade, whose own signatures mention
 * only the mod's own classes, so the JVM never has to resolve a voice-chat class
 * on an install that does not have the mod. The plugin that <i>does</i> use those
 * types is loaded by Simple Voice Chat itself and therefore only ever exists when
 * Simple Voice Chat does.
 *
 * <p>This class also owns the audio worker pool. Speech recognition and synthesis
 * are network calls to programs that take hundreds of milliseconds to answer;
 * none of that may happen on the server thread, and the pool is bounded so a slow
 * recogniser makes the world quieter rather than slower.
 */
public final class NpcVoiceBridge {

    /**
     * What the plugin can do, expressed without naming a single voice-chat type.
     * The plugin implements it and registers itself on start-up.
     */
    public interface VoiceOutput {
        /** Plays synthesised speech out of the NPC's own position in the world. */
        void speak(NpcEntity npc, ServerPlayer listener, short[] pcm48k);

        /** True once the voice chat server is up and able to open channels. */
        boolean ready();
    }

    @Nullable
    private static volatile VoiceOutput output;

    private static ExecutorService workers;

    private NpcVoiceBridge() {
    }

    /** True when Simple Voice Chat is installed at all. */
    public static boolean modPresent() {
        return ModList.get() != null && ModList.get().isLoaded("voicechat");
    }

    /** True when an NPC can actually be heard right now. */
    public static boolean canSpeak() {
        VoiceOutput current = output;
        return PokeEFNPCConfig.voiceEnabled() && current != null && current.ready()
                && TextToSpeech.configured();
    }

    /** True when the mod is listening for players speaking to NPCs. */
    public static boolean canListen() {
        return PokeEFNPCConfig.voiceEnabled() && SpeechToText.configured();
    }

    /** Called by the plugin once Simple Voice Chat has initialised it. */
    public static void register(VoiceOutput voiceOutput) {
        output = voiceOutput;
        start();
        PokeEFNPC.LOGGER.info("PokeEFNPC: Simple Voice Chat connected - "
                + "NPCs can hear you ({}) and answer aloud ({}).",
                canListen() ? "on" : "off", TextToSpeech.configured() ? "on" : "off");
    }

    public static synchronized void start() {
        if (workers != null) {
            return;
        }
        int threads = Math.max(1, PokeEFNPCConfig.voiceThreads());
        workers = new ThreadPoolExecutor(1, threads, 30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(PokeEFNPCConfig.voiceQueueSize()),
                runnable -> {
                    Thread thread = new Thread(runnable, "PokeEFNPC-voice");
                    thread.setDaemon(true);
                    thread.setPriority(Thread.MIN_PRIORITY);
                    return thread;
                },
                // Drops rather than blocks. A dropped utterance is a villager who
                // did not hear you; a blocked one would be a stalled tick.
                new ThreadPoolExecutor.DiscardPolicy());
    }

    public static synchronized void stop() {
        if (workers != null) {
            workers.shutdownNow();
            workers = null;
        }
        output = null;
        VoiceSessions.clear();
    }

    /** Runs a piece of audio work off the server thread. */
    public static void submit(Runnable task) {
        ExecutorService pool = workers;
        if (pool != null) {
            pool.execute(task);
        }
    }

    /**
     * Says a line out loud, from the NPC's position, to one listener.
     *
     * <p>Synthesis happens on the worker pool; the audio then goes out through
     * the plugin. Silently does nothing when there is no synthesiser or no voice
     * chat — the text of the line has already been sent to chat by the caller, so
     * losing the audio costs presentation and nothing else.
     */
    public static void speak(NpcEntity npc, ServerPlayer listener, String line) {
        if (!canSpeak() || line == null || line.isBlank()) {
            return;
        }
        var role = npc.role();
        var speaker = npc.getUUID();
        submit(() -> {
            short[] pcm = TextToSpeech.synthesise(line, role, speaker);
            if (pcm == null || pcm.length == 0) {
                return;
            }
            VoiceOutput current = output;
            if (current == null) {
                return;
            }
            var server = npc.getServer();
            if (server == null) {
                return;
            }
            // Back onto the server thread: opening an audio channel touches the
            // entity, and the entity belongs to the tick.
            server.execute(() -> {
                if (npc.isAlive() && listener.isAlive()) {
                    current.speak(npc, listener, AudioTools.padToFrames(pcm));
                }
            });
        });
    }
}
