package com.pokewing.pokeefnpc.net;

import com.pokewing.pokeefnpc.data.PlayerLedger;
import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.guild.GuildRank;
import com.pokewing.pokeefnpc.menu.MenuPage;
import com.pokewing.pokeefnpc.npc.Emotion;
import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.quest.Quest;
import com.pokewing.pokeefnpc.shop.ShopEntry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Everything a player can ask an NPC to do, and the one place any of it is
 * allowed to happen.
 *
 * <p>The rule this file exists to enforce: <b>the client asks, the server
 * decides</b>. Nothing here trusts a number that came off the wire. A purchase
 * re-derives the price from the NPC's live personality, the player's live
 * standing and their live guild rank rather than accepting the price the screen
 * was showing; a contract turn-in re-counts the items; contraband is re-checked
 * against trust. A tampered client can therefore ask for anything it likes and
 * still only get what the shopkeeper would actually have given it.
 */
public final class NpcActionPacket {

    /** What the player is asking for. */
    public enum Action {
        BUY, SELL, ACCEPT_QUEST, TURN_IN_QUEST, ABANDON_QUEST,
        DEPOSIT, WITHDRAW, REPAIR, HEAL, REST, ASK_EMOTE
    }

    private final int entityId;
    private final Action action;
    /** Index into the shop list, or an emote ordinal, depending on the action. */
    private final int index;
    /** How many, or how much. */
    private final int amount;
    /** The contract being acted on, for the quest actions. */
    private final UUID questId;

    public NpcActionPacket(int entityId, Action action, int index, int amount, UUID questId) {
        this.entityId = entityId;
        this.action = action;
        this.index = index;
        this.amount = amount;
        this.questId = questId;
    }

    public static NpcActionPacket shop(int entityId, Action action, int index, int amount) {
        return new NpcActionPacket(entityId, action, index, amount, new UUID(0L, 0L));
    }

    public static NpcActionPacket quest(int entityId, Action action, UUID questId) {
        return new NpcActionPacket(entityId, action, 0, 0, questId);
    }

    public static NpcActionPacket simple(int entityId, Action action, int amount) {
        return new NpcActionPacket(entityId, action, 0, amount, new UUID(0L, 0L));
    }

    public static void encode(NpcActionPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeVarInt(packet.action.ordinal());
        buf.writeVarInt(packet.index);
        buf.writeVarInt(packet.amount);
        buf.writeUUID(packet.questId);
    }

