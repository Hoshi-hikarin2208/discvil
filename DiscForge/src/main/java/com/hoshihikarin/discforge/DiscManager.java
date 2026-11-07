package com.hoshihikarin.discforge;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Scans the music folder, keeps the disc registry and creates disc items. */
public final class DiscManager {

    private final DiscForgePlugin plugin;
    private final Map<String, Disc> discs = new LinkedHashMap<>();
    private final NamespacedKey discKey;
    private final NamespacedKey jukeboxKey;

    public DiscManager(DiscForgePlugin plugin) {
        this.plugin = plugin;
        this.discKey = new NamespacedKey(plugin, "disc");
        this.jukeboxKey = new NamespacedKey(plugin, "jukebox_disc");
    }

    /** Key stored on a disc item. */
    public NamespacedKey discKey() {
        return discKey;
    }

    /** Key stored on a jukebox block that currently holds a DiscForge disc. */
    public NamespacedKey jukeboxKey() {
        return jukeboxKey;
    }

    public Collection<Disc> all() {
        return Collections.unmodifiableCollection(discs.values());
    }

    public Collection<String> ids() {
        return Collections.unmodifiableSet(discs.keySet());
    }

    public Disc get(String id) {
        return id == null ? null : discs.get(id);
    }

    /** Reads every .ogg file in plugins/DiscForge/music and (re)builds the registry. */
    public void load() {
        discs.clear();

        Path musicDir = plugin.getDataFolder().toPath().resolve("music");
        try {
            Files.createDirectories(musicDir);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not create the music folder: " + ex.getMessage());
            return;
        }

        File discsFile = new File(plugin.getDataFolder(), "discs.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(discsFile);
        Material defaultMaterial = parseMaterial(plugin.getConfig().getString("default-material"), Material.MUSIC_DISC_CAT);
        int fallback = Math.max(1, plugin.getConfig().getInt("fallback-duration-seconds", 240));
        boolean dirty = false;

        List<Path> files;
        try (Stream<Path> stream = Files.list(musicDir)) {
            files = stream
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not read the music folder: " + ex.getMessage());
            return;
        }

        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String base = fileName.substring(0, fileName.length() - 4);
            String id = sanitize(base);
            if (id.isEmpty()) {
                plugin.getLogger().warning("Skipping '" + fileName + "': the name has no usable letters or digits.");
                continue;
            }
            if (discs.containsKey(id)) {
                plugin.getLogger().warning("Skipping '" + fileName + "': another file already uses the id '" + id + "'.");
                continue;
            }

            String section = "discs." + id;
            if (!yml.isConfigurationSection(section)) {
                yml.set(section + ".name", "&b" + prettify(base));
                yml.set(section + ".author", "");
                yml.set(section + ".material", defaultMaterial.name());
                yml.set(section + ".custom-model-data", 0);
                dirty = true;
            }

            String name = yml.getString(section + ".name", prettify(base));
            String author = yml.getString(section + ".author", "");
            Material material = parseMaterial(yml.getString(section + ".material"), defaultMaterial);
            int modelData = yml.getInt(section + ".custom-model-data", 0);

            double seconds = OggDuration.seconds(file);
            int duration;
            if (seconds > 0) {
                duration = (int) Math.ceil(seconds);
            } else {
                duration = fallback;
                plugin.getLogger().warning("'" + fileName + "' is not a readable Ogg Vorbis file. "
                        + "Minecraft may not play it. Using a length of " + fallback + "s.");
            }

            discs.put(id, new Disc(id, file, name, author, material, modelData, duration));
        }

        if (dirty) {
            try {
                yml.save(discsFile);
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save discs.yml: " + ex.getMessage());
            }
        }
    }

    /** Builds the item for a disc. */
    public ItemStack createItem(Disc disc, int amount) {
        ItemStack item = new ItemStack(disc.material(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&r" + disc.displayName()));

        List<String> lore = new ArrayList<>();
        if (!disc.author().isBlank()) {
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7" + disc.author()));
        }
        lore.add(ChatColor.translateAlternateColorCodes('&', "&8Custom music disc"));
        meta.setLore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (disc.customModelData() > 0) {
            meta.setCustomModelData(disc.customModelData());
        }
        meta.getPersistentDataContainer().set(discKey, PersistentDataType.STRING, disc.id());
        item.setItemMeta(meta);
        return item;
    }

    /** @return the disc an item represents, or null if it is not a DiscForge disc */
    public Disc fromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(discKey, PersistentDataType.STRING);
        return get(id);
    }

    private static Material parseMaterial(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        return material != null && material.isItem() ? material : fallback;
    }

    /** Makes a file name safe to use as a resource location. */
    static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_-]", "");
    }

    private static String prettify(String name) {
        String[] words = name.replace('_', ' ').replace('-', ' ').trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
