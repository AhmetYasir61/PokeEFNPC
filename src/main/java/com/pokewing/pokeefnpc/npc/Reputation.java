package com.pokewing.pokeefnpc.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What one NPC thinks of each player it has dealt with.
 *
 * <p>Standing is a single number in [-100,100], and everything social reads it:
 * the greeting emote, the price on the shelf, whether the guild will show a
 * player its better contracts, and whether a guard draws steel when they walk
 * past. Keeping it to one number is what lets a role add a new social behaviour
 * without inventing its own bookkeeping.
 *
 * <p>Standing also <b>decays toward neutral</b>. A player who robbed a village a
 * hundred in-game days ago and has stayed away is met with suspicion, not
 * hatred, and a hero who saved it is eventually just a face people know. Grudges
 * fade slower than gratitude, scaled by the NPC's loyalty.
 */
public final class Reputation {

    public static final int MAX = 100;
    public static final int MIN = -100;

    /** Bands the interface and the AI actually branch on. */
    public enum Standing {
        HATED, HOSTILE, WARY, NEUTRAL, FRIENDLY, TRUSTED, REVERED;

        public String translationKey() {
            return "pokeefnpc.standing." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final Map<UUID, Float> standings = new HashMap<>();

    public float of(UUID player) {
        return player == null ? 0.0F : this.standings.getOrDefault(player, 0.0F);
    }

    public Standing standingOf(UUID player) {
        return bandOf(of(player));
    }

    public static Standing bandOf(float value) {
        if (value <= -70.0F) return Standing.HATED;
        if (value <= -35.0F) return Standing.HOSTILE;
        if (value <= -10.0F) return Standing.WARY;
        if (value < 15.0F) return Standing.NEUTRAL;
        if (value < 45.0F) return Standing.FRIENDLY;
        if (value < 80.0F) return Standing.TRUSTED;
        return Standing.REVERED;
    }

    public void adjust(UUID player, float delta) {
        if (player == null) {
            return;
        }
        this.standings.merge(player, delta, (a, b) -> Mth.clamp(a + b, MIN, MAX));
    }

    public void set(UUID player, float value) {
        if (player != null) {
            this.standings.put(player, Mth.clamp(value, MIN, MAX));
        }
    }

    /**
     * Nudges every standing back toward zero. Called on a long interval, not per
     * tick. Grudges are stickier than goodwill, and a loyal character holds both
     * longer than a fickle one.
     */
    public void decay(Personality personality) {
        float rate = 0.25F * (1.0F - personality.loyalty * 0.7F);
        this.standings.replaceAll((player, value) -> {
            float step = value < 0.0F ? rate * 0.5F : rate;
            if (Math.abs(value) <= step) {
                return 0.0F;
            }
            return value - Math.signum(value) * step;
        });
        this.standings.values().removeIf(value -> value == 0.0F);
    }

    /**
     * Price multiplier this player is charged. A trusted regular is given the
     * better end of the deal; someone the shopkeeper despises pays through the
     * nose, if they are served at all.
     */
    public float priceMultiplier(UUID player) {
        return switch (standingOf(player)) {
            case HATED -> 2.5F;
            case HOSTILE -> 1.8F;
            case WARY -> 1.25F;
            case NEUTRAL -> 1.0F;
            case FRIENDLY -> 0.92F;
            case TRUSTED -> 0.82F;
            case REVERED -> 0.7F;
        };
    }

    /** True when this NPC will open its menu for the player at all. */
    public boolean willDealWith(UUID player) {
        return standingOf(player) != Standing.HATED;
    }

    /** True when the off-book stock is shown. Fences do not trust strangers. */
    public boolean trustsWithContraband(UUID player) {
        Standing standing = standingOf(player);
        return standing == Standing.TRUSTED || standing == Standing.REVERED
                || standing == Standing.FRIENDLY;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        this.standings.forEach((player, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putFloat("Value", value);
            list.add(entry);
        });
        tag.put("Standings", list);
        return tag;
    }

    public static Reputation load(CompoundTag tag) {
        Reputation reputation = new Reputation();
        ListTag list = tag.getList("Standings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            reputation.standings.put(entry.getUUID("Player"), entry.getFloat("Value"));
        }
        return reputation;
    }
}
