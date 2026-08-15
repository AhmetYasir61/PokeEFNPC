package com.pokewing.pokeefnpc;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * How NPC faces look. Client-side, because none of it changes what happens in
 * the world — two players can disagree about eye colour and still be playing the
 * same game.
 *
 * <h2>One base character</h2>
 *
 * <p>Every NPC is drawn from a single set of eyes defined here, so the whole
 * population is one recognisable look you tune in one place rather than forty
 * separate faces. {@link #roleVariation()} then decides whether roles are allowed
 * to nudge that base — a soldier's heavier brow, a mage's glow — or whether the
 * base is used verbatim for everybody.
 *
 * <p>Editing the file re-reads it live: Forge fires a reload event, the cached
 * profile is thrown away, and the next frame is drawn from the new values. No
 * restart, which matters because tuning eyes is a look-and-adjust job.
 */
public final class PokeEFNPCClientConfig {

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue ROLE_VARIATION;
    private static final ForgeConfigSpec.ConfigValue<String> STYLE;

    private static final ForgeConfigSpec.DoubleValue EYE_SPACING;
    private static final ForgeConfigSpec.DoubleValue EYE_SCALE;
    private static final ForgeConfigSpec.DoubleValue EYE_OFFSET_X;
    private static final ForgeConfigSpec.DoubleValue EYE_OFFSET_Y;
    private static final ForgeConfigSpec.DoubleValue EYE_CONVERGE;
    private static final ForgeConfigSpec.DoubleValue IRIS_SCALE;
    private static final ForgeConfigSpec.DoubleValue PUPIL_SCALE;
    private static final ForgeConfigSpec.BooleanValue DRAW_SCLERA;

    private static final ForgeConfigSpec.ConfigValue<String> EYE_COLOR;
    private static final ForgeConfigSpec.ConfigValue<String> EYE_COLOR_RIGHT;
    private static final ForgeConfigSpec.ConfigValue<String> SCLERA_COLOR;
    private static final ForgeConfigSpec.ConfigValue<String> PUPIL_COLOR;
    private static final ForgeConfigSpec.ConfigValue<String> LINE_COLOR;

    private static final ForgeConfigSpec.BooleanValue EYE_GLOW;
    private static final ForgeConfigSpec.DoubleValue EYE_GLOW_SPREAD;

    private static final ForgeConfigSpec.BooleanValue DRAW_BROWS;
    private static final ForgeConfigSpec.DoubleValue BROW_THICKNESS;
    private static final ForgeConfigSpec.DoubleValue BROW_LENGTH;
    private static final ForgeConfigSpec.DoubleValue BROW_OFFSET_Y;
    private static final ForgeConfigSpec.DoubleValue BROW_TILT;

    private static final ForgeConfigSpec.DoubleValue MOUTH_SCALE;
    private static final ForgeConfigSpec.DoubleValue MOUTH_OFFSET_Y;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("NPC eyes: one base character every NPC is drawn from.",
                        "Colours are ARGB hex - 0xFF3A6BA5, #3A6BA5 and 3A6BA5 all work.",
                        "Saving this file applies the change immediately; no restart.")
                .push("eyes");

        ROLE_VARIATION = builder
                .comment("Let roles vary the base: a soldier gets a heavier brow, a mage",
                        "and a seer get glowing eyes, and each role tints the iris.",
                        "Off means every NPC in the world uses the settings below exactly,",
                        "which is what you want while tuning the base character.")
                .define("roleVariation", true);

        STYLE = builder
                .comment("PokeFace eye style key. Empty = PokeFace's own default.")
                .define("style", "");

        // ------------------------------------------------------------ shape

        EYE_SPACING = builder
                .comment("Distance between the eyes, in face pixels.")
                .defineInRange("eyeSpacing", 3.0D, 0.0D, 8.0D);

        EYE_SCALE = builder
                .comment("Overall eye size.")
                .defineInRange("eyeScale", 1.0D, 0.1D, 4.0D);

        EYE_OFFSET_X = builder
                .comment("Move both eyes sideways, in face pixels.")
                .defineInRange("eyeOffsetX", 0.0D, -8.0D, 8.0D);

        EYE_OFFSET_Y = builder
                .comment("Move both eyes up or down, in face pixels.",
                        "Negative is up.")
                .defineInRange("eyeOffsetY", 0.0D, -8.0D, 8.0D);

        EYE_CONVERGE = builder
                .comment("How much the two pupils turn inward toward what is being",
                        "looked at. 0 = both eyes always point the same way.")
                .defineInRange("eyeConverge", 0.35D, 0.0D, 1.0D);

        IRIS_SCALE = builder
                .comment("Iris size as a fraction of the eye.")
                .defineInRange("irisScale", 0.55D, 0.05D, 1.0D);

        PUPIL_SCALE = builder
                .comment("Pupil size as a fraction of the iris.")
                .defineInRange("pupilScale", 0.45D, 0.05D, 1.0D);

        DRAW_SCLERA = builder
                .comment("Draw the white of the eye. Off gives a solid, inhuman eye.")
                .define("drawSclera", true);

        // ----------------------------------------------------------- colour

        EYE_COLOR = builder
                .comment("Iris colour of the left eye.")
                .define("eyeColor", "0xFF3A6BA5");

        EYE_COLOR_RIGHT = builder
                .comment("Iris colour of the right eye. Set it differently from",
                        "eyeColor for heterochromia.")
                .define("eyeColorRight", "0xFF3A6BA5");

        SCLERA_COLOR = builder
                .comment("The white of the eye.")
                .define("scleraColor", "0xFFF4F1EA");

        PUPIL_COLOR = builder
                .comment("Pupil colour.")
                .define("pupilColor", "0xFF101010");

        LINE_COLOR = builder
                .comment("Outline colour, used for the lids and the brows.")
                .define("lineColor", "0xFF101010");

        EYE_GLOW = builder
                .comment("Make the eyes emit light of their own, so they read in the dark.")
                .define("eyeGlow", false);

        EYE_GLOW_SPREAD = builder
                .comment("How far the glow bleeds past the iris.")
                .defineInRange("eyeGlowSpread", 0.35D, 0.0D, 1.0D);

        // ------------------------------------------------------------ brows

        DRAW_BROWS = builder
                .comment("Draw brows at all.",
                        "WARNING: the brows are how an NPC shows what it feels - drawn",
                        "down for anger, raised for sadness. Turning them off does not",
                        "stop the mood system, it just hides it.")
                .define("drawBrows", true);

        BROW_THICKNESS = builder
                .comment("Brow weight.")
                .defineInRange("browThickness", 0.55D, 0.0D, 2.0D);

        BROW_LENGTH = builder
                .comment("Brow length, in face pixels.")
                .defineInRange("browLength", 2.6D, 0.5D, 8.0D);

        BROW_OFFSET_Y = builder
                .comment("Brow height above the eye. Negative sits lower, which reads",
                        "as a permanently sterner face.")
                .defineInRange("browOffsetY", 0.0D, -4.0D, 4.0D);

        BROW_TILT = builder
                .comment("How steeply the brows are allowed to tilt.",
                        "This caps how angry or how sad a face can look: raise it for a",
                        "broad, expressive population, lower it for a stoic one.")
                .defineInRange("browTilt", 1.4D, 0.0D, 4.0D);

        // ------------------------------------------------------------ mouth

        MOUTH_SCALE = builder
                .comment("Mouth size.")
                .defineInRange("mouthScale", 1.0D, 0.1D, 4.0D);

        MOUTH_OFFSET_Y = builder
                .comment("Mouth height, in face pixels. Negative is up.")
                .defineInRange("mouthOffsetY", 0.0D, -8.0D, 8.0D);

        builder.pop();
        SPEC = builder.build();
    }

    private PokeEFNPCClientConfig() {
    }

    public static boolean roleVariation() {
        return ROLE_VARIATION.get();
    }

    public static String style() {
        return STYLE.get();
    }

    public static float eyeSpacing() {
        return EYE_SPACING.get().floatValue();
    }

    public static float eyeScale() {
        return EYE_SCALE.get().floatValue();
    }

    public static float eyeOffsetX() {
        return EYE_OFFSET_X.get().floatValue();
    }

    public static float eyeOffsetY() {
        return EYE_OFFSET_Y.get().floatValue();
    }

    public static float eyeConverge() {
        return EYE_CONVERGE.get().floatValue();
    }

    public static float irisScale() {
        return IRIS_SCALE.get().floatValue();
    }

    public static float pupilScale() {
        return PUPIL_SCALE.get().floatValue();
    }

    public static boolean drawSclera() {
        return DRAW_SCLERA.get();
    }

    public static int eyeColor() {
        return color(EYE_COLOR.get(), 0xFF3A6BA5);
    }

    public static int eyeColorRight() {
        return color(EYE_COLOR_RIGHT.get(), 0xFF3A6BA5);
    }

    public static int scleraColor() {
        return color(SCLERA_COLOR.get(), 0xFFF4F1EA);
    }

    public static int pupilColor() {
        return color(PUPIL_COLOR.get(), 0xFF101010);
    }

    public static int lineColor() {
        return color(LINE_COLOR.get(), 0xFF101010);
    }

    public static boolean eyeGlow() {
        return EYE_GLOW.get();
    }

    public static float eyeGlowSpread() {
        return EYE_GLOW_SPREAD.get().floatValue();
    }

    public static boolean drawBrows() {
        return DRAW_BROWS.get();
    }

    public static float browThickness() {
        return BROW_THICKNESS.get().floatValue();
    }

    public static float browLength() {
        return BROW_LENGTH.get().floatValue();
    }

    public static float browOffsetY() {
        return BROW_OFFSET_Y.get().floatValue();
    }

    public static float browTilt() {
        return BROW_TILT.get().floatValue();
    }

    public static float mouthScale() {
        return MOUTH_SCALE.get().floatValue();
    }

    public static float mouthOffsetY() {
        return MOUTH_OFFSET_Y.get().floatValue();
    }

    /**
     * Reads a colour the way somebody would actually type one.
     *
     * <p>{@code 0xFF3A6BA5}, {@code #3A6BA5} and {@code 3A6BA5} all parse; a
     * six-digit value is taken as fully opaque, because somebody writing a colour
     * by hand means "this colour", not "this colour, invisible". Anything
     * unparseable falls back to the default rather than throwing, so one typo in
     * the file cannot stop faces from being drawn.
     */
    private static int color(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        String text = raw.trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        } else if (text.startsWith("0x") || text.startsWith("0X")) {
            text = text.substring(2);
        }
        if (text.isEmpty() || text.length() > 8) {
            return fallback;
        }
        try {
            long value = Long.parseLong(text, 16);
            return text.length() <= 6 ? (int) (value | 0xFF000000L) : (int) value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
