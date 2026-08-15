package com.pokewing.pokeefnpc.compat;

import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.npc.NpcEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

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

    /** Class names Epic Fight has used for the base patch type. */
    private static final String[] PATCH_BASE_CLASSES = {
            "yesman.epicfight.world.capabilities.entitypatch.EntityPatch",
            "yesman.epicfight.capabilities.entity.CapabilityEntity",
    };

    private static boolean resolved;
    private static boolean present;

    private static Method getEntityPatch;
    /**
     * Epic Fight's lookup is generic — {@code getEntityPatch(E entity, Class<C> toType)}
     * — so the second argument has to be supplied. This is the base patch class
     * to ask for, resolved once alongside the method.
     */
    private static Class<?> patchBaseClass;
    private static Method playAnimationSynchronised;

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
        getEntityPatch = findEntityPatchMethod();
        playAnimationSynchronised = findPlayAnimationMethod();

        if (getEntityPatch == null) {
            // Naming the classes that were tried makes this diagnosable from a
            // log alone, rather than needing the mod rebuilt to find out.
            PokeEFNPC.LOGGER.warn("PokeEFNPC: Epic Fight is present but no patch lookup resolved "
                            + "(tried {}). NPCs fall back to vanilla combat, which is fully "
                            + "playable - only Epic Fight's animated melee is lost.",
                    String.join(", ", CAPABILITY_CLASSES));
        } else {
            PokeEFNPC.LOGGER.info("PokeEFNPC: Epic Fight patch lookup resolved ({}#{}, {}).",
                    getEntityPatch.getDeclaringClass().getSimpleName(), getEntityPatch.getName(),
                    patchBaseClass == null ? "single-argument form"
                            : "generic form via " + patchBaseClass.getSimpleName());
        }
    }

    /**
     * Finds Epic Fight's patch lookup.
     *
     * <p>The signature that actually ships is generic and takes <b>two</b>
     * arguments — {@code getEntityPatch(E entity, Class<C> toType)} — which is
     * the shape this has to match. Older builds exposed a one-argument form, so
     * both are accepted and {@link #patchBaseClass} records which is in play:
     * non-null means the two-argument form and the class to pass for it.
     */
    private static Method findEntityPatchMethod() {
        patchBaseClass = resolvePatchBaseClass();

        Method oneArg = null;
        for (String className : CAPABILITY_CLASSES) {
            Class<?> caps;
            try {
                caps = Class.forName(className);
            } catch (Throwable ignored) {
                continue;
            }
            for (Method method : caps.getMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                if (!name.contains("entitypatch")) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 0 || !parameters[0].isAssignableFrom(LivingEntity.class)) {
                    continue;
                }
                // The two-argument generic form is the one current Epic Fight
                // ships, so it wins when both are present.
                if (parameters.length == 2 && parameters[1] == Class.class
                        && patchBaseClass != null) {
                    return method;
                }
                if (parameters.length == 1 && oneArg == null) {
                    oneArg = method;
                }
            }
        }
        if (oneArg != null) {
            // Signals the single-argument call path below.
            patchBaseClass = null;
        }
        return oneArg;
    }

    private static Class<?> resolvePatchBaseClass() {
        for (String className : PATCH_BASE_CLASSES) {
            try {
                return Class.forName(className);
            } catch (Throwable ignored) {
                // Try the next candidate.
            }
        }
        return null;
    }

    /** Calls Epic Fight's lookup with whichever signature resolved. */
    private static Object invokePatchLookup(NpcEntity npc) throws Exception {
        if (patchBaseClass != null) {
            return getEntityPatch.invoke(null, npc, patchBaseClass);
        }
        return getEntityPatch.invoke(null, npc);
    }

    private static Method findPlayAnimationMethod() {
        for (String className : ANIMATION_CLASSES) {
            try {
                Class.forName(className);
            } catch (Throwable ignored) {
                continue;
            }
            try {
                Class<?> patchClass = Class.forName(
                        "yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch");
                for (Method method : patchClass.getMethods()) {
                    String name = method.getName();
                    if ((name.equals("playAnimationSynchronized") || name.equals("playAnimation"))
                            && method.getParameterCount() >= 2) {
                        return method;
                    }
                }
            } catch (Throwable ignored) {
                // Fall through; animation playback simply stays unavailable.
            }
        }
        return null;
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
        if (!present || getEntityPatch == null || npc == null) {
            return false;
        }
        try {
            return invokePatchLookup(npc) != null;
        } catch (Throwable t) {
            // One failure means this install's Epic Fight is not shaped the way
            // the lookup expected. Stop asking rather than throwing every spawn.
            PokeEFNPC.LOGGER.warn("PokeEFNPC: Epic Fight patch lookup failed ({}); "
                    + "disabling the Epic Fight path.", t.toString());
            getEntityPatch = null;
            return false;
        }
    }

    /** The NPC's Epic Fight patch, or null when there is not one. */
    private static Object patchOf(NpcEntity npc) {
        if (!present || getEntityPatch == null) {
            return null;
        }
        try {
            return invokePatchLookup(npc);
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
        if (playAnimationSynchronised == null) {
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
            if (playAnimationSynchronised.getParameterCount() == 2) {
                playAnimationSynchronised.invoke(patch, animation, 0.15F);
            }
        } catch (Throwable ignored) {
            // Presentation only — never worth interrupting the tick for.
        }
    }

    private static Object lookupAnimation(String path) {
        if (ANIMATION_CACHE.containsKey(path)) {
            return ANIMATION_CACHE.get(path);
        }
        Object found = null;
        try {
            Class<?> manager = Class.forName("yesman.epicfight.api.animation.AnimationManager");
            for (Method method : manager.getMethods()) {
                if (method.getName().equals("byKeyOrThrow") || method.getName().equals("byKey")) {
                    if (method.getParameterCount() == 1
                            && method.getParameterTypes()[0] == String.class) {
                        found = method.invoke(null, path);
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
            // A named animation this install does not ship. Cached as absent so
            // the lookup is not repeated for every emote.
        }
        ANIMATION_CACHE.put(path, found);
        return found;
    }

    /**
     * True when the NPC is in an Epic Fight battle stance right now. Used by the
     * face driver so a guard that has drawn its weapon looks like it means it.
     */
    public static boolean isInBattleMode(NpcEntity npc) {
        Object patch = patchOf(npc);
        if (patch == null) {
            return false;
        }
        try {
            Method mode = patch.getClass().getMethod("getEntityState");
            Object state = mode.invoke(patch);
            return state != null && !state.toString().equalsIgnoreCase("FREE");
        } catch (Throwable t) {
            return false;
        }
    }
}
