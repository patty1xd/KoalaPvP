package dev.koala.koalapvp.util;

import dev.koala.koalapvp.config.KoalaConfig;
import dev.koala.koalapvp.hit.HitLagCompensator;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Hit-distance validator with two tiers:
 *
 *   1. Packet-level rewind (preferred). {@link HitLagCompensator} watches
 *      INTERACT_ENTITY packets directly off the netty thread and approves a
 *      hit if the victim's position {@code attacker.ping} ms ago was within
 *      reach. If an approval exists, the hit goes through with NO additional
 *      ping-based leniency — accuracy comes from the rewind, not from a
 *      static reach bonus.
 *
 *   2. Server-current XZ fallback. If no packet approval is on file (player
 *      on a launcher that masks packets, or no rewind history yet), fall back
 *      to the legacy XZ + dy check with the optional static ping bonus.
 *
 * Vertical distance is bounded so reach-from-above/below can't slip through
 * an XZ-only check.
 */
public final class HitValidator {

    private final KoalaConfig cfg;
    private final HitLagCompensator compensator;

    public HitValidator(KoalaConfig cfg, HitLagCompensator compensator) {
        this.cfg = cfg;
        this.compensator = compensator;
    }

    public boolean validate(Player attacker, Player victim) {
        if (!cfg.isHitValidationEnabled()) return true;

        // Tier 1 — packet-level rewind approval (most accurate, no ping bonus needed)
        if (cfg.isRewindEnabled()
                && compensator != null
                && compensator.isHitApproved(attacker.getUniqueId(), victim.getUniqueId())) {
            return true;
        }

        // Tier 2 — fallback XZ + dy check with optional static ping bonus
        Location a = attacker.getLocation();
        Location b = victim.getLocation();

        double xz = xzDistance(a, b);
        double dy = Math.abs(a.getY() - b.getY());

        double maxXZ = cfg.getMaxRange();
        if (cfg.isLagCompensation()) maxXZ += lagBonus(attacker);

        double maxY = cfg.getMaxRange() + 1.0;

        boolean ok = xz <= maxXZ && dy <= maxY;
        if (!ok && cfg.isLogHits()) {
            Logger.debug(String.format(
                "HitValidator REJECT | %s->%s xz=%.2f dy=%.2f maxXZ=%.2f maxY=%.2f (no rewind approval)",
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
        int ping = Math.max(0, p.getPing());                    // guard -1 sentinel
        double per = Math.max(1.0, cfg.getLagCompMsPerBlock()); // guard /0
        return Math.min(ping / per, cfg.getLagCompMaxBonus());
    }
}
