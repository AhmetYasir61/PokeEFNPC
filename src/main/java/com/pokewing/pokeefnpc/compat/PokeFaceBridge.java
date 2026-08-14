package com.pokewing.pokeefnpc.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.npc.NpcRole;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;

/**
 * Draws NPC faces through PokeFace, without either mod depending on the other.
 *
 * <p>PokeFace already knows how to draw a face onto a head bone —
 * {@code FaceRenderer.renderInHeadSpace} — but it only attaches that to the
 * <i>player</i> renderer. This bridge calls the same entry point from the NPC's
 * own render layer, so the NPCs get exactly the eyes, brows and mouth the player
 * has, animated by the same code, with no duplicated drawing logic and no changes
 * needed on the PokeFace side.
 *
 * <p>Everything is reflective and every failure is absorbed. Without PokeFace
 * installed {@link #isAvailable()} is false, the render layer skips, and the
 * NPCs simply have plain skin faces — the mood system underneath is unaffected
 * and still drives emotes, dialogue and behaviour.
 *
 * <p><b>The brow convention is the contract between the two mods.</b> PokeFace
 * reads {@code FaceState.brow} as -1 = drawn down and inward (angry) and +1 =
 * raised at the inner ends (sad, surprised), and {@code Emotion} produces exactly
 * that. Nothing is remapped here.
 */
@OnlyIn(Dist.CLIENT)
public final class PokeFaceBridge {

    private static boolean resolved;
    private static boolean available;

    private static Method unpack;
    private static Method renderInHeadSpace;
    private static Constructor<?> profileConstructor;
    private static Field expressionField;
    private static Field browField;
    private static Field mouthOpenField;
    private static Field mouthSmileField;
    private static Field gazeXField;
    private static Field gazeYField;
    private static Field intensityField;
    private static Field blinkLeftField;
    private static Field blinkRightField;
    private static Object[] expressionValues;

    /** One profile per role, built once — every guard has the same eyes. */
    private static final Map<NpcRole, Object> ROLE_PROFILES = new EnumMap<>(NpcRole.class);

    private PokeFaceBridge() {
    }

    public static boolean isAvailable() {
        resolve();
        return available;
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (ModList.get() == null || !ModList.get().isLoaded("pokeface")) {
            PokeEFNPC.LOGGER.info("PokeEFNPC: PokeFace not installed - NPCs keep plain faces. "
                    + "Moods still drive behaviour, emotes and dialogue.");
            return;
        }
        try {
            Class<?> faceState = Class.forName("com.pokewing.pokeface.face.FaceState");
            Class<?> faceProfile = Class.forName("com.pokewing.pokeface.face.FaceProfile");
            Class<?> expression = Class.forName("com.pokewing.pokeface.face.Expression");
            Class<?> renderer = Class.forName("com.pokewing.pokeface.client.FaceRenderer");

            unpack = faceState.getMethod("unpack", long.class, expression);
            expressionValues = (Object[]) expression.getMethod("values").invoke(null);
            profileConstructor = faceProfile.getConstructor();

            renderInHeadSpace = renderer.getMethod("renderInHeadSpace",
                    PoseStack.class, MultiBufferSource.class, faceState, faceProfile, int.class);

            expressionField = faceState.getField("expression");
            browField = faceState.getField("brow");
            mouthOpenField = faceState.getField("mouthOpen");
            mouthSmileField = faceState.getField("mouthSmile");
            gazeXField = faceState.getField("gazeX");
            gazeYField = faceState.getField("gazeY");
            intensityField = faceState.getField("intensity");
            blinkLeftField = faceState.getField("blinkLeft");
            blinkRightField = faceState.getField("blinkRight");

            available = true;
            PokeEFNPC.LOGGER.info("PokeEFNPC: PokeFace detected - NPC faces enabled.");
        } catch (Throwable t) {
            available = false;
            PokeEFNPC.LOGGER.warn("PokeEFNPC: PokeFace is present but its internals did not "
                    + "resolve ({}). NPCs keep plain faces.", t.toString());
        }
    }

    /**
     * Draws this NPC's current face. The pose stack must already be on the head
     * bone — see {@code NpcFaceLayer}, which is the only caller.
     *
     * @param blinkPhase the entity's tick count, so a crowd does not blink in unison
     */
    public static void renderFace(PoseStack poseStack, MultiBufferSource buffers,
                                  NpcEntity npc, int light, int blinkPhase) {
        resolve();
        if (!available) {
            return;
        }
        try {
            Object state = buildState(npc, blinkPhase);
            Object profile = profileFor(npc.role());
            if (state == null || profile == null) {
                return;
            }
            renderInHeadSpace.invoke(null, poseStack, buffers, state, profile, light);
        } catch (Throwable t) {
            // Never let a face take the whole renderer down with it.
            available = false;
            PokeEFNPC.LOGGER.warn("PokeEFNPC: PokeFace rendering failed ({}); "
                    + "NPC faces disabled for this session.", t.toString());
        }
    }

