package com.pokewing.pokeefnpc.data;

import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.guild.GuildRank;
import com.pokewing.pokeefnpc.quest.Quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the world remembers about a player's dealings with its people:
 * the contracts they are carrying, how far up the guild they have climbed, and
 * what is in their account at the bank.
 *
 * <p>Held as {@link SavedData} rather than on the player entity so it survives
 * death, dimension changes and the player being offline, and so an NPC can look
 * a standing up without the player being loaded.
 */
public final class PlayerLedger extends SavedData {

    private static final String DATA_NAME = PokeEFNPC.MOD_ID + "_ledger";
    /** Contracts one player may carry at once. */
    public static final int MAX_ACTIVE_QUESTS = 8;

    /** A contract in progress, and how far along it is. */
    public static final class ActiveQuest {
        public Quest quest;
        public int progress;
        /** The NPC that set it, so a DELIVER knows where it is going. */
        @Nullable
        public UUID issuer;

        public ActiveQuest(Quest quest, @Nullable UUID issuer) {
            this.quest = quest;
            this.issuer = issuer;
        }

        public boolean complete() {
            return this.progress >= this.quest.required();
        }
    }

    private final Map<UUID, List<ActiveQuest>> quests = new HashMap<>();
    private final Map<UUID, Integer> guildPoints = new HashMap<>();
    private final Map<UUID, Integer> bankBalance = new HashMap<>();

    public static PlayerLedger get(ServerLevel level) {
        // Deliberately stored on the overworld so a player's standing follows
        // them between dimensions instead of resetting in the Nether.
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                PlayerLedger::load, PlayerLedger::new, DATA_NAME);
    }

    public PlayerLedger() {
    }

    // ------------------------------------------------------------- contracts

    public List<ActiveQuest> questsOf(UUID player) {
        return this.quests.computeIfAbsent(player, key -> new ArrayList<>());
    }

    public boolean hasQuest(UUID player, UUID questId) {
        for (ActiveQuest active : questsOf(player)) {
            if (active.quest.id().equals(questId)) {
                return true;
            }
        }
        return false;
    }

    /** @return false when the player is already carrying as much as they can */
    public boolean accept(UUID player, Quest quest, @Nullable UUID issuer) {
        List<ActiveQuest> list = questsOf(player);
        if (list.size() >= MAX_ACTIVE_QUESTS || hasQuest(player, quest.id())) {
            return false;
        }
        list.add(new ActiveQuest(quest, issuer));
        setDirty();
        return true;
    }

    @Nullable
    public ActiveQuest find(UUID player, UUID questId) {
        for (ActiveQuest active : questsOf(player)) {
            if (active.quest.id().equals(questId)) {
                return active;
            }
        }
        return null;
    }

    public void abandon(UUID player, UUID questId) {
        questsOf(player).removeIf(active -> active.quest.id().equals(questId));
        setDirty();
    }

    /**
     * Credits a kill against every hunting contract this player is carrying that
     * wanted that kind of monster. Called from the death handler.
     */
    public void creditKill(UUID player, EntityType<?> type) {
        boolean changed = false;
        for (ActiveQuest active : questsOf(player)) {
            if (active.quest.type().tracksKills() && active.quest.monster() == type
                    && !active.complete()) {
                active.progress++;
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    /** Ticks off a scouting contract the player has now reached. */
    public boolean creditVisit(UUID player, net.minecraft.core.BlockPos where) {
        boolean changed = false;
        for (ActiveQuest active : questsOf(player)) {
            var destination = active.quest.destination();
            if (destination == null || active.complete()) {
                continue;
            }
            if (destination.distSqr(where) <= 32.0D * 32.0D) {
                active.progress = active.quest.required();
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    // ------------------------------------------------------------- guild

    public int guildPointsOf(UUID player) {
        return this.guildPoints.getOrDefault(player, 0);
    }

    public GuildRank rankOf(UUID player) {
        return GuildRank.forPoints(guildPointsOf(player));
    }

    /** @return the new rank when this award promoted the player, else null */
    @Nullable
    public GuildRank awardGuildPoints(UUID player, int points) {
        GuildRank before = rankOf(player);
        this.guildPoints.merge(player, points, Integer::sum);
        setDirty();
        GuildRank after = rankOf(player);
        return after != before ? after : null;
    }

    // ------------------------------------------------------------- bank

    public int balanceOf(UUID player) {
        return this.bankBalance.getOrDefault(player, 0);
    }

    public void deposit(UUID player, int amount) {
        if (amount > 0) {
            this.bankBalance.merge(player, amount, Integer::sum);
            setDirty();
        }
    }

    /** @return how much was actually withdrawn, which may be less than asked */
    public int withdraw(UUID player, int amount) {
        int balance = balanceOf(player);
        int taken = Math.min(Math.max(0, amount), balance);
        if (taken > 0) {
            this.bankBalance.put(player, balance - taken);
            setDirty();
        }
        return taken;
    }

    // ------------------------------------------------------------- persistence

    public static PlayerLedger load(CompoundTag tag) {
        PlayerLedger ledger = new PlayerLedger();

        ListTag players = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            UUID player = entry.getUUID("Player");
            ledger.guildPoints.put(player, entry.getInt("GuildPoints"));
            ledger.bankBalance.put(player, entry.getInt("Balance"));

            List<ActiveQuest> list = new ArrayList<>();
            ListTag questList = entry.getList("Quests", Tag.TAG_COMPOUND);
            for (int q = 0; q < questList.size(); q++) {
                CompoundTag questTag = questList.getCompound(q);
                ActiveQuest active = new ActiveQuest(Quest.load(questTag.getCompound("Quest")),
                        questTag.hasUUID("Issuer") ? questTag.getUUID("Issuer") : null);
                active.progress = questTag.getInt("Progress");
                list.add(active);
            }
            ledger.quests.put(player, list);
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        // Union of everyone mentioned anywhere, so a player with only a bank
        // balance and no contracts is still written out.
        java.util.Set<UUID> known = new java.util.HashSet<>();
        known.addAll(this.quests.keySet());
        known.addAll(this.guildPoints.keySet());
        known.addAll(this.bankBalance.keySet());

        for (UUID player : known) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putInt("GuildPoints", guildPointsOf(player));
            entry.putInt("Balance", balanceOf(player));

            ListTag questList = new ListTag();
            for (ActiveQuest active : this.quests.getOrDefault(player, List.of())) {
                CompoundTag questTag = new CompoundTag();
                questTag.put("Quest", active.quest.save());
                questTag.putInt("Progress", active.progress);
                if (active.issuer != null) {
                    questTag.putUUID("Issuer", active.issuer);
                }
                questList.add(questTag);
            }
            entry.put("Quests", questList);
            players.add(entry);
        }
        tag.put("Players", players);
        return tag;
    }
}
