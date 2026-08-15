package com.pokewing.pokeefnpc.registry;

import com.pokewing.pokeefnpc.PokeEFNPC;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The mod's creative tab. */
public final class ModCreativeTab {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,
                    PokeEFNPC.MOD_ID);

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("pokeefnpc.tab"))
                    .icon(() -> new ItemStack(ModItems.NPC_EDITOR.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.NPC_SPAWN_EGG.get());
                        output.accept(ModItems.SOLDIER_NPC_SPAWN_EGG.get());
                        output.accept(ModItems.NPC_EDITOR.get());
                    })
                    .build());

    private ModCreativeTab() {
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