    /**
     * Translates the synced mood into a PokeFace {@code FaceState}.
     *
     * <p>The four channels the NPC syncs are used directly; the rest — blinking,
     * gaze — are generated here because they are pure presentation and there is
     * no reason to spend bandwidth on them.
     */
    private static Object buildState(NpcEntity npc, int blinkPhase) throws Exception {
        Object state = unpack.invoke(null, 0L, expressionFor(npc));

        float intensity = npc.faceIntensity();
        browField.setFloat(state, npc.faceBrow());
        mouthOpenField.setFloat(state, npc.faceMouthOpen());
        mouthSmileField.setFloat(state, npc.faceMouthSmile());
        intensityField.setFloat(state, intensity);
        expressionField.set(state, expressionFor(npc));

        // Blink: mostly open, with a brief close on a per-entity cycle. Tired
        // characters blink slower and hold it longer, which reads as heavy-lidded.
        float blink = blinkAt(blinkPhase, npc.getId(),
                npc.displayedEmotion() == com.pokewing.pokeefnpc.npc.Emotion.TIRED);
        blinkLeftField.setFloat(state, blink);
        blinkRightField.setFloat(state, blink);

        // Eyes follow whatever the head is turned toward, which is what makes an
        // NPC that is looking at you actually look at you.
        gazeXField.setFloat(state, 0.0F);
        gazeYField.setFloat(state, npc.getXRot() / -90.0F);
        return state;
    }

    private static float blinkAt(int tick, int seed, boolean tired) {
        int period = tired ? 50 : 90;
        int phase = Math.floorMod(tick + seed * 7, period);
        int closedFor = tired ? 6 : 3;
        if (phase >= closedFor) {
            return 0.0F;
        }
        // A triangle rather than a step, so the lid moves instead of snapping.
        float half = closedFor / 2.0F;
        return 1.0F - Math.abs(phase - half) / half;
    }

    private static Object expressionFor(NpcEntity npc) {
        int ordinal = npc.displayedEmotion().expressionOrdinal();
        return expressionValues[Math.floorMod(ordinal, expressionValues.length)];
    }

    /**
     * A face profile per role, so the population is not all one person: a soldier
     * has a harder brow than a baker, a mage's eyes glow, a beggar's are dull.
     */
    private static Object profileFor(NpcRole role) {
        Object cached = ROLE_PROFILES.get(role);
        if (cached != null) {
            return cached;
        }
        try {
            Object profile = profileConstructor.newInstance();
            Class<?> type = profile.getClass();

            setFloat(type, profile, "browThickness", switch (role.category()) {
                case MARTIAL, CRIMINAL -> 0.75F;
                case NOBLE, ARCANE -> 0.45F;
                default -> 0.55F;
            });
            // How steeply the brows can tilt caps how angry a face can look, so
            // a hot-tempered role is given more room to scowl.
            setFloat(type, profile, "browTilt", 1.1F + role.temper() * 0.8F);

            int eye = switch (role.category()) {
                case MARTIAL -> 0xFF5A6B7A;
                case ARCANE -> 0xFF7E5AA8;
                case CRIMINAL -> 0xFF6B5A3A;
                case NOBLE -> 0xFF3A6BA5;
                case TRADE -> 0xFF4A7A5A;
                case GUILD -> 0xFF8A5A3A;
                case COMMONER -> 0xFF5A4A3A;
            };
            setInt(type, profile, "eyeColor", eye);
            setInt(type, profile, "eyeColorRight", eye);

            if (role == NpcRole.MAGE || role == NpcRole.SEER) {
                setBoolean(type, profile, "eyeGlow", true);
            }
            ROLE_PROFILES.put(role, profile);
            return profile;
        } catch (Throwable t) {
            PokeEFNPC.LOGGER.warn("PokeEFNPC: could not build a PokeFace profile for {} ({})",
                    role.key(), t.toString());
            return null;
        }
    }

    private static void setFloat(Class<?> type, Object target, String field, float value) {
        try {
            type.getField(field).setFloat(target, value);
        } catch (Throwable ignored) {
            // A field this PokeFace build does not have: the default stands.
        }
    }

    private static void setInt(Class<?> type, Object target, String field, int value) {
        try {
            type.getField(field).setInt(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static void setBoolean(Class<?> type, Object target, String field, boolean value) {
        try {
            type.getField(field).setBoolean(target, value);
        } catch (Throwable ignored) {
        }
    }
}
