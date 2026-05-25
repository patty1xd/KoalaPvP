package dev.koala.koalapvp.config;

import dev.koala.koalapvp.KoalaPvP;
import dev.koala.koalapvp.util.Logger;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Typed, cached wrapper around config.yml.
 * Call load() on reload. All getters hit memory only — zero I/O per hit.
 */
public class KoalaConfig {

    private final KoalaPvP plugin;

    // Worlds
    private List<String> enabledWorlds;
    private boolean allWorlds;

    // Ping compensation
    private boolean pingCompEnabled;
    private int     msPerTick;
    private int     maxStaleTicks;
    private int     groundTrustMaxTicks;

    // Hit validation
    private boolean hitValidationEnabled;
    private double  maxRange;
    private boolean lagCompensation;
    private double  lagCompMsPerBlock;
    private double  lagCompMaxBonus;

    // Debug
    private boolean logHits;

    public KoalaConfig(KoalaPvP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        enabledWorlds = cfg.getStringList("enabled-worlds");
        // '*' anywhere in the list means "all worlds", not only when sole entry.
        allWorlds     = enabledWorlds.contains("*");

        pingCompEnabled     = cfg.getBoolean("ping-compensation.enabled",            true);
        msPerTick           = cfg.getInt("ping-compensation.ms-per-tick",            50);
        maxStaleTicks       = cfg.getInt("ping-compensation.max-stale-ticks",        8);
        groundTrustMaxTicks = cfg.getInt("ping-compensation.ground-trust-max-ticks", 1);

        hitValidationEnabled = cfg.getBoolean("hit-validation.enabled",              true);
        maxRange             = cfg.getDouble("hit-validation.max-range",             4.5);
        lagCompensation      = cfg.getBoolean("hit-validation.lag-compensation",     true);
        lagCompMsPerBlock    = cfg.getDouble("hit-validation.lag-compensation-ms-per-block", 50.0);
        lagCompMaxBonus      = cfg.getDouble("hit-validation.lag-compensation-max-bonus",    2.0);

        logHits = cfg.getBoolean("debug.log-hits", false);

        Logger.info("&7Config loaded — ping-comp: &e" + pingCompEnabled
                + " &7ground-trust-ticks: &e" + groundTrustMaxTicks);
    }

    public boolean isWorldEnabled(String world) {
        return allWorlds || enabledWorlds.contains(world);
    }

    public boolean isPingCompEnabled()       { return pingCompEnabled; }
    public int     getMsPerTick()            { return msPerTick; }
    public int     getMaxStaleTicks()        { return maxStaleTicks; }
    public int     getGroundTrustMaxTicks()  { return groundTrustMaxTicks; }
    public boolean isHitValidationEnabled()  { return hitValidationEnabled; }
    public double  getMaxRange()             { return maxRange; }
    public boolean isLagCompensation()       { return lagCompensation; }
    public double  getLagCompMsPerBlock()    { return lagCompMsPerBlock; }
    public double  getLagCompMaxBonus()      { return lagCompMaxBonus; }
    public boolean isLogHits()               { return logHits; }
}
