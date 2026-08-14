package com.pokewing.pokeefnpc.item;

import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.npc.NpcNames;
import com.pokewing.pokeefnpc.npc.NpcRole;
import com.pokewing.pokeefnpc.settlement.Settlement;
import com.pokewing.pokeefnpc.settlement.SettlementManager;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The operator's tool — the second of the two ways people get into the world.
 *
 * <p>Villages populate themselves, but a builder laying out a keep or a city
 * wants to say <i>this</i> is the guild hall, <i>that</i> is the guard post, and
 * the figure by the door is a captain and stays a captain. This does all of that
 * with one item and no commands:
 *
 * <ul>
 *   <li><b>Right-click in the air</b> — cycle what the tool is currently doing.</li>
 *   <li><b>Right-click an NPC</b> in ROLE mode — set its role and lock it, so
 *       nothing will ever re-roll it.</li>
 *   <li><b>Sneak + right-click an NPC</b> — cycle which role the tool assigns.</li>
 *   <li><b>Right-click a block</b> in a landmark mode — mark the meeting point,
 *       guard post, shrine, training ground or hideout for the settlement that
 *       covers it.</li>
 *   <li><b>Right-click a block</b> in FOUND mode — found a settlement there, so a
 *       hand-built town becomes a real place with a population and defences.</li>
 * </ul>
 *
 * <p>Restricted to creative mode and operators: this rewrites world state, and
 * nothing here should be reachable in survival.
 */
public class NpcEditorItem extends Item {

    /** What the tool does on the next click. */
    public enum Mode {
        ROLE("role"),
        MEETING("meeting"),
        GUARD_POST("guard_post"),
        SHRINE("shrine"),
        TRAINING("training"),
        HIDEOUT("hideout"),
        FOUND("found");

        private final String key;

        Mode(String key) {
            this.key = key;
        }

        public String translationKey() {
            return "pokeefnpc.editor.mode." + this.key;
        }

        /** The settlement landmark this mode writes, or null when it writes none. */
        @Nullable
        public String landmark() {
            return switch (this) {
                case MEETING -> "meeting";
                case GUARD_POST -> "guard_post";
                case SHRINE -> "shrine";
                case TRAINING -> "training";
                case HIDEOUT -> "hideout";
                default -> null;
            };
        }
    }

    private static final String TAG_MODE = "EditorMode";
    private static final String TAG_ROLE = "EditorRole";

    public NpcEditorItem(Properties properties) {
        super(properties);
    }

    private static Mode modeOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        Mode[] values = Mode.values();
        return tag == null ? Mode.ROLE : values[Math.floorMod(tag.getInt(TAG_MODE), values.length)];
    }

    private static NpcRole roleOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? NpcRole.VILLAGER : NpcRole.byOrdinal(tag.getInt(TAG_ROLE));
    }

    private static void setMode(ItemStack stack, Mode mode) {
        stack.getOrCreateTag().putInt(TAG_MODE, mode.ordinal());
    }

    private static void setRole(ItemStack stack, NpcRole role) {
        stack.getOrCreateTag().putInt(TAG_ROLE, role.ordinal());
    }

    private static boolean permitted(Player player) {
        return player.isCreative() || player.hasPermissions(2);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!permitted(player)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        Mode[] values = Mode.values();
        Mode next = values[(modeOf(stack).ordinal() + 1) % values.length];
        setMode(stack, next);
        player.displayClientMessage(Component.translatable("pokeefnpc.editor.mode_set",
                Component.translatable(next.translationKey())), true);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!permitted(player) || !(target instanceof NpcEntity npc)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            NpcRole[] values = NpcRole.values();
            NpcRole next = values[(roleOf(stack).ordinal() + 1) % values.length];
            setRole(stack, next);
            player.displayClientMessage(Component.translatable("pokeefnpc.editor.role_selected",
                    Component.translatable(next.translationKey())), true);
            return InteractionResult.SUCCESS;
        }

        NpcRole role = roleOf(stack);
        npc.applyRole(role, true);
        // Locked, so nothing — not a respawn, not the settlement's own demography —
        // will ever change what an operator placed on purpose.
        npc.setLocked(true);
        npc.setCustomName(Component.literal(NpcNames.pick(npc.getRandom(), role)));
        npc.joinNearestSettlement();
        player.displayClientMessage(Component.translatable("pokeefnpc.editor.role_applied",
                npc.getName(), Component.translatable(role.translationKey())), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !permitted(player)) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        Mode mode = modeOf(stack);
        var pos = context.getClickedPos().above();
        SettlementManager manager = SettlementManager.get(level);

        if (mode == Mode.FOUND) {
            Settlement existing = manager.at(pos);
            if (existing != null) {
                player.displayClientMessage(Component.translatable(
                        "pokeefnpc.editor.already_settled", existing.name()), true);
                return InteractionResult.SUCCESS;
            }
            Settlement founded = manager.found(level, pos, "Outpost");
            player.displayClientMessage(Component.translatable("pokeefnpc.editor.founded",
                    founded.name()), true);
            return InteractionResult.SUCCESS;
        }

        String landmark = mode.landmark();
        if (landmark == null) {
            player.displayClientMessage(
                    Component.translatable("pokeefnpc.editor.wrong_mode"), true);
            return InteractionResult.SUCCESS;
        }
        Settlement settlement = manager.at(pos);
        if (settlement == null) {
            settlement = manager.nearest(pos, 128);
        }
        if (settlement == null) {
            player.displayClientMessage(
                    Component.translatable("pokeefnpc.editor.no_settlement"), true);
            return InteractionResult.SUCCESS;
        }
        settlement.setLandmark(landmark, pos);
        // A landmark outside the current bounds means the place has grown.
        if (!settlement.contains(pos)) {
            settlement.setRadius((int) Math.sqrt(settlement.center().distSqr(pos)) + 16);
        }
        manager.setDirty();
        player.displayClientMessage(Component.translatable("pokeefnpc.editor.landmark_set",
                Component.translatable(mode.translationKey()), settlement.name()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("pokeefnpc.editor.tooltip.mode",
                Component.translatable(modeOf(stack).translationKey())
                        .withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("pokeefnpc.editor.tooltip.role",
                Component.translatable(roleOf(stack).translationKey())
                        .withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("pokeefnpc.editor.tooltip.help")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
