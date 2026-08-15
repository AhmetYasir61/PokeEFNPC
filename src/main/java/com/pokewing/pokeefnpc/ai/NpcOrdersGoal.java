package com.pokewing.pokeefnpc.ai;

import com.pokewing.pokeefnpc.npc.Allegiance;
import com.pokewing.pokeefnpc.npc.NpcEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Carries out a standing order: heel, hold this spot, or walk a circuit.
 *
 * <p>This sits above the daily schedule and below self-preservation and combat,
 * which is the right place for an order — a soldier told to hold a gate stops
 * keeping shop hours, but still fights what walks up to the gate and still runs
 * from what would kill it.
 *
 * <p>An off-duty NPC's order goal never starts, so a recruited villager that has
 * been stood down goes straight back to its own routine with nothing to
 * disentangle.
 */
public class NpcOrdersGoal extends Goal {

    /** Beyond this the follower jogs; inside it, it ambles. */
    private static final double HURRY_DISTANCE = 100.0D;
    /** Close enough to the owner to stop walking. */
    private static final double HEEL_DISTANCE = 9.0D;
    /** How far from its post a holding NPC will drift before returning. */
    private static final double POST_SLACK = 4.0D;

    private final NpcEntity npc;
    private Player owner;
    private int repathCooldown;
    private int patrolCooldown;

    public NpcOrdersGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Allegiance allegiance = this.npc.allegiance();
        if (!allegiance.stance().onDuty() || !allegiance.isOwned()) {
            return false;
        }
        this.owner = ownerPlayer();
        // HOLD and PATROL are posts, not escorts: they are kept whether or not
        // the owner is anywhere nearby, which is the entire point of leaving
        // somebody on a gate.
        return this.owner != null || allegiance.stance() != Allegiance.Stance.FOLLOW;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        this.owner = null;
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        switch (this.npc.allegiance().stance()) {
            case FOLLOW -> follow();
            case HOLD -> hold();
            case PATROL -> patrol();
            default -> {
            }
        }
    }

    private void follow() {
        if (this.owner == null) {
            return;
        }
        this.npc.getLookControl().setLookAt(this.owner, 10.0F, this.npc.getMaxHeadXRot());
        double distance = this.npc.distanceToSqr(this.owner);
        if (distance < HEEL_DISTANCE) {
            this.npc.getNavigation().stop();
            return;
        }
        if (--this.repathCooldown > 0) {
            return;
        }
        this.repathCooldown = 10;
        double speed = distance > HURRY_DISTANCE ? 1.15D : 0.85D;
        // Too far to walk back from, and the owner is somewhere loaded: step
        // across rather than trail hopelessly behind. Anything less makes a
        // follower useless the moment its owner rides or sprints away.
        if (distance > 900.0D && this.npc.level() instanceof ServerLevel) {
            teleportNearOwner();
            return;
        }
        this.npc.getNavigation().moveTo(this.owner, speed);
    }

    private void hold() {
        BlockPos post = post();
        if (this.npc.blockPosition().distSqr(post) <= POST_SLACK * POST_SLACK) {
            this.npc.getNavigation().stop();
            return;
        }
        if (--this.repathCooldown > 0) {
            return;
        }
        this.repathCooldown = 20;
        this.npc.getNavigation().moveTo(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D, 0.9D);
    }

    private void patrol() {
        if (--this.patrolCooldown > 0 || !this.npc.getNavigation().isDone()) {
            return;
        }
        this.patrolCooldown = 40 + this.npc.getRandom().nextInt(40);
        BlockPos post = post();
        // A circuit around the post rather than a straight there-and-back, so a
        // patrolling guard actually covers the ground around what it is guarding.
        double angle = this.npc.getRandom().nextDouble() * Math.PI * 2.0D;
        double radius = 4.0D + this.npc.getRandom().nextDouble() * 6.0D;
        double x = post.getX() + 0.5D + Math.cos(angle) * radius;
        double z = post.getZ() + 0.5D + Math.sin(angle) * radius;
        this.npc.getNavigation().moveTo(x, post.getY(), z, 0.75D);
    }

    /** Where the order is anchored: the given post, or where the NPC was standing. */
    private BlockPos post() {
        BlockPos post = this.npc.allegiance().post();
        if (post == null) {
            post = this.npc.blockPosition();
            this.npc.allegiance().setPost(post);
        }
        return post;
    }

    private void teleportNearOwner() {
        BlockPos target = this.owner.blockPosition();
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = target.getX() + this.npc.getRandom().nextInt(5) - 2;
            int z = target.getZ() + this.npc.getRandom().nextInt(5) - 2;
            BlockPos candidate = new BlockPos(x, target.getY(), z);
            if (this.npc.level().noCollision(this.npc,
                    this.npc.getBoundingBox().move(
                            candidate.getX() + 0.5D - this.npc.getX(),
                            candidate.getY() - this.npc.getY(),
                            candidate.getZ() + 0.5D - this.npc.getZ()))) {
                this.npc.moveTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D,
                        this.npc.getYRot(), this.npc.getXRot());
                this.npc.getNavigation().stop();
                return;
            }
        }
    }

    private Player ownerPlayer() {
        var id = this.npc.allegiance().owner();
        if (id == null || !(this.npc.level() instanceof ServerLevel level)) {
            return null;
        }
        Player player = level.getPlayerByUUID(id);
        return player != null && EntitySelector.NO_SPECTATORS.test(player) ? player : null;
    }
}
