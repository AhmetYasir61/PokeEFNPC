package com.pokewing.pokeefnpc.ai;

import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.schedule.Activity;
import com.pokewing.pokeefnpc.schedule.ScheduleTemplate;
import com.pokewing.pokeefnpc.schedule.SiteResolver;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * The backbone: get to where the hour says you should be, and stay there.
 *
 * <p>This runs almost always, at a low priority, which is the point — every
 * other goal is an <i>interruption</i> of the routine, and when the interruption
 * finishes the NPC simply rejoins its day wherever the clock has got to. No
 * state is carried across the interruption, so an NPC that spent the afternoon
 * being chased across a field walks to the tavern in the evening like everyone
 * else.
 *
 * <p>Arrival is what makes it read as purpose rather than pathing: once inside
 * {@link #ARRIVE_DISTANCE} the NPC stops, faces its work, and settles into the
 * looping gesture that belongs to the activity — the smith starts hammering, the
 * clerk starts writing, the guard sets its stance.
 */
public class NpcScheduleGoal extends Goal {

    /** Close enough to count as "at work". */
    private static final double ARRIVE_DISTANCE = 2.5D;
    /** How often the destination is re-resolved, in ticks. */
    private static final int REPATH_INTERVAL = 60;

    private final NpcEntity npc;

    @Nullable
    private BlockPos destination;
    private Activity currentActivity = Activity.HOME_IDLE;
    private int repathCooldown;
    private int stuckTicks;

    public NpcScheduleGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Fighting comes first; the day can wait.
        return this.npc.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
        this.stuckTicks = 0;
    }

    @Override
    public void stop() {
        this.destination = null;
        this.npc.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }

    @Override
    public void tick() {
        Activity wanted = decideActivity();
        if (wanted != this.currentActivity) {
            this.currentActivity = wanted;
            this.npc.setActivity(wanted);
            this.destination = null;
            this.repathCooldown = 0;
            // Changing task drops whatever gesture belonged to the last one.
            this.npc.stopEmote();
        }

        if (this.repathCooldown-- <= 0) {
            this.repathCooldown = REPATH_INTERVAL;
            BlockPos resolved = SiteResolver.resolve(this.npc, wanted.site());
            if (resolved != null) {
                this.destination = resolved;
            }
        }

        if (this.destination == null) {
            return;
        }

        double distance = this.npc.blockPosition().distSqr(this.destination);
        if (distance <= ARRIVE_DISTANCE * ARRIVE_DISTANCE) {
            arrive();
        } else {
            travel(distance);
        }
    }

    /**
     * The hour decides, unless the settlement is in trouble — a raid overrides
     * everyone's routine at once, and each role reads that through its own
     * courage rather than through a separate schedule.
     */
    private Activity decideActivity() {
        if (this.npc.settlementThreat() > 0.5F) {
            return Activity.EMERGENCY;
        }
        ScheduleTemplate template = this.npc.role().schedule();
        Activity scheduled = template.at(this.npc.scheduleHour());

        // Nobody sleeps through a monster in the room.
        if (scheduled == Activity.SLEEP && this.npc.settlementThreat() > 0.2F) {
            return Activity.HOME_IDLE;
        }
        return scheduled;
    }

    private void arrive() {
        this.npc.getNavigation().stop();
        this.stuckTicks = 0;
        this.npc.getLookControl().setLookAt(
                this.destination.getX() + 0.5D,
                this.destination.getY() + 0.5D,
                this.destination.getZ() + 0.5D);

        // The activity supplies the gesture unless it defers to the role, which
        // is how a smith and a scribe both "work" without looking alike.
        Emote emote = this.currentActivity.loopEmote();
        if (emote == null && this.currentActivity == Activity.WORK) {
            emote = this.npc.role().workEmote();
        }
        if (emote != null) {
            this.npc.loopEmote(emote);
        }
        doWorkTick();
    }

    private void travel(double distance) {
        boolean moved = this.npc.getNavigation().moveTo(
                this.destination.getX() + 0.5D,
                this.destination.getY(),
                this.destination.getZ() + 0.5D,
                this.currentActivity.walkSpeed());

        if (!moved || this.npc.getNavigation().isDone()) {
            this.stuckTicks++;
            // Somewhere genuinely unreachable — a walled-off bench, a bed behind
            // a locked door. Forget it and let the resolver pick something else
            // rather than grinding against the wall for the rest of the day.
            if (this.stuckTicks > 5) {
                this.stuckTicks = 0;
                this.destination = null;
                forgetClaimIfUnreachable();
            }
        } else {
            this.stuckTicks = 0;
        }
    }

    private void forgetClaimIfUnreachable() {
        switch (this.currentActivity.site()) {
            case HOME -> this.npc.setHomePos(null);
            case WORKSTATION -> this.npc.setWorkPos(null);
            default -> {
            }
        }
    }

    /**
     * The productive part of the day. This is what actually stocks a settlement:
     * a working NPC contributes to the shared larder or the materials pile, which
     * is what pays for defences and keeps prosperity up.
     */
    private void doWorkTick() {
        if (this.npc.tickCount % 100 != 0) {
            return;
        }
        var settlement = this.npc.settlement();
        if (settlement == null) {
            return;
        }
        switch (this.currentActivity) {
            case WORK, TEND_SHOP -> {
                int output = Math.round(1.0F + this.npc.personality().diligence * 3.0F);
                switch (this.npc.role().category()) {
                    case COMMONER -> settlement.addFood(output);
                    case TRADE -> settlement.addMaterials(output);
                    default -> settlement.addMaterials(Math.max(1, output / 2));
                }
                // Work that goes well is quietly satisfying; that slow drift is
                // what keeps a busy village looking content rather than blank.
                this.npc.mood().feel(this.npc.personality(), 0.03F, 0.0F);
            }
            case SLEEP -> this.npc.mood().feel(this.npc.personality(), 0.05F, -0.2F);
            case EAT, SOCIALISE -> this.npc.mood().feel(this.npc.personality(),
                    0.04F * this.npc.personality().sociability, 0.05F);
            default -> {
            }
        }
    }
}
