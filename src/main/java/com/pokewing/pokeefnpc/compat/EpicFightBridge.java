package com.pokewing.pokeefnpc.compat;

import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.npc.NpcEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Epic Fight, reached entirely through reflection.
 *
 * <p>The rule this class keeps is that <b>nothing here is load-bearing</b>. Epic
 * Fight is an optional dependency: without it the NPCs fight with vanilla melee
 * and emote procedurally, and everything below quietly no-ops. That is why there
 * is no compile-time dependency, no mixin and no patch of Epic Fight's renderer —
 * the mod cannot break Epic Fight, and an Epic Fight point release cannot break
 * the mod. The worst case is one warning in the log and vanilla behaviour.
 *
 * <p>Two things are wanted from Epic Fight, and they are looked up independently
 * so that losing one does not cost the other:
 *
 * <ol>
 *   <li><b>A combat patch</b>, so the NPCs are real participants in Epic Fight's
 *       combat — they can be hit by its weapon arts, they stagger, they use its
 *       animated melee rather than vanilla's arm swing.</li>
 *   <li><b>Animation playback</b>, so an emote can be a proper Epic Fight
 *       animation on installs that have one.</li>
 * </ol>
 *
 * <p>Epic Fight has moved these between packages across versions, so each lookup
 * tries the names it has used and settles for the first that resolves.
 */
public final class EpicFightBridge {

    /** Class names Epic Fight has used for its capability entry point. */
    private static final String[] CAPABILITY_CLASSES = {
            "yesman.epicfight.world.capabilities.EpicFightCapabilities",
            "yesman.epicfight.capabilities.EpicFightCapabilities",
    };

    /** Class names for the animation registry. */
    private static final String[] ANIMATION_CLASSES = {
            "yesman.epicfight.api.animation.types.StaticAnimation",
            "yesman.epicfight.api.animation.AnimationManager",
    };

    private static boolean resolved;
    private static boolean present;

    /** Whether Epic Fight is installed at all. */
    public static boolean isPresent() {
        resolve();
        return present;
    }


    /**
     * Epic Fight's entity-patch capability.
     *
     * <p>Reached through the <b>field</b>, never through a method. That is not a
     * style preference: {@code EpicFightCapabilities} declares
     * {@code getLocalPlayerPatch(LocalPlayer)}, and asking Java for any method on
     * a class forces it to resolve every method signature on that class —
     * including that client-only parameter, which a dedicated server refuses to
     * load. Method reflection on this class can therefore never work server-side.
     * A single field lookup touches only the field's own type, which is
     * {@code Capability<EntityPatch>} and perfectly safe on both sides.
     */
    private static Capability<?> entityPatchCapability;

    /**
     * Epic Fight's {@code playAnimationSynchronized(AssetAccessor, float)}.
     *
     * <p>A {@link MethodHandle} rather than a {@link Method} for the same reason
     * the capability is reached by field: {@code LivingEntityPatch} declares
     * {@code flashTargetIndicator(LocalPlayerPatch)}, so anything that
     * enumerates its methods — {@code getMethod} included — drags a client-only
     * class onto a dedicated server and throws. {@code findVirtual} resolves the
     * one method symbolically and touches nothing else.
     */
    private static MethodHandle playAnimationHandle;

    /** Animations already looked up, so a missing one is only searched for once. */
    private static final Map<String, Object> ANIMATION_CACHE = new HashMap<>();

    private EpicFightBridge() {
    }

    public static boolean isLoaded() {
        resolve();
        return present;
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        present = ModList.get() != null && ModList.get().isLoaded("epicfight");
        if (!present) {
            PokeEFNPC.LOGGER.info(
                    "PokeEFNPC: Epic Fight not installed - NPCs use vanilla combat and "
                            + "procedural emotes.");
            return;
        }
        try {
            entityPatchCapability = findCapability();
            playAnimationHandle = findPlayAnimationHandle();
        } catch (Throwable t) {
            PokeEFNPC.LOGGER.warn("PokeEFNPC: Epic Fight lookup threw ({}); "
                    + "staying on vanilla combat.", t.toString());
            entityPatchCapability = null;
            playAnimationHandle = null;
        }

        if (entityPatchCapability == null) {
            PokeEFNPC.LOGGER.warn("PokeEFNPC: Epic Fight is present but its entity-patch "
                            + "capability did not resolve (tried {}). NPCs fall back to vanilla "
                            + "combat, which is fully playable.",
                    String.join(", ", CAPABILITY_CLASSES));
        } else {
            PokeEFNPC.LOGGER.info("PokeEFNPC: Epic Fight entity-patch capability resolved.");
        }
    }

