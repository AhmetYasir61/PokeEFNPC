package com.pokewing.pokeefnpc.registry;

import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.item.NpcEditorItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Items — the two things an operator uses to place and shape a population. */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PokeEFNPC.MOD_ID);

    /** Places an NPC that rolls whatever role the local settlement is short of. */
    public static final RegistryObject<Item> NPC_SPAWN_EGG = ITEMS.register("npc_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.NPC, 0x8A6B4A, 0xC9A227,
                    new Item.Properties()));

    /**
     * The operator's tool: sets a placed NPC's role, locks it, marks landmarks
     * and founds settlements. See {@link NpcEditorItem}.
     */
    public static final RegistryObject<Item> NPC_EDITOR = ITEMS.register("npc_editor",
            () -> new NpcEditorItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
