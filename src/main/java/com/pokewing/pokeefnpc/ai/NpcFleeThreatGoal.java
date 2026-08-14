package com.pokewing.pokeefnpc.ai;

import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.npc.Emotion;
import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.schedule.Activity;
import com.pokewing.pokeefnpc.schedule.SiteResolver;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * Getting out of the way.
 *
 * <p>The survival half of the settlement: when the monsters come, the people who
 * cannot fight have to do something other than stand in the open being eaten. A
 * frightened NPC runs for its own bed if it can reach one, and only bolts blindly
 * into the dark when it cannot — which is what keeps a raid looking like a village
 * emptying rather than a mob scattering.
 *
 * <p>Who runs is decided by {@code Personality#standsGround}, not by role alone.
 * A brave baker will hold a doorway; a cowardly soldier will not, and both are
 * more interesting than the alternative.
 */
public class NpcFleeThreatGoal extends Goal {

    private static final double SEARCH_RANGE = 12.0D;
    private static final double PANIC_SPEED = 1.15D;
    private static final int MAX_FLEE_TICKS = 200;

    private final NpcEntity npc;

    @Nullable
    private LivingEntity threat;
    @Nullable
    private Vec3 refuge;
    private int fleeTicks;

    public NpcFleeThreatGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.threat = nearestThreat();
        if (this.threat == null) {
            return false;
        }
        float pressure = pressureFrom(this.threat);
        if (this.npc.personality().standsGround(pressure)) {
            // Stays and fights — the target selector will pick this up, and the
            // fear still shows on the face even though the feet do not move.
            this.npc.mood().latch(this.npc.personality(), Emotion.ANGRY, 60);
            return false;
        }
        this.refuge = findRefuge();
        return this.refuge != null;
    }

    /**
     * How badly this threat frightens the NPC, in [0,1]: nearer is worse, and
     * being already hurt makes everything worse.
     */
    private float pressureFrom(LivingEntity threat) {
        double distance = Math.sqrt(this.npc.distanceToSqr(threat));
        float proximity = (float) (1.0D - Math.min(1.0D, distance / SEARCH_RANGE));
        float wounded = 1.0F - this.npc.getHealth() / this.npc.getMaxHealth();
        return Math.min(1.0F, proximity * 0.7F + wounded * 0.5F);
    }

    @Nullable
    private LivingEntity nearestThreat() {
        List<Monster> monsters = this.npc.level().getEntitiesOfClass(Monster.class,
                this.npc.getBoundingBox().inflate(SEARCH_RANGE),
                monster -> monster.isAlive() && this.npc.hasLineOfSight(monster));
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (Monster monster : monsters) {
            double distance = this.npc.distanceToSqr(monster);
            if (distance < best) {
                best = distance;
                nearest = monster;
            }
        }
        return nearest;
    }

    /**
     * Home first — a bed is a door and a roof, and NPCs converging on their own
     * houses is what a village under attack should look like. Failing that, any
     * direction away from the threat.
     */
    @Nullable
    private Vec3 findRefuge() {
        BlockPos home = this.npc.homePos();
        if (home == null) {
            home = SiteResolver.resolve(this.npc, Activity.Site.HOME);
        }
        if (home != null && home.distSqr(this.npc.blockPosition()) < 64.0D * 64.0D) {
            return Vec3.atBottomCenterOf(home);
        }
        if (this.threat == null) {
            return null;
        }
        return DefaultRandomPos.getPosAway(this.npc, 16, 7, this.threat.position());
    }

    @Override
    public boolean canContinueToUse() {
        if (this.fleeTicks > MAX_FLEE_TICKS || this.refuge == null) {
            return false;
        }
        // Safe once the thing chasing is gone or far enough behind.
        return this.threat != null && this.threat.isAlive()
                && this.npc.distanceToSqr(this.threat) < SEARCH_RANGE * SEARCH_RANGE * 2.25D;
    }

    @Override
    public void start() {
        this.fleeTicks = 0;
        this.npc.setTarget(null);
        this.npc.mood().latch(this.npc.personality(), Emotion.SURPRISED, 40);
        this.npc.playEmote(Emote.SHIVER);
        if (this.refuge != null) {
            this.npc.getNavigation().moveTo(this.refuge.x, this.refuge.y, this.refuge.z, PANIC_SPEED);
        }
        // Everyone within earshot learns there is something out there, which is
        // how one farmer seeing a zombie empties a whole square.
        var settlement = this.npc.settlement();
        if (settlement != null) {
            settlement.raiseThreat(0.15F);
        }
    }

    @Override
    public void tick() {
        this.fleeTicks++;
        if (this.threat != null) {
            this.npc.getLookControl().setLookAt(this.threat, 30.0F, 30.0F);
        }
        if (this.npc.getNavigation().isDone() && this.refuge != null) {
            this.refuge = findRefuge();
            if (this.refuge != null) {
                this.npc.getNavigation().moveTo(this.refuge.x, this.refuge.y, this.refuge.z,
                        PANIC_SPEED);
            }
        }
        // Terror does not fade while the thing is still there.
        if (this.fleeTicks % 20 == 0) {
            this.npc.mood().feel(this.npc.personality(), -0.2F, 0.5F);
        }
    }

    @Override
    public void stop() {
        this.threat = null;
        this.refuge = null;
        this.fleeTicks = 0;
        this.npc.getNavigation().stop();
        this.npc.stopEmote();
    }
}
