package com.pokewing.pokeefnpc.quest;

/**
 * The shapes a contract can take.
 *
 * <p>Kept to four on purpose. Each one is checkable from information the server
 * already has — what is in a player's bags, what they have killed since accepting,
 * where they have stood — so no quest needs its own tracking machinery, and a new
 * contract is a row of data rather than a new subsystem.
 */
public enum QuestType {

    /**
     * Bring back a quantity of something. Verified against the player's
     * inventory at turn-in, so it needs no tracking at all while it is open.
     */
    GATHER("gather"),

    /**
     * Kill a number of a given kind of monster. Counted by the death handler
     * from the moment the contract is accepted, so old kills do not pay.
     */
    HUNT("hunt"),

    /**
     * Carry goods to a named person elsewhere. Same check as GATHER, but it must
     * be handed in to a different NPC than the one who set it.
     */
    DELIVER("deliver"),

    /**
     * Reach somewhere and come back. The position is recorded when the contract
     * is written and ticked off the first time the player stands near it.
     */
    SCOUT("scout");

    private final String key;

    QuestType(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    public String translationKey() {
        return "pokeefnpc.quest.type." + this.key;
    }

    /** True when the death handler needs to watch this contract. */
    public boolean tracksKills() {
        return this == HUNT;
    }

    /** True when progress is only established at the moment of turn-in. */
    public boolean checkedAtTurnIn() {
        return this == GATHER || this == DELIVER;
    }

    public static QuestType byOrdinal(int ordinal) {
        QuestType[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
