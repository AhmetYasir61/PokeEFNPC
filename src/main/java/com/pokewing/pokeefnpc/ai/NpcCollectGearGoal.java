package com.pokewing.pokeefnpc.ai;

import com.pokewing.pokeefnpc.npc.Gear;
import com.pokewing.pokeefnpc.npc.NpcEntity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Walks over to armour or a weapon lying on the ground and picks it up.
 *
 * <p>This is what makes kitting somebody out feel natural: you throw a helmet
 * and a sword on the floor in front of a soldier and they walk over, pick them
 * up and put them on, rather than needing a menu. Vanilla mobs do something
 * similar, but only within a stride and only on their own schedule — an NPC you
 * are deliberately equipping should cross the room for it.
 *
 * <p>Only gear that is actually an upgrade is worth walking to, so a fully
 * armoured guard ignores the pile of leather you dropped and a naked villager
 * does not.
 */
public class NpcCollectGearGoal extends Goal {

    private static final double SEARCH_RANGE = 12.0D;
    private static final int SEARCH_INTERVAL = 20;

    private final NpcEntity npc;
    private ItemEntity target;
    private int searchCooldown;

    public NpcCollectGearGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.npc.canPickUpLoot() || this.npc.isSleeping()) {
            return false;
        }
        if (--this.searchCooldown > 0) {
            return false;
        }
        this.searchCooldown = SEARCH_INTERVAL;
        this.target = bestNearbyGear();
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null && this.target.isAlive()
                && this.npc.distanceToSqr(this.target) < SEARCH_RANGE * SEARCH_RANGE * 2.0D;
    }

    @Override
    public void start() {
        this.npc.getNavigation().moveTo(this.target, 1.0D);
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
        this.npc.getLookControl().setLookAt(this.target,
                this.npc.getMaxHeadYRot(), this.npc.getMaxHeadXRot());
        if (this.npc.getNavigation().isDone()) {
            this.npc.getNavigation().moveTo(this.target, 1.0D);
        }
        // Vanilla's own pickup check does the actual taking once the NPC is
        // standing on it, which keeps one code path for "this item became mine"
        // whether it was walked to or simply landed at its feet.
    }

    private ItemEntity bestNearbyGear() {
        List<ItemEntity> nearby = this.npc.level().getEntitiesOfClass(ItemEntity.class,
                this.npc.getBoundingBox().inflate(SEARCH_RANGE),
                item -> item.isAlive() && !item.hasPickUpDelay()
                        && Gear.upgrade(this.npc, item.getItem()) > 0.0D);
        return nearby.stream()
                .max(Comparator.comparingDouble(item -> Gear.upgrade(this.npc, item.getItem())))
                .orElse(null);
    }
}
