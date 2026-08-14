package com.pokewing.pokeefnpc.ai;

import com.pokewing.pokeefnpc.PokeEFNPCConfig;
import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.npc.Emotion;
import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.schedule.Activity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * The thief's trade.
 *
 * <p>A thief sidles up to a mark, lifts something, and walks away as though
 * nothing happened. The interesting part is what happens when it goes wrong:
 * every NPC that can see it loses respect for the thief, guards are told, and the
 * thief's own face gives it away — {@link Emotion#SURPRISED} the instant it is
 * spotted.
 *
 * <p>What can actually be taken is deliberately narrow and configurable: never
 * from a player's hands or armour, only a single stack from the open inventory,
 * and only when {@code stealFromPlayers} is on. Servers that do not want NPCs
 * touching player inventories turn it off and the thief still prowls, whispers
 * and fences goods — it just steals from settlement stores instead.
 */
public class NpcStealGoal extends Goal {

    private static final double APPROACH_RANGE = 10.0D;
    private static final double STEAL_RANGE = 2.0D;
    private static final int COOLDOWN = 600;

    private final NpcEntity npc;

    @Nullable
    private Player mark;
    private int cooldown;
    private int approachTicks;

    public NpcStealGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        this.cooldown = npc.getRandom().nextInt(COOLDOWN);
    }

    @Override
    public boolean canUse() {
        if (!this.npc.role().isOutlaw() || this.npc.getTarget() != null) {
            return false;
        }
        Activity activity = this.npc.activity();
        if (activity != Activity.PROWL && activity != Activity.SMUGGLE) {
            return false;
        }
        if (--this.cooldown > 0) {
            return false;
        }
        // Greed decides how often it is worth the risk at all.
        if (this.npc.getRandom().nextFloat() > this.npc.personality().greed) {
            this.cooldown = COOLDOWN / 2;
            return false;
        }
        Player nearest = this.npc.level().getNearestPlayer(this.npc, APPROACH_RANGE);
        if (nearest == null || nearest.isCreative() || nearest.isSpectator() || !nearest.isAlive()) {
            return false;
        }
        this.mark = nearest;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.mark != null && this.mark.isAlive() && this.approachTicks < 200
                && this.npc.distanceToSqr(this.mark) < APPROACH_RANGE * APPROACH_RANGE * 2.0D;
    }

    @Override
    public void start() {
        this.approachTicks = 0;
        this.cooldown = COOLDOWN;
        this.npc.loopEmote(Emote.SKULK);
    }

    @Override
    public void tick() {
        if (this.mark == null) {
            return;
        }
        this.approachTicks++;
        this.npc.getLookControl().setLookAt(this.mark, 30.0F, 30.0F);

        if (this.npc.distanceToSqr(this.mark) > STEAL_RANGE * STEAL_RANGE) {
            this.npc.getNavigation().moveTo(this.mark, 0.8D);
            return;
        }
        this.npc.getNavigation().stop();
        attemptTheft();
        this.mark = null;
    }

    private void attemptTheft() {
        if (this.mark == null) {
            return;
        }
        this.npc.playEmote(Emote.PICKPOCKET);

        // Caught? Being watched is the risk, and a player who is looking straight
        // at the thief is much harder to rob.
        boolean facing = isLookingAt(this.mark);
        float skill = this.npc.personality().greed * 0.5F + this.npc.personality().courage * 0.3F;
        boolean success = !facing && this.npc.getRandom().nextFloat() < skill;

        if (!success) {
            caught();
            return;
        }
        if (PokeEFNPCConfig.stealFromPlayers()) {
            liftFromInventory(this.mark);
        } else {
            // Stores instead of pockets — the settlement is poorer either way.
            var settlement = this.npc.settlement();
            if (settlement != null) {
                settlement.addMaterials(-4);
                settlement.addFood(-4);
            }
        }
        this.npc.mood().latch(this.npc.personality(), Emotion.HAPPY, 100);
    }

    private void liftFromInventory(Player player) {
        var inventory = player.getInventory();
        // Only the main inventory: never the hotbar the player is using, never
        // armour, never the offhand.
        for (int attempt = 0; attempt < 8; attempt++) {
            int slot = 9 + this.npc.getRandom().nextInt(27);
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int taken = Math.min(stack.getCount(), 1 + this.npc.getRandom().nextInt(3));
            ItemStack lifted = stack.split(taken);
            inventory.setChanged();
            this.npc.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, lifted);
            return;
        }
    }

    private void caught() {
        this.npc.mood().latch(this.npc.personality(), Emotion.SURPRISED, 60);
        this.npc.playEmote(Emote.SHIVER);
        if (this.mark == null) {
            return;
        }
        // The mark and everyone watching both take it badly, and the guards will
        // now have a target.
        this.npc.reputation().adjust(this.mark.getUUID(), -20.0F);
        List<NpcEntity> witnesses = this.npc.level().getEntitiesOfClass(NpcEntity.class,
                this.npc.getBoundingBox().inflate(16.0D));
        for (NpcEntity witness : witnesses) {
            if (witness == this.npc) {
                continue;
            }
            witness.mood().latch(witness.personality(), Emotion.ANGRY, 120);
            if (witness.role().isDefender()) {
                witness.setTarget(this.npc);
            }
        }
    }

    private boolean isLookingAt(Player player) {
        var look = player.getViewVector(1.0F).normalize();
        var toNpc = this.npc.position().subtract(player.getEyePosition()).normalize();
        return look.dot(toNpc) > 0.6D;
    }

    @Override
    public void stop() {
        this.mark = null;
        this.approachTicks = 0;
        this.npc.stopEmote();
        this.npc.getNavigation().stop();
    }
}
