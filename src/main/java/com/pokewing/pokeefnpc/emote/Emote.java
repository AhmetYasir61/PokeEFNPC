package com.pokewing.pokeefnpc.emote;

import com.pokewing.pokeefnpc.npc.Emotion;

/**
 * Every gesture an NPC can play, one-shot or looping.
 *
 * <p>An emote carries three things at once, and that is what lets the same enum
 * value serve three very different playback paths:
 *
 * <ul>
 *   <li>{@link #epicFightAnimation()} — the animation to ask Epic Fight for when
 *       Epic Fight is installed and the NPC has a patch. Nothing here is
 *       required to exist; a missing animation just falls through.</li>
 *   <li>{@link #pose()} — a procedural limb pose the client animates itself.
 *       This is the fallback, and it is why the emotes still read on a vanilla
 *       install with no Epic Fight at all.</li>
 *   <li>{@link #emotion()} — the face that goes with the gesture, so a bow looks
 *       courteous and a jeer looks nasty rather than both being played
 *       deadpan.</li>
 * </ul>
 *
 * <p>{@link #looping()} is what the "loop emote" behaviour hangs off: a looping
 * emote is one an NPC can settle into for a whole work shift — hammering,
 * sweeping, sowing, playing a lute — while a one-shot is a reaction it fires and
 * returns from.
 */
public enum Emote {

    // ---------------------------------------------------------------- social
    WAVE("wave", Pose.WAVE, false, 30, Emotion.HAPPY, "biped/living/wave"),
    BOW("bow", Pose.BOW, false, 40, Emotion.NEUTRAL, "biped/living/bow"),
    SALUTE("salute", Pose.SALUTE, false, 30, Emotion.FOCUSED, "biped/living/salute"),
    NOD("nod", Pose.NOD, false, 20, Emotion.NEUTRAL, null),
    SHAKE_HEAD("shake_head", Pose.SHAKE_HEAD, false, 24, Emotion.SAD, null),
    CLAP("clap", Pose.CLAP, true, 40, Emotion.HAPPY, "biped/living/clap"),
    CHEER("cheer", Pose.CHEER, true, 40, Emotion.HAPPY, "biped/living/cheer"),
    LAUGH("laugh", Pose.LAUGH, false, 40, Emotion.HAPPY, null),
    POINT("point", Pose.POINT, false, 30, Emotion.FOCUSED, "biped/living/point"),
    BECKON("beckon", Pose.BECKON, false, 30, Emotion.HAPPY, null),
    SHRUG("shrug", Pose.SHRUG, false, 30, Emotion.NEUTRAL, null),
    FACEPALM("facepalm", Pose.FACEPALM, false, 45, Emotion.SAD, null),
    THINK("think", Pose.THINK, true, 60, Emotion.FOCUSED, null),
    CROSS_ARMS("cross_arms", Pose.CROSS_ARMS, true, 80, Emotion.NEUTRAL, null),
    JEER("jeer", Pose.JEER, false, 35, Emotion.ANGRY, null),
    WEEP("weep", Pose.WEEP, true, 60, Emotion.SAD, null),
    BEG("beg", Pose.BEG, true, 60, Emotion.SAD, null),
    SHIVER("shiver", Pose.SHIVER, true, 40, Emotion.TIRED, null),
    STRETCH("stretch", Pose.STRETCH, false, 45, Emotion.TIRED, null),

    // ------------------------------------------------------------ occupation
    HAMMER("hammer", Pose.HAMMER, true, 30, Emotion.FOCUSED, "biped/living/smith"),
    SWEEP("sweep", Pose.SWEEP, true, 50, Emotion.NEUTRAL, null),
    SOW("sow", Pose.SOW, true, 45, Emotion.NEUTRAL, null),
    HARVEST("harvest", Pose.HARVEST, true, 40, Emotion.FOCUSED, null),
    MINE_SWING("mine_swing", Pose.HAMMER, true, 28, Emotion.FOCUSED, "biped/living/mine"),
    STIR("stir", Pose.STIR, true, 50, Emotion.FOCUSED, null),
    WRITE("write", Pose.WRITE, true, 60, Emotion.FOCUSED, null),
    WEIGH_COIN("weigh_coin", Pose.WEIGH_COIN, true, 45, Emotion.FOCUSED, null),
    HAGGLE("haggle", Pose.HAGGLE, false, 35, Emotion.FOCUSED, null),
    LUTE("lute", Pose.LUTE, true, 60, Emotion.HAPPY, null),
    PRAY("pray", Pose.PRAY, true, 80, Emotion.NEUTRAL, null),
    CAST("cast", Pose.CAST, false, 40, Emotion.FOCUSED, "biped/living/cast"),
    DRINK("drink", Pose.DRINK, false, 40, Emotion.HAPPY, "biped/living/drink"),
    SHARPEN("sharpen", Pose.SHARPEN, true, 40, Emotion.FOCUSED, null),
    HAUL("haul", Pose.HAUL, true, 60, Emotion.TIRED, null),

