package com.pokewing.pokeefnpc.npc;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Working out what an NPC should be wearing and carrying.
 *
 * <p>Throw a helmet at a soldier and it should put the helmet on; throw it at
 * one already wearing a better helmet and it should leave the better one on its
 * head. That is the whole job: score what is offered against what is worn, and
 * swap only when the offer is genuinely better.
 *
 * <p>Armour is scored by protection then toughness then enchantments, which is
 * the same order that decides whether a piece actually keeps somebody alive.
 * Weapons are scored by the damage they would do in this NPC's hands, so a
 * villager handed a diamond sword becomes dangerous with it rather than politely
 * holding it.
 */
public final class Gear {

    private Gear() {
    }

    /** The slot a stack belongs in, or null when it is not gear at all. */
    public static EquipmentSlot slotFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof ArmorItem armor) {
            return armor.getEquipmentSlot();
        }
        if (stack.getItem() instanceof ShieldItem) {
            return EquipmentSlot.OFFHAND;
        }
        return isWeapon(stack) ? EquipmentSlot.MAINHAND : null;
    }

    public static boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof DiggerItem
                || stack.is(Items.STICK);
    }

    /**
     * How much better the candidate is than what is worn now. Positive means
     * worth swapping; zero or less means leave it alone.
     */
    public static double upgrade(LivingEntity npc, ItemStack candidate) {
        EquipmentSlot slot = slotFor(candidate);
        if (slot == null) {
            return 0.0D;
        }
        return score(candidate) - score(npc.getItemBySlot(slot));
    }

    /**
     * A single number for a piece of gear, comparable only against gear for the
     * same slot.
     */
    public static double score(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        if (stack.getItem() instanceof ArmorItem armor) {
            double value = armor.getDefense() * 4.0D + armor.getToughness() * 2.0D;
            value += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION,
                    stack) * 1.5D;
            return value + condition(stack);
        }
        if (stack.getItem() instanceof ShieldItem) {
            return 6.0D + condition(stack);
        }
        if (isWeapon(stack)) {
            double value = attackDamage(stack) * 2.0D;
            value += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, stack);
            // A bow is worth carrying even though its melee damage is nil.
            if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem) {
                value = Math.max(value, 7.0D);
            }
            return value + condition(stack);
        }
        return 0.0D;
    }

    /**
     * Attack damage from the item's own attribute modifiers, so modded weapons
     * are rated by what they actually do rather than by a hard-coded list.
     */
    private static double attackDamage(ItemStack stack) {
        double damage = 0.0D;
        for (var entry : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).entries()) {
            if (entry.getKey() == net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) {
                damage += entry.getValue().getAmount();
            }
        }
        return damage;
    }

    /** A small tie-breaker so a fresh sword beats an identical worn-out one. */
    private static double condition(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() == 0) {
            return 0.5D;
        }
        return 0.5D * (1.0D - (double) stack.getDamageValue() / stack.getMaxDamage());
    }

    /**
     * Puts a stack on if it is an upgrade, and hands back whatever it replaced.
     *
     * <p>The replaced piece is returned rather than deleted or dropped here, so
     * the caller decides what happens to it — an NPC that takes your old iron
     * helmet should give you back the leather one, not destroy it.
     *
     * @return the displaced stack, or {@link ItemStack#EMPTY} when nothing was equipped
     */
    public static ItemStack equipIfBetter(Mob npc, ItemStack candidate) {
        EquipmentSlot slot = slotFor(candidate);
        if (slot == null || upgrade(npc, candidate) <= 0.0D) {
            return ItemStack.EMPTY;
        }
        ItemStack previous = npc.getItemBySlot(slot);
        ItemStack worn = candidate.copy();
        worn.setCount(1);
        npc.setItemSlot(slot, worn);
        // Gear given by a player belongs to that player: it drops on death so it
        // can be recovered, unlike the role's own issued kit.
        npc.setDropChance(slot, 1.0F);
        return previous;
    }
}
