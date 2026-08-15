package com.pokewing.pokeefnpc.net;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * The one door from a packet handler into client-only code.
 *
 * <p>{@link CommandPacket} is loaded on both sides, so it cannot mention a
 * screen class directly — touching one on a dedicated server would be a
 * {@code NoClassDefFoundError} at handler time. Everything client-side is
 * reached through here instead, behind a dist check, and the client class is
 * only ever named inside a method that a server never calls.
 */
public final class ClientCommandHooks {

    private ClientCommandHooks() {
    }

    public static void acceptRoster(CommandPacket packet) {
        if (FMLEnvironment.dist != Dist.CLIENT || packet.kind() != CommandPacket.Kind.ROSTER) {
            return;
        }
        com.pokewing.pokeefnpc.client.screen.CommandScreen.acceptRoster(packet.units());
    }
}
