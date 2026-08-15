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
    private static final ForgeConfigSpec.BooleanValue EPIC_FIGHT_PATCH;

    private static final ForgeConfigSpec.BooleanValue VOICE_ENABLED;
    private static final ForgeConfigSpec.DoubleValue VOICE_LISTEN_RANGE;
    private static final ForgeConfigSpec.DoubleValue VOICE_CONE_DOT;
    private static final ForgeConfigSpec.IntValue VOICE_SILENCE_MILLIS;
    private static final ForgeConfigSpec.IntValue VOICE_MIN_MILLIS;
    private static final ForgeConfigSpec.DoubleValue VOICE_SPEAK_RANGE;
    private static final ForgeConfigSpec.IntValue VOICE_THREADS;
    private static final ForgeConfigSpec.IntValue VOICE_QUEUE_SIZE;

    private static final ForgeConfigSpec.BooleanValue STT_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> STT_ENDPOINT;
    private static final ForgeConfigSpec.ConfigValue<String> STT_MODEL;
    private static final ForgeConfigSpec.ConfigValue<String> STT_LANGUAGE;
    private static final ForgeConfigSpec.IntValue STT_TIMEOUT;
    private static final ForgeConfigSpec.IntValue STT_MIN_CHARACTERS;

    private static final ForgeConfigSpec.BooleanValue TTS_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> TTS_ENDPOINT;
    private static final ForgeConfigSpec.BooleanValue TTS_JSON_BODY;
    private static final ForgeConfigSpec.ConfigValue<String> TTS_MODEL;
    private static final ForgeConfigSpec.ConfigValue<String> TTS_VOICE;
    private static final ForgeConfigSpec.DoubleValue TTS_GAIN;
    private static final ForgeConfigSpec.IntValue TTS_TIMEOUT;

    private static final ForgeConfigSpec.BooleanValue LLM_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> LLM_ENDPOINT;
    private static final ForgeConfigSpec.ConfigValue<String> LLM_MODEL;
    private static final ForgeConfigSpec.ConfigValue<String> LLM_API_KEY;
    private static final ForgeConfigSpec.DoubleValue LLM_TEMPERATURE;
    private static final ForgeConfigSpec.IntValue LLM_MAX_TOKENS;
    private static final ForgeConfigSpec.IntValue LLM_TIMEOUT;
    private static final ForgeConfigSpec.IntValue LLM_THREADS;
    private static final ForgeConfigSpec.IntValue LLM_QUEUE_SIZE;
    private static final ForgeConfigSpec.ConfigValue<String> LLM_EXTRA_PROMPT;

    private static final ForgeConfigSpec.ConfigValue<String> SKIN_URL_TEMPLATE;
    private static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> SKIN_POOL;
    private static final ForgeConfigSpec.BooleanValue SKIN_FROM_CUSTOM_NAME;

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


        builder.pop().comment(
                "Talking to NPCs out loud, through Simple Voice Chat.",
                "Simple Voice Chat is optional; without it none of this does anything.",
                "Voice input also needs a speech recogniser under [speech_to_text],",
                "and spoken replies need a synthesiser under [text_to_speech].").push("voice");

        VOICE_ENABLED = builder
                .comment("Master switch for hearing players and answering aloud.")
                .define("enabled", true);

        VOICE_LISTEN_RANGE = builder
                .comment("How far away an NPC can hear you, in blocks.")
                .defineInRange("listenRange", 6.0D, 1.0D, 32.0D);

        VOICE_CONE_DOT = builder
                .comment("How directly you must be facing an NPC to be talking TO it.",
                        "1.0 is dead-on, 0.0 is anywhere in front, negative is behind you.",
                        "0.6 is roughly a 50-degree cone, which is what stops a whole",
                        "crowded square from answering at once.")
                .defineInRange("coneDot", 0.6D, -1.0D, 1.0D);

        VOICE_SILENCE_MILLIS = builder
                .comment("Silence that ends a sentence, in milliseconds.",
                        "Lower reacts sooner but cuts people off mid-pause.")
                .defineInRange("silenceMillis", 700, 200, 5000);

        VOICE_MIN_MILLIS = builder
                .comment("Utterances shorter than this are ignored as coughs and knocks.")
                .defineInRange("minMillis", 400, 100, 5000);

        VOICE_SPEAK_RANGE = builder
                .comment("How far an NPC's spoken reply carries, in blocks.")
                .defineInRange("speakRange", 12.0D, 1.0D, 64.0D);

        VOICE_THREADS = builder
                .comment("Worker threads for speech recognition and synthesis.",
                        "These run below the game's priority and never touch the tick.")
                .defineInRange("threads", 2, 1, 8);

        VOICE_QUEUE_SIZE = builder
                .comment("Audio jobs allowed to queue before new ones are dropped.",
                        "Dropping means an NPC did not hear you - never a lagging server.")
                .defineInRange("queueSize", 8, 1, 64);

        builder.pop().comment(
                "Speech recognition, on your own machine. Nothing is sent anywhere else.",
                "Works with whisper.cpp's server and with anything exposing the",
                "OpenAI-shaped /v1/audio/transcriptions endpoint (faster-whisper etc).",
                "",
                "whisper.cpp:  ./server -m models/ggml-base.bin --port 8080",
                "              endpoint = http://127.0.0.1:8080/inference")
                .push("speech_to_text");

        STT_ENABLED = builder.define("enabled", true);
        STT_ENDPOINT = builder
                .comment("Full URL of the transcription endpoint.")
                .define("endpoint", "http://127.0.0.1:8080/inference");
        STT_MODEL = builder
                .comment("Model name, for servers that want one. Ignored by whisper.cpp.")
                .define("model", "whisper-1");
        STT_LANGUAGE = builder
                .comment("Spoken language as a two-letter code, or 'auto' to detect.",
                        "Naming it is both faster and more accurate than detection.")
                .define("language", "auto");
        STT_TIMEOUT = builder
                .comment("How long to wait for a transcription, in milliseconds.")
                .defineInRange("timeoutMillis", 12000, 1000, 60000);
        STT_MIN_CHARACTERS = builder
                .comment("Transcriptions shorter than this are treated as noise.")
                .defineInRange("minCharacters", 2, 1, 64);

        builder.pop().comment(
                "Speech synthesis, on your own machine.",
                "",
                "Piper (free, fast, offline):",
                "  piper --model en_GB-alan-medium.onnx --http --port 5000",
                "  endpoint = http://127.0.0.1:5000  |  jsonBody = false",
                "",
                "Anything OpenAI-shaped:",
                "  endpoint = http://127.0.0.1:8880/v1/audio/speech  |  jsonBody = true")
                .push("text_to_speech");

        TTS_ENABLED = builder.define("enabled", true);
        TTS_ENDPOINT = builder
                .comment("Full URL of the synthesis endpoint.")
                .define("endpoint", "http://127.0.0.1:5000");
        TTS_JSON_BODY = builder
                .comment("true sends OpenAI-shaped JSON; false posts the line as plain text.",
                        "Piper's own HTTP server wants plain text.")
                .define("jsonBody", false);
        TTS_MODEL = builder.define("model", "tts-1");
        TTS_VOICE = builder
                .comment("Voice name, for servers that offer a choice.",
                        "Leave EMPTY for Piper, which is already given its voice with",
                        "its -m flag and treats this as a model id it cannot find.",
                        "Each NPC is pitched around whatever voice is used, from its role",
                        "and its id, so one voice model still produces a village of",
                        "distinct people.")
                .define("voice", "");
        TTS_GAIN = builder
                .comment("Volume multiplier. Synthesised speech is usually quieter than a",
                        "player's microphone, so it generally wants lifting.")
                .defineInRange("gain", 1.6D, 0.1D, 8.0D);
        TTS_TIMEOUT = builder
                .comment("How long to wait for audio, in milliseconds.")
                .defineInRange("timeoutMillis", 15000, 1000, 60000);

        builder.pop().comment(
                "A language model on your own machine, so NPCs can answer things",
                "nobody wrote for them. Free, offline, and no account anywhere.",
                "",
                "Ollama:    ollama serve && ollama pull llama3.2:3b",
                "           endpoint = http://127.0.0.1:11434/v1/chat/completions",
                "llama.cpp: ./llama-server -m model.gguf --port 8081",
                "           endpoint = http://127.0.0.1:8081/v1/chat/completions",
                "",
                "The model only changes what a character SAYS and how it carries",
                "itself. It cannot set prices, hand out items or accept contracts -",
                "every one of those still goes through the normal, checked path.")
                .push("language_model");

        LLM_ENABLED = builder
                .comment("Off means NPCs use only their written dialogue.")
                .define("enabled", false);
        LLM_ENDPOINT = builder
                .define("endpoint", "http://127.0.0.1:11434/v1/chat/completions");
        LLM_MODEL = builder
                .comment("A small instruct model is the right choice here: it has to answer",
                        "in under a second on the same machine that is running the game.")
                .define("model", "llama3.2:3b");
        LLM_API_KEY = builder
                .comment("Left empty for local runners, which do not check it.")
                .define("apiKey", "");
        LLM_TEMPERATURE = builder
                .defineInRange("temperature", 0.8D, 0.0D, 2.0D);
        LLM_MAX_TOKENS = builder
                .comment("Hard cap on reply length. A villager answers in a sentence or two.")
                .defineInRange("maxTokens", 80, 16, 512);
        LLM_TIMEOUT = builder
                .defineInRange("timeoutMillis", 8000, 500, 60000);
        LLM_THREADS = builder
                .comment("Concurrent requests. More than one only helps if your model",
                        "server can actually serve them in parallel.")
                .defineInRange("threads", 1, 1, 8);
        LLM_QUEUE_SIZE = builder
                .comment("Pending requests before new ones are dropped in favour of",
                        "the NPC's written dialogue.")
                .defineInRange("queueSize", 4, 1, 32);
        LLM_EXTRA_PROMPT = builder
                .comment("Appended to every character's prompt. Use it for the tone of",
                        "your world, house rules, or a language instruction such as",
                        "'Always answer in Turkish.'")
                .define("extraPrompt", "");

        builder.pop().comment(
                "Where NPC skins come from.",
                "",
                "NameMC has no public API, and scraping it is against its terms - but",
                "what it displays is Mojang's own profile data, so that is read",
                "directly instead: the same skin, from the source. Put any account",
                "names you like in the pool below and NPCs will wear them.")
                .push("skins");

        SKIN_URL_TEMPLATE = builder
                .comment("Optional direct URL template with {name} in it, used INSTEAD of",
                        "the Mojang lookup. For offline-mode servers, a mirror, or any",
                        "skin site with direct image URLs. Empty = use Mojang.")
                .define("urlTemplate", "");

        SKIN_POOL = builder
                .comment("Account names NPCs draw their skins from. Empty = every NPC",
                        "wears the skin shipped for its role, or the default skin.")
                .defineList("pool", java.util.List.of(),
                        entry -> entry instanceof String name && name.length() <= 16);

        SKIN_FROM_CUSTOM_NAME = builder
                .comment("Let a name tag choose the skin: rename an NPC to an account",
                        "name and it wears that account's skin.")
                .define("fromCustomName", true);

        EPIC_FIGHT_PATCH = builder
                .comment("Give NPCs Epic Fight's combat, animations and renderer.",
                        "Installed by the mod itself - no datapack. Ignored entirely",
                        "when Epic Fight is not present. Turn off to keep vanilla",
                        "combat and the mod's own face rendering.")
                .define("epicFightPatch", true);

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

    public static boolean epicFightPatch() {
        return EPIC_FIGHT_PATCH.get();
    }

    // ------------------------------------------------------------------ voice

    public static boolean voiceEnabled() {
        return VOICE_ENABLED.get();
    }

    public static double voiceListenRange() {
        return VOICE_LISTEN_RANGE.get();
    }

    public static double voiceConeDot() {
        return VOICE_CONE_DOT.get();
    }

    public static int voiceSilenceMillis() {
        return VOICE_SILENCE_MILLIS.get();
    }

    public static int voiceMinMillis() {
        return VOICE_MIN_MILLIS.get();
    }

    public static double voiceSpeakRange() {
        return VOICE_SPEAK_RANGE.get();
    }

    public static int voiceThreads() {
        return VOICE_THREADS.get();
    }

    public static int voiceQueueSize() {
        return VOICE_QUEUE_SIZE.get();
    }

    // ---------------------------------------------------------------- hearing

    public static boolean sttEnabled() {
        return STT_ENABLED.get();
    }

    public static String sttEndpoint() {
        return STT_ENDPOINT.get();
    }

    public static String sttModel() {
        return STT_MODEL.get();
    }

    public static String sttLanguage() {
        return STT_LANGUAGE.get();
    }

    public static int sttTimeoutMillis() {
        return STT_TIMEOUT.get();
    }

    public static int sttMinCharacters() {
        return STT_MIN_CHARACTERS.get();
    }

    // ---------------------------------------------------------------- speaking

    public static boolean ttsEnabled() {
        return TTS_ENABLED.get();
    }

    public static String ttsEndpoint() {
        return TTS_ENDPOINT.get();
    }

    public static boolean ttsJsonBody() {
        return TTS_JSON_BODY.get();
    }

    public static String ttsModel() {
        return TTS_MODEL.get();
    }

    public static String ttsVoice() {
        return TTS_VOICE.get();
    }

    public static double ttsGain() {
        return TTS_GAIN.get();
    }

    public static int ttsTimeoutMillis() {
        return TTS_TIMEOUT.get();
    }

    // ---------------------------------------------------------------- thinking

    public static boolean llmEnabled() {
        return LLM_ENABLED.get();
    }

    public static String llmEndpoint() {
        return LLM_ENDPOINT.get();
    }

    public static String llmModel() {
        return LLM_MODEL.get();
    }

    public static String llmApiKey() {
        return LLM_API_KEY.get();
    }

    public static double llmTemperature() {
        return LLM_TEMPERATURE.get();
    }

    public static int llmMaxTokens() {
        return LLM_MAX_TOKENS.get();
    }

    public static int llmTimeoutMillis() {
        return LLM_TIMEOUT.get();
    }

    public static int llmThreads() {
        return LLM_THREADS.get();
    }

    public static int llmQueueSize() {
        return LLM_QUEUE_SIZE.get();
    }

    public static String llmExtraPrompt() {
        return LLM_EXTRA_PROMPT.get();
    }

    // ------------------------------------------------------------------ skins

    public static String skinUrlTemplate() {
        return SKIN_URL_TEMPLATE.get();
    }

    @SuppressWarnings("unchecked")
    public static java.util.List<String> skinPool() {
        return (java.util.List<String>) SKIN_POOL.get();
    }

    public static boolean skinFromCustomName() {
        return SKIN_FROM_CUSTOM_NAME.get();
    }
}
