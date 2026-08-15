package com.pokewing.pokeefnpc;

import com.pokewing.pokeefnpc.net.PokeEFNPCNetwork;
import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.registry.ModCreativeTab;
import com.pokewing.pokeefnpc.registry.ModEntities;
import com.pokewing.pokeefnpc.registry.ModItems;
import com.pokewing.pokeefnpc.registry.ModMenus;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entry point.
 *
 * <p>The system this wires together has three layers. At the bottom, an
 * {@code NpcEntity} composed of a role, a personality, a mood and a reputation.
 * In the middle, a {@code Settlement} that owns the beds, the walls, the larder
 * and the shared sense of how much danger everyone is in. On top, two ways for
 * people to get into the world at all: villages that populate themselves, and an
 * operator placing exactly who they want with the editor.
 *
 * <p>Epic Fight and PokeFace are both optional and both reached by reflection.
 * The mod is fully playable with neither installed.
 */
@Mod(PokeEFNPC.MOD_ID)
public class PokeEFNPC {

    public static final String MOD_ID = "pokeefnpc";
    public static final Logger LOGGER = LoggerFactory.getLogger("PokeEFNPC");

    public PokeEFNPC() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        ModEntities.register(bus);
        ModItems.register(bus);
        ModMenus.register(bus);
        ModCreativeTab.register(bus);

        bus.addListener(this::commonSetup);
        bus.addListener(this::registerAttributes);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, PokeEFNPCConfig.SPEC);
        // How the faces look is the one thing that is nobody else's business, so
        // it is a client config: a player can retune the eyes on a server they
        // do not own. A dedicated server loads the spec and simply never reads it.
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PokeEFNPCClientConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(new com.pokewing.pokeefnpc.event.ServerEvents());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(PokeEFNPCNetwork::register);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.NPC.get(), NpcEntity.createAttributes().build());
    }
}
