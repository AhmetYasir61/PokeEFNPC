package com.pokewing.pokeefnpc.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.skin.SkinResolver;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches an account's skin and makes it available to the NPC renderer.
 *
 * <p>Runs entirely on the client. The server only ever syncs a <i>username</i>,
 * which is a handful of bytes; each client resolves and downloads for itself,
 * exactly the way vanilla handles player skins. That means no skin traffic
 * through the server and no skin work on the server thread.
 *
 * <p>Downloads are cached twice over: on disk under
 * {@code .minecraft/pokeefnpc-skins}, so a restart costs nothing, and in memory
 * as a registered texture. A name that is still downloading, or that has no
 * account behind it, simply falls back to the role's own skin, so an NPC always
 * renders.
 */
@OnlyIn(Dist.CLIENT)
public final class NpcSkinLoader {

    private static final ExecutorService DOWNLOADER = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "PokeEFNPC-skins");
        thread.setDaemon(true);
        return thread;
    });

    /** Names that have a texture ready. */
    private static final Map<String, ResourceLocation> READY = new ConcurrentHashMap<>();
    /** Names currently being fetched, or already known to be hopeless. */
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    /** Names whose account uses the Alex-slim arm model. */
    private static final Set<String> SLIM = ConcurrentHashMap.newKeySet();

    private NpcSkinLoader() {
    }

    /**
     * The texture for this username, starting a download if there is not one yet.
     *
     * @return the texture, or null while it is not ready — callers fall back to
     *         the role skin rather than waiting
     */
    @Nullable
    public static ResourceLocation get(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.toLowerCase(Locale.ROOT);
        ResourceLocation ready = READY.get(key);
        if (ready != null) {
            return ready;
        }
        if (FAILED.contains(key) || !IN_FLIGHT.add(key)) {
            return null;
        }
        DOWNLOADER.execute(() -> fetch(name, key));
        return null;
    }

    /** True when this account wears the three-pixel-arm model. */
    public static boolean isSlim(String name) {
        return name != null && SLIM.contains(name.toLowerCase(Locale.ROOT));
    }

    private static void fetch(String name, String key) {
        try {
            byte[] png = fromDisk(key);
            if (png == null) {
                SkinResolver.Resolved resolved = SkinResolver.resolve(name);
                if (!resolved.present()) {
                    FAILED.add(key);
                    return;
                }
                if (resolved.slim()) {
                    SLIM.add(key);
                }
                png = download(resolved.url());
                toDisk(key, png);
            }

            byte[] data = png;
            // Texture registration has to happen on the render thread; NativeImage
            // and the texture manager are both GL-bound.
            Minecraft.getInstance().execute(() -> register(key, data));
        } catch (Exception e) {
            FAILED.add(key);
            PokeEFNPC.LOGGER.warn("PokeEFNPC: could not fetch the skin for '{}' ({}).",
                    name, e.toString());
        } finally {
            IN_FLIGHT.remove(key);
        }
    }

    private static void register(String key, byte[] png) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
            NativeImage image = NativeImage.read(in);
            // Legacy 64x32 skins have to be converted, or the renderer samples
            // the second-layer coordinates out of bounds and the NPC turns black.
            if (image.getHeight() == 32) {
                image = expandLegacy(image);
            }
            ResourceLocation location = new ResourceLocation(PokeEFNPC.MOD_ID,
                    "skins/" + key.replaceAll("[^a-z0-9_.-]", "_"));
            Minecraft.getInstance().getTextureManager()
                    .register(location, new DynamicTexture(image));
            READY.put(key, location);
        } catch (Exception e) {
            FAILED.add(key);
            PokeEFNPC.LOGGER.warn("PokeEFNPC: could not read the skin image for '{}' ({}).",
                    key, e.toString());
        }
    }

    /**
     * Converts a 64x32 skin to the modern 64x64 layout by mirroring the single
     * arm and leg into the slots the second pair occupies.
     */
    private static NativeImage expandLegacy(NativeImage legacy) {
        NativeImage expanded = new NativeImage(64, 64, true);
        // Everything the old format had sits in the top half unchanged.
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 32; y++) {
                expanded.setPixelRGBA(x, y, legacy.getPixelRGBA(x, y));
            }
        }
        copyMirrored(expanded, 4, 16, 4, 4, 20, 48);   // right leg -> left leg
        copyMirrored(expanded, 8, 16, 4, 4, 24, 48);
        copyMirrored(expanded, 0, 20, 4, 12, 24, 52);
        copyMirrored(expanded, 4, 20, 4, 12, 20, 52);
        copyMirrored(expanded, 8, 20, 4, 12, 16, 52);
        copyMirrored(expanded, 12, 20, 4, 12, 28, 52);

        copyMirrored(expanded, 44, 16, 4, 4, 36, 48);  // right arm -> left arm
        copyMirrored(expanded, 48, 16, 4, 4, 40, 48);
        copyMirrored(expanded, 40, 20, 4, 12, 40, 52);
        copyMirrored(expanded, 44, 20, 4, 12, 36, 52);
        copyMirrored(expanded, 48, 20, 4, 12, 32, 52);
        copyMirrored(expanded, 52, 20, 4, 12, 44, 52);
        legacy.close();
        return expanded;
    }

    private static void copyMirrored(NativeImage image, int fromX, int fromY, int width, int height,
                                     int toX, int toY) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setPixelRGBA(toX + width - 1 - x, toY + y,
                        image.getPixelRGBA(fromX + x, fromY + y));
            }
        }
    }

    // ------------------------------------------------------------- disk cache

    private static Path cacheDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("pokeefnpc-skins");
    }

    @Nullable
    private static byte[] fromDisk(String key) {
        try {
            Path file = cacheDir().resolve(key.replaceAll("[^a-z0-9_.-]", "_") + ".png");
            return Files.exists(file) ? Files.readAllBytes(file) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void toDisk(String key, byte[] png) {
        try {
            Path dir = cacheDir();
            Files.createDirectories(dir);
            Files.write(dir.resolve(key.replaceAll("[^a-z0-9_.-]", "_") + ".png"), png);
        } catch (Exception e) {
            // A cache that cannot be written is not worth complaining about; the
            // skin still works, it is just re-fetched next session.
            PokeEFNPC.LOGGER.debug("PokeEFNPC: could not cache a skin ({})", e.toString());
        }
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "PokeEFNPC");
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("HTTP " + connection.getResponseCode());
            }
            byte[] data = connection.getInputStream().readAllBytes();
            // A skin is a few kilobytes. Anything much larger is not a skin, and
            // is not going to be decoded here.
            if (data.length > 512 * 1024) {
                throw new IllegalStateException("skin too large: " + data.length + " bytes");
            }
            return data;
        } finally {
            connection.disconnect();
        }
    }

    /** Dropped on resource reload so a manually cleared cache takes effect. */
    public static void clear() {
        READY.clear();
        FAILED.clear();
        SLIM.clear();
        SkinResolver.clear();
    }
}
