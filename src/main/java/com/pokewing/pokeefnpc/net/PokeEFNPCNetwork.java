package com.pokewing.pokeefnpc.net;

import com.pokewing.pokeefnpc.PokeEFNPC;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** The mod's single packet channel. */
public final class PokeEFNPCNetwork {

    private static final String PROTOCOL = "1";

    private static SimpleChannel channel;
    private static int nextId;

    private PokeEFNPCNetwork() {
    }

    public static void register() {
        channel = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(PokeEFNPC.MOD_ID, "main"))
                .networkProtocolVersion(() -> PROTOCOL)
                .clientAcceptedVersions(PROTOCOL::equals)
                .serverAcceptedVersions(PROTOCOL::equals)
                .simpleChannel();

        channel.registerMessage(nextId++, OpenNpcMenuPacket.class,
                OpenNpcMenuPacket::encode, OpenNpcMenuPacket::decode, OpenNpcMenuPacket::handle);
        channel.registerMessage(nextId++, NpcActionPacket.class,
                NpcActionPacket::encode, NpcActionPacket::decode, NpcActionPacket::handle);
        // Travels in both directions: the roster comes back on the same type
        // that asked for it. See CommandPacket.
        channel.registerMessage(nextId++, CommandPacket.class,
                CommandPacket::encode, CommandPacket::decode, CommandPacket::handle);
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        channel.sendToServer(packet);
    }
}
