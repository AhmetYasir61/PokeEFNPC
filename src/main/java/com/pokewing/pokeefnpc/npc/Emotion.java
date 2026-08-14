package com.pokewing.pokeefnpc.npc;

/**
 * What an NPC is feeling, as one of the eight faces PokeFace can draw.
 *
 * <p>The ordinals here deliberately match {@code com.pokewing.pokeface.face.Expression}
 * so the value can be handed across the reflection bridge as a bare int without
 * either mod knowing the other's classes. {@link #expressionOrdinal()} is the
 * single place that mapping lives — if PokeFace ever reorders its atlas, fix it
 * there and nothing else changes.
 *
 * <p>The brow is the load-bearing channel for reading an NPC across a courtyard.
 * A drawn-together, downward brow ({@code brow < 0}) is anger; a raised, inner-up
 * brow ({@code brow > 0}) over a downturned mouth is sadness. That is the whole
 * contract the renderer needs, and every emotion below picks a brow value with it
 * in mind.
 */
public enum Emotion {

    /** Baseline. Nothing is happening; the idle animator owns the face. */
    NEUTRAL("neutral", 0, 0.0F, 0.0F, 0.0F),

    /** Pleased — a good trade, a gift, a finished job. */
    HAPPY("happy", 1, 0.35F, 0.85F, 0.9F),

    /**
     * Angry — robbed, struck, insulted, or looking at something it intends to
     * kill. Brows hard down and inward.
     */
    ANGRY("angry", 2, -0.95F, -0.6F, 0.35F),

    /** Hurt — took damage. Brows down but the mouth is open, not set. */
    HURT("hurt", 3, -0.7F, -0.35F, 0.8F),

    /** Surprised — a monster appeared, a stranger walked in, a shout nearby. */
    SURPRISED("surprised", 4, 0.95F, 0.0F, 0.85F),

    /** Focused — working, aiming, tracking a target. */
    FOCUSED("focused", 5, -0.4F, 0.0F, 0.1F),

    /** Tired — end of a long shift, or hungry and worn down. */
    TIRED("tired", 6, 0.05F, -0.2F, 0.15F),

    /**
     * Sad — a neighbour died, the harvest failed, the shop is empty. Brows go
     * the opposite way from anger: inner ends UP, mouth corners down.
     */
    SAD("sad", 7, 0.55F, -0.8F, 0.25F);

    private final String key;
    private final int expressionOrdinal;
    private final float brow;
    private final float mouthSmile;
    private final float mouthOpen;

    Emotion(String key, int expressionOrdinal, float brow, float mouthSmile, float mouthOpen) {
        this.key = key;
        this.expressionOrdinal = expressionOrdinal;
        this.brow = brow;
        this.mouthSmile = mouthSmile;
        this.mouthOpen = mouthOpen;
    }

    public String key() {
        return this.key;
    }

    /** The matching {@code Expression} ordinal on the PokeFace side. */
    public int expressionOrdinal() {
        return this.expressionOrdinal;
    }

    /** -1 fully drawn down and in (angry), +1 fully raised (sad/surprised). */
    public float brow() {
        return this.brow;
    }

    /** -1 corners fully down, +1 full smile. */
    public float mouthSmile() {
        return this.mouthSmile;
    }

    /** 0 shut, 1 wide. Scaled down again while the NPC is not speaking. */
    public float mouthOpen() {
        return this.mouthOpen;
    }

    public String translationKey() {
        return "pokeefnpc.emotion." + this.key;
    }

    /**
     * How much a face wants to hold this emotion rather than drift back to
     * neutral. Pain and fright are loud but brief; grief and contentment linger.
     */
    public int holdTicks() {
        return switch (this) {
            case SURPRISED -> 40;
            case HURT -> 60;
            case ANGRY -> 200;
            case HAPPY -> 160;
            case SAD -> 400;
            case FOCUSED -> 100;
            case TIRED -> 300;
            case NEUTRAL -> 0;
        };
    }

    /**
     * Where this emotion sits on the pleasant/unpleasant axis, so {@link Mood}
     * can pick a face from a continuous value instead of the other way round.
     */
    public float valence() {
        return switch (this) {
            case HAPPY -> 0.85F;
            case NEUTRAL -> 0.0F;
            case FOCUSED -> 0.1F;
            case TIRED -> -0.2F;
            case SURPRISED -> -0.1F;
            case SAD -> -0.7F;
            case HURT -> -0.8F;
            case ANGRY -> -0.9F;
        };
    }

    public static Emotion byOrdinal(int ordinal) {
        Emotion[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }

    public static Emotion byName(String name) {
        for (Emotion e : values()) {
            if (e.key.equalsIgnoreCase(name)) {
                return e;
            }
        }
        return NEUTRAL;
    }
}
