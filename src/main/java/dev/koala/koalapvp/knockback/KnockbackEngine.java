package dev.koala.koalapvp.knockback;

import dev.koala.koalapvp.KoalaPvP;
import dev.koala.koalapvp.util.Logger;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * KnockbackSync-style ground-state compensation.
 *
 * What this does (and only this):
 *   Vanilla KB is applied normally by Paper. The only problem we fix is the
 *   ground-state desync: the server's record of whether the victim is on the
 *   ground is (ping/50) ticks stale. If the victim is high-ping and the server
 *   wrongly thinks they're grounded, Paper skips applying vertical velocity.
 *   We detect that case and re-apply the vertical component ourselves.
 *
 * What this does NOT do:
 *   - Change horizontal KB values
 *   - Change vertical KB values
 *   - Add custom force calculations
 *   Everything is vanilla except the ground-state correction.
 *
 * Timing:
 *   Called from a 0-tick delayed task so Paper has already written its
 *   vanilla KB into the victim's velocity. We read that velocity and only
 *   patch the Y component when the ground state was likely wrong.
 */
public final class KnockbackEngine {

    private final KoalaPvP plugin;

    public KnockbackEngine(KoalaPvP plugin) {
        this.plugin = plugin;
    }

    /**
     * Apply ping-compensated ground-state correction to vanilla KB.
     *
     * @param victim     the player who was hit
     * @param wasOnGround ground state captured at event time (before 0-tick delay)
     */
    public void compensate(Player victim, boolean wasOnGround) {
        if (!victim.isOnline()) return;

        // How stale is the server's ground state for this victim?
        int pingTicks = 0;
        if (plugin.getKoalaConfig().isPingCompEnabled()) {
            pingTicks = Math.min(
                victim.getPing() / plugin.getKoalaConfig().getMsPerTick(),
                plugin.getKoalaConfig().getMaxStaleTicks()
            );
        }

        // If ping is low enough that ground state is trustworthy, nothing to fix
        if (pingTicks <= plugin.getKoalaConfig().getGroundTrustMaxTicks()) {
            return;
        }

        // High ping: server ground state is stale. If vanilla thought victim was
        // grounded and skipped vertical KB, the victim's Y velocity will be near
        // zero or slightly negative (gravity). We fix it by checking: if
        // wasOnGround (event-time capture) and Y velocity is very small, vanilla
        // probably applied horizontal-only KB and skipped vertical. Re-apply it.
        Vector vel = victim.getVelocity();

        // Vanilla vertical KB when grounded is ~0.4. If we see Y < 0.1 and the
        // event-time ground state said grounded, vanilla skipped it — apply it.
        if (wasOnGround && vel.getY() < 0.1) {
            vel.setY(0.4); // vanilla default vertical KB on ground hit
            victim.setVelocity(vel);

            if (plugin.getKoalaConfig().isLogHits()) {
                Logger.debug(String.format(
                    "PingComp | %s ping=%dms staleTicks=%d — patched vertical Y to 0.4",
                    victim.getName(), victim.getPing(), pingTicks
                ));
            }
        }
    }
}
