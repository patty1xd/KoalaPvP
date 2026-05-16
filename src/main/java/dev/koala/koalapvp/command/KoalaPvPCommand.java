package dev.koala.koalapvp.command;

import dev.koala.koalapvp.KoalaPvP;
import dev.koala.koalapvp.util.Logger;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class KoalaPvPCommand implements CommandExecutor, TabCompleter {

    private final KoalaPvP plugin;

    public KoalaPvPCommand(KoalaPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("koalapvp.admin")) {
            sender.sendMessage(color("&cNo permission."));
            return true;
        }

        if (args.length == 0) { sendHelp(sender, label); return true; }

        switch (args[0].toLowerCase()) {

            case "reload" -> {
                plugin.getKoalaConfig().load();
                sender.sendMessage(color("&aKoalaPvP &7config reloaded."));
                Logger.info("Config reloaded by " + sender.getName());
            }

            case "status" -> {
                var cfg = plugin.getKoalaConfig();
                sender.sendMessage(color("&8&m----&r &bKoalaPvP Status &8&m----"));
                sender.sendMessage(color("&7Version:          &e" + plugin.getDescription().getVersion()));
                sender.sendMessage(color("&7Ping comp:         &e" + cfg.isPingCompEnabled()));
                sender.sendMessage(color("&7Ms per tick:       &e" + cfg.getMsPerTick()));
                sender.sendMessage(color("&7Max stale ticks:   &e" + cfg.getMaxStaleTicks()));
                sender.sendMessage(color("&7Ground trust ticks:&e" + cfg.getGroundTrustMaxTicks()));
                sender.sendMessage(color("&7Hit validation:    &e" + cfg.isHitValidationEnabled()
                        + " &7(range &e" + cfg.getMaxRange() + " &7blocks)"));
                sender.sendMessage(color("&7Lag compensation:  &e" + cfg.isLagCompensation()));
                sender.sendMessage(color("&7Debug logging:     &e" + cfg.isLogHits()));
            }

            case "debug" -> {
                boolean current = plugin.getKoalaConfig().isLogHits();
                plugin.getConfig().set("debug.log-hits", !current);
                plugin.saveConfig();
                plugin.getKoalaConfig().load();
                sender.sendMessage(color("&7Debug logging: &e" + !current));
            }

            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(color("&8&m----&r &bKoalaPvP Commands &8&m----"));
        sender.sendMessage(color("&e/" + label + " reload &7- Reload config"));
        sender.sendMessage(color("&e/" + label + " status &7- Show current settings"));
        sender.sendMessage(color("&e/" + label + " debug  &7- Toggle hit logging"));
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1)
            return List.of("reload", "status", "debug").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        return List.of();
    }
}
