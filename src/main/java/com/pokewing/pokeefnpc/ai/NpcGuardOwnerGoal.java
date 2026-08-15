package com.pokewing.pokeefnpc.ai;

import com.pokewing.pokeefnpc.npc.NpcEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Makes a sworn NPC fight whoever is fighting its owner.
 *
 * <p>Both directions matter. Something that hits your soldier's owner becomes
 * the soldier's target, and something the owner has picked a fight with becomes
 * the soldier's target too — a bodyguard that only reacts after you are already
 * being hit is not much of a bodyguard.
 *
 * <p>Two things it will not do: turn on its own owner, and turn on another NPC
 * sworn to the same owner. Without those checks one stray arrow between two of
 * your own soldiers turns your garrison into a brawl.
 */
public class NpcGuardOwnerGoal extends TargetGoal {

    private final NpcEntity npc;
    private LivingEntity quarry;
    /** The owner's revenge/attack timestamp last acted on, so it fires once. */
    private int lastSeenTimestamp;

    public NpcGuardOwnerGoal(NpcEntity npc) {
        super(npc, false);
        this.npc = npc;
        setFlags(java.util.EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!this.npc.allegiance().stance().onDuty()) {
            return false;
        }
        Player owner = owner();
        if (owner == null) {
            return false;
        }
        LivingEntity attacker = owner.getLastHurtByMob();
        LivingEntity victim = owner.getLastHurtMob();
        int attackerAt = owner.getLastHurtByMobTimestamp();
        int victimAt = owner.getLastHurtMobTimestamp();

        LivingEntity candidate = null;
        if (attacker != null && attackerAt != this.lastSeenTimestamp) {
            candidate = attacker;
            this.lastSeenTimestamp = attackerAt;
        } else if (victim != null && victimAt != this.lastSeenTimestamp) {
            candidate = victim;
            this.lastSeenTimestamp = victimAt;
        }
        if (candidate == null || !worthAttacking(candidate)) {
            return false;
        }
        this.quarry = candidate;
        return canAttack(this.quarry, TargetingConditions.DEFAULT);
    }

    @Override
    public void start() {
        this.mob.setTarget(this.quarry);
        super.start();
    }

    /** Never the owner, and never a comrade sworn to the same person. */
    private boolean worthAttacking(LivingEntity candidate) {
        if (candidate == this.npc || !candidate.isAlive()) {
            return false;
        }
        var ownerId = this.npc.allegiance().owner();
        if (candidate instanceof Player player && player.getUUID().equals(ownerId)) {
            return false;
        }
        return !(candidate instanceof NpcEntity other)
                || !other.allegiance().isOwnedBy(ownerId);
    }

    private Player owner() {
        var id = this.npc.allegiance().owner();
        if (id == null || !(this.npc.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getPlayerByUUID(id);
    }
}
