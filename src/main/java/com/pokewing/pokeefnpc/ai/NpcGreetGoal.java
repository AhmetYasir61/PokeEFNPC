package com.pokewing.pokeefnpc.ai;

import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.npc.Emotion;
import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.npc.Reputation;
import com.pokewing.pokeefnpc.schedule.Activity;

import net.minecraft.world.entity.player.Player;

import net.minecraft.world.entity.ai.goal.Goal;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Noticing people.
 *
 * <p>An NPC that walks past a player without reacting is scenery. This is the
 * goal that makes a village feel like it knows who you are: it looks up, and
 * what it does next comes entirely out of {@link Reputation} — the innkeeper you
 * have been buying from waves, the guard whose friend you killed folds its arms
 * and stares, and the one who hates you turns its back.
 *
 * <p>Greetings are remembered per player with a long cooldown, so walking in and
 * out of a shop does not produce a wall of waving.
 */
public class NpcGreetGoal extends Goal {

    private static final double NOTICE_RANGE = 8.0D;
    private static final int GREET_COOLDOWN = 1200;
    private static final int LOOK_TICKS = 60;

    private final NpcEntity npc;
    private final Map<UUID, Long> lastGreeted = new HashMap<>();

    @Nullable
    private Player subject;
    private int ticksLeft;

    public NpcGreetGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.npc.getTarget() != null || this.npc.activity() == Activity.SLEEP) {
            return false;
        }
        // Antisocial characters mostly do not bother, and that reticence is a
        // visible difference between a bard and an executioner.
        if (this.npc.getRandom().nextFloat() > this.npc.personality().sociability) {
            return false;
        }
        Player nearest = this.npc.level().getNearestPlayer(this.npc, NOTICE_RANGE);
        if (nearest == null || !nearest.isAlive() || nearest.isSpectator()) {
            return false;
        }
        long now = this.npc.level().getGameTime();
        Long last = this.lastGreeted.get(nearest.getUUID());
        if (last != null && now - last < GREET_COOLDOWN) {
            return false;
        }
        if (!this.npc.hasLineOfSight(nearest)) {
            return false;
        }
        this.subject = nearest;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.ticksLeft > 0 && this.subject != null && this.subject.isAlive()
                && this.npc.distanceToSqr(this.subject) < NOTICE_RANGE * NOTICE_RANGE * 2.0D;
    }

    @Override
    public void start() {
        if (this.subject == null) {
            return;
        }
        this.ticksLeft = LOOK_TICKS;
        this.lastGreeted.put(this.subject.getUUID(), this.npc.level().getGameTime());

        Reputation.Standing standing = this.npc.reputation().standingOf(this.subject.getUUID());
        switch (standing) {
            case REVERED, TRUSTED -> {
                this.npc.mood().latch(this.npc.personality(), Emotion.HAPPY);
                this.npc.playEmote(this.npc.role().greetEmote());
            }
            case FRIENDLY -> {
                this.npc.mood().feel(this.npc.personality(), 0.25F, 0.15F);
                this.npc.playEmote(Emote.NOD);
            }
            case NEUTRAL -> {
                if (this.npc.personality().sociability > 0.6F) {
                    this.npc.playEmote(Emote.NOD);
                }
            }
            case WARY -> {
                this.npc.mood().latch(this.npc.personality(), Emotion.FOCUSED, 80);
                this.npc.playEmote(Emote.CROSS_ARMS);
            }
            case HOSTILE -> {
                this.npc.mood().latch(this.npc.personality(), Emotion.ANGRY, 120);
                this.npc.playEmote(Emote.JEER);
            }
            case HATED -> {
                this.npc.mood().latch(this.npc.personality(), Emotion.ANGRY);
                // The brave square up; everyone else backs off and lets the flee
                // goal take over from here.
                if (this.npc.role().isDefender() && this.npc.personality().courage > 0.6F) {
                    this.npc.playEmote(Emote.TAUNT);
                    this.npc.setTarget(this.subject);
                } else {
                    this.npc.playEmote(Emote.SHAKE_HEAD);
                }
            }
        }
    }

    @Override
    public void tick() {
        this.ticksLeft--;
        if (this.subject != null) {
            this.npc.getLookControl().setLookAt(this.subject, 30.0F, 30.0F);
        }
    }

    @Override
    public void stop() {
        this.subject = null;
        this.ticksLeft = 0;
        // The map is per-NPC and bounded by how many players it has ever met; a
        // trim keeps a long-lived server NPC from carrying every visitor forever.
        if (this.lastGreeted.size() > 64) {
            long now = this.npc.level().getGameTime();
            this.lastGreeted.values().removeIf(time -> now - time > GREET_COOLDOWN * 20L);
        }
    }
}