    private static Capability<?> findCapability() {
        for (String className : CAPABILITY_CLASSES) {
            try {
                Class<?> caps = Class.forName(className);
                // getDeclaredField resolves this field's type and nothing else.
                java.lang.reflect.Field field = caps.getDeclaredField("CAPABILITY_ENTITY");
                field.setAccessible(true);
                Object value = field.get(null);
                if (value instanceof Capability<?> capability) {
                    return capability;
                }
            } catch (Throwable ignored) {
                // Try the next candidate.
            }
        }
        return null;
    }

    private static MethodHandle findPlayAnimationHandle() {
        for (String className : ANIMATION_CLASSES) {
            try {
                Class.forName(className);
            } catch (Throwable ignored) {
                continue;
            }
        }
        try {
            Class<?> patchClass = Class.forName(
                    "yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch");
            Class<?> assetAccessor = Class.forName("yesman.epicfight.api.asset.AssetAccessor");
            return MethodHandles.publicLookup().findVirtual(patchClass, "playAnimationSynchronized",
                    MethodType.methodType(void.class, assetAccessor, float.class));
        } catch (Throwable t) {
            // Animation playback simply stays unavailable; the client's own
            // procedural animator draws the same gesture regardless.
            return null;
        }
    }

    /**
     * Asks Epic Fight for this NPC's combat patch, creating it if Epic Fight
     * makes one for the entity type.
     *
     * <p>Called when a role is applied rather than on every tick: whether an
     * entity has a patch is decided once, at the point Epic Fight first sees it.
     *
     * @return true when the NPC now has an Epic Fight patch
     */
    public static boolean registerPatch(NpcEntity npc) {
        resolve();
        return patchOf(npc) != null;
    }

    /**
     * The NPC's Epic Fight patch, or null when Epic Fight did not make one for
     * this entity type.
     *
     * <p>Epic Fight only attaches a patch to entity types it has been told
     * about. For a mod's own mob that means a datapack entry under
     * {@code data/<namespace>/epicfight_mobpatch/<entity>.json} — see the file
     * this mod ships for {@code pokeefnpc:npc}. Without one this returns null
     * and the NPCs simply use vanilla melee.
     */
    private static Object patchOf(NpcEntity npc) {
        resolve();
        Capability<?> capability = entityPatchCapability;
        if (!present || capability == null || npc == null) {
            return null;
        }
        try {
            return npc.getCapability(capability).resolve().orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Plays the Epic Fight animation belonging to an emote, if this install has
     * one under that name.
     *
     * <p>Silently does nothing otherwise, which is the case that matters: the
     * client's procedural animator draws the same gesture regardless, so a
     * missing Epic Fight animation costs presentation polish and nothing else.
     */
    public static void playAnimation(NpcEntity npc, Emote emote) {
        resolve();
        if (!present || emote == null || emote.epicFightAnimation() == null) {
            return;
        }
        if (playAnimationHandle == null) {
            return;
        }
        Object patch = patchOf(npc);
        if (patch == null) {
            return;
        }
        Object animation = lookupAnimation(emote.epicFightAnimation());
        if (animation == null) {
            return;
        }
        try {
            // Signature is (animation, transitionTime) in every version this was
            // written against; anything else is treated as unavailable.
            playAnimationHandle.invoke(patch, animation, 0.15F);
        } catch (Throwable ignored) {
            // Presentation only — never worth interrupting the tick for.
        }
    }

    /**
     * Epic Fight's {@code AnimationManager.byKey(String)}, resolved the same way
     * and for the same reason as the playback handle.
     */
    private static MethodHandle animationByKeyHandle;
    private static boolean animationByKeyResolved;

    private static synchronized MethodHandle animationByKey() {
        if (!animationByKeyResolved) {
            animationByKeyResolved = true;
            try {
                Class<?> manager = Class.forName("yesman.epicfight.api.animation.AnimationManager");
                Class<?> accessor = Class.forName(
                        "yesman.epicfight.api.animation.AnimationManager$AnimationAccessor");
                animationByKeyHandle = MethodHandles.publicLookup().findStatic(manager, "byKey",
                        MethodType.methodType(accessor, String.class));
            } catch (Throwable t) {
                animationByKeyHandle = null;
            }
        }
        return animationByKeyHandle;
    }

    private static Object lookupAnimation(String path) {
        if (ANIMATION_CACHE.containsKey(path)) {
            return ANIMATION_CACHE.get(path);
        }
        Object found = null;
        MethodHandle byKey = animationByKey();
        if (byKey != null) {
            try {
                found = byKey.invoke(path);
            } catch (Throwable ignored) {
                // A named animation this install does not ship. Cached as absent
                // so the lookup is not repeated for every emote.
            }
        }
        ANIMATION_CACHE.put(path, found);
        return found;
    }
}
