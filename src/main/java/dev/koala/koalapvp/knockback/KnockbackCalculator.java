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
 * Why we do NOT read victim.getVelocity():
 *   The calculator runs inside a 0-tick delayed task. By the time it fires,
 *   Paper has already processed and cleared its own vanilla KB. Reading
 *   victim.getVelocity() returns near-zero, so "halve existing + add ours"
 *   produces near-zero output — that was the zero-KB bug.
 *
 *   Instead we build the KB vector entirely from scratch:
 *     vel.x = dir.x * h
 *     vel.z = dir.z * h
 *     vel.y = v  (ping-compensated ground check)
 *   Then call setVelocity() directly. This is deterministic and immune to
 *   whatever state vanilla left behind.
 *
 * Ping compensation (the actual KnockbackSync value-add):
 *   Server ground state is (ping / 50) ticks stale. If staleness exceeds
 *   groundTrustMaxTicks we treat the victim as airborne — prevents vertical
 *   KB being eaten on high-ping players the server wrongly thinks are grounded.
 */
public final class KnockbackCalculator {

    private final KoalaConfig cfg;

    public KnockbackCalculator(KoalaConfig cfg) {
        this.cfg = cfg;
    }

    /**
     * @param attacker the attacking player
     * @param victim   the player receiving knockback
     * @param charge   attack cooldown [0.0-1.0] captured at event time
     */
    public KnockbackProfile compute(Player attacker, Player victim, float charge) {

        // 1. Cooldown gate
        if (cfg.isCooldownEnabled() && charge < cfg.getMinChargeThreshold()) {
            if (cfg.isLogHits())
                Logger.debug("KB rejected — charge: " + String.format("%.2f", charge));
            return KnockbackProfile.rejected();
        }

        // 2. Ping staleness
        int pingTicks = 0;
        if (cfg.isPingCompEnabled()) {
            pingTicks = Math.min(
                victim.getPing() / cfg.getMsPerTick(),
                cfg.getMaxStaleTicks()
            );
        }

        // 3. Ground state with ping compensation
        // If too stale, assume airborne — never eat vertical KB on high-ping victims
        final boolean isOnGround = (pingTicks <= cfg.getGroundTrustMaxTicks())
                && victim.isOnGround();

        // 4. Direction: attacker -> victim, XZ only, normalised
        // Adding this to velocity pushes the victim AWAY from attacker
        Vector dir = victim.getLocation().toVector()
                .subtract(attacker.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() > 1e-6) {
            dir.normalize();
        } else {
            // Stacked exactly on top — use attacker's facing
            dir = attacker.getLocation().getDirection();
            dir.setY(0);
            if (dir.lengthSquared() > 1e-6) dir.normalize();
            else dir.setX(1.0);
        }

        // 5. Base force
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

        // 8. Netherite resistance
        if (cfg.isRespectNetheriteResistance()) {
            double resist = netheriteResistance(victim);
            h *= (1.0 - resist);
            v *= (1.0 - resist);
        }

        // 9. Cooldown scaling (charge^2 matches vanilla damage curve)
        if (cfg.isCooldownEnabled() && cfg.isScaleKnockback()) {
            double scale = (double) charge * charge;
            h *= scale;
            v *= scale;
        }

        // 10. Build vector from scratch — do NOT read existing velocity
        // Paper zeroes its own KB before our task fires, so existing velocity
        // is unreliable. We own the entire vector.
        double vx = dir.getX() * h;
        double vz = dir.getZ() * h;
        // Always apply vertical — victim is either grounded (ping-compensated)
        // or airborne and still deserves the upward component
        double vy = v;

        // If airborne and not grounded, reduce vertical slightly so mid-air
        // combos don't launch people into space
        if (!isOnGround) {
            vy *= 0.85;
        }

        // 11. Clamp
        vx = clamp(vx, -cfg.getMaxHorizontalVelocity(), cfg.getMaxHorizontalVelocity());
        vz = clamp(vz, -cfg.getMaxHorizontalVelocity(), cfg.getMaxHorizontalVelocity());
        if (vy > cfg.getMaxVerticalVelocity()) vy = cfg.getMaxVerticalVelocity();

        Vector finalVel = new Vector(vx, vy, vz);

        if (cfg.isLogHits()) {
            Logger.debug(String.format(
                "KB | %s->%s ping=%dms staleTicks=%d isGround=%b(trusted=%b) " +
                "charge=%.2f kbLvl=%d h=%.3f v=%.3f vec=(%.3f, %.3f, %.3f)",
                attacker.getName(), victim.getName(),
                victim.getPing(), pingTicks,
                victim.isOnGround(), pingTicks <= cfg.getGroundTrustMaxTicks(),
                charge, kbLevel, h, v,
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
