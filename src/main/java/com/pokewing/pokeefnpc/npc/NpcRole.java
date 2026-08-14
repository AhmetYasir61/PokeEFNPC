package com.pokewing.pokeefnpc.npc;

import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.menu.MenuPage;
import com.pokewing.pokeefnpc.schedule.ScheduleTemplate;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.List;

/**
 * Every part a character can play in the world, and everything that follows from
 * the choice.
 *
 * <p>A role is the one place a kind of person is described. It fixes the
 * statistics it fights with, the traits it is typically born with, the routine
 * it keeps, the gestures it falls into, the tabs its interface offers and how
 * likely it is to turn up when a village populates itself. Everything else in
 * the mod reads those answers rather than special-casing "if this is a guard",
 * which is what keeps adding the next role to a single entry here.
 */
public enum NpcRole {

    // ============================================================== commoners
    VILLAGER("villager", Category.COMMONER,
            20.0F, 1.0F, 0.0F, 0.24F,
            0.30F, 0.75F, 0.45F, 0.45F, 0.55F, 0.15F,
            ScheduleTemplate.FARMHAND, Emote.SWEEP, 14, Items.AIR,
            pages(MenuPage.TALK, MenuPage.SHOP, MenuPage.INFORMATION)),

    FARMER("farmer", Category.COMMONER,
            22.0F, 2.0F, 0.0F, 0.24F,
            0.30F, 0.60F, 0.40F, 0.35F, 0.85F, 0.20F,
            ScheduleTemplate.FARMHAND, Emote.SOW, 12, Items.IRON_HOE,
            pages(MenuPage.TALK, MenuPage.SHOP, MenuPage.QUESTS)),

    SHEPHERD("shepherd", Category.COMMONER,
            22.0F, 2.0F, 0.0F, 0.25F,
            0.35F, 0.50F, 0.35F, 0.30F, 0.70F, 0.25F,
            ScheduleTemplate.FARMHAND, Emote.HAUL, 8, Items.SHEARS,
            pages(MenuPage.TALK, MenuPage.SHOP)),

    FISHERMAN("fisherman", Category.COMMONER,
            22.0F, 2.0F, 0.0F, 0.24F,
            0.35F, 0.55F, 0.40F, 0.35F, 0.65F, 0.15F,
            ScheduleTemplate.FARMHAND, Emote.HAUL, 7, Items.FISHING_ROD,
            pages(MenuPage.TALK, MenuPage.SHOP)),

    LUMBERJACK("lumberjack", Category.COMMONER,
            24.0F, 3.5F, 0.0F, 0.25F,
            0.50F, 0.45F, 0.40F, 0.55F, 0.80F, 0.10F,
            ScheduleTemplate.ARTISAN, Emote.HAMMER, 8, Items.IRON_AXE,
            pages(MenuPage.TALK, MenuPage.SHOP)),

    MINER("miner", Category.COMMONER,
            24.0F, 3.0F, 2.0F, 0.24F,
            0.45F, 0.40F, 0.50F, 0.50F, 0.85F, 0.00F,
            ScheduleTemplate.ARTISAN, Emote.MINE_SWING, 9, Items.IRON_PICKAXE,
            pages(MenuPage.TALK, MenuPage.SHOP, MenuPage.QUESTS)),

    BUILDER("builder", Category.COMMONER,
            24.0F, 2.5F, 2.0F, 0.25F,
            0.45F, 0.55F, 0.40F, 0.40F, 0.90F, 0.15F,
            ScheduleTemplate.ARTISAN, Emote.HAMMER, 8, Items.IRON_SHOVEL,
            pages(MenuPage.TALK, MenuPage.SHOP, MenuPage.SETTLEMENT)),

    /** The mender. Patches armour, clothing and sails for a few coins. */
    TAILOR("tailor", Category.COMMONER,
            20.0F, 1.0F, 0.0F, 0.24F,
            0.25F, 0.65F, 0.45F, 0.30F, 0.80F, 0.20F,
            ScheduleTemplate.SHOPKEEPER, Emote.WRITE, 7, Items.SHEARS,
            pages(MenuPage.TALK, MenuPage.SHOP, MenuPage.SMITHING)),

