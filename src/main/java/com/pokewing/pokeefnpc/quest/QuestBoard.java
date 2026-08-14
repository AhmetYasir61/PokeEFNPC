package com.pokewing.pokeefnpc.quest;

import com.pokewing.pokeefnpc.npc.NpcRole;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The contracts one NPC currently has to offer.
 *
 * <p>Boards rewrite themselves. Contracts lapse, new ones are posted, and what
 * gets posted depends on the role and on how the settlement is doing — a
 * prosperous village asks for luxuries and pays well, a struggling one asks for
 * food and pays what it can. That is the whole reward economy: no static tables,
 * just the settlement's own state read back out as work.
 */
public final class QuestBoard {

    /** How many contracts one NPC keeps posted. */
    private static final int MAX_OPEN = 3;
    /** How long a contract stays up, in ticks (about a fortnight of game days). */
    private static final long LIFETIME = 24000L * 14L;
    /** How often the board is even considered for a rewrite. */
    private static final long REFRESH_INTERVAL = 24000L;

    private final List<Quest> open = new ArrayList<>();
    private long lastRefresh;

    public List<Quest> open() {
        return this.open;
    }

    /** The contracts a player of this guild rank is allowed to see. */
    public List<Quest> visibleTo(int guildRank) {
        List<Quest> visible = new ArrayList<>();
        for (Quest quest : this.open) {
            if (quest.requiredRank() <= guildRank) {
                visible.add(quest);
            }
        }
        return visible;
    }

    public Quest byId(java.util.UUID id) {
        for (Quest quest : this.open) {
            if (quest.id().equals(id)) {
                return quest;
            }
        }
        return null;
    }

    public void remove(Quest quest) {
        this.open.remove(quest);
    }

    /**
     * Clears out anything stale and posts replacements up to {@link #MAX_OPEN}.
     * Cheap to call often — it does nothing until the interval has elapsed.
     */
    public void refresh(RandomSource random, NpcRole role, long gameTime, float prosperity) {
        if (!offersWork(role)) {
            return;
        }
        this.open.removeIf(quest -> quest.expired(gameTime));
        if (gameTime - this.lastRefresh < REFRESH_INTERVAL && this.open.size() >= MAX_OPEN) {
            return;
        }
        this.lastRefresh = gameTime;
        while (this.open.size() < MAX_OPEN) {
            this.open.add(generate(random, role, gameTime, prosperity));
        }
    }

    private static boolean offersWork(NpcRole role) {
        return role.hasPage(com.pokewing.pokeefnpc.menu.MenuPage.QUESTS);
    }

    private Quest generate(RandomSource random, NpcRole role, long gameTime, float prosperity) {
        // A poor settlement pays less and asks for staples; a rich one pays well
        // and asks for things it wants rather than things it needs.
        float payScale = 0.5F + prosperity * 1.5F;

        Quest quest = switch (role.category()) {
            case MARTIAL -> huntContract(random, payScale);
            case GUILD -> random.nextBoolean()
                    ? huntContract(random, payScale) : scoutContract(random, payScale);
            case ARCANE -> gatherContract(random, payScale, ARCANE_WANTS);
            case TRADE -> random.nextBoolean()
                    ? gatherContract(random, payScale, TRADE_WANTS)
                    : deliverContract(random, payScale);
            case CRIMINAL -> gatherContract(random, payScale, CRIMINAL_WANTS);
            case NOBLE -> random.nextBoolean()
                    ? scoutContract(random, payScale) : gatherContract(random, payScale, LUXURIES);
            case COMMONER -> gatherContract(random, payScale,
                    prosperity < 0.4F ? STAPLES : TRADE_WANTS);
        };

        // The best-paying work is reserved for people the guild already knows.
        if (quest.rewardEmeralds() > 40) {
            quest.withRank(2);
        }
        return quest.issuedBy(null, gameTime, LIFETIME);
    }

    private static final Item[] STAPLES = {Items.WHEAT, Items.BREAD, Items.POTATO, Items.CARROT,
            Items.COOKED_BEEF, Items.OAK_LOG, Items.COAL};
    private static final Item[] TRADE_WANTS = {Items.IRON_INGOT, Items.LEATHER, Items.STRING,
            Items.PAPER, Items.COPPER_INGOT, Items.STONE_BRICKS};
    private static final Item[] ARCANE_WANTS = {Items.LAPIS_LAZULI, Items.BLAZE_ROD,
            Items.SPIDER_EYE, Items.NETHER_WART, Items.GHAST_TEAR, Items.PHANTOM_MEMBRANE};
    private static final Item[] CRIMINAL_WANTS = {Items.GUNPOWDER, Items.ENDER_PEARL,
            Items.GOLD_INGOT, Items.TNT};
    private static final Item[] LUXURIES = {Items.DIAMOND, Items.GOLD_BLOCK, Items.EMERALD_BLOCK,
            Items.ENCHANTED_BOOK};

    private static final EntityType<?>[] BOUNTIES = {EntityType.ZOMBIE, EntityType.SKELETON,
            EntityType.SPIDER, EntityType.CREEPER, EntityType.WITCH, EntityType.PILLAGER,
            EntityType.ENDERMAN, EntityType.DROWNED};

    private Quest gatherContract(RandomSource random, float payScale, Item[] pool) {
        Item item = pool[random.nextInt(pool.length)];
        int count = 4 + random.nextInt(20);
        int pay = Math.max(2, Math.round(count * 0.8F * payScale));
        return Quest.gather(item, count, pay);
    }

    private Quest deliverContract(RandomSource random, float payScale) {
        Item item = TRADE_WANTS[random.nextInt(TRADE_WANTS.length)];
        int count = 2 + random.nextInt(8);
        int pay = Math.max(4, Math.round(count * 2.0F * payScale));
        return Quest.deliver(item, count, pay);
    }

    private Quest huntContract(RandomSource random, float payScale) {
        EntityType<?> monster = BOUNTIES[random.nextInt(BOUNTIES.length)];
        int count = 3 + random.nextInt(10);
        int pay = Math.max(6, Math.round(count * 3.5F * payScale));
        return Quest.hunt(monster, count, pay);
    }

    private Quest scoutContract(RandomSource random, float payScale) {
        // Somewhere out of sight but not out of reach: far enough to be a
        // journey, near enough to be worth the emeralds.
        int distance = 200 + random.nextInt(800);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        BlockPos destination = new BlockPos(
                (int) (Math.cos(angle) * distance), 64, (int) (Math.sin(angle) * distance));
        int pay = Math.max(10, Math.round(distance / 20.0F * payScale));
        return Quest.scout(destination, pay);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Quest quest : this.open) {
            list.add(quest.save());
        }
        tag.put("Open", list);
        tag.putLong("LastRefresh", this.lastRefresh);
        return tag;
    }

    public static QuestBoard load(CompoundTag tag) {
        QuestBoard board = new QuestBoard();
        ListTag list = tag.getList("Open", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            board.open.add(Quest.load(list.getCompound(i)));
        }
        board.lastRefresh = tag.getLong("LastRefresh");
        return board;
    }
}
