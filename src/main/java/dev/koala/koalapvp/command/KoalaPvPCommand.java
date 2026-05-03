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
            sender.sendMessage(color("&cYou don't have permission to use this command."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {
                plugin.getKoalaConfig().load();
                sender.sendMessage(color("&aKoalaPvP &7config reloaded successfully."));
                Logger.info("Config reloaded by " + sender.getName());
            }

            case "status" -> {
                var cfg = plugin.getKoalaConfig();
                sender.sendMessage(color("&8&m----&r &bKoalaPvP Status &8&m----"));
                sender.sendMessage(color("&7Version:      &e" + plugin.getDescription().getVersion()));
                sender.sendMessage(color("&7Horizontal:   &e" + cfg.getHorizontal()));
                sender.sendMessage(color("&7Vertical:     &e" + cfg.getVertical()));
                sender.sendMessage(color("&7Sprint bonus: &e+" + cfg.getSprintBonus()));
                sender.sendMessage(color("&7Ping comp:    &e" + cfg.isPingCompEnabled()
                        + " &7(trust ≤ &e" + cfg.getGroundTrustMaxTicks() + " &7stale ticks)"));
                sender.sendMessage(color("&7Validation:   &e" + cfg.isHitValidationEnabled()
                        + " &7(range &e" + cfg.getMaxRange() + " &7blocks)"));
                sender.sendMessage(color("&7Lag-comp:     &e" + cfg.isLagCompensation()));
                sender.sendMessage(color("&7Cooldown:     &e" + cfg.isCooldownEnabled()
                        + " &7(scale: &e" + cfg.isScaleKnockback() + "&7)"));
                sender.sendMessage(color("&7Debug:        &e" + cfg.isLogHits()));
            }

            case "debug" -> {
                // Toggle debug logging at runtime without a full reload
                boolean current = plugin.getKoalaConfig().isLogHits();
                // We re-write config and reload just the debug node
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
        sender.sendMessage(color("&e/" + label + " reload &7— Reload config.yml"));
        sender.sendMessage(color("&e/" + label + " status &7— Show current KB values"));
        sender.sendMessage(color("&e/" + label + " debug  &7— Toggle hit debug logging"));
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "status", "debug").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