    BAKER("baker", Category.COMMONER,
            20.0F, 1.0F, 0.0F, 0.24F,
            0.25F, 0.80F, 0.40F, 0.30F, 0.80F, 0.35F,
            ScheduleTemplate.SHOPKEEPER, Emote.STIR, 7, Items.BREAD,
            pages(MenuPage.TALK, MenuPage.SHOP)),

    INNKEEPER("innkeeper", Category.COMMONER,
            24.0F, 2.0F, 0.0F, 0.23F,
            0.40F, 0.95F, 0.55F, 0.35F, 0.75F, 0.35F,
            ScheduleTemplate.SHOPKEEPER, Emote.WEIGH_COIN, 6, Items.BARREL,
            pages(MenuPage.TALK, MenuPage.LODGING, MenuPage.SHOP, MenuPage.INFORMATION)),

    BEGGAR("beggar", Category.COMMONER,
            14.0F, 1.0F, 0.0F, 0.23F,
            0.10F, 0.55F, 0.75F, 0.35F, 0.20F, -0.45F,
            ScheduleTemplate.VAGRANT, Emote.BEG, 5, Items.AIR,
            pages(MenuPage.TALK, MenuPage.INFORMATION)),

    // ================================================================ martial
    SOLDIER("soldier", Category.MARTIAL,
            30.0F, 6.0F, 6.0F, 0.27F,
            0.75F, 0.45F, 0.35F, 0.60F, 0.75F, 0.00F,
            ScheduleTemplate.WATCH_DAY, Emote.SHARPEN, 10, Items.IRON_SWORD,
            pages(MenuPage.TALK, MenuPage.QUESTS, MenuPage.TRAINING)),

    GUARD("guard", Category.MARTIAL,
            32.0F, 6.0F, 8.0F, 0.26F,
            0.80F, 0.40F, 0.30F, 0.55F, 0.85F, 0.00F,
            ScheduleTemplate.WATCH_DAY, Emote.GUARD_STANCE, 12, Items.IRON_SWORD,
            pages(MenuPage.TALK, MenuPage.QUESTS, MenuPage.INFORMATION)),

    NIGHT_WATCH("night_watch", Category.MARTIAL,
            30.0F, 5.5F, 7.0F, 0.26F,
            0.75F, 0.35F, 0.30F, 0.55F, 0.80F, -0.10F,
            ScheduleTemplate.WATCH_NIGHT, Emote.SCAN_HORIZON, 8, Items.IRON_SWORD,
            pages(MenuPage.TALK, MenuPage.QUESTS, MenuPage.INFORMATION)),

    CAPTAIN("captain", Category.MARTIAL,
            44.0F, 8.5F, 12.0F, 0.28F,
            0.95F, 0.55F, 0.30F, 0.65F, 0.90F, 0.05F,
            ScheduleTemplate.WATCH_DAY, Emote.SCAN_HORIZON, 3, Items.DIAMOND_SWORD,
            pages(MenuPage.TALK, MenuPage.QUESTS, MenuPage.HIRE, MenuPage.TRAINING,
                    MenuPage.SETTLEMENT)),

    KNIGHT("knight", Category.MARTIAL,
            48.0F, 9.0F, 15.0F, 0.26F,
            1.00F, 0.50F, 0.25F, 0.50F, 0.85F, 0.10F,
            ScheduleTemplate.WATCH_DAY, Emote.SHARPEN, 3, Items.DIAMOND_SWORD,
            pages(MenuPage.TALK, MenuPage.QUESTS, MenuPage.TRAINING)),

    ARCHER("archer", Category.MARTIAL,
            26.0F, 5.0F, 4.0F, 0.27F,
            0.70F, 0.45F, 0.40F, 0.50F, 0.75F, 0.00F,
            ScheduleTemplate.WATCH_DAY, Emote.SCAN_HORIZON, 8, Items.BOW,
            pages(MenuPage.TALK, MenuPage.SHOP, MenuPage.TRAINING)),

    MERCENARY("mercenary", Category.MARTIAL,
            36.0F, 7.5F, 8.0F, 0.28F,
            0.85F, 0.40F, 0.85F, 0.70F, 0.55F, -0.05F,
            ScheduleTemplate.ADVENTURING, Emote.SHARPEN, 5, Items.IRON_AXE,
            pages(MenuPage.TALK, MenuPage.HIRE, MenuPage.QUESTS, MenuPage.BLACK_MARKET)),

