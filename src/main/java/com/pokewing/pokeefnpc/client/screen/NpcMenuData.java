package com.pokewing.pokeefnpc.client.screen;

import com.pokewing.pokeefnpc.guild.GuildRank;
import com.pokewing.pokeefnpc.menu.MenuPage;
import com.pokewing.pokeefnpc.npc.Emotion;
import com.pokewing.pokeefnpc.npc.NpcRole;
import com.pokewing.pokeefnpc.npc.Reputation;
import com.pokewing.pokeefnpc.quest.Quest;
import com.pokewing.pokeefnpc.shop.ShopEntry;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * The client's copy of whatever the server last told it about the open NPC.
 *
 * <p>A static holder rather than menu state, because the window is opened by the
 * container and filled by a packet that arrives a moment later — and refilled
 * every time a transaction changes something. The screen reads from here on every
 * frame, so a purchase updates the shelf, the purse and the standing without the
 * window being reopened.
 */
@OnlyIn(Dist.CLIENT)
public final class NpcMenuData {

    private static int entityId = -1;
    private static NpcRole role = NpcRole.VILLAGER;
    private static Emotion emotion = Emotion.NEUTRAL;
    private static Reputation.Standing standing = Reputation.Standing.NEUTRAL;
    private static int guildPoints;
    private static int bankBalance;
    private static List<MenuPage> pages = new ArrayList<>();
    private static List<ShopEntry> shop = new ArrayList<>();
    private static List<Integer> prices = new ArrayList<>();
    private static List<Quest> quests = new ArrayList<>();
    private static List<Integer> questProgress = new ArrayList<>();

    private static String settlementName = "";
    private static int population;
    private static float defence;
    private static float threat;
    private static float prosperity;
    private static int foodStores;
    private static int materialStores;

    /** Bumped on every packet, so the screen knows to rebuild its widgets. */
    private static int revision;

    private NpcMenuData() {
    }

    public static void accept(int newEntityId, int roleOrdinal, int emotionOrdinal,
                              int standingOrdinal, int newGuildPoints, int newBankBalance,
                              List<MenuPage> newPages, List<ShopEntry> newShop,
                              List<Integer> newPrices, List<Quest> newQuests,
                              List<Integer> newProgress, String newSettlementName,
                              int newPopulation, float newDefence, float newThreat,
                              float newProsperity, int newFood, int newMaterials) {
        entityId = newEntityId;
        role = NpcRole.byOrdinal(roleOrdinal);
        emotion = Emotion.byOrdinal(emotionOrdinal);
        Reputation.Standing[] standings = Reputation.Standing.values();
        standing = standings[Math.floorMod(standingOrdinal, standings.length)];
        guildPoints = newGuildPoints;
        bankBalance = newBankBalance;
        pages = newPages;
        shop = newShop;
        prices = newPrices;
        quests = newQuests;
        questProgress = newProgress;
        settlementName = newSettlementName;
        population = newPopulation;
        defence = newDefence;
        threat = newThreat;
        prosperity = newProsperity;
        foodStores = newFood;
        materialStores = newMaterials;
        revision++;
    }

    public static int entityId() {
        return entityId;
    }

    public static NpcRole role() {
        return role;
    }

    public static Emotion emotion() {
        return emotion;
    }

    public static Reputation.Standing standing() {
        return standing;
    }

    public static int guildPoints() {
        return guildPoints;
    }

    public static GuildRank rank() {
        return GuildRank.forPoints(guildPoints);
    }

    public static int bankBalance() {
        return bankBalance;
    }

    public static List<MenuPage> pages() {
        return pages;
    }

    public static List<ShopEntry> shop() {
        return shop;
    }

    public static int priceAt(int index) {
        return index >= 0 && index < prices.size() ? prices.get(index) : 0;
    }

    public static List<Quest> quests() {
        return quests;
    }

    /** Progress on a contract the player already holds, or -1 when they do not. */
    public static int progressAt(int index) {
        return index >= 0 && index < questProgress.size() ? questProgress.get(index) : -1;
    }

    public static String settlementName() {
        return settlementName;
    }

    public static int population() {
        return population;
    }

    public static float defence() {
        return defence;
    }

    public static float threat() {
        return threat;
    }

    public static float prosperity() {
        return prosperity;
    }

    public static int foodStores() {
        return foodStores;
    }

    public static int materialStores() {
        return materialStores;
    }

    public static int revision() {
        return revision;
    }
}
