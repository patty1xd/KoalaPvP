package dev.koala.koalapvp.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

public final class Logger {

    private static final String PREFIX = "&8[&bKoalaPvP&8] &r";

    private Logger() {}

    public static void info(String message) {
        Bukkit.getConsoleSender().sendMessage(color(PREFIX + message));
    }
    public static void warn(String message) {
        Bukkit.getConsoleSender().sendMessage(color(PREFIX + "&e" + message));
    }
    public static void severe(String message) {
        Bukkit.getConsoleSender().sendMessage(color(PREFIX + "&c" + message));
    }
    public static void debug(String message) {
        Bukkit.getConsoleSender().sendMessage(color(PREFIX + "&7[DEBUG] " + message));
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
