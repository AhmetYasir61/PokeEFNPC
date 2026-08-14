package com.pokewing.pokeefnpc.registry;

import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.npc.NpcEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entity registration. */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, PokeEFNPC.MOD_ID);

    /**
     * One entity type for every role.
     *
     * <p>Registered as {@link MobCategory#CREATURE} rather than a custom
     * category so it inherits vanilla's despawn and cap behaviour for friendly
     * mobs — though {@code removeWhenFarAway} is overridden to false anyway,
     * because a settlement's population has to be stable to mean anything.
     */
    public static final RegistryObject<EntityType<NpcEntity>> NPC = ENTITIES.register("npc",
            () -> EntityType.Builder.<NpcEntity>of(NpcEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(12)
                    .updateInterval(2)
                    .build(PokeEFNPC.MOD_ID + ":npc"));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
