package com.pokewing.pokeefnpc;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server-side settings.
 *
 * <p>Everything here is on the server spec rather than the client one, because
 * every switch below changes what actually happens in the world — whether NPCs
 * place blocks, whether villages grow on their own, how many people a settlement
 * supports. Those have to be the same for everyone connected.
 */
public final class PokeEFNPCConfig {

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue NATURAL_POPULATION;
    private static final ForgeConfigSpec.BooleanValue ALLOW_NPC_BUILDING;
    private static final ForgeConfigSpec.BooleanValue STEAL_FROM_PLAYERS;
    private static final ForgeConfigSpec.IntValue MAX_SETTLEMENT_POPULATION;
    private static final ForgeConfigSpec.IntValue POPULATION_INTERVAL_TICKS;
    private static final ForgeConfigSpec.DoubleValue OUTLAW_SPAWN_CHANCE;
    private static final ForgeConfigSpec.BooleanValue REPLACE_VANILLA_VILLAGERS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("PokeEFNPC - a living medieval population.").push("world");

        NATURAL_POPULATION = builder
                .comment("Let villages grow their own people over time.",
                        "Off means NPCs only exist where an operator has placed them.")
                .define("naturalPopulation", true);

        MAX_SETTLEMENT_POPULATION = builder
                .comment("Most NPCs one settlement will grow to on its own.",
                        "Hand-placed NPCs are not counted against this.")
                .defineInRange("maxSettlementPopulation", 24, 1, 200);

        POPULATION_INTERVAL_TICKS = builder
                .comment("How often a settlement is considered for a new resident, in ticks.",
                        "24000 is one in-game day.")
                .defineInRange("populationIntervalTicks", 6000, 200, 240000);

        OUTLAW_SPAWN_CHANCE = builder
                .comment("Chance that a settlement's new resident is an outlaw",
                        "(thief, smuggler, fence) rather than an honest trade.")
                .defineInRange("outlawSpawnChance", 0.08D, 0.0D, 1.0D);

        REPLACE_VANILLA_VILLAGERS = builder
                .comment("Convert vanilla villagers that spawn in villages into NPCs.",
                        "Off keeps both populations side by side.")
                .define("replaceVanillaVillagers", false);

        builder.pop().comment("What NPCs are allowed to do.").push("behaviour");

        ALLOW_NPC_BUILDING = builder
                .comment("Let NPCs place torches and fences to close gaps in their defences.",
                        "Turn this off on servers where NPCs must not alter terrain.",
                        "They still fight, flee and repair — they simply stop building.")
                .define("allowNpcBuilding", true);

        STEAL_FROM_PLAYERS = builder
                .comment("Let thieves lift items from player inventories.",
                        "Off means they steal from settlement stores instead,",
                        "so the role still works without touching player property.")
                .define("stealFromPlayers", false);

        builder.pop();
        SPEC = builder.build();
    }

    private PokeEFNPCConfig() {
    }

    public static boolean naturalPopulation() {
        return NATURAL_POPULATION.get();
    }

    public static boolean allowNpcBuilding() {
        return ALLOW_NPC_BUILDING.get();
    }

    public static boolean stealFromPlayers() {
        return STEAL_FROM_PLAYERS.get();
    }

    public static int maxSettlementPopulation() {
        return MAX_SETTLEMENT_POPULATION.get();
    }

    public static int populationIntervalTicks() {
        return POPULATION_INTERVAL_TICKS.get();
    }

    public static double outlawSpawnChance() {
        return OUTLAW_SPAWN_CHANCE.get();
    }

    public static boolean replaceVanillaVillagers() {
        return REPLACE_VANILLA_VILLAGERS.get();
    }
}
