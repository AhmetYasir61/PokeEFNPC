package com.pokewing.pokeefnpc.net;

import com.pokewing.pokeefnpc.data.PlayerLedger;
import com.pokewing.pokeefnpc.guild.GuildRank;
import com.pokewing.pokeefnpc.menu.MenuPage;
import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.npc.Reputation;
import com.pokewing.pokeefnpc.quest.Quest;
import com.pokewing.pokeefnpc.settlement.Settlement;
import com.pokewing.pokeefnpc.shop.ShopEntry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Everything the screen needs to draw itself as <i>this</i> NPC's window.
 *
 * <p>Sent alongside the container open, because none of it belongs in slots: the
 * tab strip is decided by the role, the prices are resolved per player from what
 * the shopkeeper thinks of them, and the contract list is filtered by guild rank.
 *
 * <p>The filtering happens <b>here, on the server</b>. A fence's off-book stock is
 * never written to a client that was not trusted with it, so the secrecy is real
 * rather than a matter of the interface declining to draw something it was
 * already given.
 */
public final class OpenNpcMenuPacket {

    private final int entityId;
    private final int roleOrdinal;
    private final int emotionOrdinal;
    private final int standingOrdinal;
    private final int guildPoints;
    private final int bankBalance;
    private final List<MenuPage> pages;
    private final List<ShopEntry> shop;
    private final List<Integer> shopPrices;
    private final List<Quest> quests;
    private final List<Integer> questProgress;

    /** Settlement summary, for roles that show the town's books. */
    private final String settlementName;
    private final int population;
    private final float defence;
    private final float threat;
    private final float prosperity;
    private final int foodStores;
    private final int materialStores;

    private OpenNpcMenuPacket(int entityId, int roleOrdinal, int emotionOrdinal, int standingOrdinal,
                              int guildPoints, int bankBalance, List<MenuPage> pages,
                              List<ShopEntry> shop, List<Integer> shopPrices,
                              List<Quest> quests, List<Integer> questProgress,
                              String settlementName, int population, float defence, float threat,
                              float prosperity, int foodStores, int materialStores) {
        this.entityId = entityId;
        this.roleOrdinal = roleOrdinal;
        this.emotionOrdinal = emotionOrdinal;
        this.standingOrdinal = standingOrdinal;
        this.guildPoints = guildPoints;
        this.bankBalance = bankBalance;
        this.pages = pages;
        this.shop = shop;
        this.shopPrices = shopPrices;
        this.quests = quests;
        this.questProgress = questProgress;
        this.settlementName = settlementName;
        this.population = population;
        this.defence = defence;
        this.threat = threat;
        this.prosperity = prosperity;
        this.foodStores = foodStores;
        this.materialStores = materialStores;
    }

    /** Assembles the packet for one NPC and one player. */
    public static OpenNpcMenuPacket of(NpcEntity npc, ServerPlayer player) {
        PlayerLedger ledger = PlayerLedger.get((ServerLevel) npc.level());
        GuildRank rank = ledger.rankOf(player.getUUID());
        Reputation reputation = npc.reputation();

        float markup = npc.personality().markup();
        float standingMultiplier = reputation.priceMultiplier(player.getUUID()) * rank.discount();

        List<ShopEntry> visible =
                npc.stock().visibleTo(reputation, player.getUUID(), rank.level());
        List<Integer> prices = new ArrayList<>(visible.size());
        for (ShopEntry entry : visible) {
            prices.add(entry.priceFor(markup, standingMultiplier));
        }

        List<Quest> offered = npc.questBoard().visibleTo(rank.level());
        List<Integer> progress = new ArrayList<>(offered.size());
        for (Quest quest : offered) {
            var active = ledger.find(player.getUUID(), quest.id());
            progress.add(active == null ? -1 : active.progress);
        }

        Settlement settlement = npc.settlement();
        return new OpenNpcMenuPacket(
                npc.getId(),
                npc.role().ordinal(),
                npc.displayedEmotion().ordinal(),
                reputation.standingOf(player.getUUID()).ordinal(),
                ledger.guildPointsOf(player.getUUID()),
                ledger.balanceOf(player.getUUID()),
                new ArrayList<>(npc.role().pages()),
                visible, prices, offered, progress,
                settlement == null ? "" : settlement.name(),
                settlement == null ? 0 : settlement.population(),
                settlement == null ? 0.0F : settlement.defence(),
                settlement == null ? 0.0F : settlement.threat(),
                settlement == null ? 0.0F : settlement.prosperity(),
                settlement == null ? 0 : settlement.foodStores(),
                settlement == null ? 0 : settlement.materialStores());
    }

