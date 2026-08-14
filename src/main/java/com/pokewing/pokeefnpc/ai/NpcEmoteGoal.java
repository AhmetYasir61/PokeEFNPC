package com.pokewing.pokeefnpc.ai;

import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.npc.Emotion;
import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.schedule.Activity;

import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;

/**
 * Idle gestures — the part that makes a still village look alive rather than
 * paused.
 *
 * <p>The schedule goal already puts a looping work gesture on an NPC that has
 * arrived somewhere. This fills the rest: the NPC standing in the square with
 * nothing to do stretches, folds its arms, thinks, waves at a neighbour. It picks
 * from the role's own repertoire, so the mix reads as character.
 *
 * <p>Two rules keep it from turning into a puppet show. Gestures are spaced by a
 * long, randomised cooldown, and an NPC in a strong mood only plays gestures that
 * suit it — a frightened or furious character does not stretch and yawn.
 */
public class NpcEmoteGoal extends Goal {

    private static final int MIN_COOLDOWN = 120;
    private static final int MAX_COOLDOWN = 500;

    private final NpcEntity npc;
    private int cooldown;

    public NpcEmoteGoal(NpcEntity npc) {
        this.npc = npc;
        // No movement or look flag: idling is something an NPC does *while*
        // standing where the schedule put it, not instead of it.
        setFlags(EnumSet.noneOf(Flag.class));
        this.cooldown = npc.getRandom().nextInt(MAX_COOLDOWN);
    }

    @Override
    public boolean canUse() {
        if (this.npc.getTarget() != null) {
            return false;
        }
        Activity activity = this.npc.activity();
        if (activity == Activity.SLEEP || activity == Activity.EMERGENCY) {
            return false;
        }
        // Do not step on a work loop that is already running.
        if (this.npc.currentEmote() != null && this.npc.isEmoteLooping()
                && activity == Activity.WORK) {
            return false;
        }
        return --this.cooldown <= 0;
    }

    @Override
    public void start() {
        this.cooldown = MIN_COOLDOWN
                + this.npc.getRandom().nextInt(MAX_COOLDOWN - MIN_COOLDOWN);
        Emote chosen = pick();
        if (chosen == null) {
            return;
        }
        // A gesture the NPC has settled into is looped; a reaction is fired once.
        // That distinction is the whole difference between "the smith is working"
        // and "the smith just waved at you".
        if (chosen.looping() && idleEnoughToSettle()) {
            this.npc.loopEmote(chosen);
        } else {
            this.npc.playEmote(chosen);
        }
    }

    private boolean idleEnoughToSettle() {
        return this.npc.getNavigation().isDone();
    }

    /**
     * Chooses a gesture that suits both the role and the current mood. Mood wins
     * where the two disagree: an angry blacksmith folds its arms and glares
     * instead of cheerfully hammering.
     */
    private Emote pick() {
        Emotion emotion = this.npc.mood().current();
        float intensity = this.npc.mood().intensity();

        if (intensity > 0.55F) {
            Emote moodEmote = switch (emotion) {
                case ANGRY -> this.npc.getRandom().nextBoolean() ? Emote.JEER : Emote.CROSS_ARMS;
                case SAD -> this.npc.getRandom().nextBoolean() ? Emote.WEEP : Emote.SHAKE_HEAD;
                case HAPPY -> this.npc.getRandom().nextBoolean() ? Emote.CHEER : Emote.CLAP;
                case TIRED -> Emote.STRETCH;
                case SURPRISED -> Emote.POINT;
                case HURT -> Emote.SHIVER;
                default -> null;
            };
            if (moodEmote != null) {
                return moodEmote;
            }
        }

        List<Emote> repertoire = this.npc.role().idleEmotes();
        if (repertoire.isEmpty()) {
            return null;
        }
        return repertoire.get(this.npc.getRandom().nextInt(repertoire.size()));
    }
}
