package com.pokewing.pokeefnpc.schedule;

import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.npc.NpcRole;
import com.pokewing.pokeefnpc.settlement.Settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Turns "this NPC wants to be somewhere of <i>this kind</i>" into an actual
 * block position.
 *
 * <p>Resolution goes cheapest-first and is deliberately forgiving: a remembered
 * claim, then the settlement's shared landmark, then a scan of the immediate
 * area, then the settlement centre as a last resort. An NPC therefore always has
 * somewhere to go — it never stalls because the perfect spot is missing — and it
 * upgrades to a better spot the moment one is found.
 */
public final class SiteResolver {

    /** Radius of the local scan when nothing is remembered or shared. */
    private static final int SCAN_RADIUS = 12;

    private SiteResolver() {
    }

    @Nullable
    public static BlockPos resolve(NpcEntity npc, Activity.Site site) {
        Settlement settlement = npc.settlement();
        return switch (site) {
            case HOME -> firstOf(npc.homePos(),
                    settlement == null ? null : settlement.homeOf(npc.getUUID()),
                    findNearby(npc, Kind.BED),
                    settlement == null ? null : settlement.landmark("meeting"));

            case WORKSTATION -> firstOf(npc.workPos(),
                    settlement == null ? null : settlement.workstationOf(npc.getUUID()),
                    findNearby(npc, workstationKind(npc.role())),
                    settlement == null ? null : settlement.landmark("meeting"));

            case MEETING_POINT -> settlement == null
                    ? findNearby(npc, Kind.BELL) : settlement.landmarkOrCenter("meeting");

            case GUARD_POST -> settlement == null ? null : settlement.landmarkOrCenter("guard_post");

            case PATROL_ROUTE -> settlement == null ? null : patrolPoint(npc, settlement);

            case DEFENCE_LINE -> settlement == null ? null : defenceTarget(npc, settlement);

            case TRAINING_GROUND -> settlement == null
                    ? null : settlement.landmarkOrCenter("training");

            case SHRINE -> settlement == null ? null : settlement.landmarkOrCenter("shrine");

            case HIDEOUT -> settlement == null
                    ? null : settlement.landmarkOrCenter("hideout");

            case WILDERNESS -> wildernessPoint(npc, settlement);
        };
    }