    // ------------------------------------------------------------- martial
    GUARD_STANCE("guard_stance", Pose.GUARD_STANCE, true, 80, Emotion.FOCUSED, "biped/living/guard"),
    TAUNT("taunt", Pose.TAUNT, false, 35, Emotion.ANGRY, "biped/living/taunt"),
    ROAR("roar", Pose.ROAR, false, 40, Emotion.ANGRY, "biped/living/roar"),
    SCAN_HORIZON("scan_horizon", Pose.SCAN_HORIZON, true, 70, Emotion.FOCUSED, null),
    DRAW_WEAPON("draw_weapon", Pose.DRAW_WEAPON, false, 25, Emotion.FOCUSED, null),

    // ------------------------------------------------------------- criminal
    SKULK("skulk", Pose.SKULK, true, 60, Emotion.FOCUSED, null),
    PICKPOCKET("pickpocket", Pose.PICKPOCKET, false, 35, Emotion.FOCUSED, null),
    WHISPER("whisper", Pose.WHISPER, false, 40, Emotion.FOCUSED, null),
    COUNT_LOOT("count_loot", Pose.WEIGH_COIN, true, 45, Emotion.HAPPY, null);

    /**
     * Which procedural limb routine the client runs when Epic Fight is not
     * driving the body. Kept as its own enum so several emotes can share one
     * routine (a miner's swing and a smith's hammer are the same motion).
     */
    public enum Pose {
        WAVE, BOW, SALUTE, NOD, SHAKE_HEAD, CLAP, CHEER, LAUGH, POINT, BECKON, SHRUG,
        FACEPALM, THINK, CROSS_ARMS, JEER, WEEP, BEG, SHIVER, STRETCH,
        HAMMER, SWEEP, SOW, HARVEST, STIR, WRITE, WEIGH_COIN, HAGGLE, LUTE, PRAY,
        CAST, DRINK, SHARPEN, HAUL,
        GUARD_STANCE, TAUNT, ROAR, SCAN_HORIZON, DRAW_WEAPON,
        SKULK, PICKPOCKET, WHISPER
    }

    private final String key;
    private final Pose pose;
    private final boolean looping;
    private final int durationTicks;
    private final Emotion emotion;
    private final String epicFightAnimation;

    Emote(String key, Pose pose, boolean looping, int durationTicks, Emotion emotion,
          String epicFightAnimation) {
        this.key = key;
        this.pose = pose;
        this.looping = looping;
        this.durationTicks = durationTicks;
        this.emotion = emotion;
        this.epicFightAnimation = epicFightAnimation;
    }

    public String key() {
        return this.key;
    }

    public Pose pose() {
        return this.pose;
    }

    /** True when this gesture is meant to be held and repeated, not fired once. */
    public boolean looping() {
        return this.looping;
    }

    /** Length of one cycle, in ticks. A looping emote repeats this. */
    public int durationTicks() {
        return this.durationTicks;
    }

    /** The face that belongs with this gesture. */
    public Emotion emotion() {
        return this.emotion;
    }

    /**
     * Path of the Epic Fight animation to try, without the namespace, or null
     * when this emote has no Epic Fight counterpart and should always run
     * procedurally.
     */
    public String epicFightAnimation() {
        return this.epicFightAnimation;
    }

    public String translationKey() {
        return "pokeefnpc.emote." + this.key;
    }

    public static Emote byOrdinal(int ordinal) {
        Emote[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }

    public static Emote byName(String name) {
        for (Emote e : values()) {
            if (e.key.equalsIgnoreCase(name)) {
                return e;
            }
        }
        return WAVE;
    }
}
