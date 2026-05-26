package dev.koala.koalapvp.listener;

import dev.koala.koalapvp.KoalaPvP;
import dev.koala.koalapvp.util.HitValidator;
import dev.koala.koalapvp.util.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

/**
 * Two-stage listener.
 *
 *   1. HIGHEST + ignoreCancelled — runs BEFORE vanilla KB. Validates the hit
 *      range and may cancel the event. Captures the victim's ground state and
 *      pre-KB velocity while they're still untouched.
 *
 *   2. MONITOR — runs AFTER vanilla KB has been applied. Hands the captured
 *      state to the engine, which decides whether to subtract the grounded
 *      y-bonus that vanilla wrongly added for a high-ping airborne victim.
 *
 * Splitting the priorities matters: Bukkit documents that MONITOR listeners
 * must NOT mutate or cancel the event. The old code did both, which some
 * plugin stacks silently ignore.
 */
public final class CombatListener implements Listener {

    private final KoalaPvP     plugin;
    private final HitValidator hitValidator;

    public CombatListener(KoalaPvP plugin) {
        this.plugin       = plugin;
        this.hitValidator = new HitValidator(plugin.getKoalaConfig(), plugin.getHitLagCompensator());
    }

    /** Stage 1 — validate + cancel before vanilla processes the hit. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageEarly(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity()  instanceof Player victim))   return;

        if (!plugin.getKoalaConfig().isWorldEnabled(victim.getWorld().getName())) return;
        if (attacker.hasPermission("koalapvp.bypass")) return;
        if (victim.isBlocking()) return;

        if (!hitValidator.validate(attacker, victim)) {
            event.setCancelled(true);
            if (plugin.getKoalaConfig().isLogHits())
                Logger.debug("Hit cancelled (range): "
                        + attacker.getName() + " -> " + victim.getName());
        }
    }

    /**
     * Stage 2 — capture pre-KB state and schedule the post-KB compensation.
     * Runs at MONITOR so we know vanilla KB has now fired. The actual velocity
     * patch is scheduled for the next tick, at which point Paper has written
     * its grounded-KB y-bonus into the victim's velocity.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageMonitor(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity()  instanceof Player victim))   return;

        if (!plugin.getKoalaConfig().isWorldEnabled(victim.getWorld().getName())) return;
        if (attacker.hasPermission("koalapvp.bypass")) return;
        if (victim.hasPermission("koalapvp.bypass")) return;
        if (victim.isBlocking()) return;

        final boolean wasOnGround = victim.isOnGround();
        final Vector  preVelocity = victim.getVelocity().clone();

        plugin.getServer().getScheduler().runTask(plugin, () ->
            plugin.getKnockbackEngine().compensate(attacker, victim, wasOnGround, preVelocity)
        );
    }
}
