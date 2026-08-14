package com.pokewing.pokeefnpc.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The fixed part of a character: the traits it is born with and keeps for life.
 *
 * <p>{@link Mood} is the weather; this is the climate. Two guards standing the
 * same watch behave differently because one is braver and one is greedier, and
 * because the {@link #baseline} they each settle back to is not the same.
 *
 * <p>Every trait is in [0,1] except {@link #baseline}, which is a valence in
 * [-1,1]. A role supplies the centre of each trait and the individual is rolled
 * around it, so a Guard is reliably braver than a Beggar without every guard
 * being the same guard.
 */
public final class Personality {

    /** Willingness to close with a threat rather than run from it. */
    public float courage = 0.5F;
    /** How readily it starts conversations, waves, and gathers in the square. */
    public float sociability = 0.5F;
    /** How hard it bargains, and how much a bribe moves it. */
    public float greed = 0.5F;
    /** How fast mood swings — a high value spikes and settles quickly. */
    public float temper = 0.5F;
    /** How much work it gets through before it wants a break. */
    public float diligence = 0.5F;
    /** How long a grudge or a kindness is remembered. */
    public float loyalty = 0.5F;
    /** The valence this character drifts back to when nothing is happening. */
    public float baseline;

    public Personality() {
    }

    /**
     * Rolls an individual around a role's centre. {@code spread} is how far a
     * given character may wander from its role's typical value.
     */
    public static Personality roll(RandomSource random, NpcRole role, float spread) {
        Personality p = new Personality();
        p.courage = jitter(random, role.courage(), spread);
        p.sociability = jitter(random, role.sociability(), spread);
        p.greed = jitter(random, role.greed(), spread);
        p.temper = jitter(random, role.temper(), spread);
        p.diligence = jitter(random, role.diligence(), spread);
        p.loyalty = jitter(random, 0.5F, spread);
        p.baseline = Mth.clamp(role.baseline() + (random.nextFloat() - 0.5F) * spread, -1.0F, 1.0F);
        return p;
    }

    private static float jitter(RandomSource random, float centre, float spread) {
        return Mth.clamp(centre + (random.nextFloat() - 0.5F) * spread, 0.0F, 1.0F);
    }

    /**
     * How quickly mood moves toward whatever just happened. A hot-tempered
     * character reacts almost at once; a phlegmatic one takes its time.
     */
    public float reactivity() {
        return 0.04F + this.temper * 0.16F;
    }

    /** How quickly mood slides back to {@link #baseline} once things are calm. */
    public float recovery() {
        return 0.004F + (1.0F - this.loyalty) * 0.012F;
    }

    /**
     * True when this character would rather fight than flee, given how bad the
     * situation is. {@code threat} is 0..1.
     */
    public boolean standsGround(float threat) {
        return this.courage >= threat;
    }

    /**
     * Price multiplier this character asks for, before reputation is applied.
     * A greedy merchant marks up; a generous one barely does.
     */
    public float markup() {
        return 0.85F + this.greed * 0.45F;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Courage", this.courage);
        tag.putFloat("Sociability", this.sociability);
        tag.putFloat("Greed", this.greed);
        tag.putFloat("Temper", this.temper);
        tag.putFloat("Diligence", this.diligence);
        tag.putFloat("Loyalty", this.loyalty);
        tag.putFloat("Baseline", this.baseline);
        return tag;
    }

    public static Personality load(CompoundTag tag) {
        Personality p = new Personality();
        p.courage = tag.getFloat("Courage");
        p.sociability = tag.getFloat("Sociability");
        p.greed = tag.getFloat("Greed");
        p.temper = tag.getFloat("Temper");
        p.diligence = tag.getFloat("Diligence");
        p.loyalty = tag.getFloat("Loyalty");
        p.baseline = tag.getFloat("Baseline");
        return p;
    }
}
