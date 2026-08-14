package com.pokewing.pokeefnpc.guild;

/**
 * How far up an order a player has climbed.
 *
 * <p>Rank is earned by finishing contracts and is spent on access: better work,
 * better stock, and a fence's real inventory. Because it is a single ladder
 * shared by every guild-affiliated role, a player who has proved themselves to
 * the adventurers' hall is also somebody the guild clerk will talk to properly —
 * which is the point of belonging to something.
 */
public enum GuildRank {

    OUTSIDER("outsider", 0, 0xFF9E9E9E),
    INITIATE("initiate", 10, 0xFFB0A18A),
    JOURNEYMAN("journeyman", 40, 0xFF7FA860),
    VETERAN("veteran", 120, 0xFF6E9BC4),
    MASTER("master", 300, 0xFF8E6BC4),
    GRANDMASTER("grandmaster", 700, 0xFFD4AF37);

    private final String key;
    private final int pointsRequired;
    private final int color;

    GuildRank(String key, int pointsRequired, int color) {
        this.key = key;
        this.pointsRequired = pointsRequired;
        this.color = color;
    }

    public String key() {
        return this.key;
    }

    public int pointsRequired() {
        return this.pointsRequired;
    }

    public int color() {
        return this.color;
    }

    /** Ordinal doubles as the rank number that gates stock and contracts. */
    public int level() {
        return ordinal();
    }

    public String translationKey() {
        return "pokeefnpc.rank." + this.key;
    }

    /** Cut of the price a member of this rank is spared, as a multiplier. */
    public float discount() {
        return 1.0F - level() * 0.03F;
    }

    public static GuildRank forPoints(int points) {
        GuildRank best = OUTSIDER;
        for (GuildRank rank : values()) {
            if (points >= rank.pointsRequired) {
                best = rank;
            }
        }
        return best;
    }

    /** Points still owed before the next promotion, or 0 at the top. */
    public static int pointsToNext(int points) {
        GuildRank current = forPoints(points);
        GuildRank[] values = values();
        if (current.ordinal() + 1 >= values.length) {
            return 0;
        }
        return values[current.ordinal() + 1].pointsRequired - points;
    }
}