    HUNTER("hunter", Category.MARTIAL,
            26.0F, 5.5F, 3.0F, 0.29F,
            0.70F, 0.30F, 0.45F, 0.45F, 0.80F, 0.00F,
            ScheduleTemplate.WANDERER, Emote.SCAN_HORIZON, 7, Items.BOW,
            pages(MenuPage.TALK, MenuPage.SHOP, MenuPage.QUESTS)),

    EXECUTIONER("executioner", Category.MARTIAL,
            40.0F, 10.0F, 6.0F, 0.24F,
            0.90F, 0.10F, 0.40F, 0.75F, 0.70F, -0.35F,
            ScheduleTemplate.NOCTURNAL, Emote.SHARPEN, 1, Items.IRON_AXE,
            pages(MenuPage.TALK, MenuPage.QUESTS)),

    // ================================================================== guild
    TRAVELLER("traveller", Category.GUILD,
            24.0F, 4.0F, 2.0F, 0.30F,
            0.60F, 0.85F, 0.45F, 0.40F, 0.50F, 0.30F,
            ScheduleTemplate.WANDERER, Emote.HAUL, 6, Items.AIR,
            pages(MenuPage.TALK, MenuPage.SHOP, MenuPage.INFORMATION)),

    ADVENTURER("adventurer", Category.GUILD,
            30.0F, 6.5F, 5.0F, 0.29F,
            0.80F, 0.65F, 0.60F, 0.55F, 0.60F, 0.20F,
            ScheduleTemplate.ADVENTURING, Emote.SHARPEN, 8, Items.IRON_SWORD,
            pages(MenuPage.TALK, MenuPage.QUESTS, MenuPage.HIRE, MenuPage.GUILD)),

    ADVENTURER_CHIEF("adventurer_chief", Category.GUILD,
            50.0F, 10.0F, 12.0F, 0.29F,
            0.95F, 0.70F, 0.55F, 0.55F, 0.80F, 0.25F,
            ScheduleTemplate.ADVENTURING, Emote.CROSS_ARMS, 2, Items.NETHERITE_SWORD,
            pages(MenuPage.TALK, MenuPage.QUESTS, MenuPage.GUILD, MenuPage.TRAINING,
                    MenuPage.HIRE)),

    GUILD_CLERK("guild_clerk", Category.GUILD,
            20.0F, 1.5F, 0.0F, 0.24F,
            0.25F, 0.80F, 0.45F, 0.25F, 0.95F, 0.25F,
            ScheduleTemplate.SHOPKEEPER, Emote.WRITE, 5, Items.WRITABLE_BOOK,
            pages(MenuPage.TALK, MenuPage.QUESTS, MenuPage.GUILD, MenuPage.BANK,
                    MenuPage.INFORMATION)),

    GUILD_MASTER("guild_master", Category.GUILD,
            46.0F, 8.0F, 10.0F, 0.26F,
            0.85F, 0.75F, 0.60F, 0.45F, 0.90F, 0.30F,
            ScheduleTemplate.NOBLE_LEISURE, Emote.CROSS_ARMS, 1, Items.DIAMOND_SWORD,
            pages(MenuPage.TALK, MenuPage.GUILD, MenuPage.QUESTS, MenuPage.TRAINING,
                    MenuPage.BANK, MenuPage.HIRE)),

    // ================================================================= trades
    MERCHANT("merchant", Category.TRADE,
            24.0F, 2.0F, 2.0F, 0.25F,
            0.35F, 0.90F, 0.80F, 0.35F, 0.85F, 0.30F,
            ScheduleTemplate.SHOPKEEPER, Emote.HAGGLE, 10, Items.EMERALD,
            pages(MenuPage.TALK, MenuPage.SHOP, MenuPage.BANK, MenuPage.INFORMATION)),

    BLACKSMITH("blacksmith", Category.TRADE,
            30.0F, 5.0F, 4.0F, 0.24F,
            0.55F, 0.55F, 0.55F, 0.55F, 0.95F, 0.15F,
            ScheduleTemplate.ARTISAN, Emote.HAMMER, 9, Items.IRON_INGOT,
            pages(MenuPage.TALK, MenuPage.SHOP, MenuPage.SMITHING, MenuPage.QUESTS)),

