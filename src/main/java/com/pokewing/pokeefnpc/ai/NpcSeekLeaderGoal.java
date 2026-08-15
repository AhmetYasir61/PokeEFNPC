package com.pokewing.pokeefnpc.ai;

import com.pokewing.pokeefnpc.npc.NpcEntity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * A regent looking for somebody to serve.
 *
 * <p>When a leader is gone for good, one of their own is raised to stand in
 * their place, and this is what that one does: walks up to people, sizes them
 * up, and waits to be taken into service. It does not swear itself to the first
 * stranger it sees — the player still has to offer the commission — but it makes
 * itself found, which is the difference between a leaderless garrison quietly
 * rotting and one that comes looking for a new banner.
 *
 * <p>Only the regent does this. Everybody else went back to their own lives the
 * moment the succession happened, which is the point of having a regent at all.
 */
public class NpcSeekLeaderGoal extends Goal {

    private static final double SEARCH_RANGE = 48.0D;
    /** Close enough to be noticed and spoken to. */
    private static final double APPROACH_DISTANCE = 3.5D;

    private final NpcEntity npc;
    private Player candidate;
    private int cooldown;
    private int pitchCooldown;

    public NpcSeekLeaderGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.npc.isRegent() || this.npc.allegiance().isOwned()) {
            return false;
        }
        if (--this.cooldown > 0) {
            return false;
        }
        this.cooldown = 40;
        this.candidate = this.npc.level().getNearestPlayer(this.npc, SEARCH_RANGE);
        return this.candidate != null && this.candidate.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.npc.isRegent() && !this.npc.allegiance().isOwned()
                && this.candidate != null && this.candidate.isAlive()
                && this.npc.distanceToSqr(this.candidate) < SEARCH_RANGE * SEARCH_RANGE;
    }

    @Override
    public void stop() {
        this.candidate = null;
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.candidate == null) {
            return;
        }
        this.npc.getLookControl().setLookAt(this.candidate, 30.0F, 30.0F);
        double distance = this.npc.distanceToSqr(this.candidate);
        if (distance > APPROACH_DISTANCE * APPROACH_DISTANCE) {
            if (this.npc.getNavigation().isDone()) {
                this.npc.getNavigation().moveTo(this.candidate, 0.9D);
            }
            return;
        }
        this.npc.getNavigation().stop();
        if (--this.pitchCooldown > 0) {
            return;
        }
        // Said out loud, and not often: a regent that repeats its offer every
        // tick is a nuisance rather than a character.
        this.pitchCooldown = 200;
        this.npc.offerService(this.candidate);
    }
}
