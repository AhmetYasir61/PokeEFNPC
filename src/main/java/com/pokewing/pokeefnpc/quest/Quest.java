package com.pokewing.pokeefnpc.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * One contract, as written on the board.
 *
 * <p>A quest is pure data — what is wanted, how much of it, what it pays, who set
 * it and when it lapses. It carries no progress: progress belongs to the player
 * who took it, in {@link PlayerQuestLog}. Keeping the two apart is what lets the
 * same contract be offered to several players at once and lets a settlement keep
 * its board when nobody is logged in.
 */
public final class Quest {

    private UUID id = UUID.randomUUID();
    private QuestType type = QuestType.GATHER;

    /** For GATHER and DELIVER. */
    private Item item = Items.AIR;
    /** For HUNT. */
    @Nullable
    private EntityType<?> monster;
    /** For SCOUT. */
    @Nullable
    private BlockPos destination;

    private int required = 1;
    private int rewardEmeralds;
    private int rewardReputation;
    private int rewardGuildPoints;
    /** Guild rank needed before this one is even shown. */
    private int requiredRank;

    /** Who set it, so a DELIVER cannot be handed straight back. */
    @Nullable
    private UUID issuer;
    /** Game time after which the board replaces it. */
    private long expiresAt;

    public Quest() {
    }

    public static Quest gather(Item item, int count, int emeralds) {
        Quest quest = new Quest();
        quest.type = QuestType.GATHER;
        quest.item = item;
        quest.required = count;
        quest.rewardEmeralds = emeralds;
        quest.rewardReputation = 5;
        quest.rewardGuildPoints = 1;
        return quest;
    }

    public static Quest hunt(EntityType<?> monster, int count, int emeralds) {
        Quest quest = new Quest();
        quest.type = QuestType.HUNT;
        quest.monster = monster;
        quest.required = count;
        quest.rewardEmeralds = emeralds;
        quest.rewardReputation = 10;
        quest.rewardGuildPoints = 3;
        return quest;
    }

    public static Quest deliver(Item item, int count, int emeralds) {
        Quest quest = new Quest();
        quest.type = QuestType.DELIVER;
        quest.item = item;
        quest.required = count;
        quest.rewardEmeralds = emeralds;
        quest.rewardReputation = 8;
        quest.rewardGuildPoints = 2;
        return quest;
    }

    public static Quest scout(BlockPos destination, int emeralds) {
        Quest quest = new Quest();
        quest.type = QuestType.SCOUT;
        quest.destination = destination;
        quest.required = 1;
        quest.rewardEmeralds = emeralds;
        quest.rewardReputation = 6;
        quest.rewardGuildPoints = 2;
        return quest;
    }

    public Quest withRank(int rank) {
        this.requiredRank = rank;
        return this;
    }

    public Quest issuedBy(UUID issuer, long gameTime, long lifetimeTicks) {
        this.issuer = issuer;
        this.expiresAt = gameTime + lifetimeTicks;
        return this;
    }

    public UUID id() {
        return this.id;
    }

    public QuestType type() {
        return this.type;
    }

    public Item item() {
        return this.item;
    }

    @Nullable
    public EntityType<?> monster() {
        return this.monster;
    }

    @Nullable
    public BlockPos destination() {
        return this.destination;
    }

    public int required() {
        return this.required;
    }

    public int rewardEmeralds() {
        return this.rewardEmeralds;
    }

    public int rewardReputation() {
        return this.rewardReputation;
    }

    public int rewardGuildPoints() {
        return this.rewardGuildPoints;
    }

    public int requiredRank() {
        return this.requiredRank;
    }

    @Nullable
    public UUID issuer() {
        return this.issuer;
    }

    public boolean expired(long gameTime) {
        return this.expiresAt > 0 && gameTime > this.expiresAt;
    }

