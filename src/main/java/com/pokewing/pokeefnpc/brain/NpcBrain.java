package com.pokewing.pokeefnpc.brain;

import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.PokeEFNPCConfig;
import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.npc.Personality;
import com.pokewing.pokeefnpc.npc.Reputation;
import com.pokewing.pokeefnpc.schedule.ScheduleTemplate;
import com.pokewing.pokeefnpc.settlement.Settlement;
import com.pokewing.pokeefnpc.voice.NpcVoiceBridge;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gives one NPC something to say that it was not written to say.
 *
 * <p>The model is never asked "what would a villager say?". It is told <i>who
 * this particular person is</i> — its trade, the traits it was rolled with, the
 * mood the simulation has it in right now, what it thinks of the player it is
 * talking to, what hour it is, and how its settlement is faring — and asked to
 * answer as that person. So the same question put to a contented baker and to a
 * frightened one during a raid gets genuinely different answers, because the
 * facts in the prompt are different facts.
 *
 * <p>The simulation stays authoritative. The model can <i>express</i> a mood
 * through a tag, but it cannot invent a price, hand out an item, accept a
 * contract or change a reputation — every one of those goes through
 * {@code NpcActionPacket} exactly as before. What the model changes is what the
 * character says and how it carries itself, and nothing else.
 *
 * <p><b>Nothing here runs on the server thread.</b> Requests go to a small
 * bounded pool; replies come back onto the server thread through the server's
 * own task queue. If the pool is full the request is dropped and the NPC uses
 * its written line — a slow model can make the world quieter, never laggier.
 */
public final class NpcBrain {

    /** How many turns of one conversation are remembered. */
    private static final int MEMORY_TURNS = 6;
    /** Conversations kept before the oldest is forgotten. */
    private static final int MAX_CONVERSATIONS = 64;

    private static ExecutorService executor;
    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();

    /**
     * Rolling memory, keyed by NPC and player together — an NPC remembers each
     * person separately, which is what lets it say "you again".
     */
    private static final Map<String, Deque<LlmClient.Message>> CONVERSATIONS = new HashMap<>();

    private NpcBrain() {
    }

    public static synchronized void start() {
        if (executor != null) {
            return;
        }
        int threads = Math.max(1, PokeEFNPCConfig.llmThreads());
        executor = new ThreadPoolExecutor(threads, threads, 30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(PokeEFNPCConfig.llmQueueSize()),
                runnable -> {
                    Thread thread = new Thread(runnable, "PokeEFNPC-brain");
                    thread.setDaemon(true);
                    // Below the game. A local model will happily eat every core
                    // it is given, and the server tick must win that fight.
                    thread.setPriority(Thread.MIN_PRIORITY);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardPolicy());
    }

    public static synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        CONVERSATIONS.clear();
    }

    /** True when an NPC should try the model rather than its written lines. */
    public static boolean available() {
        return executor != null && LlmClient.available();
    }

    /**
     * Asks the NPC to answer something a player said or spoke aloud.
     *
     * <p>Returns immediately. The reply — if there is one — arrives on the server
     * thread a moment later as chat, a mood shift, a gesture, and spoken audio
     * when voice output is configured.
     *
     * @param heard  what the player typed or said
     * @param spoken true when this came in over voice, so the answer goes back
     *               out over voice as well
     */
    public static void converse(NpcEntity npc, ServerPlayer player, String heard, boolean spoken) {
        if (!available() || heard == null || heard.isBlank()) {
            return;
        }
        if (IN_FLIGHT.get() >= PokeEFNPCConfig.llmQueueSize()) {
            return;
        }
        MinecraftServer server = npc.getServer();
        if (server == null) {
            return;
        }

        // The prompt is built HERE, on the server thread, while the world state
        // it describes is safe to read. The worker thread only ever touches the
        // immutable strings it was handed.
        String system = buildCharacterPrompt(npc, player);
        String key = conversationKey(npc, player);
        List<LlmClient.Message> conversation = assemble(key, system, trim(heard));

        IN_FLIGHT.incrementAndGet();
        executor.execute(() -> {
            try {
                String raw = LlmClient.complete(conversation);
                if (raw == null) {
                    return;
                }
                Directives.Parsed parsed = Directives.parse(raw);
                if (!parsed.hasText()) {
                    return;
                }
                remember(key, trim(heard), parsed.text());
                // Back onto the server thread before touching anything in the world.
                server.execute(() -> deliver(npc, player, parsed, spoken));
            } catch (Throwable t) {
                PokeEFNPC.LOGGER.debug("PokeEFNPC: brain request failed ({})", t.toString());
            } finally {
                IN_FLIGHT.decrementAndGet();
            }
        });
    }

