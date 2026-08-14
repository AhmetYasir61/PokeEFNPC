package com.pokewing.pokeefnpc.shop;

import com.pokewing.pokeefnpc.npc.NpcRole;
import com.pokewing.pokeefnpc.npc.Reputation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What one shopkeeper has on the shelf, and how it comes back.
 *
 * <p>Stock is finite and refills slowly, scaled by how well the settlement is
 * doing. That single link is what makes the economy feel connected to the world:
 * buy out the smith and it takes days to recover, let the village starve and the
 * shelves stay bare, defend it well and the shops fill up.
 */
public final class ShopStock {

    /** Fraction of a full shelf restored per bookkeeping pass, at full prosperity. */
    private static final float RESTOCK_RATE = 0.15F;

    private final List<ShopEntry> entries = new ArrayList<>();
    /** Coin on hand. A shop that has spent up cannot keep buying from players. */
    private int purse = 64;

    public List<ShopEntry> entries() {
        return this.entries;
    }

    public void add(ShopEntry entry) {
        this.entries.add(entry);
    }

    public int purse() {
        return this.purse;
    }

    public void setPurse(int purse) {
        this.purse = Math.max(0, purse);
    }

    public void addToPurse(int amount) {
        this.purse = Math.max(0, this.purse + amount);
    }

    /**
     * The lines this player is allowed to see.
     *
     * <p>Contraband is filtered here rather than in the screen, so an off-book
     * listing never reaches a client that was not trusted with it — the fence's
     * stock is genuinely secret, not merely hidden in the interface.
     */
    public List<ShopEntry> visibleTo(Reputation reputation, UUID player, int guildRank) {
        List<ShopEntry> visible = new ArrayList<>();
        boolean trusted = reputation.trustsWithContraband(player);
        for (ShopEntry entry : this.entries) {
            if (entry.contraband() && !trusted) {
                continue;
            }
            if (entry.requiredRank() > guildRank) {
                continue;
            }
            visible.add(entry);
        }
        return visible;
    }

    /**
     * Refills the shelves a little. Called on the NPC's slow tick, not per tick.
     *
     * @param prosperity the settlement's prosperity in [0,1]; 0.5 when it has none
     */
    public void restock(RandomSource random, NpcRole role, float prosperity) {
        if (this.entries.isEmpty()) {
            return;
        }
        float rate = RESTOCK_RATE * (0.4F + prosperity);
        for (ShopEntry entry : this.entries) {
            if (entry.unlimited() || random.nextFloat() > rate) {
                continue;
            }
            entry.restock(1, ShopTables.stockCapFor(role));
        }
        // Takings come in over time, so a shop that has bought a lot from players
        // is not permanently broke.
        int income = Math.round(2.0F + prosperity * 8.0F);
        addToPurse(income);
        if (this.purse > ShopTables.purseCapFor(role)) {
            this.purse = ShopTables.purseCapFor(role);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (ShopEntry entry : this.entries) {
            list.add(entry.save());
        }
        tag.put("Entries", list);
        tag.putInt("Purse", this.purse);
        return tag;
    }

    public static ShopStock load(CompoundTag tag) {
        ShopStock stock = new ShopStock();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            stock.entries.add(ShopEntry.load(list.getCompound(i)));
        }
        stock.purse = tag.getInt("Purse");
        return stock;
    }
}