    public static void encode(OpenNpcMenuPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeVarInt(packet.roleOrdinal);
        buf.writeVarInt(packet.emotionOrdinal);
        buf.writeVarInt(packet.standingOrdinal);
        buf.writeVarInt(packet.guildPoints);
        buf.writeVarInt(packet.bankBalance);

        buf.writeVarInt(packet.pages.size());
        for (MenuPage page : packet.pages) {
            buf.writeVarInt(page.ordinal());
        }

        buf.writeVarInt(packet.shop.size());
        for (int i = 0; i < packet.shop.size(); i++) {
            packet.shop.get(i).write(buf, packet.shopPrices.get(i));
        }

        buf.writeVarInt(packet.quests.size());
        for (int i = 0; i < packet.quests.size(); i++) {
            packet.quests.get(i).write(buf);
            buf.writeVarInt(packet.questProgress.get(i) + 1);
        }

        buf.writeUtf(packet.settlementName, 64);
        buf.writeVarInt(packet.population);
        buf.writeFloat(packet.defence);
        buf.writeFloat(packet.threat);
        buf.writeFloat(packet.prosperity);
        buf.writeVarInt(packet.foodStores);
        buf.writeVarInt(packet.materialStores);
    }

    public static OpenNpcMenuPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int role = buf.readVarInt();
        int emotion = buf.readVarInt();
        int standing = buf.readVarInt();
        int guildPoints = buf.readVarInt();
        int balance = buf.readVarInt();

        int pageCount = buf.readVarInt();
        List<MenuPage> pages = new ArrayList<>(pageCount);
        for (int i = 0; i < pageCount; i++) {
            pages.add(MenuPage.byOrdinal(buf.readVarInt()));
        }

        int shopCount = buf.readVarInt();
        List<ShopEntry> shop = new ArrayList<>(shopCount);
        List<Integer> prices = new ArrayList<>(shopCount);
        for (int i = 0; i < shopCount; i++) {
            ShopEntry entry = ShopEntry.read(buf);
            shop.add(entry);
            prices.add(entry.quotedPrice());
        }

        int questCount = buf.readVarInt();
        List<Quest> quests = new ArrayList<>(questCount);
        List<Integer> progress = new ArrayList<>(questCount);
        for (int i = 0; i < questCount; i++) {
            quests.add(Quest.read(buf));
            progress.add(buf.readVarInt() - 1);
        }

        return new OpenNpcMenuPacket(entityId, role, emotion, standing, guildPoints, balance,
                pages, shop, prices, quests, progress,
                buf.readUtf(64), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(OpenNpcMenuPacket packet,
                              Supplier<net.minecraftforge.network.NetworkEvent.Context> context) {
        // The client-only store is reached through DistExecutor rather than
        // called directly: naming a @OnlyIn(CLIENT) class from a method the
        // dedicated server also loads is how a packet handler ends up throwing
        // NoClassDefFoundError on a server that never renders anything.
        context.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.pokewing.pokeefnpc.client.screen.NpcMenuData.accept(
                        packet.entityId, packet.roleOrdinal, packet.emotionOrdinal,
                        packet.standingOrdinal, packet.guildPoints, packet.bankBalance,
                        packet.pages, packet.shop, packet.shopPrices, packet.quests,
                        packet.questProgress, packet.settlementName, packet.population,
                        packet.defence, packet.threat, packet.prosperity, packet.foodStores,
                        packet.materialStores)));
        context.get().setPacketHandled(true);
    }
}
