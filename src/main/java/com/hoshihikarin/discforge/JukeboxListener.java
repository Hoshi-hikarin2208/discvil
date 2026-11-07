package com.hoshihikarin.discforge;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/** Plays custom discs when they are used on jukeboxes. */
public final class JukeboxListener implements Listener {

    private final DiscForgePlugin plugin;
    private final DiscManager discs;
    private final Map<String, BukkitTask> active = new HashMap<>();

    public JukeboxListener(DiscForgePlugin plugin, DiscManager discs) {
        this.plugin = plugin;
        this.discs = discs;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.JUKEBOX) {
            return;
        }
        BlockState state = block.getState();
        if (!(state instanceof Jukebox jukebox)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean holdingItem = hand.getType() != Material.AIR;
        if (player.isSneaking() && holdingItem) {
            return;
        }

        String stored = jukebox.getPersistentDataContainer().get(discs.jukeboxKey(), PersistentDataType.STRING);
        if (stored != null) {
            event.setCancelled(true);
            eject(block, jukebox);
            return;
        }

        Disc held = discs.fromItem(hand);
        if (held == null || jukebox.hasRecord()) {
            return;
        }
        event.setCancelled(true);

        if (!player.hasPermission("discforge.play")) {
            player.sendMessage(plugin.prefix() + plugin.color(
                    plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission to play custom discs.")));
            return;
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack remaining = hand.clone();
            remaining.setAmount(hand.getAmount() - 1);
            player.getInventory().setItemInMainHand(remaining.getAmount() > 0 ? remaining : new ItemStack(Material.AIR));
        }

        jukebox.getPersistentDataContainer().set(discs.jukeboxKey(), PersistentDataType.STRING, held.id());
        jukebox.update();
        startPlaying(block, held);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        handleDestroyed(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::handleDestroyed);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::handleDestroyed);
    }

    private void handleDestroyed(Block block) {
        if (block.getType() != Material.JUKEBOX) {
            return;
        }
        if (block.getState() instanceof Jukebox jukebox
                && jukebox.getPersistentDataContainer().has(discs.jukeboxKey(), PersistentDataType.STRING)) {
            eject(block, jukebox);
        }
    }

    private void startPlaying(Block block, Disc disc) {
        stopTask(block);

        World world = block.getWorld();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        float volume = (float) plugin.getConfig().getDouble("volume", 4.0);
        world.playSound(center, disc.soundKey(), SoundCategory.RECORDS, volume, 1f);

        if (plugin.getConfig().getBoolean("show-now-playing", true)) {
            String text = plugin.color(plugin.getConfig().getString("messages.now-playing", "&d♪ &fNow playing: &b%disc%")
                    .replace("%disc%", ChatColor.stripColor(plugin.color(disc.displayName()))));
            double range = volume * 16.0;
            for (Player nearby : world.getPlayers()) {
                if (nearby.getLocation().distanceSquared(center) <= range * range) {
                    nearby.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
                }
            }
        }

        boolean particles = plugin.getConfig().getBoolean("particles", true);
        int[] elapsed = {0};
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                elapsed[0]++;
                boolean loaded = world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
                if (!loaded || block.getType() != Material.JUKEBOX || elapsed[0] >= disc.durationSeconds()) {
                    active.remove(key(block));
                    cancel();
                    return;
                }
                if (particles) {
                    world.spawnParticle(Particle.NOTE, center.clone().add(0, 0.8, 0), 1, 0.3, 0.1, 0.3, 0);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
        active.put(key(block), task);
    }

    private void eject(Block block, Jukebox jukebox) {
        PersistentDataContainer data = jukebox.getPersistentDataContainer();
        String id = data.get(discs.jukeboxKey(), PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        Disc disc = discs.get(id);

        data.remove(discs.jukeboxKey());
        jukebox.update();
        stopTask(block);

        World world = block.getWorld();
        Location drop = block.getLocation().add(0.5, 1.0, 0.5);
        if (disc != null) {
            for (Player player : world.getPlayers()) {
                player.stopSound(disc.soundKey(), SoundCategory.RECORDS);
            }
            world.dropItemNaturally(drop, discs.createItem(disc, 1));
        } else {
            ItemStack placeholder = new ItemStack(Material.MUSIC_DISC_CAT);
            ItemMeta meta = placeholder.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GRAY + "Missing disc (" + id + ")");
                placeholder.setItemMeta(meta);
            }
            world.dropItemNaturally(drop, placeholder);
            plugin.getLogger().warning("A jukebox held the disc '" + id + "' but its music file no longer exists.");
        }
    }

    private void stopTask(Block block) {
        BukkitTask task = active.remove(key(block));
        if (task != null) {
            task.cancel();
        }
    }

    private static String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + "," + block.getY() + "," + block.getZ();
    }
}
