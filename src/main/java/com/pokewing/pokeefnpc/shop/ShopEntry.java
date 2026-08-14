package com.pokewing.pokeefnpc.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * One line on a shopkeeper's slate.
 *
 * <p>Prices are stored as the <b>base</b> price only. What a given player is
 * actually charged is worked out at the moment of the deal from the shopkeeper's
 * greed and what it thinks of them, so the same entry quotes a different number
 * to a trusted regular and to somebody who once burned the place down. Keeping
 * the modifiers out of the stored entry is what lets reputation stay live rather
 * than being baked in when the shop restocks.
 */
public final class ShopEntry {

    /** What is being traded. */
    private ItemStack offer = ItemStack.EMPTY;
    /** Base price in the shop's currency, before markup and standing. */
    private int basePrice;
    /** How many are left. -1 means unlimited. */
    private int stock;
    /** True when the NPC is buying this from the player rather than selling it. */
    private boolean buying;
    /** Off-book goods: only shown to players the NPC actually trusts. */
    private boolean contraband;
    /** Minimum guild rank needed to see it at all; 0 for anything public. */
    private int requiredRank;

    public ShopEntry() {
    }

    public ShopEntry(ItemStack offer, int basePrice, int stock, boolean buying) {
        this.offer = offer;
        this.basePrice = basePrice;
        this.stock = stock;
        this.buying = buying;
    }

    public ShopEntry contraband(int requiredRank) {
        this.contraband = true;
        this.requiredRank = requiredRank;
        return this;
    }

    public ShopEntry requiringRank(int rank) {
        this.requiredRank = rank;
        return this;
    }

    public ItemStack offer() {
        return this.offer;
    }

    public int basePrice() {
        return this.basePrice;
    }

    public int stock() {
        return this.stock;
    }

    public boolean buying() {
        return this.buying;
    }

    public boolean contraband() {
        return this.contraband;
    }

    public int requiredRank() {
        return this.requiredRank;
    }

    public boolean unlimited() {
        return this.stock < 0;
    }

    public boolean inStock() {
        return unlimited() || this.stock > 0;
    }

    public void take(int count) {
        if (!unlimited()) {
            this.stock = Math.max(0, this.stock - count);
        }
    }

    public void restock(int count, int cap) {
        if (!unlimited()) {
            this.stock = Math.min(cap, this.stock + count);
        }
    }

    /**
     * The price this particular player is quoted.
     *
     * @param markup     the shopkeeper's own greed, from {@code Personality#markup()}
     * @param reputation the standing multiplier, from {@code Reputation#priceMultiplier}
     */
    public int priceFor(float markup, float reputation) {
        float price = this.basePrice * markup * reputation;
        if (this.buying) {
            // When the NPC is the buyer the modifiers have to invert, otherwise a
            // shopkeeper who likes you would pay you less for your goods.
            price = this.basePrice / Math.max(0.1F, markup * reputation);
        }
        return Math.max(1, Math.round(price));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Offer", this.offer.save(new CompoundTag()));
        tag.putInt("Price", this.basePrice);
        tag.putInt("Stock", this.stock);
        tag.putBoolean("Buying", this.buying);
        tag.putBoolean("Contraband", this.contraband);
        tag.putInt("Rank", this.requiredRank);
        return tag;
    }

    public static ShopEntry load(CompoundTag tag) {
        ShopEntry entry = new ShopEntry();
        entry.offer = ItemStack.of(tag.getCompound("Offer"));
        entry.basePrice = tag.getInt("Price");
        entry.stock = tag.getInt("Stock");
        entry.buying = tag.getBoolean("Buying");
        entry.contraband = tag.getBoolean("Contraband");
        entry.requiredRank = tag.getInt("Rank");
        return entry;
    }

    /** Writes the entry with the price already resolved for one player. */
    public void write(FriendlyByteBuf buf, int resolvedPrice) {
        buf.writeItem(this.offer);
        buf.writeVarInt(resolvedPrice);
        buf.writeVarInt(this.basePrice);
        buf.writeInt(this.stock);
        buf.writeBoolean(this.buying);
        buf.writeBoolean(this.contraband);
        buf.writeVarInt(this.requiredRank);
    }

    public static ShopEntry read(FriendlyByteBuf buf) {
        ShopEntry entry = new ShopEntry();
        entry.offer = buf.readItem();
        entry.quotedPrice = buf.readVarInt();
        entry.basePrice = buf.readVarInt();
        entry.stock = buf.readInt();
        entry.buying = buf.readBoolean();
        entry.contraband = buf.readBoolean();
        entry.requiredRank = buf.readVarInt();
        return entry;
    }

    /**
     * The price the server quoted this client, set only on the client copy. The
     * server never reads it — a purchase is always re-priced authoritatively — so
     * a tampered client cannot talk a shopkeeper down.
     */
    private int quotedPrice;

    public int quotedPrice() {
        return this.quotedPrice;
    }
}
