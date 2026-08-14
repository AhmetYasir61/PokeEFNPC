package com.pokewing.pokeefnpc.npc;

import net.minecraft.util.RandomSource;

/**
 * Names for the people the world makes on its own.
 *
 * <p>A name is the cheapest way to turn a spawned mob into somebody, so every
 * NPC gets one. Martial and noble roles draw a byname off their station — "Ser
 * Roderic", "Aldric of the Watch" — which is what makes a captain read as a
 * captain in the corner of the screen before you have clicked on them.
 */
public final class NpcNames {

    private static final String[] GIVEN = {
            "Alaric", "Bram", "Cedric", "Dagna", "Edda", "Fenn", "Gareth", "Halla",
            "Ivo", "Jorunn", "Kell", "Lysa", "Milo", "Nedra", "Osric", "Perrin",
            "Quill", "Roderic", "Sable", "Torvald", "Ulla", "Varek", "Wynn", "Yorick",
            "Astrid", "Berta", "Corin", "Dorn", "Elka", "Faring", "Greta", "Hobb",
            "Ilsa", "Jarek", "Kestrel", "Lorne", "Mabel", "Nils", "Odile", "Piet",
    };

    private static final String[] SURNAME = {
            "Ashdown", "Blackbriar", "Coldwell", "Dunmoor", "Everly", "Fairbrook",
            "Grimsby", "Hollowend", "Ironshaw", "Larkspur", "Marchwood", "Northgate",
            "Oakhurst", "Pinefall", "Quarrick", "Ravensworth", "Stonebridge",
            "Thornfield", "Underhill", "Wraymere",
    };

    private NpcNames() {
    }

    public static String pick(RandomSource random, NpcRole role) {
        String given = GIVEN[random.nextInt(GIVEN.length)];
        return switch (role) {
            case KNIGHT, CAPTAIN -> "Ser " + given;
            case GUILD_MASTER, ADVENTURER_CHIEF -> "Master " + given;
            case PRIEST, HEALER -> "Brother " + given;
            case SEER -> "Old " + given;
            case ELDER -> "Elder " + given;
            case NOBLE -> "Lord " + given;
            case BEGGAR, BANDIT, THIEF -> given;
            default -> given + " " + SURNAME[random.nextInt(SURNAME.length)];
        };
    }
}
