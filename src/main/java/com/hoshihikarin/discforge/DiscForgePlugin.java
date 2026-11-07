package com.hoshihikarin.discforge;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Arrays;

/** DiscForge: turn your own .ogg music into custom music discs. */
public final class DiscForgePlugin extends JavaPlugin implements Listener {

    private DiscManager discs;
    private PackManager pack;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        discs = new DiscManager(this);
        pack = new PackManager(this);
        discs.load();
        rebuildPack();
        pack.startHost();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new JukeboxListener(this, discs), this);

        PluginCommand command = getCommand("discforge");
        if (command != null) {
            DiscCommand handler = new DiscCommand(this, discs, pack);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        getLogger().info("Loaded " + discs.all().size() + " custom disc(s).");
        if (discs.all().isEmpty()) {
            getLogger().info("Add .ogg files to plugins/DiscForge/music and run /discforge reload.");
        }
        if (pack.url() == null) {
            getLogger().info("Set resource-pack.host.public-address (or external-url) in config.yml "
                    + "so players can download the music.");
        }
    }

    @Override
    public void onDisable() {
        if (pack != null) {
            pack.stopHost();
        }
    }

    /**
     * Reloads the config and music folder and rebuilds the resource pack.
     * Players receive the new pack if its contents changed.
     *
     * @return the number of discs loaded
     */
    public int reloadAll() {
        reloadConfig();
        discs.load();
        byte[] before = pack.hash();
        rebuildPack();
        if (!Arrays.equals(before, pack.hash())) {
            Bukkit.getOnlinePlayers().forEach(pack::sendTo);
        }
        return discs.all().size();
    }

    private void rebuildPack() {
        try {
            pack.build(discs.all());
        } catch (IOException ex) {
            getLogger().severe("Could not build the resource pack: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("resource-pack.send-on-join", true)) {
            return;
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                pack.sendTo(player);
            }
        }, 40L);
    }

    /** Translates &amp; color codes. */
    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public String prefix() {
        return color(getConfig().getString("messages.prefix", "&8[&bDiscForge&8] &7"));
    }
}