    ALCHEMIST("alchemist", Category.TRADE,
            22.0F, 2.0F, 0.0F, 0.24F,
            0.40F, 0.35F, 0.65F, 0.45F, 0.90F, 0.05F,
            ScheduleTemplate.NOCTURNAL, Emote.STIR, 6, Items.BREWING_STAND,
            pages(MenuPage.TALK, MenuPage.SHOP, MenuPage.INFIRMARY, MenuPage.QUESTS)),

    BANKER("banker", Category.TRADE,
            22.0F, 1.5F, 2.0F, 0.23F,
            0.30F, 0.60F, 0.95F, 0.30F, 0.95F, 0.20F,
            ScheduleTemplate.SHOPKEEPER, Emote.WEIGH_COIN, 3, Items.GOLD_INGOT,
            pages(MenuPage.TALK, MenuPage.BANK, MenuPage.INFORMATION)),

    SCRIBE("scribe", Category.TRADE,
            18.0F, 1.0F, 0.0F, 0.23F,
            0.20F, 0.55F, 0.40F, 0.25F, 0.95F, 0.20F,
            ScheduleTemplate.SHOPKEEPER, Emote.WRITE, 5, Items.WRITABLE_BOOK,
            pages(MenuPage.TALK, MenuPage.SHOP, MenuPage.INFORMATION, MenuPage.QUESTS)),

    // ========================================================= arcane / faith
    MAGE("mage", Category.ARCANE,
            24.0F, 3.0F, 2.0F, 0.25F,
            0.60F, 0.35F, 0.50F, 0.50F, 0.85F, 0.05F,
            ScheduleTemplate.NOCTURNAL, Emote.CAST, 5, Items.BLAZE_ROD,
            pages(MenuPage.TALK, MenuPage.SHOP, MenuPage.TRAINING, MenuPage.QUESTS)),

    PRIEST("priest", Category.ARCANE,
            24.0F, 2.0F, 2.0F, 0.23F,
            0.55F, 0.75F, 0.20F, 0.25F, 0.85F, 0.40F,
            ScheduleTemplate.CLERGY, Emote.PRAY, 5, Items.AIR,
            pages(MenuPage.TALK, MenuPage.INFIRMARY, MenuPage.QUESTS, MenuPage.INFORMATION)),

    HEALER("healer", Category.ARCANE,
            22.0F, 1.5F, 0.0F, 0.25F,
            0.50F, 0.80F, 0.30F, 0.30F, 0.90F, 0.35F,
            ScheduleTemplate.CLERGY, Emote.STIR, 6, Items.GLISTERING_MELON_SLICE,
            pages(MenuPage.TALK, MenuPage.INFIRMARY, MenuPage.SHOP)),

    SEER("seer", Category.ARCANE,
            20.0F, 2.0F, 0.0F, 0.22F,
            0.45F, 0.40F, 0.45F, 0.35F, 0.60F, 0.00F,
            ScheduleTemplate.NOCTURNAL, Emote.THINK, 3, Items.ENDER_EYE,
            pages(MenuPage.TALK, MenuPage.INFORMATION, MenuPage.QUESTS)),

    BARD("bard", Category.ARCANE,
            20.0F, 2.5F, 0.0F, 0.27F,
            0.45F, 1.00F, 0.55F, 0.40F, 0.50F, 0.55F,
            ScheduleTemplate.VAGRANT, Emote.LUTE, 5, Items.NOTE_BLOCK,
            pages(MenuPage.TALK, MenuPage.INFORMATION, MenuPage.LODGING)),

    // =============================================================== criminal
    THIEF("thief", Category.CRIMINAL,
            22.0F, 4.0F, 2.0F, 0.31F,
            0.35F, 0.30F, 0.95F, 0.60F, 0.45F, -0.20F,
            ScheduleTemplate.NOCTURNAL, Emote.SKULK, 6, Items.IRON_SWORD,
            pages(MenuPage.TALK, MenuPage.BLACK_MARKET)),

    SMUGGLER("smuggler", Category.CRIMINAL,
            26.0F, 5.0F, 3.0F, 0.28F,
            0.55F, 0.45F, 0.90F, 0.55F, 0.65F, -0.10F,
            ScheduleTemplate.NOCTURNAL, Emote.HAUL, 4, Items.IRON_SWORD,
            pages(MenuPage.TALK, MenuPage.BLACK_MARKET, MenuPage.QUESTS)),

