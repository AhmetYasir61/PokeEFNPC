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
 * <p><b>Who is being spoken to is decided once, at the first frame</b>, not at
 * the last. Otherwise a player who turns their head mid-sentence delivers the
 * back half of it to a different villager. The NPC chosen is the nearest one
 * inside the listening cone the player is facing.
 *
 * <p>No Simple Voice Chat type appears here: the plugin decodes to plain PCM
 * before handing frames over, which keeps this class loadable on a server with
 * no voice chat installed.
 */
public final class VoiceSessions {

    /** Cap on one utterance, so a stuck transmit key cannot exhaust memory. */
    private static final int MAX_SAMPLES = AudioTools.VOICE_RATE * 30;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private static final class Session {
        final List<short[]> frames = new ArrayList<>();
        int totalSamples;
        long lastPacketMillis;
        /** Entity id of the NPC being addressed, fixed at the first frame. */
        int targetEntityId = -1;
        /** Loudest frame seen, used to reject a run of near-silence. */
        float peakLoudness;
    }

    private VoiceSessions() {
    }

    public static void clear() {
        SESSIONS.clear();
    }

    /**
     * Records one decoded frame from a player.
     *
     * @param pcm mono 16-bit at {@link AudioTools#VOICE_RATE}
     */
    public static void feed(ServerPlayer player, short[] pcm) {
        if (!NpcVoiceBridge.canListen() || pcm.length == 0) {
            return;
        }
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), key -> new Session());
        synchronized (session) {
            if (session.frames.isEmpty()) {
                NpcEntity target = findAddressee(player);
                if (target == null) {
                    // Nobody is being spoken to. Do not buffer ordinary player
                    // chatter — the whole village should not be transcribing
                    // every conversation on the server.
                    SESSIONS.remove(player.getUUID());
                    return;
                }
                session.targetEntityId = target.getId();
            }
            if (session.totalSamples + pcm.length > MAX_SAMPLES) {
                return;
            }
            session.frames.add(pcm);
            session.totalSamples += pcm.length;
            session.peakLoudness = Math.max(session.peakLoudness, AudioTools.loudness(pcm));
            session.lastPacketMillis = System.currentTimeMillis();
        }
    }

    /**
     * Closes off any utterance that has gone quiet, and sends it to be
     * recognised. Called once per server tick.
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
            int targetId;
            synchronized (session) {
                if (session.frames.isEmpty() || now - session.lastPacketMillis < gap) {
                    continue;
                }
                utterance = AudioTools.concat(session.frames, session.totalSamples);
                targetId = session.targetEntityId;

                boolean tooShort = session.totalSamples
                        < AudioTools.VOICE_RATE * PokeEFNPCConfig.voiceMinMillis() / 1000;
                boolean tooQuiet = session.peakLoudness < 0.01F;
                iterator.remove();
                if (tooShort || tooQuiet) {
                    // A cough, a knocked microphone, or the tail of somebody
                    // else's sentence. Not worth waking the recogniser for.
                    continue;
                }
            }
            dispatch(server, entry.getKey(), targetId, utterance);
        }
    }

    /** Hands one finished utterance to the recogniser, then to the NPC's brain. */
    private static void dispatch(MinecraftServer server, UUID speakerId, int targetEntityId,
                                 short[] utterance) {
        ServerPlayer player = server.getPlayerList().getPlayer(speakerId);
        if (player == null) {
            return;
        }
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
     * The NPC a player is talking to: nearest, within range, roughly in front of
     * them, and in line of sight.
     *
     * <p>The cone matters more than the range. Standing in a crowded square and
     * looking at the smith should reach the smith, and a village where everyone
     * within eight blocks answers at once would be unusable.
     */
    @Nullable
    public static NpcEntity findAddressee(ServerPlayer player) {
        double range = PokeEFNPCConfig.voiceListenRange();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();

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

    private static double listeningRangeSquared() {
        double range = PokeEFNPCConfig.voiceListenRange() * 1.5D;
        return range * range;
    }

    /** Convenience for the brain: whether this player is mid-utterance. */
    public static boolean isSpeaking(UUID player) {
        Session session = SESSIONS.get(player);
        return session != null && !session.frames.isEmpty();
    }
}
