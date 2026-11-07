package com.hoshihikarin.discforge;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Handles /discforge. */
public final class DiscCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("help", "list", "give", "reload", "pack", "info");

    private final DiscForgePlugin plugin;
    private final DiscManager discs;
    private final PackManager pack;

    public DiscCommand(DiscForgePlugin plugin, DiscManager discs, PackManager pack) {
        this.plugin = plugin;
        this.discs = discs;
        this.pack = pack;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "give" -> give(sender, label, args);
            case "reload" -> {
                int count = plugin.reloadAll();
                reply(sender, "&aReloaded. &f" + count + "&a disc(s) loaded.");
            }
            case "pack" -> {
                Bukkit.getOnlinePlayers().forEach(pack::sendTo);
                reply(sender, pack.url() == null
                        ? "&cNo pack link is set. Set &fresource-pack.host.public-address&c in config.yml."
                        : "&aResource pack sent to all online players.");
            }
            case "info" -> info(sender);
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        reply(sender, "&bDiscForge &7commands:");
        sender.sendMessage(plugin.color("&f/" + label + " list &7- show all discs"));
        sender.sendMessage(plugin.color("&f/" + label + " give <disc> [player] [amount] &7- give a disc"));
        sender.sendMessage(plugin.color("&f/" + label + " reload &7- rescan the music folder"));
        sender.sendMessage(plugin.color("&f/" + label + " pack &7- resend the resource pack"));
        sender.sendMessage(plugin.color("&f/" + label + " info &7- show pack status"));
    }

    private void list(CommandSender sender) {
        if (discs.all().isEmpty()) {
            reply(sender, "&eNo discs yet. Add .ogg files to &fplugins/DiscForge/music&e and run &f/discforge reload&e.");
            return;
        }
        reply(sender, "&b" + discs.all().size() + " &7disc(s):");
        for (Disc disc : discs.all()) {
            int seconds = disc.durationSeconds();
            sender.sendMessage(plugin.color("&8- &f" + disc.id() + " &7(" + disc.displayName() + "&7, "
                    + String.format("%d:%02d", seconds / 60, seconds % 60) + ")"));
        }
    }

    private void give(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            reply(sender, "&cUsage: /" + label + " give <disc> [player] [amount]");
            return;
        }
        Disc disc = discs.get(args[1].toLowerCase(Locale.ROOT));
        if (disc == null) {
            reply(sender, "&cUnknown disc '&f" + args[1] + "&c'. Use /" + label + " list.");
            return;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                reply(sender, "&cPlayer '&f" + args[2] + "&c' is not online.");
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            reply(sender, "&cThe console must name a player.");
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ex) {
                reply(sender, "&c'&f" + args[3] + "&c' is not a number.");
                return;
            }
        }

        for (int i = 0; i < amount; i++) {
            Map<Integer, ItemStack> leftover = target.getInventory().addItem(discs.createItem(disc, 1));
            for (ItemStack item : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), item);
            }
        }
        reply(sender, "&aGave &f" + amount + "x " + disc.id() + "&a to &f" + target.getName() + "&a.");
    }

    private void info(CommandSender sender) {
        String url = pack.url();
        reply(sender, "&bDiscForge status");
        sender.sendMessage(plugin.color("&7Discs: &f" + discs.all().size()));
        sender.sendMessage(plugin.color("&7Pack link: &f" + (url == null ? "not set" : url)));
        sender.sendMessage(plugin.color("&7Pack SHA-1: &f"
                + (pack.hash().length == 0 ? "none" : HexFormat.of().formatHex(pack.hash()))));
    }

    private void reply(CommandSender sender, String message) {
        sender.sendMessage(plugin.prefix() + plugin.color(message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                return filter(new ArrayList<>(discs.ids()), args[1]);
            }
            if (args.length == 3) {
                List<String> names = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                return filter(names, args[2]);
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
