package com.pokewing.pokeefnpc.compat;

import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.PokeEFNPCConfig;
import com.pokewing.pokeefnpc.registry.ModEntities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Gives the NPCs their Epic Fight combat patch from inside the mod, with no
 * datapack involved.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Epic Fight's supported route for patching a modded mob is a datapack:
 * {@code data/<ns>/epicfight_mobpatch/<entity>.json}. That works, but it puts a
 * file the mod depends on outside the mod, where it can be forgotten, half
 * installed, or shipped inside the jar — and shipped inside the jar it once
 * broke logins outright, because Epic Fight syncs mob patches to clients during
 * the join handshake and a patch it cannot read takes the handshake down with
 * it.
 *
 * <p>So the JSON lives in the mod's own assets and is installed here instead.
 *
 * <h2>What it does</h2>
 *
 * <p>Exactly what Epic Fight's own {@code MobPatchReloadListener.apply} does,
 * and in the same order — the sequence was read out of that method rather than
 * guessed:
 *
 * <ol>
 *   <li>parse the JSON to NBT with {@code TagParser}</li>
 *   <li>{@code MobPatchReloadListener.deserialize(type, tag, false, resources)}
 *       to build a patch provider</li>
 *   <li>{@code EntityPatchProvider.putCustomEntityPatch(type, factory)} so every
 *       NPC gets a patch</li>
 *   <li>on a client, {@code RenderEngine.registerCustomEntityRenderer(type,
 *       name, tag)} so it is drawn with Epic Fight's renderer rather than the
 *       vanilla one</li>
 * </ol>
 *
 * <p>All three entry points are {@code public static} on Epic Fight's side, so
 * none of this reaches into anything private. Everything is reflective and every
 * failure is swallowed with a log line: without Epic Fight the NPCs keep vanilla
 * combat, which is a perfectly playable mod.
 *
 * <h2>Timing</h2>
 *
 * <p>Installed once the resource manager exists on each side — server start, and
 * client join — because step 2 loads the mesh through it. Re-running is safe:
 * the registration is a map put, so a second install replaces the first rather
 * than stacking.
 */
public final class EpicFightPatchInstaller {

    private static final String PATCH_RESOURCE = "/assets/pokeefnpc/epicfight/npc_patch.json";

    private static boolean installedServer;
    private static boolean installedClient;

    private EpicFightPatchInstaller() {
    }

    /** Server side. Safe to call on every server start. */
    public static void installServer(ResourceManager resources) {
        if (installedServer || !PokeEFNPCConfig.epicFightPatch()) {
            return;
        }
        installedServer = install(resources, false);
    }

    /** Client side, once the client has resources and renderers. */
    public static void installClient(ResourceManager resources) {
        if (installedClient || !PokeEFNPCConfig.epicFightPatch()) {
            return;
        }
        installedClient = install(resources, true);
    }

    private static boolean install(ResourceManager resources, boolean client) {
        if (!EpicFightBridge.isPresent()) {
            return false;
        }
        CompoundTag tag = readPatch();
        if (tag == null) {
            return false;
        }
        try {
            EntityType<?> type = ModEntities.NPC.get();

            Class<?> listener = Class.forName(
                    "yesman.epicfight.api.data.reloader.MobPatchReloadListener");
            Class<?> providerClass = Class.forName(
                    "yesman.epicfight.api.data.reloader.MobPatchReloadListener$AbstractMobPatchProvider");

            Method deserialize = listener.getMethod("deserialize", EntityType.class,
                    CompoundTag.class, boolean.class, ResourceManager.class);
            Object provider = deserialize.invoke(null, type, tag, false, resources);
            if (provider == null) {
                warn("Epic Fight accepted the patch but produced no provider");
                return false;
            }

            Method get = providerClass.getMethod("get", Entity.class);
            Function<Entity, Supplier<?>> factory = entity -> () -> {
                try {
                    return get.invoke(provider, entity);
                } catch (Throwable t) {
                    // One NPC without a patch is a cosmetic loss; an exception
                    // thrown out of a capability provider is a crash.
                    return null;
                }
            };

            Class<?> patchProvider = Class.forName(
                    "yesman.epicfight.world.capabilities.provider.EntityPatchProvider");
            Method put = patchProvider.getMethod("putCustomEntityPatch",
                    EntityType.class, Function.class);
            put.invoke(null, type, factory);

            if (client) {
                registerRenderer(type, tag);
            }
            PokeEFNPC.LOGGER.info("PokeEFNPC: Epic Fight patch installed from the mod ({} side); "
                    + "no datapack needed.", client ? "client" : "server");
            return true;
        } catch (Throwable t) {
            warn(t.toString());
            return false;
        }
    }

    /**
     * Swaps the entity's renderer for Epic Fight's.
     *
     * <p>This is the half that actually changes how an NPC looks. Without it the
     * patch is installed, the combat behaviours run, and the NPC still stands
     * there with vanilla posture — which is exactly the symptom that sent me
     * looking for this call in the first place.
     */
    private static void registerRenderer(EntityType<?> type, CompoundTag tag) throws Exception {
        String name = tag.contains("renderer") ? tag.getString("renderer") : "humanoid";
        Class<?> clientEngine = Class.forName("yesman.epicfight.client.ClientEngine");
        Object engine = clientEngine.getMethod("getInstance").invoke(null);
        if (engine == null) {
            return;
        }
        Object renderEngine = clientEngine.getField("renderEngine").get(engine);
        if (renderEngine == null) {
            return;
        }
        renderEngine.getClass()
                .getMethod("registerCustomEntityRenderer", EntityType.class, String.class,
                        CompoundTag.class)
                .invoke(renderEngine, type, name, tag);
    }

    /** The patch JSON that ships inside the jar. */
    private static CompoundTag readPatch() {
        try (InputStream in = EpicFightPatchInstaller.class.getResourceAsStream(PATCH_RESOURCE)) {
            if (in == null) {
                warn("the bundled patch file is missing from the jar");
                return null;
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // Epic Fight parses its own datapack JSON exactly this way, so the
            // file on disk and the file in here are read by identical rules.
            return TagParser.parseTag(json);
        } catch (Exception e) {
            warn("the bundled patch file did not parse (" + e + ")");
            return null;
        }
    }

    private static void warn(String reason) {
        PokeEFNPC.LOGGER.warn("PokeEFNPC: could not install the Epic Fight patch ({}). "
                + "NPCs keep vanilla combat.", reason);
    }
}
