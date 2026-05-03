package dev.koala.koalapvp.knockback;

import dev.koala.koalapvp.config.KoalaConfig;
import dev.koala.koalapvp.util.Logger;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * KnockbackSync-style lag-compensated knockback calculator.
 *
 * Core problem solved:
 *   Server calculates KB from stale server-side state. Two specific issues:
 *
 *   1. Ground-state desync — server thinks victim is grounded, but their
 *      client is already airborne (or vice versa) due to ping lag.
 *      Vanilla applies less vertical KB if server thinks player is grounded,
 *      so high-ping victims "eat" vertical KB they shouldn't.
 *      Fix: if staleness (ping ticks) > groundTrustMaxTicks, treat as airborne.
 *
 *   2. Direction — attacker->victim vector is fine since the attacker just
 *      swung (their position is current). Victim position is slightly stale
 *      but the direction error is negligible vs the ground-state error.
 *
 * Velocity model (matches vanilla):
 *   newVel.x = (existingVel.x / 2) + dir.x * h
 *   newVel.z = (existingVel.z / 2) + dir.z * h
 *   newVel.y = v   (only if grounded or already rising, checked on ORIGINAL y)
 *
 * The vertical condition is evaluated against the ORIGINAL velocity Y before
 * we halve anything — otherwise vanilla's tiny upward KB nudge would always
 * satisfy vel.getY() > 0 and we'd apply vertical lift even mid-air.
 */
public final class KnockbackCalculator {

    private final KoalaConfig cfg;

    public KnockbackCalculator(KoalaConfig cfg) {
        this.cfg = cfg;
    }

    /**
     * @param attacker the attacking player
     * @param victim   the player receiving knockback
     * @param charge   attack cooldown fraction [0.0-1.0] captured at event time
     *                 (not re-read here — by the time the 0-tick task fires
     *                  the server has already reset it to 1.0)
     */
    public KnockbackProfile compute(Player attacker, Player victim, float charge) {

        // 1. Cooldown gate
        if (cfg.isCooldownEnabled() && charge < cfg.getMinChargeThreshold()) {
            if (cfg.isLogHits())
                Logger.debug("KB rejected — charge too low: " + String.format("%.2f", charge));
            return KnockbackProfile.rejected();
        }

        // 2. Ping staleness — how many ticks is victim state stale
        int pingTicks = 0;
        if (cfg.isPingCompEnabled()) {
            pingTicks = Math.min(
                victim.getPing() / cfg.getMsPerTick(),
                cfg.getMaxStaleTicks()
            );
        }

        // 3. Ground-state compensation
        // If state is too stale to trust, assume airborne so vertical KB
        // is never incorrectly eaten by a wrong grounded check.
        final boolean isOnGround = (pingTicks <= cfg.getGroundTrustMaxTicks())
                && victim.isOnGround();

        // 4. Direction: attacker -> victim, XZ only, normalised
        // dir points AWAY from attacker — adding it to victim velocity
        // pushes victim away. Positive direction = correct knockback.
        Vector dir = victim.getLocation().toVector()
                .subtract(attacker.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() > 1e-6) {
            dir.normalize();
        } else {
            // Players stacked exactly — use attacker's facing direction
            dir = attacker.getLocation().getDirection();
            dir.setY(0);
            if (dir.lengthSquared() > 1e-6) dir.normalize();
            else dir.setX(1.0); // absolute last resort fallback
        }

        // 5. Base force values
        double h = cfg.getHorizontal();
        double v = cfg.getVertical();

        // 6. Sprint bonus
        if (attacker.isSprinting()) h += cfg.getSprintBonus();

        // 7. Knockback enchantment
        ItemStack held = attacker.getInventory().getItemInMainHand();
        int kbLevel = (held != null) ? held.getEnchantmentLevel(Enchantment.KNOCKBACK) : 0;
        if (kbLevel == 1) {
            h += cfg.getKb1HorizontalAdd();
            v += cfg.getKb1VerticalAdd();
        } else if (kbLevel >= 2) {
            h += cfg.getKb2HorizontalAdd();
            v += cfg.getKb2VerticalAdd();
        }

        // 8. Netherite resistance (each piece = 10%, max 40%)
        if (cfg.isRespectNetheriteResistance()) {
            double resist = netheriteResistance(victim);
            h *= (1.0 - resist);
            v *= (1.0 - resist);
        }

        // 9. Cooldown scaling — charge^2 matches vanilla damage curve
        if (cfg.isCooldownEnabled() && cfg.isScaleKnockback()) {
            double scale = (double) charge * charge;
            h *= scale;
            v *= scale;
        }

        // 10. Build final velocity
        // At this point (inside 0-tick task) Paper has already written
        // vanilla KB into victim velocity. We read it, halve XZ, add ours.
        Vector existing = victim.getVelocity();

        // Capture original Y BEFORE modification.
        // Vanilla's small upward nudge means existing.getY() is slightly
        // positive even mid-air — using it for the vertical condition would
        // always apply lift. We use the pre-nudge value instead.
        final double originalY = existing.getY();

        // Halve existing horizontal momentum (vanilla behaviour)
        double newX = cfg.isHalveExistingHorizontal()
                ? existing.getX() / 2.0
                : existing.getX();
        double newZ = cfg.isHalveExistingHorizontal()
                ? existing.getZ() / 2.0
                : existing.getZ();

        // Add knockback force away from attacker (dir is attacker->victim, positive)
        newX += dir.getX() * h;
        newZ += dir.getZ() * h;

        // Vertical: apply lift only if genuinely grounded or already rising.
        // Checked against originalY (before vanilla nudge), not existing.getY().
        double newY = existing.getY();
        if (isOnGround || originalY > 0) {
            newY = v;
        }

        // 11. Clamp
        newX = clamp(newX, -cfg.getMaxHorizontalVelocity(), cfg.getMaxHorizontalVelocity());
        newZ = clamp(newZ, -cfg.getMaxHorizontalVelocity(), cfg.getMaxHorizontalVelocity());
        if (newY > cfg.getMaxVerticalVelocity()) newY = cfg.getMaxVerticalVelocity();

        Vector finalVel = new Vector(newX, newY, newZ);

        if (cfg.isLogHits()) {
            Logger.debug(String.format(
                "KB | %s->%s ping=%dms staleTicks=%d groundTrusted=%b isGround=%b " +
                "charge=%.2f kbLvl=%d vec=(%.3f, %.3f, %.3f)",
                attacker.getName(), victim.getName(),
                victim.getPing(), pingTicks,
                pingTicks <= cfg.getGroundTrustMaxTicks(), isOnGround,
                charge, kbLevel,
                finalVel.getX(), finalVel.getY(), finalVel.getZ()
            ));
        }

        return new KnockbackProfile(finalVel);
    }

    private double netheriteResistance(Player victim) {
        int n = 0;
        for (ItemStack piece : victim.getInventory().getArmorContents()) {
            if (piece != null && piece.getType().name().startsWith("NETHERITE_")) n++;
        }
        return Math.min(n * 0.10, 0.40);
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
