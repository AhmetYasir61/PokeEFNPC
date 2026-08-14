package com.pokewing.pokeefnpc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * Which role skins this install actually has.
 *
 * <p>Role textures are treated as optional so a resource pack — or a later
 * release of the mod — can add art for a role without any code change, and a role
 * with no art yet simply wears the default skin instead of the missing-texture
 * chequerboard.
 */
@OnlyIn(Dist.CLIENT)
public final class NpcSkins {

    private static final Map<ResourceLocation, Boolean> CACHE = new HashMap<>();

    private NpcSkins() {
    }

    public static boolean exists(ResourceLocation location) {
        Boolean known = CACHE.get(location);
        if (known != null) {
            return known;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean present = minecraft != null
                && minecraft.getResourceManager().getResource(location).isPresent();
        CACHE.put(location, present);
        return present;
    }

    /** Dropped on a resource reload, so a pack swap is picked up. */
    public static void clear() {
        CACHE.clear();
    }
}
