package com.pokewing.pokeefnpc.ai;

import com.pokewing.pokeefnpc.PokeEFNPCConfig;
import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.npc.NpcRole;
import com.pokewing.pokeefnpc.schedule.Activity;
import com.pokewing.pokeefnpc.settlement.Settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Building the walls, and lighting them.
 *
 * <p>This is what makes a settlement's survival its own achievement rather than
 * the player's. Builders and guards walk the perimeter, notice where it is dark
 * or open, and close it — a torch on an unlit stretch, a fence across a gap —
 * paying for it out of the settlement's shared {@link Settlement#materialStores()}.
 * Over enough in-game days a village that is left alone genuinely becomes hard to
 * overrun, and one that keeps losing its builders does not.
 *
 * <p>Every placement is bounded: only into air, only on solid ground, only inside
 * the settlement ring, only from the shared stores, and only when the config
 * allows terrain edits at all. That last switch matters on servers where nobody
 * wants NPCs rearranging the landscape.
 */
public class NpcFortifyGoal extends Goal {

    private static final double REACH = 3.0D;
    private static final int SURVEY_INTERVAL = 300;
    private static final int MATERIAL_COST = 2;

    private final NpcEntity npc;

    @Nullable
    private BlockPos target;
    private int surveyCooldown;
    private int workTicks;

    public NpcFortifyGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        this.surveyCooldown = npc.getRandom().nextInt(SURVEY_INTERVAL);
    }

    @Override
    public boolean canUse() {
        if (!PokeEFNPCConfig.allowNpcBuilding()) {
            return false;
        }
        if (this.npc.getTarget() != null) {
            return false;
        }
        if (!buildsDefences(this.npc.role())) {
            return false;
        }
        Activity activity = this.npc.activity();
        if (activity != Activity.FORTIFY && activity != Activity.PATROL
                && activity != Activity.EMERGENCY) {
            return false;
        }
        Settlement settlement = this.npc.settlement();
        if (settlement == null || settlement.materialStores() < MATERIAL_COST) {
            return false;
        }
        if (--this.surveyCooldown > 0) {
            return false;
        }
        this.surveyCooldown = SURVEY_INTERVAL;
        this.target = chooseWeakPoint(settlement);
        return this.target != null;
    }

    private static boolean buildsDefences(NpcRole role) {
        return role == NpcRole.BUILDER || role == NpcRole.GUARD || role == NpcRole.NIGHT_WATCH
                || role == NpcRole.SOLDIER || role == NpcRole.CAPTAIN || role == NpcRole.LUMBERJACK
                || role == NpcRole.MINER;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null && this.workTicks < 200
                && PokeEFNPCConfig.allowNpcBuilding();
    }

    @Override
    public void start() {
        this.workTicks = 0;
    }

    @Override
    public void stop() {
        this.target = null;
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }
        this.workTicks++;
        double distance = this.npc.blockPosition().distSqr(this.target);
        if (distance > REACH * REACH) {
            this.npc.getNavigation().moveTo(
                    this.target.getX() + 0.5D, this.target.getY(), this.target.getZ() + 0.5D, 0.7D);
            return;
        }

        this.npc.getNavigation().stop();
        this.npc.getLookControl().setLookAt(
                this.target.getX() + 0.5D, this.target.getY() + 0.5D, this.target.getZ() + 0.5D);
        this.npc.loopEmote(Emote.HAMMER);

        // Building takes a moment, so it reads as work rather than a block
        // appearing out of nowhere.
        if (this.workTicks % 40 != 0) {
            return;
        }
        Settlement settlement = this.npc.settlement();
        if (settlement == null || !settlement.spendMaterials(MATERIAL_COST)) {
            this.target = null;
            return;
        }
        place(settlement, this.target);
        this.target = null;
    }

    private void place(Settlement settlement, BlockPos pos) {
        Level level = this.npc.level();
        if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
            settlement.clearWeakPoint(pos);
            return;
        }
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) {
            settlement.clearWeakPoint(pos);
            return;
        }

        // Dark before open: a lit gap stops monsters spawning in it, which is
        // worth more than a fence they can path around.
        BlockState toPlace = level.getBrightness(LightLayer.BLOCK, pos) < 8
                ? Blocks.TORCH.defaultBlockState()
                : Blocks.OAK_FENCE.defaultBlockState();

        level.setBlockAndUpdate(pos, toPlace);
        settlement.clearWeakPoint(pos);
        this.npc.mood().feel(this.npc.personality(), 0.15F, 0.05F);
    }

    /**
     * Picks somewhere on the perimeter that needs work — a reported gap first,
     * otherwise a fresh survey of a random arc of the ring. Surveying an arc
     * rather than the whole circle keeps the cost flat no matter how large the
     * settlement has grown.
     */
    @Nullable
    private BlockPos chooseWeakPoint(Settlement settlement) {
        if (!settlement.weakPoints().isEmpty()) {
            return settlement.weakPoints().get(0);
        }

        Level level = this.npc.level();
        BlockPos centre = settlement.center();
        int radius = Math.max(8, settlement.radius() - 6);
        double startAngle = this.npc.getRandom().nextDouble() * Math.PI * 2.0D;

        for (int step = 0; step < 24; step++) {
            double angle = startAngle + step * (Math.PI / 24.0D);
            int x = centre.getX() + (int) (Math.cos(angle) * radius);
            int z = centre.getZ() + (int) (Math.sin(angle) * radius);
            BlockPos surface = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, centre.getY(), z));
            if (!level.isLoaded(surface) || !level.getBlockState(surface).isAir()) {
                continue;
            }
            boolean dark = level.getBrightness(LightLayer.BLOCK, surface) < 8;
            if (dark) {
                settlement.reportWeakPoint(surface);
                return surface;
            }
        }
        return null;
    }
}
