package com.pokewing.pokeefnpc.shop;

import com.pokewing.pokeefnpc.npc.NpcRole;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What each trade actually sells.
 *
 * <p>Stock is rolled per NPC rather than shared per role, so two blacksmiths in
 * the same village do not have identical shelves and it is worth walking to the
 * other one. The rolls are deliberately coarse — a handful of lines each — because
 * a short, legible slate reads better in a menu than forty items nobody scrolls
 * through.
 *
 * <p>Emeralds are the currency throughout, matching vanilla trading so the mod's
 * economy plugs into the one players already have.
 */
public final class ShopTables {

    private ShopTables() {
    }

    /** Ceiling a shelf refills to. */
    public static int stockCapFor(NpcRole role) {
        return switch (role.category()) {
            case TRADE -> 16;
            case CRIMINAL -> 4;
            case COMMONER -> 12;
            default -> 8;
        };
    }

    /** Most coin a shop will ever have on hand to buy from players. */
    public static int purseCapFor(NpcRole role) {
        return switch (role) {
            case BANKER, GUILD_MASTER, NOBLE -> 512;
            case MERCHANT, BLACK_MARKET_DEALER -> 256;
            case BEGGAR -> 4;
            default -> role.carriesMoney() ? 128 : 48;
        };
    }

    /** Rolls a fresh shelf for a newly created NPC. */
    public static ShopStock freshStock(RandomSource random, NpcRole role) {
        ShopStock stock = new ShopStock();
        stock.setPurse(purseCapFor(role) / 2);

        switch (role) {
            case FARMER, VILLAGER -> {
                sell(stock, random, Items.WHEAT, 1, 8, 16);
                sell(stock, random, Items.BREAD, 2, 6, 12);
                sell(stock, random, Items.CARROT, 1, 6, 12);
                sell(stock, random, Items.POTATO, 1, 6, 12);
                buy(stock, Items.BONE_MEAL, 1);
            }
            case SHEPHERD -> {
                sell(stock, random, Items.WHITE_WOOL, 2, 6, 12);
                sell(stock, random, Items.MUTTON, 2, 4, 8);
                sell(stock, random, Items.LEATHER, 3, 4, 8);
            }
            case FISHERMAN -> {
                sell(stock, random, Items.COD, 2, 6, 12);
                sell(stock, random, Items.SALMON, 3, 4, 8);
                sell(stock, random, Items.FISHING_ROD, 8, 1, 2);
            }
            case LUMBERJACK -> {
                sell(stock, random, Items.OAK_LOG, 1, 12, 24);
                sell(stock, random, Items.STICK, 1, 16, 32);
                buy(stock, Items.IRON_AXE, 6);
            }
            case MINER -> {
                sell(stock, random, Items.COAL, 1, 12, 24);
                sell(stock, random, Items.IRON_ORE, 4, 4, 8);
                sell(stock, random, Items.COBBLESTONE, 1, 24, 48);
                buy(stock, Items.IRON_PICKAXE, 8);
            }
            case BUILDER -> {
                sell(stock, random, Items.OAK_PLANKS, 1, 24, 48);
                sell(stock, random, Items.STONE_BRICKS, 2, 16, 32);
                sell(stock, random, Items.OAK_FENCE, 2, 12, 24);
                sell(stock, random, Items.TORCH, 1, 24, 48);
            }
            case TAILOR -> {
                sell(stock, random, Items.LEATHER_CHESTPLATE, 8, 2, 3);
                sell(stock, random, Items.LEATHER_BOOTS, 5, 2, 3);
                sell(stock, random, Items.WHITE_BANNER, 6, 2, 3);
                buy(stock, Items.LEATHER, 2);
            }
            case BAKER -> {
                sell(stock, random, Items.BREAD, 2, 8, 16);
                sell(stock, random, Items.COOKIE, 1, 12, 24);
                sell(stock, random, Items.CAKE, 12, 1, 2);
                buy(stock, Items.WHEAT, 1);
            }
            case INNKEEPER -> {
                sell(stock, random, Items.COOKED_BEEF, 4, 6, 12);
                sell(stock, random, Items.BREAD, 2, 8, 16);
                sell(stock, random, Items.MILK_BUCKET, 6, 2, 4);
                sell(stock, random, Items.POTION, 10, 2, 4);
            }
            case MERCHANT -> {
                sell(stock, random, Items.EMERALD_BLOCK, 40, 1, 2);
                sell(stock, random, Items.COMPASS, 12, 2, 4);
                sell(stock, random, Items.MAP, 8, 3, 6);
                sell(stock, random, Items.ENDER_PEARL, 24, 2, 4);
                buy(stock, Items.GOLD_INGOT, 6);
                buy(stock, Items.DIAMOND, 24);
            }
            case BLACKSMITH -> {
                sell(stock, random, Items.IRON_SWORD, 12, 2, 4);
                sell(stock, random, Items.IRON_CHESTPLATE, 24, 1, 2);
                sell(stock, random, Items.SHIELD, 10, 2, 4);
                sell(stock, random, Items.IRON_INGOT, 4, 8, 16);
                buy(stock, Items.IRON_ORE, 3);
                buy(stock, Items.COAL, 1);
            }
            case ALCHEMIST -> {
                sell(stock, random, Items.POTION, 14, 3, 6);
                sell(stock, random, Items.GLASS_BOTTLE, 2, 8, 16);
                sell(stock, random, Items.BLAZE_POWDER, 10, 3, 6);
                sell(stock, random, Items.GOLDEN_APPLE, 32, 1, 2);
                buy(stock, Items.NETHER_WART, 4);
            }
            case HEALER -> {
                sell(stock, random, Items.GOLDEN_CARROT, 8, 4, 8);
                sell(stock, random, Items.GOLDEN_APPLE, 30, 1, 2);
                sell(stock, random, Items.MILK_BUCKET, 6, 3, 6);
            }
            case ARCHER, HUNTER -> {
                sell(stock, random, Items.ARROW, 1, 32, 64);
                sell(stock, random, Items.BOW, 14, 2, 3);
                sell(stock, random, Items.COOKED_PORKCHOP, 3, 6, 12);
                buy(stock, Items.FEATHER, 1);
            }
            case MAGE -> {
                sell(stock, random, Items.ENCHANTED_BOOK, 40, 1, 2);
                sell(stock, random, Items.LAPIS_LAZULI, 4, 8, 16);
                sell(stock, random, Items.ENDER_EYE, 28, 2, 3);
            }
            case SCRIBE, GUILD_CLERK -> {
                sell(stock, random, Items.BOOK, 3, 8, 16);
                sell(stock, random, Items.PAPER, 1, 16, 32);
                sell(stock, random, Items.MAP, 8, 3, 6);
                sell(stock, random, Items.WRITABLE_BOOK, 6, 3, 6);
            }
            case BANKER -> {
                sell(stock, random, Items.GOLD_INGOT, 10, 8, 16);
                buy(stock, Items.GOLD_NUGGET, 1);
                buy(stock, Items.DIAMOND, 26);
                buy(stock, Items.EMERALD_BLOCK, 8);
            }
            case TRAVELLER -> {
                sell(stock, random, Items.MAP, 10, 2, 4);
                sell(stock, random, Items.COMPASS, 14, 1, 2);
                sell(stock, random, Items.COOKED_BEEF, 5, 4, 8);
                sell(stock, random, Items.ENDER_PEARL, 26, 1, 2);
            }
            case MERCENARY -> {
                sell(stock, random, Items.IRON_AXE, 14, 1, 2);
                sell(stock, random, Items.SHIELD, 12, 2, 3);
                buy(stock, Items.ROTTEN_FLESH, 1);
            }
            case BEGGAR -> buy(stock, Items.BREAD, 1);

            // ------------------------------------------------ off the books
            case BLACK_MARKET_DEALER -> {
                // The visible half of the slate. Nothing here is incriminating,
                // which is exactly the point — you have to be trusted before the
                // rest of it is shown to you at all.
                sell(stock, random, Items.ROTTEN_FLESH, 1, 8, 16);
                sell(stock, random, Items.STRING, 2, 8, 16);
                contraband(stock, Items.ENDER_EYE, 40, 2, 0);
                contraband(stock, Items.GOLDEN_APPLE, 60, 2, 0);
                contraband(stock, Items.ENCHANTED_GOLDEN_APPLE, 220, 1, 2);
                contraband(stock, Items.TOTEM_OF_UNDYING, 300, 1, 3);
                contraband(stock, Items.NETHERITE_SCRAP, 180, 1, 2);
                contraband(stock, Items.TNT, 45, 3, 1);
                buyContraband(stock, Items.DIAMOND, 18);
                buyContraband(stock, Items.ANCIENT_DEBRIS, 120);
            }
            case SMUGGLER -> {
                sell(stock, random, Items.GUNPOWDER, 6, 6, 12);
                contraband(stock, Items.TNT, 40, 4, 0);
                contraband(stock, Items.BLAZE_ROD, 30, 3, 0);
                buyContraband(stock, Items.GOLD_INGOT, 5);
            }
            case THIEF -> {
                contraband(stock, Items.LEATHER_BOOTS, 20, 1, 0);
                contraband(stock, Items.ENDER_PEARL, 35, 2, 0);
                buyContraband(stock, Items.EMERALD, 1);
            }
            default -> {
                // Roles whose business is services rather than goods — guards,
                // priests, nobles — legitimately have nothing on a shelf. Their
                // menus are built from their other pages.
            }
        }
        return stock;
    }

    private static void sell(ShopStock stock, RandomSource random, Item item,
                             int basePrice, int minStock, int maxStock) {
        int count = minStock + random.nextInt(Math.max(1, maxStock - minStock + 1));
        // A little wobble on the price so two shops of the same trade are worth
        // comparing.
        int price = Math.max(1, basePrice + random.nextInt(3) - 1);
        stock.add(new ShopEntry(new ItemStack(item), price, count, false));
    }

    private static void buy(ShopStock stock, Item item, int basePrice) {
        stock.add(new ShopEntry(new ItemStack(item), basePrice, -1, true));
    }

    private static void contraband(ShopStock stock, Item item, int basePrice, int count, int rank) {
        stock.add(new ShopEntry(new ItemStack(item), basePrice, count, false).contraband(rank));
    }

    private static void buyContraband(ShopStock stock, Item item, int basePrice) {
        stock.add(new ShopEntry(new ItemStack(item), basePrice, -1, true).contraband(0));
    }
}
