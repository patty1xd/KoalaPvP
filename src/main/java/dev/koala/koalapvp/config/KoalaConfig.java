package dev.koala.koalapvp.config;

import dev.koala.koalapvp.KoalaPvP;
import dev.koala.koalapvp.util.Logger;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class KoalaConfig {

    private final KoalaPvP plugin;

    private List<String> enabledWorlds;
    private boolean allWorlds;

    private double groundHorizontal, groundVertical, groundFriction;
    private double airHorizontal, airVertical;
    private double sprintBonus;
    private double kb1HorizontalAdd, kb1VerticalAdd;
    private double kb2HorizontalAdd, kb2VerticalAdd;
    private boolean cooldownEnabled;
    private double minChargeThreshold;
    private boolean scaleKnockback;
    private int smoothingTicks;
    private double maxVerticalVelocity;
    private boolean resetExistingVelocity;
    private boolean hitValidationEnabled;
    private double maxRange;
    private boolean lagCompensation;
    private double lagCompMsPerBlock, lagCompMaxBonus;
    private boolean respectNetheriteResistance;
    private boolean logHits;

    public KoalaConfig(KoalaPvP plugin) { this.plugin = plugin; }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        enabledWorlds = cfg.getStringList("enabled-worlds");
        allWorlds = enabledWorlds.size() == 1 && enabledWorlds.get(0).equals("*");

        groundHorizontal = cfg.getDouble("knockback.ground.horizontal", 0.32);
        groundVertical   = cfg.getDouble("knockback.ground.vertical",   0.30);
        groundFriction   = cfg.getDouble("knockback.ground.friction",   2.4);
        airHorizontal    = cfg.getDouble("knockback.air.horizontal",    0.28);
        airVertical      = cfg.getDouble("knockback.air.vertical",      0.27);
        sprintBonus      = cfg.getDouble("knockback.sprint-bonus",      0.03);

        kb1HorizontalAdd = cfg.getDouble("knockback.enchant-kb1.horizontal-add", 0.50);
        kb1VerticalAdd   = cfg.getDouble("knockback.enchant-kb1.vertical-add",   0.10);
        kb2HorizontalAdd = cfg.getDouble("knockback.enchant-kb2.horizontal-add", 1.00);
        kb2VerticalAdd   = cfg.getDouble("knockback.enchant-kb2.vertical-add",   0.20);

        cooldownEnabled      = cfg.getBoolean("knockback.cooldown.enabled",           true);
        minChargeThreshold   = cfg.getDouble("knockback.cooldown.min-charge-threshold", 0.1);
        scaleKnockback       = cfg.getBoolean("knockback.cooldown.scale-knockback",   true);

        smoothingTicks        = Math.max(1, cfg.getInt("knockback.smoothing-ticks", 2));
        maxVerticalVelocity   = cfg.getDouble("knockback.max-vertical-velocity",     0.55);
        resetExistingVelocity = cfg.getBoolean("knockback.reset-existing-velocity",  true);

        hitValidationEnabled  = cfg.getBoolean("hit-validation.enabled",              true);
        maxRange              = cfg.getDouble("hit-validation.max-range",             4.5);
        lagCompensation       = cfg.getBoolean("hit-validation.lag-compensation",     true);
        lagCompMsPerBlock     = cfg.getDouble("hit-validation.lag-compensation-ms-per-block", 50.0);
        lagCompMaxBonus       = cfg.getDouble("hit-validation.lag-compensation-max-bonus",    2.0);

        respectNetheriteResistance = cfg.getBoolean("armor.respect-netherite-resistance", true);
        logHits = cfg.getBoolean("debug.log-hits", false);

        Logger.info("&7Config loaded. All-worlds: &e" + allWorlds
                + "&7, Max-range: &e" + maxRange
                + "&7, Smoothing ticks: &e" + smoothingTicks);
    }

    public boolean isWorldEnabled(String worldName) {
        if (allWorlds) return true;
        return enabledWorlds.contains(worldName);
    }

    public double getGroundHorizontal()        { return groundHorizontal; }
    public double getGroundVertical()          { return groundVertical; }
    public double getGroundFriction()          { return groundFriction; }
    public double getAirHorizontal()           { return airHorizontal; }
    public double getAirVertical()             { return airVertical; }
    public double getSprintBonus()             { return sprintBonus; }
    public double getKb1HorizontalAdd()        { return kb1HorizontalAdd; }
    public double getKb1VerticalAdd()          { return kb1VerticalAdd; }
    public double getKb2HorizontalAdd()        { return kb2HorizontalAdd; }
    public double getKb2VerticalAdd()          { return kb2VerticalAdd; }
    public boolean isCooldownEnabled()         { return cooldownEnabled; }
    public double getMinChargeThreshold()      { return minChargeThreshold; }
    public boolean isScaleKnockback()          { return scaleKnockback; }
    public int getSmoothingTicks()             { return smoothingTicks; }
    public double getMaxVerticalVelocity()     { return maxVerticalVelocity; }
    public boolean isResetExistingVelocity()   { return resetExistingVelocity; }
    public boolean isHitValidationEnabled()    { return hitValidationEnabled; }
    public double getMaxRange()                { return maxRange; }
    public boolean isLagCompensation()         { return lagCompensation; }
    public double getLagCompMsPerBlock()       { return lagCompMsPerBlock; }
    public double getLagCompMaxBonus()         { return lagCompMaxBonus; }
    public boolean isRespectNetheriteResistance(){ return respectNetheriteResistance; }
    public boolean isLogHits()                 { return logHits; }
}
