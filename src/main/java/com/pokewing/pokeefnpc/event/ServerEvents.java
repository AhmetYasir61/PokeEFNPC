package com.pokewing.pokeefnpc.event;

import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.PokeEFNPCConfig;
import com.pokewing.pokeefnpc.data.PlayerLedger;
import com.pokewing.pokeefnpc.npc.Emotion;
import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.npc.NpcNames;
import com.pokewing.pokeefnpc.npc.NpcRole;
import com.pokewing.pokeefnpc.registry.ModEntities;
import com.pokewing.pokeefnpc.settlement.Settlement;
import com.pokewing.pokeefnpc.settlement.SettlementManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The world's own clock: the part of the system that runs whether or not anybody
 * is stood next to an NPC.
 *
 * <p>Three jobs, all deliberately on long intervals rather than per tick:
 * settlements do their bookkeeping, villages grow their populations, and the
 * things players do out in the world are credited against the contracts they are
 * carrying.
 */
public class ServerEvents {

    /** How often the settlement bookkeeping pass runs, in ticks. */
    private static final int SETTLEMENT_INTERVAL = 600;
    /** How often players are checked against scouting contracts. */
    private static final int SCOUT_INTERVAL = 100;

    private int settlementTimer;
    private int populationTimer;
    private int scoutTimer;

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (++this.settlementTimer >= SETTLEMENT_INTERVAL) {
            this.settlementTimer = 0;
            SettlementManager.get(level).tickAll(level);
        }
        if (++this.populationTimer >= PokeEFNPCConfig.populationIntervalTicks()) {
            this.populationTimer = 0;
            growPopulations(level);
        }
        if (++this.scoutTimer >= SCOUT_INTERVAL) {
            this.scoutTimer = 0;
            creditScouting(level);
        }
    }

    /**
     * Villages growing their own people.
     *
     * <p>This is the half of the system an operator never has to touch. A
     * settlement that is under strength and has food to spare produces a new
     * resident, and {@link Settlement#neededRole} decides what that resident is
     * from what the place is currently short of — so a village that keeps being
     * raided starts producing guards, and one that is starving produces farmers.
     */
    private void growPopulations(ServerLevel level) {
        if (!PokeEFNPCConfig.naturalPopulation()) {
            return;
        }
        int cap = PokeEFNPCConfig.maxSettlementPopulation();
        for (Settlement settlement : SettlementManager.get(level).all()) {
            BlockPos centre = settlement.center();
            // Only places somebody is actually near: spawning into unloaded
            // chunks would build a population nobody ever sees.
            if (!level.isLoaded(centre) || level.getNearestPlayer(
                    centre.getX(), centre.getY(), centre.getZ(), 128.0D, false) == null) {
                continue;
            }
            if (settlement.population() >= cap) {
                continue;
            }
            // A village feeds its newcomers or it does not get any. This is the
            // link that makes a well-defended, well-fed settlement visibly grow
            // and a besieged one visibly shrink.
            if (settlement.foodStores() < settlement.population() * 3 + 8) {
                continue;
            }
            if (level.random.nextFloat() > 0.5F) {
                continue;
            }
            spawnResident(level, settlement);
        }
    }

    private void spawnResident(ServerLevel level, Settlement settlement) {
        BlockPos spot = findSpawnSpot(level, settlement);
        if (spot == null) {
            return;
        }
        NpcEntity npc = ModEntities.NPC.get().create(level);
        if (npc == null) {
            return;
        }
        NpcRole role = level.random.nextFloat() < PokeEFNPCConfig.outlawSpawnChance()
                ? outlawRole(level) : settlement.neededRole(level.random);

        npc.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        npc.applyRole(role, true);
        npc.setNatural(true);
        npc.setCustomName(Component.literal(NpcNames.pick(level.random, role)));
        npc.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.NATURAL,
                null, null);
        // finalizeSpawn would otherwise re-roll the role on an unlocked NPC.
        npc.applyRole(role, false);
        npc.setSettlement(settlement);
        settlement.addResident(npc.getUUID(), role, level.getGameTime());
        level.addFreshEntity(npc);

        // Feeding a new mouth costs the village something up front.
        settlement.addFood(-8);
    }

    private NpcRole outlawRole(ServerLevel level) {
        NpcRole[] outlaws = {NpcRole.THIEF, NpcRole.SMUGGLER, NpcRole.BLACK_MARKET_DEALER};
        return outlaws[level.random.nextInt(outlaws.length)];
    }

    private BlockPos findSpawnSpot(ServerLevel level, Settlement settlement) {
        BlockPos centre = settlement.center();
        for (int attempt = 0; attempt < 12; attempt++) {
            int radius = settlement.radius();
            BlockPos candidate = centre.offset(
                    level.random.nextInt(radius * 2) - radius, 0,
                    level.random.nextInt(radius * 2) - radius);
            BlockPos surface = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    candidate);
            if (!level.isLoaded(surface)) {
                continue;
            }
            if (level.getBlockState(surface).isAir()
                    && level.getBlockState(surface.above()).isAir()
                    && !level.getBlockState(surface.below()).isAir()
                    && !level.getFluidState(surface.below()).is(net.minecraft.tags.FluidTags.WATER)) {
                return surface;
            }
        }
        return null;
    }

    /** Ticks off scouting contracts for players who have reached the spot. */
    private void creditScouting(ServerLevel level) {
        PlayerLedger ledger = PlayerLedger.get(level);
        for (ServerPlayer player : level.players()) {
            ledger.creditVisit(player.getUUID(), player.blockPosition());
        }
    }

    /**
     * A death anywhere in the world, read three ways: as progress on a hunting
     * contract, as a threat to the nearest settlement, and — when the dead was
     * one of its own — as something the neighbours take personally.
     */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        Entity killer = event.getSource().getEntity();

        if (killer instanceof ServerPlayer player) {
            PlayerLedger.get(level).creditKill(player.getUUID(), entity.getType());
        }

        if (entity instanceof NpcEntity dead) {
            onResidentKilled(level, dead, killer);
        }
    }

    private void onResidentKilled(ServerLevel level, NpcEntity dead, Entity killer) {
        Settlement settlement = dead.settlement();
        if (settlement == null) {
            settlement = SettlementManager.get(level).nearest(dead.blockPosition(), 96);
        }
        if (settlement == null) {
            return;
        }
        // Losing people is what makes a settlement frightened, and a frightened
        // settlement builds walls and produces guards instead of bakers.
        settlement.raiseThreat(0.25F);

        if (!(killer instanceof Player culprit)) {
            return;
        }
        // A murder in a small place is not forgotten by the people who saw it.
        for (NpcEntity witness : level.getEntitiesOfClass(NpcEntity.class,
                dead.getBoundingBox().inflate(32.0D))) {
            witness.witnessed(culprit, -35.0F, Emotion.ANGRY);
        }
        PokeEFNPC.LOGGER.debug("PokeEFNPC: {} was killed by {} in {}",
                dead.getName().getString(), culprit.getName().getString(), settlement.name());
    }
}