    /** Runs on the server thread. */
    private static void deliver(NpcEntity npc, ServerPlayer player, Directives.Parsed parsed,
                                boolean spoken) {
        if (!npc.isAlive() || npc.distanceToSqr(player) > 400.0D) {
            return;
        }
        player.sendSystemMessage(Component.translatable("pokeefnpc.dialogue.line",
                npc.getName(), Component.literal(parsed.text())));

        if (parsed.mood() != null) {
            npc.mood().latch(npc.personality(), parsed.mood());
        }
        if (parsed.emote() != null) {
            npc.playEmote(parsed.emote());
        }
        // Mouth flap for roughly as long as the line takes to say.
        npc.mood().speakFor(Math.min(200, 20 + parsed.text().length() * 2));

        if (spoken) {
            NpcVoiceBridge.speak(npc, player, parsed.text());
        }
    }

    /**
     * The character sheet handed to the model.
     *
     * <p>Everything in here is a fact the simulation already knows. Nothing is
     * invented for the prompt, which is why the model's answers stay consistent
     * with what the player can see the NPC doing.
     */
    private static String buildCharacterPrompt(NpcEntity npc, ServerPlayer player) {
        Personality personality = npc.personality();
        Reputation.Standing standing = npc.reputation().standingOf(player.getUUID());
        Settlement settlement = npc.settlement();
        int hour = ScheduleTemplate.hourOf(npc.level().getDayTime());

        StringBuilder prompt = new StringBuilder(1024);
        prompt.append("You are ").append(npc.getName().getString())
                .append(", a ").append(npc.role().key().replace('_', ' '))
                .append(" in a medieval fantasy village. Stay in character at all times. ")
                .append("You are not an assistant, you have no knowledge of the modern world, ")
                .append("and you never mention being a program.\n\n");

        prompt.append("Who you are:\n");
        prompt.append("- Trade: ").append(npc.role().key().replace('_', ' ')).append('\n');
        prompt.append("- Temperament: ").append(describeTraits(personality)).append('\n');
        prompt.append("- Right now you feel: ").append(npc.mood().current().key())
                .append(" (strength ").append(Math.round(npc.mood().intensity() * 100))
                .append("%)\n");
        prompt.append("- You are currently: ").append(npc.activity().key().replace('_', ' '))
                .append('\n');

        prompt.append("\nWho you are talking to:\n");
        prompt.append("- Name: ").append(player.getGameProfile().getName()).append('\n');
        prompt.append("- What you think of them: ")
                .append(standing.name().toLowerCase(java.util.Locale.ROOT)).append('\n');

        prompt.append("\nThe world around you:\n");
        prompt.append("- Time: ").append(describeHour(hour)).append('\n');
        if (settlement != null) {
            prompt.append("- Your village: ").append(settlement.name())
                    .append(", ").append(settlement.population()).append(" people\n");
            prompt.append("- Its state: ").append(describeSettlement(settlement)).append('\n');
        } else {
            prompt.append("- You belong to no settlement; you are on the road.\n");
        }

        prompt.append("\nHow to answer:\n");
        prompt.append("- One or two short sentences. Speak plainly, as a working person would.\n");
        prompt.append("- Let your mood and your opinion of them colour how you say it.\n");
        prompt.append("- Never invent prices, items or deals; if they want to trade, ")
                .append("tell them to look at your wares.\n");
        prompt.append("- You may end with at most one [emote:name] and one [mood:name] tag ")
                .append("to show what you do with your face and hands. ")
                .append(Directives.tagVocabulary()).append('\n');

        String extra = PokeEFNPCConfig.llmExtraPrompt();
        if (!extra.isBlank()) {
            prompt.append('\n').append(extra).append('\n');
        }
        return prompt.toString();
    }

