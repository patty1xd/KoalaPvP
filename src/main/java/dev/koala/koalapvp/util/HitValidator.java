package dev.koala.koalapvp.util;

import dev.koala.koalapvp.config.KoalaConfig;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Validates whether a hit is physically plausible given distance and latency.
 *
 * Distance is measured on the XZ plane only (horizontal).
 * Using full 3D distance breaks consistency on stairs, slopes, and ladders —
 * the client registers hits based on bounding-box intersection which is
 * dominated by horizontal separation, not vertical. XZ-only matches that.
 *
 * Lag compensation adds per-player leniency based on ping:
 *   bonus = clamp(ping_ms / ms_per_block, 0, max_bonus)  [in blocks]
 * Both attacker and victim ping are counted because both positions may be stale.
 */
public final class HitValidator {

    private final KoalaConfig cfg;

    public HitValidator(KoalaConfig cfg) {
        this.cfg = cfg;
    }

    /**
     * @return true  — hit is accepted
     *         false — hit should be cancelled
     */
    public boolean validate(Player attacker, Player victim) {
        if (!cfg.isHitValidationEnabled()) return true;

        double xzDistance  = xzDistance(attacker.getLocation(), victim.getLocation());
        double allowedRange = cfg.getMaxRange();

        if (cfg.isLagCompensation()) {
            allowedRange += lagBonus(attacker) + lagBonus(victim);
        }

        boolean ok = xzDistance <= allowedRange;
        if (!ok && cfg.isLogHits()) {
            Logger.debug(String.format(
                    "HitValidator REJECT | %s→%s xzDist=%.2f max=%.2f",
                    attacker.getName(), victim.getName(), xzDistance, allowedRange));
        }
        return ok;
    }

    /** Horizontal (XZ) distance only — ignores Y. */
    private double xzDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double lagBonus(Player player) {
        double bonus = player.getPing() / cfg.getLagCompMsPerBlock();
        return Math.min(bonus, cfg.getLagCompMaxBonus());
    }
}
