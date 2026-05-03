package dev.koala.koalapvp.config;

import dev.koala.koalapvp.KoalaPvP;
import dev.koala.koalapvp.util.Logger;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Typed, cached wrapper around config.yml.
 * Call load() on reload. All getters hit memory — zero file I/O per hit.
 */
public class KoalaConfig {

    private final KoalaPvP plugin;

    // Worlds
    private List<String> enabledWorlds;
    private boolean allWorlds;

    // Knockback base
    private double horizontal;
    private double vertical;
    private boolean halveExistingHorizontal;
    private double sprintBonus;

    // Enchants
    private double kb1HorizontalAdd, kb1VerticalAdd;
    private double kb2HorizontalAdd, kb2VerticalAdd;

    // Cooldown
    private boolean cooldownEnabled;
    private double  minChargeThreshold;
    private boolean scaleKnockback;

    // Clamps
    private double maxHorizontalVelocity;
    private double maxVerticalVelocity;

    // Armor
    private boolean respectNetheriteResistance;

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
        allWorlds     = enabledWorlds.size() == 1 && enabledWorlds.get(0).equals("*");

        horizontal              = cfg.getDouble("knockback.horizontal",                0.42);
        vertical                = cfg.getDouble("knockback.vertical",                  0.35);
        halveExistingHorizontal = cfg.getBoolean("knockback.halve-existing-horizontal", true);
        sprintBonus             = cfg.getDouble("knockback.sprint-bonus",              0.02);

        kb1HorizontalAdd = cfg.getDouble("knockback.enchant-kb1.horizontal-add", 0.50);
        kb1VerticalAdd   = cfg.getDouble("knockback.enchant-kb1.vertical-add",   0.10);
        kb2HorizontalAdd = cfg.getDouble("knockback.enchant-kb2.horizontal-add", 1.00);
        kb2VerticalAdd   = cfg.getDouble("knockback.enchant-kb2.vertical-add",   0.20);

        cooldownEnabled    = cfg.getBoolean("knockback.cooldown.enabled",             true);
        minChargeThreshold = cfg.getDouble("knockback.cooldown.min-charge-threshold", 0.10);
        scaleKnockback     = cfg.getBoolean("knockback.cooldown.scale-knockback",     true);

        maxHorizontalVelocity    = cfg.getDouble("knockback.max-horizontal-velocity", 2.0);
        maxVerticalVelocity      = cfg.getDouble("knockback.max-vertical-velocity",   0.8);
        respectNetheriteResistance = cfg.getBoolean("knockback.respect-netherite-resistance", true);

        pingCompEnabled     = cfg.getBoolean("ping-compensation.enabled",             true);
        msPerTick           = cfg.getInt("ping-compensation.ms-per-tick",             50);
        maxStaleTicks       = cfg.getInt("ping-compensation.max-stale-ticks",         8);
        groundTrustMaxTicks = cfg.getInt("ping-compensation.ground-trust-max-ticks",  1);

        hitValidationEnabled = cfg.getBoolean("hit-validation.enabled",               true);
        maxRange             = cfg.getDouble("hit-validation.max-range",              4.5);
        lagCompensation      = cfg.getBoolean("hit-validation.lag-compensation",      true);
        lagCompMsPerBlock    = cfg.getDouble("hit-validation.lag-compensation-ms-per-block", 50.0);
        lagCompMaxBonus      = cfg.getDouble("hit-validation.lag-compensation-max-bonus",    2.0);

        logHits = cfg.getBoolean("debug.log-hits", false);

        Logger.info("&7Config loaded — horizontal: &e" + horizontal
                + " &7vertical: &e" + vertical
                + " &7ping-comp: &e" + pingCompEnabled);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public boolean isWorldEnabled(String world) {
        return allWorlds || enabledWorlds.contains(world);
    }

    public double  getHorizontal()               { return horizontal; }
    public double  getVertical()                  { return vertical; }
    public boolean isHalveExistingHorizontal()    { return halveExistingHorizontal; }
    public double  getSprintBonus()               { return sprintBonus; }
    public double  getKb1HorizontalAdd()          { return kb1HorizontalAdd; }
    public double  getKb1VerticalAdd()            { return kb1VerticalAdd; }
    public double  getKb2HorizontalAdd()          { return kb2HorizontalAdd; }
    public double  getKb2VerticalAdd()            { return kb2VerticalAdd; }
    public boolean isCooldownEnabled()            { return cooldownEnabled; }
    public double  getMinChargeThreshold()        { return minChargeThreshold; }
    public boolean isScaleKnockback()             { return scaleKnockback; }
    public double  getMaxHorizontalVelocity()     { return maxHorizontalVelocity; }
    public double  getMaxVerticalVelocity()       { return maxVerticalVelocity; }
    public boolean isRespectNetheriteResistance() { return respectNetheriteResistance; }
    public boolean isPingCompEnabled()            { return pingCompEnabled; }
    public int     getMsPerTick()                 { return msPerTick; }
    public int     getMaxStaleTicks()             { return maxStaleTicks; }
    public int     getGroundTrustMaxTicks()       { return groundTrustMaxTicks; }
    public boolean isHitValidationEnabled()       { return hitValidationEnabled; }
    public double  getMaxRange()                  { return maxRange; }
    public boolean isLagCompensation()            { return lagCompensation; }
    public double  getLagCompMsPerBlock()         { return lagCompMsPerBlock; }
    public double  getLagCompMaxBonus()           { return lagCompMaxBonus; }
    public boolean isLogHits()                    { return logHits; }
}
