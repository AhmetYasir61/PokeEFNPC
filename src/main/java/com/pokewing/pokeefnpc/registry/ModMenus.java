package com.pokewing.pokeefnpc.registry;

import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.menu.NpcMenu;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Menu registration.
 *
 * <p>Only one type, for every role. The per-role interface comes from the pages
 * a role declares, not from a menu class per trade — see {@link NpcMenu}.
 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, PokeEFNPC.MOD_ID);

    public static final RegistryObject<MenuType<NpcMenu>> NPC_MENU = MENUS.register("npc",
            () -> IForgeMenuType.create(NpcMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
