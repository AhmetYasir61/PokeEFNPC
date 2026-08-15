package com.pokewing.pokeefnpc.item;

import com.pokewing.pokeefnpc.npc.Allegiance;
import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.npc.NpcRole;
import com.pokewing.pokeefnpc.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Places a soldier already sworn to you — and promotes villagers who are willing.
 *
 * <p>Two uses, deliberately the same item. Used on the ground it places a fighter
 * that is yours from the first tick. Used on an NPC that already exists, it
 * offers that NPC the commission: a villager who trusts you enough takes it and
 * becomes your soldier, keeping the gear you gave it and everything it knows
 * about you. One that does not trust you refuses, and says so.
 *
 * <p>Promotion is the interesting half. It means a population you have actually
 * lived alongside can turn into a garrison — the smith you have traded with for
 * a month, not an anonymous unit you spawned.
 */
public class SoldierEggItem extends Item {

    /** What a promoted or placed soldier becomes. */
    private final NpcRole role;

    public SoldierEggItem(NpcRole role, Properties properties) {
        super(properties);
        this.role = role;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        NpcEntity npc = ModEntities.NPC.get().create(serverLevel);
        if (npc == null) {
            return InteractionResult.PASS;
        }
        npc.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                player.getYRot(), 0.0F);
        npc.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(pos),
                MobSpawnType.SPAWN_EGG, null, null);
        npc.applyRole(this.role, true);
        npc.enlist(player, this.role);
        // Placed rather than persuaded: it stands where you put it instead of
        // trotting after you, which is what an operator laying out a garrison
        // actually wants.
        npc.setStance(Allegiance.Stance.HOLD);
        serverLevel.addFreshEntity(npc);

        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(target instanceof NpcEntity npc) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (npc.allegiance().isOwned()) {
            npc.speak(serverPlayer, npc.allegiance().isOwnedBy(player.getUUID())
                    ? "pokeefnpc.dialogue.already_yours"
                    : "pokeefnpc.dialogue.sworn_elsewhere");
            return InteractionResult.CONSUME;
        }
        if (!npc.willEnlistWith(player)) {
            // Not a refusal to be argued with — trust is earned by trading,
            // gifts and not hitting people, and the NPC says as much.
            npc.speak(serverPlayer, "pokeefnpc.dialogue.enlist_refused");
            return InteractionResult.CONSUME;
        }
        npc.enlist(serverPlayer, this.role);
        npc.speak(serverPlayer, "pokeefnpc.dialogue.enlist_accepted");
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("pokeefnpc.tooltip.soldier_egg")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("pokeefnpc.tooltip.soldier_egg_orders")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
