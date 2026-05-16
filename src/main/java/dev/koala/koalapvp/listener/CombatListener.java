package dev.koala.koalapvp.listener;

import dev.koala.koalapvp.KoalaPvP;
import dev.koala.koalapvp.util.HitValidator;
import dev.koala.koalapvp.util.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Intercepts PvP hits and applies KnockbackSync-style ground-state correction.
 *
 * Priority: MONITOR — we run after everything else, including vanilla damage
 * processing. ignoreCancelled = true so we never act on blocked/cancelled hits.
 *
 * We do NOT cancel or replace vanilla KB. Vanilla handles all the knockback
 * math. Our only job is to capture the victim's ground state at event time
 * (before it goes stale) and pass it to the engine via a 0-tick task that
 * runs after Paper has written its vanilla KB velocity.
 *
 * Ground state must be captured HERE because:
 *   - The 0-tick task fires after Paper updates entity positions
 *   - victim.isOnGround() inside the task may return a different (updated) value
 *   - We need the state as-of the hit, not as-of 1 tick later
 */
public final class CombatListener implements Listener {

    private final KoalaPvP    plugin;
    private final HitValidator hitValidator;

    public CombatListener(KoalaPvP plugin) {
        this.plugin       = plugin;
        this.hitValidator = new HitValidator(plugin.getKoalaConfig());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {

        // Both must be players
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity()  instanceof Player victim))   return;

        // World enabled
        if (!plugin.getKoalaConfig().isWorldEnabled(victim.getWorld().getName())) return;

        // Bypass permission
        if (attacker.hasPermission("koalapvp.bypass")) return;

        // Shield block — vanilla handles it, don't interfere
        if (victim.isBlocking()) return;

        // Hit validation — XZ range + lag compensation
        if (!hitValidator.validate(attacker, victim)) {
            event.setCancelled(true);
            if (plugin.getKoalaConfig().isLogHits())
                Logger.debug("Hit cancelled (range): "
                        + attacker.getName() + " -> " + victim.getName());
            return;
        }

        // Capture ground state NOW at event time before it becomes stale.
        // This is the key data point for ping compensation.
        final boolean wasOnGround = victim.isOnGround();

        // Schedule compensation for end of this tick.
        // By then Paper has written vanilla KB into victim's velocity.
        // The engine checks if ground-state desync caused missing vertical KB
        // and patches it if so.
        plugin.getServer().getScheduler().runTask(plugin, () ->
            plugin.getKnockbackEngine().compensate(victim, wasOnGround)
        );
    }
}