    /**
     * The fence. Sells what nobody will list openly, and the stock rotates on
     * its own timetable rather than a shop's.
     */
    BLACK_MARKET_DEALER("black_market_dealer", Category.CRIMINAL,
            28.0F, 5.0F, 4.0F, 0.26F,
            0.50F, 0.55F, 1.00F, 0.50F, 0.80F, -0.05F,
            ScheduleTemplate.NOCTURNAL, Emote.WHISPER, 2, Items.ENDER_EYE,
            pages(MenuPage.TALK, MenuPage.BLACK_MARKET, MenuPage.BANK, MenuPage.INFORMATION)),

    BANDIT("bandit", Category.CRIMINAL,
            28.0F, 6.5F, 4.0F, 0.30F,
            0.70F, 0.25F, 0.90F, 0.85F, 0.40F, -0.40F,
            ScheduleTemplate.NOCTURNAL, Emote.TAUNT, 4, Items.IRON_AXE,
            pages(MenuPage.TALK)),

    // ================================================================= gentry
    NOBLE("noble", Category.NOBLE,
            26.0F, 3.0F, 6.0F, 0.24F,
            0.40F, 0.65F, 0.85F, 0.45F, 0.35F, 0.35F,
            ScheduleTemplate.NOBLE_LEISURE, Emote.CROSS_ARMS, 2, Items.GOLDEN_SWORD,
            pages(MenuPage.TALK, MenuPage.QUESTS, MenuPage.HIRE, MenuPage.BANK)),

    ELDER("elder", Category.NOBLE,
            22.0F, 1.5F, 2.0F, 0.20F,
            0.55F, 0.85F, 0.30F, 0.30F, 0.60F, 0.30F,
            ScheduleTemplate.NOBLE_LEISURE, Emote.THINK, 2, Items.AIR,
            pages(MenuPage.TALK, MenuPage.SETTLEMENT, MenuPage.QUESTS, MenuPage.INFORMATION));

    /** Broad grouping, used for faction reactions and spawn quotas. */
    public enum Category {
        COMMONER, MARTIAL, GUILD, TRADE, ARCANE, CRIMINAL, NOBLE
    }

    private final String key;
    private final Category category;
    private final float maxHealth;
    private final float attackDamage;
    private final float armor;
    private final float movementSpeed;
    private final float courage;
    private final float sociability;
    private final float greed;
    private final float temper;
    private final float diligence;
    private final float baseline;
    private final ScheduleTemplate schedule;
    private final Emote workEmote;
    private final int villageWeight;
    private final Item mainHand;
    private final EnumSet<MenuPage> pages;

    NpcRole(String key, Category category,
            float maxHealth, float attackDamage, float armor, float movementSpeed,
            float courage, float sociability, float greed, float temper, float diligence,
            float baseline,
            ScheduleTemplate schedule, Emote workEmote, int villageWeight, Item mainHand,
            EnumSet<MenuPage> pages) {
        this.key = key;
        this.category = category;
        this.maxHealth = maxHealth;
        this.attackDamage = attackDamage;
        this.armor = armor;
        this.movementSpeed = movementSpeed;
        this.courage = courage;
        this.sociability = sociability;
        this.greed = greed;
        this.temper = temper;
        this.diligence = diligence;
        this.baseline = baseline;
        this.schedule = schedule;
        this.workEmote = workEmote;
        this.villageWeight = villageWeight;
        this.mainHand = mainHand;
        this.pages = pages;
    }

    private static EnumSet<MenuPage> pages(MenuPage first, MenuPage... rest) {
        return EnumSet.of(first, rest);
    }

    public String key() {
        return this.key;
    }

    public Category category() {
        return this.category;
    }

    public float maxHealth() {
        return this.maxHealth;
    }

    public float attackDamage() {
        return this.attackDamage;
    }

    public float armor() {
        return this.armor;
    }

    public float movementSpeed() {
        return this.movementSpeed;
    }

    public float courage() {
        return this.courage;
    }

    public float sociability() {
        return this.sociability;
    }

    public float greed() {
        return this.greed;
    }

    public float temper() {
        return this.temper;
    }

    public float diligence() {
        return this.diligence;
    }

    public float baseline() {
        return this.baseline;
    }

