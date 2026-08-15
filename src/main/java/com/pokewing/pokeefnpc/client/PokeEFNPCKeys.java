package com.pokewing.pokeefnpc.client;

import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.client.screen.CommandScreen;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.lwjgl.glfw.GLFW;

/** The command screen's key. Bound to 0 by default, rebindable like any other. */
@Mod.EventBusSubscriber(modid = PokeEFNPC.MOD_ID, value = Dist.CLIENT)
public final class PokeEFNPCKeys {

    public static final KeyMapping OPEN_COMMAND = new KeyMapping(
            "key.pokeefnpc.command",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_0,
            "key.categories.pokeefnpc");

    private PokeEFNPCKeys() {
    }

    @Mod.EventBusSubscriber(modid = PokeEFNPC.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_COMMAND);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        // consumeClick drains the queue, so a held key opens the panel once
        // rather than fighting to reopen it every tick.
        if (OPEN_COMMAND.consumeClick()) {
            minecraft.setScreen(new CommandScreen());
        }
    }
}
