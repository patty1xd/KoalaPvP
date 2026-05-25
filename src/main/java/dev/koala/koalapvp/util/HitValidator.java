package dev.koala.koalapvp.util;

import dev.koala.koalapvp.config.KoalaConfig;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Hit-distance validator.
 *
 * Distance is computed in XZ (horizontal) — vanilla reach is dominated by the
 * horizontal component of bounding-box intersection — plus a separate vertical
 * ({@code |dy|}) ceiling so an attacker can't reach straight up/down past
 * their hitbox.
 *
 * Lag compensation adds leniency based on the ATTACKER's ping. The relevant
 * lag for hit registration is how far the attacker's actions are delayed, not
 * the victim's. A guard handles the {@code -1} sentinel Paper returns for
 * players whose ping hasn't been computed yet.
 */
public final class HitValidator {

    private final KoalaConfig cfg;

    public HitValidator(KoalaConfig cfg) {
        this.cfg = cfg;
    }

    public boolean validate(Player attacker, Player victim) {
        if (!cfg.isHitValidationEnabled()) return true;

        Location a = attacker.getLocation();
        Location b = victim.getLocation();

        double xz = xzDistance(a, b);
        double dy = Math.abs(a.getY() - b.getY());

        double maxXZ = cfg.getMaxRange();
        if (cfg.isLagCompensation()) maxXZ += lagBonus(attacker);

        // Vertical ceiling — same base range +1; XZ-only would otherwise let
        // reach hacks from above/below sail through.
        double maxY = cfg.getMaxRange() + 1.0;

        boolean ok = xz <= maxXZ && dy <= maxY;
        if (!ok && cfg.isLogHits()) {
            Logger.debug(String.format(
                "HitValidator REJECT | %s->%s xz=%.2f dy=%.2f maxXZ=%.2f maxY=%.2f",
                attacker.getName(), victim.getName(), xz, dy, maxXZ, maxY));
        }
        return ok;
    }

    private double xzDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double lagBonus(Player p) {
        int ping = Math.max(0, p.getPing());                       // guard -1 sentinel
        double per = Math.max(1.0, cfg.getLagCompMsPerBlock());    // guard /0
        return Math.min(ping / per, cfg.getLagCompMaxBonus());
    }
}
