package com.pokewing.pokeefnpc.client;

import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.PokeEFNPCClientConfig;
import com.pokewing.pokeefnpc.client.screen.NpcScreen;
import com.pokewing.pokeefnpc.compat.PokeFaceBridge;
import com.pokewing.pokeefnpc.registry.ModEntities;
import com.pokewing.pokeefnpc.registry.ModMenus;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-side registration. */
@Mod.EventBusSubscriber(modid = PokeEFNPC.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PokeEFNPCClient {

    private PokeEFNPCClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.NPC_MENU.get(), NpcScreen::new));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.NPC.get(), NpcRenderer::new);
    }

    /**
     * Throws away the built face profiles when the eye settings change.
     *
     * <p>Forge fires this when the file is edited on disk as well as when it is
     * first loaded, which is what turns tuning the base character into a
     * look-and-adjust loop instead of a restart each time.
     */
    @SubscribeEvent
    public static void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() == PokeEFNPCClientConfig.SPEC) {
            PokeFaceBridge.invalidateProfiles();
        }
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        // The vanilla player mesh, so NPCs take ordinary player skins and
        // PokeFace's head-bone maths lines up without adjustment.
        event.registerLayerDefinition(NpcRenderer.MAIN_LAYER,
                () -> LayerDefinition.create(
                        PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64));
    }

    /**
     * Installs the Epic Fight patch on the client, when joining a world.
     *
     * <p>Later than the server's install and for a different reason: the
     * renderer swap needs Minecraft's renderers and resources to exist, and at
     * client setup they do not. Joining is the first moment both are true.
     *
     * <p>Its own class because joining is a Forge-bus event while everything
     * else in here is mod-bus, and a handler on the wrong bus is never called
     * at all — silently.
     */
    @Mod.EventBusSubscriber(modid = PokeEFNPC.MOD_ID, value = Dist.CLIENT)
    public static final class Join {
        @SubscribeEvent
        public static void onJoin(
                net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
            com.pokewing.pokeefnpc.compat.EpicFightPatchInstaller.installClient(
                    net.minecraft.client.Minecraft.getInstance().getResourceManager());
        }
    }

}
