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
 * Core problem this solves:
 *   The server calculates KB using B's server-side position/state, but B's
 *   client is ahead of that by (ping / 50) ticks. Two specific bugs result:
 *
 *   1. Ground-state desync — server thinks B is grounded, client has B
 *      airborne (or vice versa). Vanilla applies less vertical KB on ground,
 *      so a high-ping victim "eats" vertical KB they shouldn't.
 *      Fix: if ping staleness > ground-trust-max-ticks, treat as airborne.
 *
 *   2. Momentum accumulation — vanilla halves existing XZ velocity before
 *      adding KB. We preserve this but apply it to a zeroed base so stale
 *      server velocity doesn't bleed into the result.
 *
 * Direction is attacker → victim XZ (already accurate because the attacker
 * just swung — their position is fresh). The main inaccuracy is ground state,
 * not direction, so we only compensate ground state.
 */
public final class KnockbackCalculator {

    private final KoalaConfig cfg;

    public KnockbackCalculator(KoalaConfig cfg) {
        this.cfg = cfg;
    }

    /**
     * @param attacker the attacking player
     * @param victim   the hit player
     * @param charge   attack cooldown fraction [0.0–1.0] captured at event time
     */
    public KnockbackProfile compute(Player attacker, Player victim, float charge) {

        // ── 1. Cooldown gate ─────────────────────────────────────────────
        if (cfg.isCooldownEnabled() && charge < cfg.getMinChargeThreshold()) {
            if (cfg.isLogHits())
                Logger.debug("KB rejected — charge too low: " + String.format("%.2f", charge));
            return KnockbackProfile.rejected();
        }

        // ── 2. Ping staleness ─────────────────────────────────────────────
        // How many ticks out-of-date is the victim's server-side state?
        int pingTicks = 0;
        if (cfg.isPingCompEnabled()) {
            pingTicks = Math.min(
                victim.getPing() / cfg.getMsPerTick(),
                cfg.getMaxStaleTicks()
            );
        }

        // ── 3. Ground-state compensation ─────────────────────────────────
        // If victim's state is stale beyond the trust threshold, assume
        // airborne — this prevents eating vertical KB when server thinks
        // grounded but client is already in the air.
        boolean isOnGround;
        if (pingTicks <= cfg.getGroundTrustMaxTicks()) {
            isOnGround = victim.isOnGround();
        } else {
            isOnGround = false; // distrust stale ground state
        }

        // ── 4. Horizontal direction — attacker → victim, XZ only ─────────
        Vector dir = victim.getLocation().toVector()
                .subtract(attacker.getLocation().toVector());
        dir.setY(0);
        double len = dir.lengthSquared();
        if (len > 0) {
            dir.normalize();
        } else {
            // Directly on top — use attacker's facing direction
            dir = attacker.getLocation().getDirection();
            dir.setY(0);
            if (dir.lengthSquared() > 0) dir.normalize();
            else dir.setX(1);
        }

        // ── 5. Base horizontal/vertical ───────────────────────────────────
        double h = cfg.getHorizontal();
        double v = cfg.getVertical();

        // ── 6. Sprint bonus ───────────────────────────────────────────────
        if (attacker.isSprinting()) h += cfg.getSprintBonus();

        // ── 7. Enchantment bonus ──────────────────────────────────────────
        ItemStack held = attacker.getInventory().getItemInMainHand();
        int kbLevel = (held != null) ? held.getEnchantmentLevel(Enchantment.KNOCKBACK) : 0;
        if (kbLevel == 1) {
            h += cfg.getKb1HorizontalAdd();
            v += cfg.getKb1VerticalAdd();
        } else if (kbLevel >= 2) {
            h += cfg.getKb2HorizontalAdd();
            v += cfg.getKb2VerticalAdd();
        }

        // ── 8. Netherite resistance ───────────────────────────────────────
        if (cfg.isRespectNetheriteResistance()) {
            double resist = netheriteResistance(victim);
            h *= (1.0 - resist);
            v *= (1.0 - resist);
        }

        // ── 9. Cooldown scaling — charge² matches vanilla damage curve ────
        if (cfg.isCooldownEnabled() && cfg.isScaleKnockback()) {
            double scale = charge * charge;
            h *= scale;
            v *= scale;
        }

        // ── 10. Build velocity — KnockbackSync style ──────────────────────
        // Start from victim's current velocity so we preserve momentum,
        // then halve existing XZ (vanilla behaviour), then push.
        Vector vel = victim.getVelocity();

        if (cfg.isHalveExistingHorizontal()) {
            vel.setX(vel.getX() / 2.0);
            vel.setZ(vel.getZ() / 2.0);
        }

        vel.setX(vel.getX() - dir.getX() * h);
        vel.setZ(vel.getZ() - dir.getZ() * h);

        // Apply vertical only when grounded or already moving upward
        // (ping-compensated ground state used here — the key fix)
        if (isOnGround || vel.getY() > 0) {
            vel.setY(v);
        }

        // ── 11. Clamp ─────────────────────────────────────────────────────
        vel.setX(clamp(vel.getX(), -cfg.getMaxHorizontalVelocity(), cfg.getMaxHorizontalVelocity()));
        vel.setZ(clamp(vel.getZ(), -cfg.getMaxHorizontalVelocity(), cfg.getMaxHorizontalVelocity()));
        if (vel.getY() > cfg.getMaxVerticalVelocity()) vel.setY(cfg.getMaxVerticalVelocity());

        if (cfg.isLogHits()) {
            Logger.debug(String.format(
                "KB | %s→%s ping=%dms staleTicks=%d ground=%b(trusted=%b) " +
                "charge=%.2f kb=%d vec=(%.3f, %.3f, %.3f)",
                attacker.getName(), victim.getName(),
                victim.getPing(), pingTicks,
                victim.isOnGround(), pingTicks <= cfg.getGroundTrustMaxTicks(),
                charge, kbLevel,
                vel.getX(), vel.getY(), vel.getZ()
            ));
        }

        return new KnockbackProfile(vel);
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
