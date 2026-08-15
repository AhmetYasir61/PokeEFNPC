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
import com.pokewing.pokeefnpc.brain.NpcBrain;
import com.pokewing.pokeefnpc.settlement.SettlementManager;
import com.pokewing.pokeefnpc.voice.NpcVoiceBridge;
import com.pokewing.pokeefnpc.voice.VoiceSessions;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
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
    private int successionTimer;

    /** How often companies are checked for a leader who is never coming back. */
    private static final int SUCCESSION_INTERVAL = 2400;

    /** How many commanders peel off to rejoin a respawned leader. */
    private static final int MAX_ESCORTS = 2;

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
        // Rare on purpose: it walks every loaded NPC and a ban is not something
        // that needs noticing within the second.
        if (++this.successionTimer >= SUCCESSION_INTERVAL
                && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            this.successionTimer = 0;
            checkForLostLeaders(level.getServer());
        }
        // Checked every tick and cheap when nobody is talking: an utterance ends
        // when the packets stop, so the only way to notice is to keep looking.
        if (NpcVoiceBridge.canListen()) {
            VoiceSessions.sweep(level.getServer());
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


    // ------------------------------------------------------------- lifecycle

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Both pools are started here rather than at mod construction, so a
        // single-player world that is loaded and quit repeatedly does not leak a
        // thread pool per load.
        NpcBrain.start();
        if (NpcVoiceBridge.modPresent()) {
            NpcVoiceBridge.start();
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        NpcBrain.stop();
        NpcVoiceBridge.stop();
    }

    /**
     * Typed chat reaches an NPC the same way speech does.
     *
     * <p>The message is <b>not</b> consumed — it goes to chat as normal — but if
     * the player happens to be looking at somebody while they type it, that
     * somebody hears it. So talking to a villager needs no command and no
     * special syntax: stand in front of them and say something.
     */
    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null || !NpcBrain.available()) {
            return;
        }
        NpcEntity addressee = VoiceSessions.findAddressee(player);
        if (addressee != null) {
            addressee.heard(player, event.getRawText(), false);
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

    /**
     * When a player falls, everyone sworn to them sets out for the spot.
     *
     * <p>On foot, from wherever they were — no teleport. The march is the point:
     * a garrison that converges on the place its leader died, fighting through
     * whatever is between, reads as loyalty. A garrison that blinks to the
     * corpse reads as a spawner.
     */
    @SubscribeEvent
    public void onPlayerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        var where = player.blockPosition();
        // A generous radius: soldiers left guarding a keep two hundred blocks
        // away should still hear about it and start walking.
        var bounds = player.getBoundingBox().inflate(256.0D);
        for (com.pokewing.pokeefnpc.npc.NpcEntity npc
                : player.level().getEntitiesOfClass(
                        com.pokewing.pokeefnpc.npc.NpcEntity.class, bounds)) {
            if (npc.allegiance().isOwnedBy(player.getUUID())) {
                npc.ownerFell(where);
            }
        }
    }


    /**
     * When a fallen leader is back on their feet, the garrison stands down.
     *
     * <p>Commanders — captains and knights — set out to rejoin the leader, on
     * foot like everything else. Everyone else goes back to the ground they are
     * meant to hold, because a base that empties every time its owner respawns
     * is a base that gets taken while nobody is looking.
     */
    @SubscribeEvent
    public void onPlayerRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        var where = player.blockPosition();
        int escorts = 0;
        for (var level : player.server.getAllLevels()) {
            for (com.pokewing.pokeefnpc.npc.NpcEntity npc
                    : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(
                            com.pokewing.pokeefnpc.npc.NpcEntity.class),
                            npc -> npc.allegiance().isOwnedBy(player.getUUID()))) {
                // A guard of two, not the whole army: any more and the walls are
                // bare for as long as the walk takes.
                boolean escort = npc.isCommander() && escorts < MAX_ESCORTS
                        && npc.level() == player.level();
                if (escort) {
                    escorts++;
                }
                npc.ownerReturned(where, escort);
            }
        }
    }


    /**
     * Checks whether any company's leader is gone for good, and appoints a
     * regent if so.
     *
     * <p>"Gone for good" means banned, not merely logged off — somebody who
     * quits for the night still has a garrison waiting for them in the morning.
     * A ban is the one signal the server can give that a player is genuinely not
     * coming back, so it is the one used.
     *
     * <p>The succession itself is small and deliberate: the most senior soldier
     * still standing is raised to regent and sets out looking for a new leader;
     * everybody else is released and goes back to the work of the settlement, so
     * a lost lord costs a town its lord rather than its people.
     */
    private void checkForLostLeaders(net.minecraft.server.MinecraftServer server) {
        java.util.Map<java.util.UUID, java.util.List<com.pokewing.pokeefnpc.npc.NpcEntity>> companies =
                new java.util.HashMap<>();
        for (var level : server.getAllLevels()) {
            for (com.pokewing.pokeefnpc.npc.NpcEntity npc
                    : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(
                            com.pokewing.pokeefnpc.npc.NpcEntity.class),
                            candidate -> candidate.allegiance().isOwned())) {
                companies.computeIfAbsent(npc.allegiance().owner(),
                        key -> new java.util.ArrayList<>()).add(npc);
            }
        }
        for (var entry : companies.entrySet()) {
            if (!isGoneForGood(server, entry.getKey())) {
                continue;
            }
            java.util.List<com.pokewing.pokeefnpc.npc.NpcEntity> company = entry.getValue();
            // Rank first, then whoever is in the best shape: a wounded captain
            // still outranks a healthy recruit, but a dead one leads nobody.
            company.sort(java.util.Comparator
                    .comparing(com.pokewing.pokeefnpc.npc.NpcEntity::isCommander)
                    .thenComparing(com.pokewing.pokeefnpc.npc.NpcEntity::getHealth)
                    .reversed());
            boolean appointed = false;
            for (com.pokewing.pokeefnpc.npc.NpcEntity npc : company) {
                if (!appointed && npc.isAlive()) {
                    npc.becomeRegent();
                    appointed = true;
                } else {
                    npc.leaderLost();
                }
            }
        }
    }

    /** Banned, and not simply logged out. */
    private boolean isGoneForGood(net.minecraft.server.MinecraftServer server,
                                  java.util.UUID owner) {
        if (owner == null || server.getPlayerList().getPlayer(owner) != null) {
            return false;
        }
        var profile = server.getProfileCache() == null
                ? java.util.Optional.<com.mojang.authlib.GameProfile>empty()
                : server.getProfileCache().get(owner);
        return profile.isPresent() && server.getPlayerList().getBans().isBanned(profile.get());
    }


    /**
     * Installs the Epic Fight patch as soon as the server has resources.
     *
     * <p>Deliberately not at mod-construction time: the patch needs a resource
     * manager to load its mesh through, and that does not exist until the server
     * is up.
     */
    @SubscribeEvent
    public void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        com.pokewing.pokeefnpc.compat.EpicFightPatchInstaller.installServer(
                event.getServer().getResourceManager());
    }

}
