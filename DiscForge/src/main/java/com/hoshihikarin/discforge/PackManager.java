package com.hoshihikarin.discforge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds the resource pack that carries the music and optionally hosts it. */
public final class PackManager {

    public static final String NAMESPACE = "discforge";

    /** Fixed timestamp so the same music always produces the same zip and hash. */
    private static final long ENTRY_TIME = 315532800000L;

    private final DiscForgePlugin plugin;
    private volatile byte[] packBytes = new byte[0];
    private volatile byte[] sha1 = new byte[0];
    private volatile int discCount;
    private HttpServer server;
    private ExecutorService executor;

    public PackManager(DiscForgePlugin plugin) {
        this.plugin = plugin;
    }

    /** Builds the pack in memory and writes a copy to plugins/DiscForge/resourcepack. */
    public void build(Collection<Disc> discs) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Gson gson = new Gson();

        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "pack.mcmeta", gson.toJson(mcmeta()).getBytes(StandardCharsets.UTF_8));
            put(zip, "assets/" + NAMESPACE + "/sounds.json", gson.toJson(sounds(discs)).getBytes(StandardCharsets.UTF_8));
            for (Disc disc : discs) {
                ZipEntry entry = new ZipEntry("assets/" + NAMESPACE + "/sounds/" + disc.id() + ".ogg");
                entry.setTime(ENTRY_TIME);
                zip.putNextEntry(entry);
                Files.copy(disc.file(), zip);
                zip.closeEntry();
            }
        }

        byte[] data = bytes.toByteArray();
        try {
            this.sha1 = MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-1 is not available", ex);
        }
        this.packBytes = data;
        this.discCount = discs.size();

        Path dir = plugin.getDataFolder().toPath().resolve("resourcepack");
        Files.createDirectories(dir);
        Files.write(dir.resolve("DiscForge-pack.zip"), data);
    }

    private JsonObject mcmeta() {
        JsonObject supported = new JsonObject();
        supported.addProperty("min_inclusive", 34);
        supported.addProperty("max_inclusive", 999);

        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", plugin.getConfig().getInt("resource-pack.pack-format", 46));
        pack.add("supported_formats", supported);
        pack.addProperty("description", "DiscForge custom music discs");

        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }

    private JsonObject sounds(Collection<Disc> discs) {
        JsonObject root = new JsonObject();
        for (Disc disc : discs) {
            JsonObject sound = new JsonObject();
            sound.addProperty("name", NAMESPACE + ":" + disc.id());
            sound.addProperty("stream", true);

            JsonArray list = new JsonArray();
            list.add(sound);

            JsonObject event = new JsonObject();
            event.addProperty("category", "record");
            event.add("sounds", list);
            root.add(disc.id(), event);
        }
        return root;
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(ENTRY_TIME);
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    /** Starts the built-in web server if it is enabled in the config. */
    public void startHost() {
        ConfigurationSection host = plugin.getConfig().getConfigurationSection("resource-pack.host");
        if (host == null || !host.getBoolean("enabled", true)) {
            return;
        }
        int port = host.getInt("port", 8123);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            executor = Executors.newFixedThreadPool(2);
            server.setExecutor(executor);
            server.createContext("/pack.zip", exchange -> {
                try {
                    byte[] data = packBytes;
                    exchange.getResponseHeaders().set("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, data.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(data);
                    }
                } finally {
                    exchange.close();
                }
            });
            server.start();
            plugin.getLogger().info("Resource pack host listening on port " + port + ".");
        } catch (IOException ex) {
            server = null;
            plugin.getLogger().warning("Could not start the resource pack host on port " + port + ": " + ex.getMessage());
        }
    }

    public void stopHost() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** @return the download link players receive, or null if none is configured */
    public String url() {
        String external = plugin.getConfig().getString("resource-pack.external-url", "");
        if (!external.isBlank()) {
            return external.trim();
        }
        if (server == null) {
            return null;
        }
        String address = plugin.getConfig().getString("resource-pack.host.public-address", "");
        if (address.isBlank()) {
            return null;
        }
        int port = plugin.getConfig().getInt("resource-pack.host.port", 8123);
        return "http://" + address.trim() + ":" + port + "/pack.zip";
    }

    public byte[] hash() {
        return sha1;
    }

    /** Asks a player to download the pack. Does nothing if there is no music or no link. */
    public void sendTo(Player player) {
        String url = url();
        if (url == null || discCount == 0 || sha1.length == 0) {
            return;
        }
        player.setResourcePack(url, sha1);
    }
}