    public static NpcActionPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        Action[] actions = Action.values();
        Action action = actions[Math.floorMod(buf.readVarInt(), actions.length)];
        return new NpcActionPacket(entityId, action, buf.readVarInt(), buf.readVarInt(),
                buf.readUUID());
    }

    public static void handle(NpcActionPacket packet,
                              Supplier<net.minecraftforge.network.NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            var entity = player.level().getEntity(packet.entityId);
            if (!(entity instanceof NpcEntity npc)) {
                return;
            }
            // Reach check: the whole transaction is void if the player is not
            // actually stood in front of this NPC.
            if (npc.distanceToSqr(player) > 64.0D || !npc.isAlive()) {
                return;
            }
            if (!npc.reputation().willDealWith(player.getUUID())) {
                return;
            }
            packet.apply(npc, player);
        });
        ctx.setPacketHandled(true);
    }

    private void apply(NpcEntity npc, ServerPlayer player) {
        PlayerLedger ledger = PlayerLedger.get((ServerLevel) npc.level());
        switch (this.action) {
            case BUY -> buy(npc, player, ledger);
            case SELL -> sell(npc, player, ledger);
            case ACCEPT_QUEST -> acceptQuest(npc, player, ledger);
            case TURN_IN_QUEST -> turnInQuest(npc, player, ledger);
            case ABANDON_QUEST -> abandonQuest(npc, player, ledger);
            case DEPOSIT -> deposit(npc, player, ledger);
            case WITHDRAW -> withdraw(npc, player, ledger);
            case REPAIR -> repair(npc, player);
            case HEAL -> heal(npc, player);
            case REST -> rest(npc, player);
            case ASK_EMOTE -> askEmote(npc, player);
        }
    }

    /** The visible slate, rebuilt server-side — never the client's copy of it. */
    private List<ShopEntry> visibleStock(NpcEntity npc, ServerPlayer player, PlayerLedger ledger) {
        return npc.stock().visibleTo(npc.reputation(), player.getUUID(),
                ledger.rankOf(player.getUUID()).level());
    }

    private float priceMultiplier(NpcEntity npc, ServerPlayer player, PlayerLedger ledger) {
        return npc.reputation().priceMultiplier(player.getUUID())
                * ledger.rankOf(player.getUUID()).discount();
    }

    private void buy(NpcEntity npc, ServerPlayer player, PlayerLedger ledger) {
        List<ShopEntry> stock = visibleStock(npc, player, ledger);
        if (this.index < 0 || this.index >= stock.size()) {
            return;
        }
        ShopEntry entry = stock.get(this.index);
        if (entry.buying() || !entry.inStock()) {
            return;
        }
        int count = Math.max(1, Math.min(this.amount, 64));
        if (!entry.unlimited()) {
            count = Math.min(count, entry.stock());
        }
        int unitPrice = entry.priceFor(npc.personality().markup(),
                priceMultiplier(npc, player, ledger));
        int total = unitPrice * count;

        if (!takeEmeralds(player, total)) {
            npc.speak(player, "pokeefnpc.dialogue.no_money");
            npc.mood().latch(npc.personality(), Emotion.TIRED, 40);
            return;
        }
        ItemStack bought = entry.offer().copy();
        bought.setCount(bought.getCount() * count);
        giveOrDrop(player, bought);

        entry.take(count);
        npc.stock().addToPurse(total);
        // A sale is a good day, and being a regular is how standing is built.
        npc.reputation().adjust(player.getUUID(), Math.min(3.0F, total / 20.0F));
        npc.mood().latch(npc.personality(), Emotion.HAPPY, 80);
        npc.playEmote(Emote.WEIGH_COIN);
        resend(npc, player);
    }

    private void sell(NpcEntity npc, ServerPlayer player, PlayerLedger ledger) {
        List<ShopEntry> stock = visibleStock(npc, player, ledger);
        if (this.index < 0 || this.index >= stock.size()) {
            return;
        }
        ShopEntry entry = stock.get(this.index);
        if (!entry.buying()) {
            return;
        }
        int wanted = Math.max(1, Math.min(this.amount, 64));
        int held = countIn(player, entry.offer());
        int count = Math.min(wanted, held);
        if (count <= 0) {
            npc.speak(player, "pokeefnpc.dialogue.nothing_to_sell");
            return;
        }
        int unitPrice = entry.priceFor(npc.personality().markup(),
                priceMultiplier(npc, player, ledger));
        int total = unitPrice * count;

        // A shop can run out of coin, which is what keeps players from emptying
        // an entire economy into one villager.
        if (npc.stock().purse() < total) {
            count = npc.stock().purse() / Math.max(1, unitPrice);
            total = unitPrice * count;
            if (count <= 0) {
                npc.speak(player, "pokeefnpc.dialogue.shop_broke");
                npc.mood().latch(npc.personality(), Emotion.SAD, 60);
                return;
            }
        }
        removeFrom(player, entry.offer(), count);
        npc.stock().addToPurse(-total);
        giveOrDrop(player, new ItemStack(Items.EMERALD, total));
        npc.reputation().adjust(player.getUUID(), Math.min(2.0F, total / 25.0F));
        npc.mood().feel(npc.personality(), 0.2F, 0.1F);
        npc.playEmote(Emote.HAGGLE);
        resend(npc, player);
    }

    private void acceptQuest(NpcEntity npc, ServerPlayer player, PlayerLedger ledger) {
        Quest quest = npc.questBoard().byId(this.questId);
        if (quest == null) {
            return;
        }
        if (quest.requiredRank() > ledger.rankOf(player.getUUID()).level()) {
            npc.speak(player, "pokeefnpc.dialogue.rank_too_low");
            return;
        }
        if (!ledger.accept(player.getUUID(), quest, npc.getUUID())) {
            npc.speak(player, "pokeefnpc.dialogue.too_many_contracts");
            return;
        }
        npc.questBoard().remove(quest);
        npc.speak(player, "pokeefnpc.dialogue.quest_accepted");
        npc.mood().latch(npc.personality(), Emotion.FOCUSED, 60);
        npc.playEmote(Emote.NOD);
        resend(npc, player);
    }

    private void turnInQuest(NpcEntity npc, ServerPlayer player, PlayerLedger ledger) {
        var active = ledger.find(player.getUUID(), this.questId);
        if (active == null) {
            return;
        }
        Quest quest = active.quest;

        // A delivery has to go to somebody else — you cannot hand a parcel back
        // to the person who gave it to you.
        if (quest.type() == com.pokewing.pokeefnpc.quest.QuestType.DELIVER
                && npc.getUUID().equals(active.issuer)) {
            npc.speak(player, "pokeefnpc.dialogue.wrong_recipient");
            return;
        }

        if (quest.type().checkedAtTurnIn()) {
            int held = countIn(player, new ItemStack(quest.item()));
            if (held < quest.required()) {
                npc.speak(player, "pokeefnpc.dialogue.quest_incomplete");
                npc.mood().latch(npc.personality(), Emotion.TIRED, 40);
                return;
            }
            removeFrom(player, new ItemStack(quest.item()), quest.required());
        } else if (!active.complete()) {
            npc.speak(player, "pokeefnpc.dialogue.quest_incomplete");
            return;
        }

        giveOrDrop(player, new ItemStack(Items.EMERALD, quest.rewardEmeralds()));
        npc.reputation().adjust(player.getUUID(), quest.rewardReputation());
        GuildRank promoted = ledger.awardGuildPoints(player.getUUID(), quest.rewardGuildPoints());
        ledger.abandon(player.getUUID(), quest.id());

        npc.mood().latch(npc.personality(), Emotion.HAPPY);
        npc.playEmote(Emote.CLAP);
        npc.speak(player, "pokeefnpc.dialogue.quest_complete");
        if (promoted != null) {
            player.sendSystemMessage(Component.translatable("pokeefnpc.guild.promoted",
                    Component.translatable(promoted.translationKey())));
            npc.playEmote(Emote.CHEER);
        }
        resend(npc, player);
    }

    private void abandonQuest(NpcEntity npc, ServerPlayer player, PlayerLedger ledger) {
        if (ledger.find(player.getUUID(), this.questId) == null) {
            return;
        }
        ledger.abandon(player.getUUID(), this.questId);
        // Walking away from work costs you, and the NPC takes it personally.
        npc.reputation().adjust(player.getUUID(), -6.0F);
        npc.mood().latch(npc.personality(), Emotion.SAD, 80);
        npc.playEmote(Emote.SHAKE_HEAD);
        resend(npc, player);
    }

    private void deposit(NpcEntity npc, ServerPlayer player, PlayerLedger ledger) {
        if (!npc.offersPage(MenuPage.BANK)) {
            return;
        }
        int amount = Math.max(1, Math.min(this.amount, 4096));
        int held = countIn(player, new ItemStack(Items.EMERALD));
        amount = Math.min(amount, held);
        if (amount <= 0) {
            return;
        }
        removeFrom(player, new ItemStack(Items.EMERALD), amount);
        ledger.deposit(player.getUUID(), amount);
        npc.playEmote(Emote.WRITE);
        resend(npc, player);
    }

    private void withdraw(NpcEntity npc, ServerPlayer player, PlayerLedger ledger) {
        if (!npc.offersPage(MenuPage.BANK)) {
            return;
        }
        int taken = ledger.withdraw(player.getUUID(), Math.max(1, Math.min(this.amount, 4096)));
        if (taken > 0) {
            giveOrDrop(player, new ItemStack(Items.EMERALD, taken));
            npc.playEmote(Emote.WEIGH_COIN);
        }
        resend(npc, player);
    }

    private void repair(NpcEntity npc, ServerPlayer player) {
        if (!npc.offersPage(MenuPage.SMITHING)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !held.isDamaged()) {
            npc.speak(player, "pokeefnpc.dialogue.nothing_to_repair");
            return;
        }
        // Priced off the damage actually being mended, so a nicked sword is
        // cheap and a ruined one is not.
        int cost = Math.max(1, Math.round(held.getDamageValue() / 12.0F
                * npc.personality().markup()
                * npc.reputation().priceMultiplier(player.getUUID())));
        if (!takeEmeralds(player, cost)) {
            npc.speak(player, "pokeefnpc.dialogue.no_money");
            return;
        }
        held.setDamageValue(0);
        npc.stock().addToPurse(cost);
        npc.playEmote(Emote.HAMMER);
        npc.mood().latch(npc.personality(), Emotion.HAPPY, 60);
        npc.reputation().adjust(player.getUUID(), 2.0F);
        resend(npc, player);
    }

    private void heal(NpcEntity npc, ServerPlayer player) {
        if (!npc.offersPage(MenuPage.INFIRMARY)) {
            return;
        }
        int cost = Math.round(8.0F * npc.reputation().priceMultiplier(player.getUUID()));
        if (!takeEmeralds(player, cost)) {
            npc.speak(player, "pokeefnpc.dialogue.no_money");
            return;
        }
        player.heal(player.getMaxHealth());
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        npc.stock().addToPurse(cost);
        npc.playEmote(Emote.PRAY);
        npc.mood().latch(npc.personality(), Emotion.HAPPY, 80);
        resend(npc, player);
    }

    private void rest(NpcEntity npc, ServerPlayer player) {
        if (!npc.offersPage(MenuPage.LODGING)) {
            return;
        }
        int cost = Math.round(5.0F * npc.reputation().priceMultiplier(player.getUUID()));
        if (!takeEmeralds(player, cost)) {
            npc.speak(player, "pokeefnpc.dialogue.no_money");
            return;
        }
        player.getFoodData().eat(8, 1.0F);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 0));
        player.setRespawnPosition(player.level().dimension(), npc.blockPosition(),
                player.getYRot(), false, true);
        npc.stock().addToPurse(cost);
        npc.playEmote(Emote.DRINK);
        npc.reputation().adjust(player.getUUID(), 1.0F);
        resend(npc, player);
    }

    /** Asking an NPC to perform a gesture — free, and mostly for the theatre. */
    private void askEmote(NpcEntity npc, ServerPlayer player) {
        // Only a friendly NPC will perform on request. Being told to dance by
        // somebody it dislikes gets exactly the reaction it deserves.
        if (npc.reputation().standingOf(player.getUUID()).ordinal()
                < com.pokewing.pokeefnpc.npc.Reputation.Standing.FRIENDLY.ordinal()) {
            npc.playEmote(Emote.SHAKE_HEAD);
            npc.mood().latch(npc.personality(), Emotion.ANGRY, 40);
            return;
        }
        Emote emote = Emote.byOrdinal(this.index);
        npc.playEmote(emote);
        npc.mood().latch(npc.personality(), emote.emotion(), 80);
    }

    // ------------------------------------------------------------- inventory

    private static boolean takeEmeralds(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return true;
        }
        ItemStack emerald = new ItemStack(Items.EMERALD);
        if (countIn(player, emerald) < amount) {
            return false;
        }
        removeFrom(player, emerald, amount);
        return true;
    }

    private static int countIn(ServerPlayer player, ItemStack sample) {
        int total = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ItemStack.isSameItemSameTags(stack, sample)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void removeFrom(ServerPlayer player, ItemStack sample, int amount) {
        int remaining = amount;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!ItemStack.isSameItemSameTags(stack, sample)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
        inventory.setChanged();
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        // Stacks larger than one item's max have to be split, or a 200-emerald
        // reward silently becomes 64.
        while (!stack.isEmpty()) {
            int slice = Math.min(stack.getCount(), stack.getMaxStackSize());
            ItemStack part = stack.split(slice);
            if (!player.getInventory().add(part)) {
                player.drop(part, false);
            }
        }
    }

    /** Redraws the open window with whatever just changed. */
    private static void resend(NpcEntity npc, ServerPlayer player) {
        PokeEFNPCNetwork.sendTo(player, OpenNpcMenuPacket.of(npc, player));
    }
}
