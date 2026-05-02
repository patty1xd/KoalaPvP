package dev.koala.koalapvp;

import dev.koala.koalapvp.command.KoalaPvPCommand;
import dev.koala.koalapvp.config.KoalaConfig;
import dev.koala.koalapvp.knockback.KnockbackEngine;
import dev.koala.koalapvp.listener.CombatListener;
import dev.koala.koalapvp.util.Logger;
import org.bukkit.plugin.java.JavaPlugin;

public final class KoalaPvP extends JavaPlugin {

    private static KoalaPvP instance;
    private KoalaConfig koalaConfig;
    private KnockbackEngine knockbackEngine;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        koalaConfig = new KoalaConfig(this);
        koalaConfig.load();

        knockbackEngine = new KnockbackEngine(this);

        getServer().getPluginManager().registerEvents(new CombatListener(this), this);

        KoalaPvPCommand cmd = new KoalaPvPCommand(this);
        getCommand("koalapvp").setExecutor(cmd);
        getCommand("koalapvp").setTabCompleter(cmd);

        Logger.info("&aKoalaPvP &7v" + getDescription().getVersion() + " &aenabled.");
        Logger.info("&7Knockback engine ready. Smoothing ticks: &e"
                + koalaConfig.getSmoothingTicks());
    }

    @Override
    public void onDisable() {
        Logger.info("&cKoalaPvP disabled.");
    }

    public static KoalaPvP getInstance() { return instance; }
    public KoalaConfig getKoalaConfig()  { return koalaConfig; }
    public KnockbackEngine getKnockbackEngine() { return knockbackEngine; }
}
