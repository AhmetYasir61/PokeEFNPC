package com.pokewing.pokeefnpc.menu;

/**
 * The tabs an NPC's interface can be made of.
 *
 * <p>Every role gets its own menu, but not by way of its own menu class. A role
 * declares which pages it offers and the screen assembles exactly those, so the
 * guild master's window genuinely is a different window from the fence's — a
 * different tab strip, different title, different trim colour — while there is
 * still only one container type to register and one packet to keep in sync.
 * Adding a role is then a line in an enum, not a new screen.
 */
public enum MenuPage {

    /** Talk. Every NPC has this, and it is always the first tab. */
    TALK("talk", 0xFFB9A27A),

    /** Ordinary buying and selling at listed prices. */
    SHOP("shop", 0xFF7FA860),

    /** Contracts on offer, and the ones this player is carrying. */
    QUESTS("quests", 0xFFC9A227),

    /** Rank, standing and dues for whichever order this NPC speaks for. */
    GUILD("guild", 0xFF8E6BC4),

    /** Off-book goods: no listed prices, stock rotates, asks about reputation. */
    BLACK_MARKET("black_market", 0xFF4A4A55),

    /** Repairs, sharpening, reforging — services rather than goods. */
    SMITHING("smithing", 0xFFB06A3B),

    /** Deposits, loans and the running tally of what a player owes. */
    BANK("bank", 0xFFD4AF37),

    /** Paid instruction: buy a technique, a stance, a blessing. */
    TRAINING("training", 0xFF6E9BC4),

    /** Hire this NPC, or a squad of them, for a stretch of time. */
    HIRE("hire", 0xFFA85C5C),

    /** Rumours, maps and directions — what a settlement knows about the world. */
    INFORMATION("information", 0xFF5C9AA8),

    /** Bed for the night, meals, and stabling. */
    LODGING("lodging", 0xFFC48E6B),

    /** Healing, cures and blessings. */
    INFIRMARY("infirmary", 0xFFC46B93),

    /** The settlement's own books: population, defences, threat, stores. */
    SETTLEMENT("settlement", 0xFF6BA88E);

    private final String key;
    private final int accentColor;

    MenuPage(String key, int accentColor) {
        this.key = key;
        this.accentColor = accentColor;
    }

    public String key() {
        return this.key;
    }

    /** ARGB trim colour, so each kind of business looks like itself. */
    public int accentColor() {
        return this.accentColor;
    }

    public String translationKey() {
        return "pokeefnpc.page." + this.key;
    }

    public static MenuPage byOrdinal(int ordinal) {
        MenuPage[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