    @Nullable
    private static BlockPos firstOf(BlockPos... candidates) {
        for (BlockPos candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * A point on the ring of the settlement, chosen from the NPC's own id so each
     * guard walks a different arc and the beats do not all overlap.
     */
    private static BlockPos patrolPoint(NpcEntity npc, Settlement settlement) {
        long seed = npc.getUUID().getLeastSignificantBits();
        // Advances with the clock, so a patrol is a circuit rather than a post.
        double phase = (seed % 360) + (npc.level().getGameTime() / 200.0D) * 20.0D;
        double radians = Math.toRadians(phase);
        int radius = Math.max(8, settlement.radius() - 8);
        BlockPos centre = settlement.center();
        return new BlockPos(
                centre.getX() + (int) (Math.cos(radians) * radius),
                centre.getY(),
                centre.getZ() + (int) (Math.sin(radians) * radius));
    }

    /** The nearest gap in the walls, or the ring itself when there is none. */
    private static BlockPos defenceTarget(NpcEntity npc, Settlement settlement) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos weak : settlement.weakPoints()) {
            double distance = weak.distSqr(npc.blockPosition());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = weak;
            }
        }
        return best != null ? best : patrolPoint(npc, settlement);
    }

    /** Somewhere outside the walls, for the roles whose work is out there. */
    private static BlockPos wildernessPoint(NpcEntity npc, @Nullable Settlement settlement) {
        BlockPos origin = settlement == null ? npc.blockPosition() : settlement.center();
        int reach = settlement == null ? 48 : settlement.radius() + 40;
        double radians = npc.getRandom().nextDouble() * Math.PI * 2.0D;
        return new BlockPos(
                origin.getX() + (int) (Math.cos(radians) * reach),
                origin.getY(),
                origin.getZ() + (int) (Math.sin(radians) * reach));
    }

    /** The kinds of block the resolver knows how to recognise. */
    public enum Kind {
        BED, BELL, ANVIL, FARMLAND, FURNACE, BREWING_STAND, LECTERN, BARREL,
        CRAFTING_TABLE, CAULDRON, COMPOSTER, ENCHANTING_TABLE, GRINDSTONE, LOOM,
        SMOKER, FLETCHING_TABLE, CAMPFIRE
    }

    /** Which block this role calls its bench. */
    public static Kind workstationKind(NpcRole role) {
        return switch (role) {
            case BLACKSMITH -> Kind.ANVIL;
            case FARMER, VILLAGER -> Kind.FARMLAND;
            case BAKER -> Kind.SMOKER;
            case ALCHEMIST, HEALER -> Kind.BREWING_STAND;
            case SCRIBE, GUILD_CLERK, SEER -> Kind.LECTERN;
            case MERCHANT, BANKER, INNKEEPER -> Kind.BARREL;
            case MINER, LUMBERJACK, BUILDER -> Kind.CRAFTING_TABLE;
            case TAILOR -> Kind.LOOM;
            case ARCHER, HUNTER -> Kind.FLETCHING_TABLE;
            case MAGE -> Kind.ENCHANTING_TABLE;
            case PRIEST -> Kind.CAMPFIRE;
            case SOLDIER, GUARD, NIGHT_WATCH, KNIGHT, CAPTAIN, MERCENARY, EXECUTIONER
                    -> Kind.GRINDSTONE;
            case SHEPHERD, FISHERMAN -> Kind.COMPOSTER;
            default -> Kind.CRAFTING_TABLE;
        };
    }

    public static boolean matches(BlockState state, Kind kind) {
        return switch (kind) {
            case BED -> state.is(BlockTags.BEDS);
            case BELL -> state.is(Blocks.BELL);
            case ANVIL -> state.is(BlockTags.ANVIL);
            case FARMLAND -> state.is(Blocks.FARMLAND);
            case FURNACE -> state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE);
            case BREWING_STAND -> state.is(Blocks.BREWING_STAND);
            case LECTERN -> state.is(Blocks.LECTERN);
            case BARREL -> state.is(Blocks.BARREL) || state.is(Blocks.CHEST);
            case CRAFTING_TABLE -> state.is(Blocks.CRAFTING_TABLE);
            case CAULDRON -> state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON);
            case COMPOSTER -> state.is(Blocks.COMPOSTER) || state.is(Blocks.HAY_BLOCK);
            case ENCHANTING_TABLE -> state.is(Blocks.ENCHANTING_TABLE) || state.is(Blocks.BOOKSHELF);
            case GRINDSTONE -> state.is(Blocks.GRINDSTONE);
            case LOOM -> state.is(Blocks.LOOM);
            case SMOKER -> state.is(Blocks.SMOKER) || state.is(Blocks.FURNACE);
            case FLETCHING_TABLE -> state.is(Blocks.FLETCHING_TABLE);
            case CAMPFIRE -> state.is(BlockTags.CAMPFIRES) || state.is(Blocks.LANTERN);
        };
    }

    /**
     * Nearest unclaimed block of the wanted kind. Deliberately a bounded cube
     * scan rather than a POI query: it finds player-built benches that were never
     * registered as points of interest, which is what people actually build.
     */
    @Nullable
    public static BlockPos findNearby(NpcEntity npc, Kind kind) {
        Level level = npc.level();
        BlockPos origin = npc.blockPosition();
        Settlement settlement = npc.settlement();
        long gameTime = level.getGameTime();

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(cursor) || !matches(level.getBlockState(cursor), kind)) {
                        continue;
                    }
                    if (settlement != null && settlement.isClaimed(cursor, npc.getUUID(), gameTime)) {
                        continue;
                    }
                    double distance = cursor.distSqr(origin);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return best;
    }
}
