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

    public KoalaPvPCommand(KoalaPvP plugin) { this.plugin = plugin; }

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
                sender.sendMessage(color("&7Version: &e" + plugin.getDescription().getVersion()));
                sender.sendMessage(color("&7Ground KB:  H=&e" + cfg.getGroundHorizontal()
                        + " &7V=&e" + cfg.getGroundVertical() + " &7Fr=&e" + cfg.getGroundFriction()));
                sender.sendMessage(color("&7Air KB:     H=&e" + cfg.getAirHorizontal()
                        + " &7V=&e" + cfg.getAirVertical()));
                sender.sendMessage(color("&7Sprint+:    &e+" + cfg.getSprintBonus()));
                sender.sendMessage(color("&7Smoothing:  &e" + cfg.getSmoothingTicks() + " tick(s)"));
                sender.sendMessage(color("&7Max-Y:      &e" + cfg.getMaxVerticalVelocity()));
                sender.sendMessage(color("&7Validation: &e" + cfg.isHitValidationEnabled()
                        + " &7(range &e" + cfg.getMaxRange() + " &7blocks)"));
                sender.sendMessage(color("&7Lag-comp:   &e" + cfg.isLagCompensation()));
                sender.sendMessage(color("&7Cooldown:   &e" + cfg.isCooldownEnabled()));
                sender.sendMessage(color("&7Debug:      &e" + cfg.isLogHits()));
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
        sender.sendMessage(color("&e/" + label + " status &7- Show KB values"));
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