    public ScheduleTemplate schedule() {
        return this.schedule;
    }

    /** The looping gesture this role settles into while working. */
    public Emote workEmote() {
        return this.workEmote;
    }

    /**
     * Relative likelihood of turning up when a settlement rolls its population.
     * Zero means the role never appears on its own and must be placed.
     */
    public int villageWeight() {
        return this.villageWeight;
    }

    public Item mainHand() {
        return this.mainHand;
    }

    public EnumSet<MenuPage> pages() {
        return EnumSet.copyOf(this.pages);
    }

    public boolean hasPage(MenuPage page) {
        return this.pages.contains(page);
    }

    /** Skin for this role, under {@code textures/entity/npc/}. */
    public ResourceLocation texture() {
        return new ResourceLocation("pokeefnpc", "textures/entity/npc/" + this.key + ".png");
    }

    public String translationKey() {
        return "pokeefnpc.role." + this.key;
    }

    /** True when this role draws steel for the settlement rather than hiding. */
    public boolean isDefender() {
        return this.category == Category.MARTIAL || this.category == Category.GUILD
                || this == BLACKSMITH || this == MAGE;
    }

    /** True when villagers and guards treat this NPC as an outlaw on sight. */
    public boolean isOutlaw() {
        return this.category == Category.CRIMINAL;
    }

    /**
     * True when the role trades in coin at all — used to decide whether an NPC
     * carries a purse worth stealing, and whether a thief bothers with it.
     */
    public boolean carriesMoney() {
        return this.category == Category.TRADE || this.category == Category.NOBLE
                || this == INNKEEPER || this == BLACK_MARKET_DEALER || this == GUILD_CLERK;
    }

    /**
     * Idle gestures this role falls into between tasks. Deliberately not the
     * work emote — this is what it does with its hands while it has nothing to
     * do, which is where most of a settlement's character comes from.
     */
    public List<Emote> idleEmotes() {
        return switch (this.category) {
            case MARTIAL -> List.of(Emote.GUARD_STANCE, Emote.SCAN_HORIZON, Emote.CROSS_ARMS,
                    Emote.SHARPEN, Emote.STRETCH);
            case CRIMINAL -> List.of(Emote.SKULK, Emote.WHISPER, Emote.COUNT_LOOT,
                    Emote.CROSS_ARMS);
            case ARCANE -> List.of(Emote.THINK, Emote.PRAY, Emote.CAST, Emote.WRITE);
            case TRADE -> List.of(Emote.WEIGH_COIN, Emote.HAGGLE, Emote.WRITE, Emote.CROSS_ARMS);
            case GUILD -> List.of(Emote.CROSS_ARMS, Emote.SHARPEN, Emote.THINK, Emote.WAVE);
            case NOBLE -> List.of(Emote.CROSS_ARMS, Emote.THINK, Emote.NOD);
            case COMMONER -> List.of(Emote.WAVE, Emote.STRETCH, Emote.THINK, Emote.SHRUG,
                    Emote.NOD);
        };
    }

    /** The gesture to use when greeting a player it likes. */
    public Emote greetEmote() {
        return switch (this.category) {
            case MARTIAL -> Emote.SALUTE;
            case NOBLE, GUILD -> Emote.NOD;
            case CRIMINAL -> Emote.WHISPER;
            case ARCANE -> Emote.BOW;
            default -> Emote.WAVE;
        };
    }

    public static NpcRole byOrdinal(int ordinal) {
        NpcRole[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }

    public static NpcRole byName(String name) {
        for (NpcRole role : values()) {
            if (role.key.equalsIgnoreCase(name)) {
                return role;
            }
        }
        return VILLAGER;
    }

    /**
     * Picks a role for a newly settled character, weighted by
     * {@link #villageWeight()}. This is what gives a village its mix without
     * anyone writing a census: mostly farmers and villagers, a few guards, one
     * elder if the roll goes that way.
     */
    public static NpcRole randomForVillage(RandomSource random) {
        int total = 0;
        for (NpcRole role : values()) {
            total += role.villageWeight;
        }
        int roll = random.nextInt(Math.max(1, total));
        for (NpcRole role : values()) {
            roll -= role.villageWeight;
            if (roll < 0) {
                return role;
            }
        }
        return VILLAGER;
    }
}
