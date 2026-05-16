package dev.koala.koalapvp.util;

import dev.koala.koalapvp.config.KoalaConfig;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Validates hit distance using XZ-only (horizontal) measurement.
 *
 * Full 3D distance breaks on slopes and stairs because the client registers
 * hits based on bounding-box intersection, which is dominated by horizontal
 * separation. XZ-only matches that behaviour.
 *
 * Lag compensation adds leniency per player based on ping so legitimate
 * hits from high-ping players aren't falsely rejected.
 */
public final class HitValidator {

    private final KoalaConfig cfg;

    public HitValidator(KoalaConfig cfg) {
        this.cfg = cfg;
    }

    public boolean validate(Player attacker, Player victim) {
        if (!cfg.isHitValidationEnabled()) return true;

        double dist = xzDistance(attacker.getLocation(), victim.getLocation());
        double max  = cfg.getMaxRange();

        if (cfg.isLagCompensation()) {
            max += lagBonus(attacker) + lagBonus(victim);
        }

        boolean ok = dist <= max;
        if (!ok && cfg.isLogHits()) {
            Logger.debug(String.format(
                "HitValidator REJECT | %s->%s xzDist=%.2f max=%.2f",
                attacker.getName(), victim.getName(), dist, max));
        }
        return ok;
    }

    private double xzDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double lagBonus(Player p) {
        return Math.min(p.getPing() / cfg.getLagCompMsPerBlock(), cfg.getLagCompMaxBonus());
    }
}