    /** One line describing the job, assembled from the contract's own data. */
    public Component describe() {
        return switch (this.type) {
            case GATHER -> Component.translatable("pokeefnpc.quest.desc.gather",
                    this.required, this.item.getDescription());
            case DELIVER -> Component.translatable("pokeefnpc.quest.desc.deliver",
                    this.required, this.item.getDescription());
            case HUNT -> Component.translatable("pokeefnpc.quest.desc.hunt",
                    this.required, this.monster == null
                            ? Component.literal("?") : this.monster.getDescription());
            case SCOUT -> Component.translatable("pokeefnpc.quest.desc.scout",
                    this.destination == null ? 0 : this.destination.getX(),
                    this.destination == null ? 0 : this.destination.getZ());
        };
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putInt("Type", this.type.ordinal());
        tag.putString("Item", BuiltInRegistries.ITEM.getKey(this.item).toString());
        if (this.monster != null) {
            tag.putString("Monster", BuiltInRegistries.ENTITY_TYPE.getKey(this.monster).toString());
        }
        if (this.destination != null) {
            tag.put("Destination", NbtUtils.writeBlockPos(this.destination));
        }
        tag.putInt("Required", this.required);
        tag.putInt("Emeralds", this.rewardEmeralds);
        tag.putInt("Reputation", this.rewardReputation);
        tag.putInt("GuildPoints", this.rewardGuildPoints);
        tag.putInt("Rank", this.requiredRank);
        if (this.issuer != null) {
            tag.putUUID("Issuer", this.issuer);
        }
        tag.putLong("Expires", this.expiresAt);
        return tag;
    }

    public static Quest load(CompoundTag tag) {
        Quest quest = new Quest();
        quest.id = tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID();
        quest.type = QuestType.byOrdinal(tag.getInt("Type"));
        quest.item = BuiltInRegistries.ITEM.get(new ResourceLocation(tag.getString("Item")));
        if (tag.contains("Monster")) {
            quest.monster = BuiltInRegistries.ENTITY_TYPE
                    .get(new ResourceLocation(tag.getString("Monster")));
        }
        if (tag.contains("Destination")) {
            quest.destination = NbtUtils.readBlockPos(tag.getCompound("Destination"));
        }
        quest.required = tag.getInt("Required");
        quest.rewardEmeralds = tag.getInt("Emeralds");
        quest.rewardReputation = tag.getInt("Reputation");
        quest.rewardGuildPoints = tag.getInt("GuildPoints");
        quest.requiredRank = tag.getInt("Rank");
        quest.issuer = tag.hasUUID("Issuer") ? tag.getUUID("Issuer") : null;
        quest.expiresAt = tag.getLong("Expires");
        return quest;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.id);
        buf.writeVarInt(this.type.ordinal());
        buf.writeVarInt(BuiltInRegistries.ITEM.getId(this.item));
        buf.writeBoolean(this.monster != null);
        if (this.monster != null) {
            buf.writeVarInt(BuiltInRegistries.ENTITY_TYPE.getId(this.monster));
        }
        buf.writeBoolean(this.destination != null);
        if (this.destination != null) {
            buf.writeBlockPos(this.destination);
        }
        buf.writeVarInt(this.required);
        buf.writeVarInt(this.rewardEmeralds);
        buf.writeVarInt(this.rewardReputation);
        buf.writeVarInt(this.rewardGuildPoints);
        buf.writeVarInt(this.requiredRank);
    }

    public static Quest read(FriendlyByteBuf buf) {
        Quest quest = new Quest();
        quest.id = buf.readUUID();
        quest.type = QuestType.byOrdinal(buf.readVarInt());
        quest.item = BuiltInRegistries.ITEM.byId(buf.readVarInt());
        quest.monster = buf.readBoolean()
                ? BuiltInRegistries.ENTITY_TYPE.byId(buf.readVarInt()) : null;
        quest.destination = buf.readBoolean() ? buf.readBlockPos() : null;
        quest.required = buf.readVarInt();
        quest.rewardEmeralds = buf.readVarInt();
        quest.rewardReputation = buf.readVarInt();
        quest.rewardGuildPoints = buf.readVarInt();
        quest.requiredRank = buf.readVarInt();
        return quest;
    }
}
