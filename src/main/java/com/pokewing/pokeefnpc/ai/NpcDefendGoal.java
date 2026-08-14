package com.pokewing.pokeefnpc.ai;

import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.npc.Emotion;
import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.settlement.Settlement;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Monster;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

/**
 * Standing between the monsters and everybody else.
 *
 * <p>An ordinary attack-nearest-monster goal makes guards react to what is in
 * front of them. This makes them react to what is threatening the <i>settlement</i>:
 * targets are ranked by how close they are to the village centre, not to the
 * guard, so the watch converges on whatever has got furthest in rather than each
 * guard peeling off after its own zombie.
 *
 * <p>Outlaws are fair game too. A thief that has been caught in the act, or a
 * bandit that wandered in, is dealt with by the same goal — from the defender's
 * point of view it is the same job.
 */
public class NpcDefendGoal extends TargetGoal {

    private static final double GUARD_RANGE = 24.0D;

    private final NpcEntity npc;

    @Nullable
    private LivingEntity candidate;

    public NpcDefendGoal(NpcEntity npc) {
        super(npc, false, false);
        this.npc = npc;
    }

    @Override
    public boolean canUse() {
        if (!this.npc.role().isDefender()) {
            return false;
        }
        // Badly wounded defenders break off; the flee goal takes it from there.
        if (this.npc.getHealth() / this.npc.getMaxHealth() < 0.25F
                && this.npc.personality().courage < 0.9F) {
            return false;
        }
        this.candidate = findWorstThreat();
        return this.candidate != null;
    }

    @Nullable
    private LivingEntity findWorstThreat() {
        Settlement settlement = this.npc.settlement();
        var bounds = this.npc.getBoundingBox().inflate(GUARD_RANGE);

        List<LivingEntity> hostiles = this.npc.level().getEntitiesOfClass(LivingEntity.class, bounds,
                entity -> entity.isAlive() && isHostile(entity));
        if (hostiles.isEmpty()) {
            return null;
        }
        if (settlement == null) {
            return hostiles.stream()
                    .min(Comparator.comparingDouble(this.npc::distanceToSqr))
                    .orElse(null);
        }
        // Whatever is deepest inside the walls is the emergency.
        return hostiles.stream()
                .min(Comparator.comparingDouble(entity -> entity.blockPosition()
                        .distSqr(settlement.center())))
                .orElse(null);
    }

    private boolean isHostile(LivingEntity entity) {
        if (entity instanceof Monster) {
            return true;
        }
        if (entity instanceof NpcEntity other && other != this.npc) {
            // Outlaws are only attacked once they have actually made themselves
            // known — a fence quietly running a shop is not a target.
            return other.role().isOutlaw() && other.getTarget() != null;
        }
        return false;
    }

    @Override
    public void start() {
        this.npc.setTarget(this.candidate);
        this.npc.mood().latch(this.npc.personality(), Emotion.ANGRY);
        this.npc.playEmote(this.npc.personality().courage > 0.7F ? Emote.ROAR : Emote.DRAW_WEAPON);

        Settlement settlement = this.npc.settlement();
        if (settlement != null) {
            settlement.raiseThreat(0.1F);
        }
        // Nearby defenders come too. A watch that answers together is the
        // difference between a village surviving a raid and losing one guard at
        // a time.
        for (NpcEntity ally : this.npc.level().getEntitiesOfClass(NpcEntity.class,
                this.npc.getBoundingBox().inflate(16.0D))) {
            if (ally != this.npc && ally.role().isDefender() && ally.getTarget() == null) {
                ally.setTarget(this.candidate);
                ally.mood().latch(ally.personality(), Emotion.FOCUSED, 100);
            }
        }
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.npc.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (this.npc.getHealth() / this.npc.getMaxHealth() < 0.15F) {
            return false;
        }
        return this.npc.distanceToSqr(target) < GUARD_RANGE * GUARD_RANGE * 2.25D;
    }

    @Override
    public void stop() {
        super.stop();
        this.candidate = null;
        // The fight is over. Relief, not cheer — the mood settles on its own from
        // here, and a defender that lost neighbours will still look grim.
        this.npc.mood().feel(this.npc.personality(), 0.2F, -0.3F);
    }
}
