package com.pokewing.pokeefnpc.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * The running emotional state of one NPC — the thing that actually shows on its
 * face.
 *
 * <p>Two continuous channels do the work. {@link #valence} is pleasant to
 * unpleasant, {@link #arousal} is calm to worked-up. Events nudge them; every
 * tick they slide back toward the character's {@link Personality#baseline}. The
 * discrete {@link Emotion} is then <i>derived</i> from where the two channels
 * are, which is why an NPC that has been shoved twice looks crosser than one
 * shoved once, without anyone writing that rule down.
 *
 * <p>A {@link #latched} emotion overrides the derivation for a while. That is
 * for the moments that must read exactly — a spear in the ribs is HURT, not
 * "somewhat negative, fairly aroused" — and it expires on its own.
 */
public final class Mood {

    /** -1 miserable, +1 delighted. */
    private float valence;
    /** 0 placid, 1 agitated. */
    private float arousal;

    private Emotion latched = Emotion.NEUTRAL;
    private int latchTicks;

    /** Set while the NPC has a line of dialogue on screen, to flap the mouth. */
    private int speakingTicks;

    /** Last emotion actually shown, so the entity only re-syncs on a change. */
    private Emotion shown = Emotion.NEUTRAL;

    public float valence() {
        return this.valence;
    }

    public float arousal() {
        return this.arousal;
    }

    public boolean isSpeaking() {
        return this.speakingTicks > 0;
    }

    public void speakFor(int ticks) {
        this.speakingTicks = Math.max(this.speakingTicks, ticks);
    }

    /**
     * Records something that happened to this NPC.
     *
     * @param valenceDelta how much better or worse this made things, in [-1,1]
     * @param arousalDelta how much it stirred the NPC up, in [-1,1]
     */
    public void feel(Personality personality, float valenceDelta, float arousalDelta) {
        float rate = personality.reactivity() * 6.0F;
        this.valence = Mth.clamp(this.valence + valenceDelta * rate, -1.0F, 1.0F);
        this.arousal = Mth.clamp(this.arousal + Math.abs(arousalDelta) * rate, 0.0F, 1.0F);
    }

    /**
     * Forces a specific face for {@code Emotion#holdTicks}, and moves the
     * underlying channels with it so the NPC does not snap back to cheerful the
     * instant the latch expires.
     */
    public void latch(Personality personality, Emotion emotion) {
        latch(personality, emotion, emotion.holdTicks());
    }

    public void latch(Personality personality, Emotion emotion, int ticks) {
        // A weaker feeling never cuts a stronger one short: being mildly pleased
        // mid-fight should not wipe the anger off a guard's face.
        if (this.latchTicks > 0 && Math.abs(this.latched.valence()) > Math.abs(emotion.valence())) {
            return;
        }
        this.latched = emotion;
        this.latchTicks = Math.max(1, ticks);
        feel(personality, emotion.valence() * 0.5F, 0.4F);
    }

    /** Advances one tick. Returns true when the visible emotion changed. */
    public boolean tick(Personality personality) {
        if (this.latchTicks > 0) {
            this.latchTicks--;
        }
        if (this.speakingTicks > 0) {
            this.speakingTicks--;
        }

        float recovery = personality.recovery();
        this.valence = approach(this.valence, personality.baseline, recovery);
        this.arousal = approach(this.arousal, 0.0F, recovery * 1.5F);

        Emotion now = current();
        if (now != this.shown) {
            this.shown = now;
            return true;
        }
        return false;
    }

    private static float approach(float from, float to, float rate) {
        float delta = to - from;
        if (Math.abs(delta) <= rate) {
            return to;
        }
        return from + Math.signum(delta) * rate;
    }

    /** The face to draw right now. */
    public Emotion current() {
        if (this.latchTicks > 0) {
            return this.latched;
        }
        return derive();
    }

    /**
     * Reads a face out of the two channels.
     *
     * <p>The asymmetry between the two unhappy quadrants is the whole point of
     * having arousal at all: unhappy <i>and</i> stirred up is anger, unhappy and
     * flat is sadness. That is what makes a robbed shopkeeper glare and a
     * bereaved one droop, from one piece of arithmetic.
     */
    private Emotion derive() {
        boolean stirred = this.arousal >= 0.45F;
        if (this.valence <= -0.55F) {
            return stirred ? Emotion.ANGRY : Emotion.SAD;
        }
        if (this.valence <= -0.2F) {
            return stirred ? Emotion.FOCUSED : Emotion.TIRED;
        }
        if (this.valence >= 0.4F) {
            return Emotion.HAPPY;
        }
        return stirred ? Emotion.FOCUSED : Emotion.NEUTRAL;
    }

    /**
     * How hard to play whatever face is showing, in [0,1]. Strong feeling in
     * either direction reads at full strength; a flat mood barely registers.
     */
    public float intensity() {
        float fromValence = Math.abs(this.valence);
        return Mth.clamp(Math.max(fromValence, this.arousal * 0.8F) + 0.15F, 0.0F, 1.0F);
    }

    /**
     * The brow channel, in [-1,1]. Negative is drawn down and inward (angry),
     * positive is raised at the inner ends (sad, surprised).
     *
     * <p>Derived from the emotion's canonical brow scaled by how strongly the
     * NPC feels it, so a mildly irritated shopkeeper has a slight furrow and a
     * furious one has a full scowl, off the same enum value.
     */
    public float brow() {
        return Mth.clamp(current().brow() * intensity(), -1.0F, 1.0F);
    }

    public float mouthSmile() {
        return Mth.clamp(current().mouthSmile() * intensity(), -1.0F, 1.0F);
    }

    /**
     * Mouth opening. Mostly shut unless the NPC is mid-sentence, in which case
     * it flaps — {@code phase} is the entity's tick count, so each NPC in a
     * crowd is out of step with the others.
     */
    public float mouthOpen(int phase) {
        float base = current().mouthOpen() * intensity() * 0.4F;
        if (this.speakingTicks > 0) {
            float flap = (Mth.sin(phase * 0.55F) + 1.0F) * 0.5F;
            return Mth.clamp(base + flap * 0.65F, 0.0F, 1.0F);
        }
        return base;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Valence", this.valence);
        tag.putFloat("Arousal", this.arousal);
        tag.putInt("Latched", this.latched.ordinal());
        tag.putInt("LatchTicks", this.latchTicks);
        return tag;
    }

    public static Mood load(CompoundTag tag) {
        Mood mood = new Mood();
        mood.valence = tag.getFloat("Valence");
        mood.arousal = tag.getFloat("Arousal");
        mood.latched = Emotion.byOrdinal(tag.getInt("Latched"));
        mood.latchTicks = tag.getInt("LatchTicks");
        mood.shown = mood.current();
        return mood;
    }
}
