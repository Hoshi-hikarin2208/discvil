package com.hoshihikarin.discforge;

import org.bukkit.Material;

import java.nio.file.Path;

/**
 * A custom music disc backed by an .ogg file in the plugin's music folder.
 *
 * @param id              resource-location safe identifier (lowercase letters, digits, "_" and "-")
 * @param file            the source audio file
 * @param displayName     name shown on the item (supports & color codes)
 * @param author          artist shown in the item lore, may be empty
 * @param material        the vanilla disc item used for the custom disc
 * @param customModelData optional custom model data, 0 means unused
 * @param durationSeconds length of the track in seconds
 */
public record Disc(String id, Path file, String displayName, String author, Material material,
                   int customModelData, int durationSeconds) {

    /** The sound event name registered in the generated resource pack. */
    public String soundKey() {
        return PackManager.NAMESPACE + ":" + id;
    }
}
