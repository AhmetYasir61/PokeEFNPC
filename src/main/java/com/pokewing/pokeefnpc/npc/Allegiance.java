package com.pokewing.pokeefnpc.npc;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Who an NPC answers to, and what it has been told to do.
 *
 * <p>Most of the population answers to nobody: they live their own routine and
 * an owner is never set. Once somebody takes an NPC into service — by promoting
 * a villager who already trusts them, or by placing a soldier themselves — that
 * NPC gets an owner and a {@link Stance}, and the stance is what decides whether
 * it walks at your heel, holds a spot, or goes back to its own life.
 *
 * <p>The owner is stored as a bare UUID rather than a player reference on
 * purpose: the owner logs out, the chunk unloads, the server restarts, and the
 * NPC must still remember whose soldier it is.
 */
public final class Allegiance {

    /** What an NPC in somebody's service is currently doing. */
    public enum Stance {
        /**
         * Back to its own routine. An owned NPC in this stance still knows who
         * you are — it simply is not on duty.
         */
        OFF_DUTY,
        /** Walks with its owner and fights what the owner fights. */
        FOLLOW,
        /** Stands where it was told and defends that spot. */
        HOLD,
        /** Walks a circuit around the spot it was posted at. */
        PATROL;

        public String translationKey() {
            return "pokeefnpc.stance." + name().toLowerCase(java.util.Locale.ROOT);
        }

        /** The next stance when the order is cycled by hand. */
        public Stance next() {
            Stance[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        /** Whether this stance means the NPC is on duty rather than living its own life. */
        public boolean onDuty() {
            return this != OFF_DUTY;
        }
    }

    @Nullable
    private UUID owner;
    private Stance stance = Stance.OFF_DUTY;
    /** Where a HOLD or PATROL stance is anchored. Null means "wherever I stood". */
    @Nullable
    private long[] post;

    @Nullable
    public UUID owner() {
        return this.owner;
    }

    public boolean isOwned() {
        return this.owner != null;
    }

    public boolean isOwnedBy(@Nullable UUID player) {
        return player != null && player.equals(this.owner);
    }

    /**
     * Takes this NPC into somebody's service. A newly sworn NPC follows, because
     * the moment you recruit somebody is the moment you want them with you.
     */
    public void swearTo(UUID player) {
        this.owner = player;
        this.stance = Stance.FOLLOW;
        this.post = null;
    }

    /** Releases the NPC back to its own life entirely. */
    public void release() {
        this.owner = null;
        this.stance = Stance.OFF_DUTY;
        this.post = null;
    }

    public Stance stance() {
        return this.stance;
    }

    public void setStance(Stance stance) {
        this.stance = stance;
        if (stance != Stance.HOLD && stance != Stance.PATROL) {
            this.post = null;
        }
    }

    public void setPost(net.minecraft.core.BlockPos pos) {
        this.post = new long[] { pos.asLong() };
    }

    @Nullable
    public net.minecraft.core.BlockPos post() {
        return this.post == null ? null : net.minecraft.core.BlockPos.of(this.post[0]);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (this.owner != null) {
            tag.putUUID("Owner", this.owner);
        }
        tag.putString("Stance", this.stance.name());
        if (this.post != null) {
            tag.putLong("Post", this.post[0]);
        }
        return tag;
    }

    public static Allegiance load(CompoundTag tag) {
        Allegiance allegiance = new Allegiance();
        if (tag.hasUUID("Owner")) {
            allegiance.owner = tag.getUUID("Owner");
        }
        try {
            allegiance.stance = Stance.valueOf(tag.getString("Stance"));
        } catch (IllegalArgumentException e) {
            // An older save, or a stance that no longer exists. Off duty is the
            // safe reading: it puts the NPC back on its own routine rather than
            // leaving it standing somewhere waiting for an order that never comes.
            allegiance.stance = Stance.OFF_DUTY;
        }
        if (tag.contains("Post")) {
            allegiance.post = new long[] { tag.getLong("Post") };
        }
        return allegiance;
    }
}
