package com.pokewing.pokeefnpc.voice;

import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.npc.NpcEntity;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;

import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Simple Voice Chat plugin — the only class in the mod that names a voice
 * chat type.
 *
 * <p>Simple Voice Chat finds this through the {@link ForgeVoicechatPlugin}
 * annotation and loads it itself. That is what makes the whole feature optional
 * without reflection: on an install with no voice chat, nothing ever asks the
 * class loader for this file, and every other class in the mod goes through
 * {@link NpcVoiceBridge}, whose signatures mention no voice chat types at all.
 *
 * <p>Two directions of audio:
 *
 * <ul>
 *   <li><b>In</b> — every microphone packet is decoded from Opus to PCM and
 *       handed to {@link VoiceSessions}, which decides when the sentence ended
 *       and who it was aimed at. One decoder is kept per speaker, because Opus
 *       is a stateful codec and sharing one across players would smear their
 *       audio together.</li>
 *   <li><b>Out</b> — a reply is played on an {@link EntityAudioChannel} anchored
 *       to the NPC, so it comes out of the villager's own mouth with the game's
 *       normal distance falloff and directionality rather than as a flat sound
 *       in the player's head.</li>
 * </ul>
 *
 * <p>The microphone event is <b>never cancelled</b>: players hear each other
 * exactly as they always did, and the NPCs are simply listening in.
 */
@ForgeVoicechatPlugin
public class NpcVoicePlugin implements VoicechatPlugin, NpcVoiceBridge.VoiceOutput {

    public static final String PLUGIN_ID = PokeEFNPC.MOD_ID;

    @Nullable
    private volatile VoicechatServerApi serverApi;
    @Nullable
    private volatile VoicechatApi api;

    /** One Opus decoder per speaker; the codec carries state between packets. */
    private final Map<UUID, OpusDecoder> decoders = new ConcurrentHashMap<>();
    /** Channels currently playing a line, so an NPC does not talk over itself. */
    private final Map<UUID, AudioPlayer> speaking = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        this.api = api;
        NpcVoiceBridge.register(this);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        this.serverApi = event.getVoicechat();
        PokeEFNPC.LOGGER.info("PokeEFNPC: voice chat server up; NPCs are listening.");
    }

    @Override
    public boolean ready() {
        return this.serverApi != null && this.api != null;
    }

    // ------------------------------------------------------------- listening

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        if (!NpcVoiceBridge.canListen()) {
            return;
        }
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null) {
            return;
        }
        // Only the sender's own copy of the packet is processed. Simple Voice
        // Chat fires this event once per receiver as well, and transcribing the
        // same sentence once per listener would be both wrong and expensive.
        if (event.getReceiverConnection() != null) {
            return;
        }
        var voicePlayer = sender.getPlayer();
        if (voicePlayer == null || !(voicePlayer.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        byte[] opus = event.getPacket().getOpusEncodedData();
        if (opus == null || opus.length == 0) {
            return;
        }
        try {
            OpusDecoder decoder = decoderFor(player.getUUID());
            if (decoder == null) {
                return;
            }
            short[] pcm = decoder.decode(opus);
            if (pcm != null && pcm.length > 0) {
                VoiceSessions.feed(player, pcm);
            }
        } catch (Throwable t) {
            // A bad frame is not worth breaking anybody's voice chat over.
            PokeEFNPC.LOGGER.debug("PokeEFNPC: could not decode a voice frame ({})", t.toString());
            resetDecoder(player.getUUID());
        }
    }

    @Nullable
    private OpusDecoder decoderFor(UUID player) {
        VoicechatApi current = this.api;
        if (current == null) {
            return null;
        }
        return this.decoders.computeIfAbsent(player, key -> current.createDecoder());
    }

    private void resetDecoder(UUID player) {
        OpusDecoder decoder = this.decoders.remove(player);
        if (decoder != null && !decoder.isClosed()) {
            decoder.close();
        }
    }

    // ------------------------------------------------------------- speaking

    /**
     * Plays a synthesised line out of the NPC's position.
     *
     * <p>Runs on the server thread — {@link NpcVoiceBridge} has already done the
     * synthesis on a worker and hopped back, because opening a channel reads the
     * entity.
     */
    @Override
    public void speak(NpcEntity npc, ServerPlayer listener, short[] pcm48k) {
        VoicechatServerApi server = this.serverApi;
        VoicechatApi voiceApi = this.api;
        if (server == null || voiceApi == null || pcm48k.length == 0) {
            return;
        }
        // Already mid-sentence: stop the old line rather than layering two
        // voices out of the same mouth.
        AudioPlayer previous = this.speaking.remove(npc.getUUID());
        if (previous != null && !previous.isStopped()) {
            previous.stopPlaying();
        }

        try {
            EntityAudioChannel channel = server.createEntityAudioChannel(
                    npc.getUUID(), voiceApi.fromEntity(npc));
            if (channel == null) {
                return;
            }
            channel.setCategory(PLUGIN_ID);
            channel.setDistance((float) com.pokewing.pokeefnpc.PokeEFNPCConfig.voiceSpeakRange());
            // Only the person being answered hears it. A villager replying to one
            // player should not broadcast to everyone standing in the square.
            channel.setFilter(target -> target.getUuid().equals(listener.getUUID()));

            AudioPlayer player = server.createAudioPlayer(channel, voiceApi.createEncoder(), pcm48k);
            player.setOnStopped(() -> {
                this.speaking.remove(npc.getUUID());
                // The mouth stops moving when the audio does, not on a guess at
                // how long the line was.
                var minecraftServer = npc.getServer();
                if (minecraftServer != null) {
                    minecraftServer.execute(() -> npc.mood().stopSpeaking());
                }
            });
            this.speaking.put(npc.getUUID(), player);
            player.startPlaying();

            // Keep the mouth flapping for as long as this is going on. The exact
            // length is unknown until playback ends, so it is topped up here and
            // cut off precisely by the stop handler above.
            npc.mood().speakFor(20 * 30);
        } catch (Throwable t) {
            PokeEFNPC.LOGGER.warn("PokeEFNPC: could not play an NPC's voice ({})", t.toString());
            this.speaking.remove(npc.getUUID());
        }
    }

    /** Drops a player's decoder when they leave. */
    public void forget(UUID player) {
        resetDecoder(player);
    }
}
