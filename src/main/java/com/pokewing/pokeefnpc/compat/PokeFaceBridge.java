package com.pokewing.pokeefnpc.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.PokeEFNPCClientConfig;
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

    /**
     * One profile per role, built once — every guard has the same eyes. Cleared
     * by {@link #invalidateProfiles()} whenever the base character changes, so
     * editing the config is a look-and-adjust loop rather than a restart.
     */
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

    /** Throws away the built profiles, so the next frame rebuilds from config. */
    public static synchronized void invalidateProfiles() {
        ROLE_PROFILES.clear();
    }

    /**
     * The face every NPC is drawn from.
     *
     * <p>There is one base character, configured in {@code pokeefnpc-client.toml},
     * and it is applied to everybody. Roles then vary it — a soldier has a harder
     * brow than a baker, a mage's eyes glow — but only as a nudge on top of the
     * base, and only while {@code roleVariation} is on. Turning that off gives
     * one identical face across the whole population, which is what you want
     * while tuning the base: change a value, look at any NPC, see it.
     */
    private static Object profileFor(NpcRole role) {
        Object cached = ROLE_PROFILES.get(role);
        if (cached != null) {
            return cached;
        }
        try {
            Object profile = profileConstructor.newInstance();
            Class<?> type = profile.getClass();

            applyBase(type, profile);
            if (PokeEFNPCClientConfig.roleVariation()) {
                applyRoleVariation(type, profile, role);
            }
            ROLE_PROFILES.put(role, profile);
            return profile;
        } catch (Throwable t) {
            PokeEFNPC.LOGGER.warn("PokeEFNPC: could not build a PokeFace profile for {} ({})",
                    role.key(), t.toString());
            return null;
        }
    }

    /** The configured base character, written onto a fresh profile. */
    private static void applyBase(Class<?> type, Object profile) {
        String style = PokeEFNPCClientConfig.style();
        if (!style.isBlank()) {
            setString(type, profile, "style", style);
        }

        setFloat(type, profile, "eyeSpacing", PokeEFNPCClientConfig.eyeSpacing());
        setFloat(type, profile, "eyeScale", PokeEFNPCClientConfig.eyeScale());
        setFloat(type, profile, "eyeOffsetX", PokeEFNPCClientConfig.eyeOffsetX());
        setFloat(type, profile, "eyeOffsetY", PokeEFNPCClientConfig.eyeOffsetY());
        setFloat(type, profile, "eyeConverge", PokeEFNPCClientConfig.eyeConverge());
        setFloat(type, profile, "irisScale", PokeEFNPCClientConfig.irisScale());
        setFloat(type, profile, "pupilScale", PokeEFNPCClientConfig.pupilScale());
        setBoolean(type, profile, "drawSclera", PokeEFNPCClientConfig.drawSclera());

        setInt(type, profile, "eyeColor", PokeEFNPCClientConfig.eyeColor());
        setInt(type, profile, "eyeColorRight", PokeEFNPCClientConfig.eyeColorRight());
        setInt(type, profile, "scleraColor", PokeEFNPCClientConfig.scleraColor());
        setInt(type, profile, "scleraColorRight", PokeEFNPCClientConfig.scleraColor());
        setInt(type, profile, "pupilColor", PokeEFNPCClientConfig.pupilColor());
        setInt(type, profile, "pupilColorRight", PokeEFNPCClientConfig.pupilColor());
        setInt(type, profile, "lineColor", PokeEFNPCClientConfig.lineColor());

        setBoolean(type, profile, "eyeGlow", PokeEFNPCClientConfig.eyeGlow());
        setFloat(type, profile, "eyeGlowSpread", PokeEFNPCClientConfig.eyeGlowSpread());

        setBoolean(type, profile, "drawBrows", PokeEFNPCClientConfig.drawBrows());
        setFloat(type, profile, "browThickness", PokeEFNPCClientConfig.browThickness());
        setFloat(type, profile, "browLength", PokeEFNPCClientConfig.browLength());
        setFloat(type, profile, "browOffsetY", PokeEFNPCClientConfig.browOffsetY());
        setFloat(type, profile, "browTilt", PokeEFNPCClientConfig.browTilt());

        setFloat(type, profile, "mouthScale", PokeEFNPCClientConfig.mouthScale());
        setFloat(type, profile, "mouthOffsetY", PokeEFNPCClientConfig.mouthOffsetY());
    }

    /**
     * Role flavour, applied relative to the base rather than replacing it, so a
     * soldier stays recognisably the same character as the baker — just sterner.
     */
    private static void applyRoleVariation(Class<?> type, Object profile, NpcRole role) {
        float thickness = PokeEFNPCClientConfig.browThickness() * switch (role.category()) {
            case MARTIAL, CRIMINAL -> 1.35F;
            case NOBLE, ARCANE -> 0.8F;
            default -> 1.0F;
        };
        setFloat(type, profile, "browThickness", thickness);

        // How steeply the brows can tilt caps how angry a face can look, so a
        // hot-tempered role is given more room to scowl.
        setFloat(type, profile, "browTilt",
                PokeEFNPCClientConfig.browTilt() * (0.8F + role.temper() * 0.55F));

        // The base iris is pulled toward the role's own colour rather than
        // replaced by it, so recolouring the base still moves every NPC.
        int tint = switch (role.category()) {
            case MARTIAL -> 0xFF5A6B7A;
            case ARCANE -> 0xFF7E5AA8;
            case CRIMINAL -> 0xFF6B5A3A;
            case NOBLE -> 0xFF3A6BA5;
            case TRADE -> 0xFF4A7A5A;
            case GUILD -> 0xFF8A5A3A;
            case COMMONER -> 0xFF5A4A3A;
        };
        setInt(type, profile, "eyeColor",
                blend(PokeEFNPCClientConfig.eyeColor(), tint, 0.5F));
        setInt(type, profile, "eyeColorRight",
                blend(PokeEFNPCClientConfig.eyeColorRight(), tint, 0.5F));

        if (role == NpcRole.MAGE || role == NpcRole.SEER) {
            setBoolean(type, profile, "eyeGlow", true);
        }
    }

    /** Mixes two ARGB colours per channel, keeping the first one's alpha. */
    private static int blend(int base, int tint, float amount) {
        int alpha = (base >>> 24) & 0xFF;
        int red = channel(base, 16, tint, amount);
        int green = channel(base, 8, tint, amount);
        int blue = channel(base, 0, tint, amount);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int channel(int base, int shift, int tint, float amount) {
        int from = (base >>> shift) & 0xFF;
        int to = (tint >>> shift) & 0xFF;
        return Math.round(from + (to - from) * amount);
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

    private static void setString(Class<?> type, Object target, String field, String value) {
        try {
            type.getField(field).set(target, value);
        } catch (Throwable ignored) {
        }
    }
}
