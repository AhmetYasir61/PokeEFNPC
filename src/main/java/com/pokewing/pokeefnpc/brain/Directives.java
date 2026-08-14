package com.pokewing.pokeefnpc.brain;

import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.npc.Emotion;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lets the model act as well as speak.
 *
 * <p>The system prompt invites the model to tag a reply with
 * <code>[emote:wave]</code> and <code>[mood:angry]</code>. Those tags are pulled
 * out here, mapped onto the mod's own enums, and stripped from the text before
 * anybody sees it. That is what makes the language model part of the character
 * rather than a chat window bolted onto one: the same reply that says "get away
 * from my stall" also folds the shopkeeper's arms and puts the scowl on its face.
 *
 * <p>Nothing is trusted. An unknown emote or mood name is discarded, tags are
 * capped, and a reply consisting only of tags leaves no text at all — which the
 * caller treats as no answer.
 */
public final class Directives {

    private static final Pattern TAG = Pattern.compile("\\[(emote|mood)\\s*:\\s*([a-zA-Z_]{1,32})]");

    /** How many tags are honoured before the rest are simply stripped. */
    private static final int MAX_TAGS = 4;

    /** A reply, split into what is said and what is done. */
    public record Parsed(String text, @Nullable Emote emote, @Nullable Emotion mood) {
        public boolean hasText() {
            return !this.text.isBlank();
        }
    }

    private Directives() {
    }

    public static Parsed parse(String raw) {
        Emote emote = null;
        Emotion mood = null;
        int honoured = 0;

        Matcher matcher = TAG.matcher(raw);
        StringBuilder cleaned = new StringBuilder();
        while (matcher.find()) {
            if (honoured < MAX_TAGS) {
                String kind = matcher.group(1).toLowerCase(Locale.ROOT);
                String value = matcher.group(2);
                if (kind.equals("emote")) {
                    Emote found = findEmote(value);
                    if (found != null) {
                        emote = found;
                        honoured++;
                    }
                } else {
                    Emotion found = findMood(value);
                    if (found != null) {
                        mood = found;
                        honoured++;
                    }
                }
            }
            // Stripped whether or not it was understood, so a hallucinated tag
            // never reaches the player as literal text.
            matcher.appendReplacement(cleaned, "");
        }
        matcher.appendTail(cleaned);

        return new Parsed(tidy(cleaned.toString()), emote, mood);
    }

    /**
     * Trims the reply into something a person says out loud: whitespace
     * collapsed, surrounding quotes and stage directions dropped, and a hard
     * length cap so a runaway generation cannot flood the chat.
     */
    private static String tidy(String text) {
        String tidied = text.replaceAll("\\s+", " ").trim();
        // Small models love to answer with the character's name as a prefix or
        // to wrap the whole line in quotes; both read badly in chat.
        tidied = tidied.replaceAll("^\"|\"$", "").trim();
        tidied = tidied.replaceAll("^\\*[^*]{0,64}\\*", "").trim();
        if (tidied.length() > 240) {
            // Cut at the last sentence end that fits, rather than mid-word.
            int cut = Math.max(tidied.lastIndexOf('.', 240), tidied.lastIndexOf('!', 240));
            cut = Math.max(cut, tidied.lastIndexOf('?', 240));
            tidied = cut > 40 ? tidied.substring(0, cut + 1) : tidied.substring(0, 240);
        }
        return tidied;
    }

    @Nullable
    private static Emote findEmote(String name) {
        for (Emote emote : Emote.values()) {
            if (emote.key().equalsIgnoreCase(name)) {
                return emote;
            }
        }
        return null;
    }

    @Nullable
    private static Emotion findMood(String name) {
        for (Emotion emotion : Emotion.values()) {
            if (emotion.key().equalsIgnoreCase(name)) {
                return emotion;
            }
        }
        return null;
    }

    /** The list of tags offered to the model, built from the enums themselves. */
    public static String tagVocabulary() {
        StringBuilder emotes = new StringBuilder();
        for (Emote emote : Emote.values()) {
            if (emotes.length() > 0) {
                emotes.append(", ");
            }
            emotes.append(emote.key());
        }
        StringBuilder moods = new StringBuilder();
        for (Emotion emotion : Emotion.values()) {
            if (moods.length() > 0) {
                moods.append(", ");
            }
            moods.append(emotion.key());
        }
        return "Emotes: " + emotes + ". Moods: " + moods + ".";
    }
}