    private static String describeTraits(Personality personality) {
        List<String> traits = new ArrayList<>();
        traits.add(personality.courage > 0.65F ? "brave"
                : personality.courage < 0.35F ? "timid" : "steady");
        traits.add(personality.sociability > 0.65F ? "talkative"
                : personality.sociability < 0.35F ? "withdrawn" : "civil");
        traits.add(personality.greed > 0.65F ? "grasping"
                : personality.greed < 0.35F ? "generous" : "fair-dealing");
        traits.add(personality.temper > 0.65F ? "quick-tempered"
                : personality.temper < 0.35F ? "even-tempered" : "ordinary-tempered");
        traits.add(personality.diligence > 0.65F ? "hard-working" : "easily distracted");
        return String.join(", ", traits);
    }

    private static String describeHour(int hour) {
        if (hour < 2) return "just after dawn";
        if (hour < 5) return "morning";
        if (hour < 7) return "midday";
        if (hour < 11) return "afternoon";
        if (hour < 13) return "evening";
        if (hour < 15) return "dusk";
        return "the dead of night";
    }

    private static String describeSettlement(Settlement settlement) {
        List<String> notes = new ArrayList<>();
        if (settlement.threat() > 0.5F) {
            notes.add("under attack right now");
        } else if (settlement.threat() > 0.2F) {
            notes.add("uneasy, monsters have been seen");
        } else {
            notes.add("quiet");
        }
        if (settlement.foodStores() <= 0) {
            notes.add("the stores are empty and people are hungry");
        } else if (settlement.prosperity() > 0.7F) {
            notes.add("trade has been good");
        } else if (settlement.prosperity() < 0.3F) {
            notes.add("times are hard");
        }
        if (settlement.defence() < 0.2F) {
            notes.add("the walls are in a poor state");
        }
        return String.join("; ", notes);
    }

    // ------------------------------------------------------------- memory

    private static String conversationKey(NpcEntity npc, ServerPlayer player) {
        return npc.getUUID() + "|" + player.getUUID();
    }

    private static synchronized List<LlmClient.Message> assemble(String key, String system,
                                                                 String heard) {
        List<LlmClient.Message> conversation = new ArrayList<>();
        conversation.add(LlmClient.Message.system(system));
        Deque<LlmClient.Message> history = CONVERSATIONS.get(key);
        if (history != null) {
            conversation.addAll(history);
        }
        conversation.add(LlmClient.Message.user(heard));
        return conversation;
    }

    private static synchronized void remember(String key, String heard, String said) {
        Deque<LlmClient.Message> history =
                CONVERSATIONS.computeIfAbsent(key, k -> new ArrayDeque<>());
        history.addLast(LlmClient.Message.user(heard));
        history.addLast(LlmClient.Message.assistant(said));
        while (history.size() > MEMORY_TURNS * 2) {
            history.removeFirst();
        }
        // Bounded overall, so a busy server does not accumulate a conversation
        // for every pairing that has ever happened.
        if (CONVERSATIONS.size() > MAX_CONVERSATIONS) {
            var iterator = CONVERSATIONS.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    /** Drops what one NPC remembers, e.g. when it dies. */
    public static synchronized void forget(UUID npc) {
        CONVERSATIONS.keySet().removeIf(key -> key.startsWith(npc.toString()));
    }

    @Nullable
    private static String trim(String heard) {
        if (heard == null) {
            return null;
        }
        String trimmed = heard.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
    }
}
