package com.pokewing.pokeefnpc.settlement;

import com.pokewing.pokeefnpc.PokeEFNPC;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every settlement in one dimension, persisted with the world.
 *
 * <p>Kept as {@link SavedData} rather than being rebuilt from the entities in
 * the world, because a settlement has to outlive having nobody in it. A village
 * that is wiped out by a raid still owns its beds, its walls and its name when
 * the next generation moves in, and a chunk that unloads for a week comes back
 * to the same claims it had.
 */
public final class SettlementManager extends SavedData {

    private static final String DATA_NAME = PokeEFNPC.MOD_ID + "_settlements";

    /** How close two centres can be before they are treated as one place. */
    private static final int MERGE_DISTANCE = 48;

    private final Map<UUID, Settlement> settlements = new HashMap<>();

    public static SettlementManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                SettlementManager::load, SettlementManager::new, DATA_NAME);
    }

    public SettlementManager() {
    }

    public Collection<Settlement> all() {
        return this.settlements.values();
    }

    public Settlement byId(UUID id) {
        return id == null ? null : this.settlements.get(id);
    }

    /** The settlement whose bounds contain this position, or null. */
    public Settlement at(BlockPos pos) {
        for (Settlement settlement : this.settlements.values()) {
            if (settlement.contains(pos)) {
                return settlement;
            }
        }
        return null;
    }

    /** The nearest settlement within {@code maxDistance}, or null. */
    public Settlement nearest(BlockPos pos, int maxDistance) {
        Settlement best = null;
        double bestDistance = (double) maxDistance * maxDistance;
        for (Settlement settlement : this.settlements.values()) {
            double distance = settlement.center().distSqr(pos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = settlement;
            }
        }
        return best;
    }

    /**
     * Finds the settlement covering this spot, founding one if the spot looks
     * inhabited and no settlement has claimed it yet.
     *
     * <p>"Looks inhabited" is deliberately vanilla's own notion of a village, so
     * the mod's society grows on top of worldgen villages the player already
     * recognises rather than inventing a parallel map of its own.
     */
    public Settlement getOrFound(ServerLevel level, BlockPos pos) {
        Settlement existing = at(pos);
        if (existing != null) {
            return existing;
        }
        Settlement near = nearest(pos, MERGE_DISTANCE);
        if (near != null) {
            // Close enough to be the same place: widen it rather than founding a
            // second settlement on the far side of the same square.
            near.setRadius(Math.max(near.radius(), (int) Math.sqrt(near.center().distSqr(pos)) + 16));
            setDirty();
            return near;
        }
        if (!level.isVillage(pos)) {
            return null;
        }
        return found(level, pos, defaultName(level, pos));
    }

    /** Founds a settlement outright — what an operator's marker does. */
    public Settlement found(ServerLevel level, BlockPos pos, String name) {
        Settlement settlement = new Settlement(UUID.randomUUID(), name, pos.immutable(), 64);
        settlement.setLandmark("meeting", pos.immutable());
        // A young settlement starts with enough in the stores to get through its
        // first few days, otherwise the very first bookkeeping pass starves it.
        settlement.addFood(64);
        settlement.addMaterials(32);
        this.settlements.put(settlement.id(), settlement);
        setDirty();
        PokeEFNPC.LOGGER.info("PokeEFNPC: founded settlement '{}' at {} in {}",
                name, pos, level.dimension().location());
        return settlement;
    }

    public void remove(UUID id) {
        if (this.settlements.remove(id) != null) {
            setDirty();
        }
    }

    private static String defaultName(ServerLevel level, BlockPos pos) {
        // Deterministic from the position, so the same village keeps its name
        // across a re-found after everyone in it has died.
        long seed = pos.asLong() ^ level.dimension().location().hashCode();
        String[] first = {"Ash", "Ever", "Stone", "Green", "Black", "Red", "Elder", "North",
                "Ravens", "Oak", "Iron", "Mill", "Fair", "Wolf"};
        String[] second = {"ford", "brook", "hollow", "gate", "field", "watch", "reach",
                "marsh", "hill", "crest", "vale", "barrow"};
        int a = (int) Math.floorMod(seed >> 8, first.length);
        int b = (int) Math.floorMod(seed >> 20, second.length);
        return first[a] + second[b];
    }

    /**
     * Runs the bookkeeping pass on every settlement in the level.
     *
     * <p>Called on a long interval from the world tick handler, not per entity
     * tick — the counts it needs are expensive to gather and none of them change
     * meaningfully second to second.
     */
    public void tickAll(ServerLevel level) {
        long gameTime = level.getGameTime();
        for (Settlement settlement : this.settlements.values()) {
            int hostiles = countHostiles(level, settlement);
            settlement.tick(gameTime, hostiles);
        }
        setDirty();
    }

    private int countHostiles(ServerLevel level, Settlement settlement) {
        BlockPos center = settlement.center();
        // Only counts what is actually loaded — an unloaded village is not being
        // attacked as far as the simulation is concerned.
        if (!level.isLoaded(center)) {
            return 0;
        }
        int radius = settlement.radius();
        net.minecraft.world.phys.AABB bounds = new net.minecraft.world.phys.AABB(center).inflate(radius);
        List<net.minecraft.world.entity.monster.Monster> monsters =
                level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, bounds,
                        monster -> monster.isAlive() && !monster.isRemoved());
        return monsters.size();
    }

    // ------------------------------------------------------------- persistence

    public static SettlementManager load(CompoundTag tag) {
        SettlementManager manager = new SettlementManager();
        ListTag list = tag.getList("Settlements", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                Settlement settlement = Settlement.load(list.getCompound(i));
                manager.settlements.put(settlement.id(), settlement);
            } catch (Exception e) {
                PokeEFNPC.LOGGER.warn("PokeEFNPC: dropped an unreadable settlement record ({})",
                        e.toString());
            }
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        List<Settlement> ordered = new ArrayList<>(this.settlements.values());
        for (Settlement settlement : ordered) {
            list.add(settlement.save());
        }
        tag.put("Settlements", list);
        return tag;
    }
}
