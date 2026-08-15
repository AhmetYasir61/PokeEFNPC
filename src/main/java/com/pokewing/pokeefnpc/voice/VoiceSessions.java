package com.pokewing.pokeefnpc.voice;

import com.pokewing.pokeefnpc.PokeEFNPCConfig;
import com.pokewing.pokeefnpc.npc.NpcEntity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Works out when somebody has finished speaking, and who they were speaking to.
 *
 * <p>Simple Voice Chat delivers 20 ms packets while a player is transmitting and
 * simply stops when they are not, so an "utterance" is the run of packets bounded
 * by a gap of silence. Frames are accumulated per player here and closed off by
 * {@link #sweep} once the gap is long enough, which is what lets a player just
 * talk — no key to hold, no command to type.
 *
 * <h2>Threading</h2>
 *
 * <p><b>{@link #feed} runs on Simple Voice Chat's packet thread and must never
 * touch the world.</b> An earlier version resolved the addressee there, which
 * called {@code hasLineOfSight}; that reaches into the chunk source, which blocks
 * on the server thread to load a chunk — while the server thread was itself
 * blocked on this class's own lock inside {@code sweep}. The two waited on each
 * other and the watchdog killed the server. So {@code feed} now does nothing but
 * copy audio into a buffer.
 *
 * <p>What it <i>does</i> record is a cheap snapshot of where the speaker was and
 * which way they were looking on the first frame — plain vector arithmetic over
 * fields, no world access. {@link #sweep}, which runs on the server thread,
 * resolves that aim into an actual NPC. The behaviour is unchanged from the
 * outside: who you are talking to is still decided by where you were pointed when
 * you <i>started</i> the sentence, so turning your head mid-sentence does not
 * hand the back half of it to a different villager.
 *
 * <p>No Simple Voice Chat type appears here: the plugin decodes to plain PCM
 * before handing frames over, which keeps this class loadable on a server with
 * no voice chat installed.
 */
public final class VoiceSessions {

    /** Cap on one utterance, so a stuck transmit key cannot exhaust memory. */
    private static final int MAX_SAMPLES = AudioTools.VOICE_RATE * 30;
    /** Cap on simultaneous speakers tracked, so a crowd cannot exhaust memory. */
    private static final int MAX_SESSIONS = 32;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private static final class Session {
        final List<short[]> frames = new ArrayList<>();
        int totalSamples;
        long lastPacketMillis;
        /** Loudest frame seen, used to reject a run of near-silence. */
        float peakLoudness;
        /**
         * Where the speaker's eyes were and which way they faced on the first
         * frame. Captured off-thread, but only from position and rotation
         * fields — never from anything that can touch a chunk.
         */
        Vec3 eye = Vec3.ZERO;
        Vec3 look = Vec3.ZERO;
    }

    private VoiceSessions() {
    }

    public static void clear() {
        SESSIONS.clear();
    }

    /**
     * Records one decoded frame from a player. <b>Voice thread. No world access
     * of any kind may be added to this method.</b>
     *
     * @param pcm mono 16-bit at {@link AudioTools#VOICE_RATE}
     */
    public static void feed(ServerPlayer player, short[] pcm) {
        if (!NpcVoiceBridge.canListen() || pcm.length == 0) {
            return;
        }
        Session existing = SESSIONS.get(player.getUUID());
        if (existing == null) {
            if (SESSIONS.size() >= MAX_SESSIONS) {
                return;
            }
            Session started = new Session();
            // Field reads only: position and rotation. Cheap, and safe to touch
            // from another thread in a way that a chunk lookup is not.
            started.eye = player.getEyePosition();
            started.look = player.getViewVector(1.0F).normalize();
            existing = SESSIONS.putIfAbsent(player.getUUID(), started);
            if (existing == null) {
                existing = started;
            }
        }
        synchronized (existing) {
            if (existing.totalSamples + pcm.length > MAX_SAMPLES) {
                return;
            }
            existing.frames.add(pcm);
            existing.totalSamples += pcm.length;
            existing.peakLoudness = Math.max(existing.peakLoudness, AudioTools.loudness(pcm));
            existing.lastPacketMillis = System.currentTimeMillis();
        }
    }

    /**
     * Closes off any utterance that has gone quiet, resolves who it was aimed at,
     * and sends it to be recognised. <b>Server thread, once per tick.</b>
     */
    public static void sweep(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long gap = PokeEFNPCConfig.voiceSilenceMillis();

        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            Session session = entry.getValue();

            short[] utterance;
            Vec3 eye;
            Vec3 look;
            // Everything under the lock is pure arithmetic on buffers. The world
            // work below happens after it is released, so this monitor can never
            // be held while anything blocks.
            synchronized (session) {
                if (session.frames.isEmpty() || now - session.lastPacketMillis < gap) {
                    continue;
                }
                boolean tooShort = session.totalSamples
                        < AudioTools.VOICE_RATE * PokeEFNPCConfig.voiceMinMillis() / 1000;
                boolean tooQuiet = session.peakLoudness < 0.01F;
                utterance = tooShort || tooQuiet
                        ? null : AudioTools.concat(session.frames, session.totalSamples);
                eye = session.eye;
                look = session.look;
            }
            iterator.remove();
            if (utterance == null) {
                // A cough, a knocked microphone, or the tail of somebody else's
                // sentence. Not worth waking the recogniser for.
                continue;
            }
            dispatch(server, entry.getKey(), eye, look, utterance);
        }
    }

    /** Hands one finished utterance to the recogniser, then to the NPC's brain. */
    private static void dispatch(MinecraftServer server, UUID speakerId, Vec3 eye, Vec3 look,
                                 short[] utterance) {
        ServerPlayer player = server.getPlayerList().getPlayer(speakerId);
        if (player == null) {
            return;
        }
        // Safe here: this is the server thread.
        NpcEntity addressee = resolveAddressee(player, eye, look);
        if (addressee == null) {
            // Ordinary player chatter, not aimed at anybody. Never transcribed —
            // the whole village should not be listening in on every conversation.
            return;
        }
        int targetEntityId = addressee.getId();

        NpcVoiceBridge.submit(() -> {
            String heard = SpeechToText.transcribe(utterance);
            if (heard == null) {
                return;
            }
            // Back onto the server thread to look the entity up and answer.
            server.execute(() -> {
                if (!(player.level() instanceof ServerLevel level)) {
                    return;
                }
                var entity = level.getEntity(targetEntityId);
                if (!(entity instanceof NpcEntity npc) || !npc.isAlive()) {
                    return;
                }
                if (npc.distanceToSqr(player) > listeningRangeSquared()) {
                    return;
                }
                npc.hearSpoken(player, heard);
            });
        });
    }

    /**
     * The NPC a player was talking to: nearest, within range, roughly along the
     * aim they had when they started speaking, and in line of sight.
     *
     * <p><b>Server thread only</b> — {@code hasLineOfSight} can load a chunk.
     *
     * <p>The cone matters more than the range. Standing in a crowded square and
     * looking at the smith should reach the smith, and a village where everyone
     * within eight blocks answers at once would be unusable.
     */
    @Nullable
    public static NpcEntity resolveAddressee(ServerPlayer player, Vec3 eye, Vec3 look) {
        double range = PokeEFNPCConfig.voiceListenRange();
        if (look.lengthSqr() < 1.0E-6D) {
            return null;
        }

        List<NpcEntity> nearby = player.level().getEntitiesOfClass(NpcEntity.class,
                player.getBoundingBox().inflate(range),
                npc -> npc.isAlive() && !npc.isSleeping());

        NpcEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (NpcEntity npc : nearby) {
            Vec3 toNpc = npc.getEyePosition().subtract(eye);
            double distance = toNpc.length();
            if (distance > range || distance < 0.01D) {
                continue;
            }
            double facing = look.dot(toNpc.normalize());
            if (facing < PokeEFNPCConfig.voiceConeDot()) {
                continue;
            }
            if (!npc.hasLineOfSight(player)) {
                continue;
            }
            // Prefer whatever is most directly in front, then nearest — so a
            // villager you are looking straight at beats one closer but off to
            // the side.
            double score = distance * (2.0D - facing);
            if (score < bestScore) {
                bestScore = score;
                best = npc;
            }
        }
        return best;
    }

    /**
     * Who this player is addressing right now, from their current aim. For the
     * typed-chat path, which already runs on the server thread.
     */
    @Nullable
    public static NpcEntity findAddressee(ServerPlayer player) {
        return resolveAddressee(player, player.getEyePosition(),
                player.getViewVector(1.0F).normalize());
    }

    private static double listeningRangeSquared() {
        double range = PokeEFNPCConfig.voiceListenRange() * 1.5D;
        return range * range;
    }

    /** Convenience for the brain: whether this player is mid-utterance. */
    public static boolean isSpeaking(UUID player) {
        Session session = SESSIONS.get(player);
        if (session == null) {
            return false;
        }
        synchronized (session) {
            return !session.frames.isEmpty();
        }
    }
}
