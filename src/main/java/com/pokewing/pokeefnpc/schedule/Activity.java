package com.pokewing.pokeefnpc.schedule;

import com.pokewing.pokeefnpc.emote.Emote;

/**
 * What an NPC is trying to do with this stretch of its day.
 *
 * <p>An activity is a <i>destination plus a habit</i>: where the NPC wants to
 * stand ({@link Site}) and what it does with its hands once it gets there
 * ({@link #loopEmote()}). Keeping those together is what makes the world read as
 * purposeful — a blacksmith at midday is not "wandering near an anvil", it is at
 * its anvil, hammering, and it will walk back if you push it away.
 */
public enum Activity {

    /** In bed at home. Anything hostile nearby cancels this outright. */
    SLEEP("sleep", Site.HOME, null, 0.35F),

    /** Awake at home, before the shift starts or after it ends. */
    HOME_IDLE("home_idle", Site.HOME, Emote.STRETCH, 0.5F),

    /** At the workstation, doing the trade. The core of most days. */
    WORK("work", Site.WORKSTATION, null, 0.6F),

    /** Behind the counter, waiting for custom. Shops only open during this. */
    TEND_SHOP("tend_shop", Site.WORKSTATION, Emote.WEIGH_COIN, 0.5F),

    /** In the square with the neighbours. Where the waving and gossip happens. */
    SOCIALISE("socialise", Site.MEETING_POINT, null, 0.5F),

    /** Eating, at home or at the inn. */
    EAT("eat", Site.MEETING_POINT, Emote.DRINK, 0.5F),

    /** Walking a beat between guard posts. */
    PATROL("patrol", Site.PATROL_ROUTE, Emote.SCAN_HORIZON, 0.7F),

    /** Standing a post, not moving off it. */
    STAND_WATCH("stand_watch", Site.GUARD_POST, Emote.GUARD_STANCE, 0.6F),

    /** Repairing and extending the settlement's walls and lights. */
    FORTIFY("fortify", Site.DEFENCE_LINE, Emote.HAMMER, 0.75F),

    /** Weapons practice — how martial NPCs spend a peacetime afternoon. */
    TRAIN("train", Site.TRAINING_GROUND, Emote.SHARPEN, 0.6F),

    /** At the shrine. */
    WORSHIP("worship", Site.SHRINE, Emote.PRAY, 0.4F),

    /** Out of the settlement entirely, on the roads. Travellers and hunters. */
    ROAM("roam", Site.WILDERNESS, null, 0.8F),

    /** Working a black-market drop after dark. */
    SMUGGLE("smuggle", Site.HIDEOUT, Emote.WHISPER, 0.7F),

    /** Casing the village for something to lift. */
    PROWL("prowl", Site.WILDERNESS, Emote.SKULK, 0.65F),

    /**
     * Everything is on fire. Not scheduled — the settlement's threat tracker
     * forces every NPC into it, and each role interprets it through its own
     * courage: guards to the wall, farmers to the cellar.
     */
    EMERGENCY("emergency", Site.DEFENCE_LINE, null, 1.0F);

    /** The kind of place this activity wants to happen at. */
    public enum Site {
        HOME, WORKSTATION, MEETING_POINT, GUARD_POST, PATROL_ROUTE, DEFENCE_LINE,
        TRAINING_GROUND, SHRINE, HIDEOUT, WILDERNESS
    }

    private final String key;
    private final Site site;
    private final Emote loopEmote;
    private final float walkSpeed;

    Activity(String key, Site site, Emote loopEmote, float walkSpeed) {
        this.key = key;
        this.site = site;
        this.loopEmote = loopEmote;
        this.walkSpeed = walkSpeed;
    }

    public String key() {
        return this.key;
    }

    public Site site() {
        return this.site;
    }

    /**
     * The looping gesture to settle into once in place, or null when the role
     * supplies its own — a smith and a scribe both WORK, but they do not do the
     * same thing with their arms, so WORK defers to
     * {@code NpcRole#workEmote()}.
     */
    public Emote loopEmote() {
        return this.loopEmote;
    }

    public float walkSpeed() {
        return this.walkSpeed;
    }

    /** True when being interrupted by a customer is fine. */
    public boolean interruptible() {
        return this != SLEEP && this != EMERGENCY;
    }

    /** True when a shop or service menu will open at all. */
    public boolean openForBusiness() {
        return this == TEND_SHOP || this == WORK || this == SOCIALISE || this == SMUGGLE;
    }

    public String translationKey() {
        return "pokeefnpc.activity." + this.key;
    }

    public static Activity byOrdinal(int ordinal) {
        Activity[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
