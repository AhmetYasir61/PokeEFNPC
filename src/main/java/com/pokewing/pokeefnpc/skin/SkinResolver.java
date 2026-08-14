package com.pokewing.pokeefnpc.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.PokeEFNPCConfig;

import javax.annotation.Nullable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a username into the URL of that account's skin.
 *
 * <p><b>On NameMC:</b> NameMC has no public API, and scraping its pages is both
 * fragile and against its terms. What it actually displays is Mojang's own
 * profile data, so that is what this reads — the same skin, from the source,
 * with no scraping and no rate-limit games:
 *
 * <ol>
 *   <li><code>api.mojang.com/users/profiles/minecraft/&lt;name&gt;</code> → the account's UUID</li>
 *   <li><code>sessionserver.mojang.com/session/minecraft/profile/&lt;uuid&gt;</code> → a
 *       base64 <i>textures</i> property holding the skin URL and whether the
 *       model is Alex-slim</li>
 * </ol>
 *
 * <p>If you would rather point at a mirror or a proxy — including one that
 * fronts NameMC — {@link PokeEFNPCConfig#skinUrlTemplate()} takes a URL with
 * <code>{name}</code> in it and is used instead, with no lookup at all. That is
 * the escape hatch for offline-mode servers and for skin sites with their own
 * direct-image URLs.
 *
 * <p>Everything here blocks on the network and must be called from a worker.
 * Results are cached in memory, including <i>failures</i>, so a mistyped name in
 * the config does not produce one lookup per NPC per session.
 */
public final class SkinResolver {

    /** Sentinel for "looked this up and there is nothing", so it is not retried. */
    private static final String NONE = "";

    private static final Map<String, Resolved> CACHE = new ConcurrentHashMap<>();

    /** A resolved skin: where to fetch the PNG, and which arm model it wants. */
    public record Resolved(String url, boolean slim) {
        public boolean present() {
            return !this.url.isEmpty();
        }
    }

    private static final Resolved MISSING = new Resolved(NONE, false);

    private SkinResolver() {
    }

    /** A previously resolved answer, without touching the network. */
    @Nullable
    public static Resolved cached(String name) {
        return name == null || name.isBlank()
                ? null : CACHE.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Resolves a username. Blocking.
     *
     * @return the skin location, or a {@link Resolved} whose {@code present()} is
     *         false when the account does not exist or the lookup failed
     */
    public static Resolved resolve(String name) {
        if (name == null || name.isBlank()) {
            return MISSING;
        }
        String key = name.toLowerCase(Locale.ROOT);
        Resolved known = CACHE.get(key);
        if (known != null) {
            return known;
        }

        Resolved resolved;
        String template = PokeEFNPCConfig.skinUrlTemplate();
        if (!template.isBlank()) {
            // Direct template: no lookup, the URL is assumed to be the PNG.
            resolved = new Resolved(template.replace("{name}", name), false);
        } else {
            resolved = resolveFromMojang(name);
        }
        CACHE.put(key, resolved);
        return resolved;
    }

    private static Resolved resolveFromMojang(String name) {
        try {
            String uuid = lookupUuid(name);
            if (uuid == null) {
                PokeEFNPC.LOGGER.info("PokeEFNPC: no account named '{}'; that NPC keeps its "
                        + "default skin.", name);
                return MISSING;
            }
            return lookupTextures(uuid);
        } catch (Exception e) {
            PokeEFNPC.LOGGER.warn("PokeEFNPC: could not look up the skin for '{}' ({}).",
                    name, e.toString());
            return MISSING;
        }
    }

    @Nullable
    private static String lookupUuid(String name) throws Exception {
        String body = get("https://api.mojang.com/users/profiles/minecraft/"
                + java.net.URLEncoder.encode(name, StandardCharsets.UTF_8));
        if (body == null || body.isBlank()) {
            // Mojang answers 204 with no body for a name nobody owns.
            return null;
        }
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        return root.has("id") ? root.get("id").getAsString() : null;
    }

    private static Resolved lookupTextures(String uuid) throws Exception {
        String body = get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
        if (body == null || body.isBlank()) {
            return MISSING;
        }
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray properties = root.getAsJsonArray("properties");
        if (properties == null) {
            return MISSING;
        }
        for (var element : properties) {
            JsonObject property = element.getAsJsonObject();
            if (!"textures".equals(property.get("name").getAsString())) {
                continue;
            }
            // The value is a base64 JSON blob; the skin URL and the "slim" model
            // flag both live inside it.
            String decoded = new String(
                    Base64.getDecoder().decode(property.get("value").getAsString()),
                    StandardCharsets.UTF_8);
            JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject()
                    .getAsJsonObject("textures");
            if (textures == null || !textures.has("SKIN")) {
                return MISSING;
            }
            JsonObject skin = textures.getAsJsonObject("SKIN");
            String url = skin.get("url").getAsString();
            boolean slim = skin.has("metadata")
                    && "slim".equalsIgnoreCase(skin.getAsJsonObject("metadata")
                            .get("model").getAsString());
            return new Resolved(url, slim);
        }
        return MISSING;
    }

    @Nullable
    private static String get(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(6000);
            connection.setRequestProperty("User-Agent", "PokeEFNPC");
            int status = connection.getResponseCode();
            if (status == 204 || status == 404) {
                return null;
            }
            if (status != 200) {
                throw new IllegalStateException("HTTP " + status);
            }
            return new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    /** Forgets everything, so a config change or a skin change is picked up. */
    public static void clear() {
        CACHE.clear();
    }
}
