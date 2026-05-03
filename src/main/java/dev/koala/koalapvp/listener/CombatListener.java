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
 * Main combat event handler.
 *
 * Priority: MONITOR (not HIGHEST).
 *   We run last, after every other plugin has processed the event.
 *   We only act if the event is still not cancelled.
 *   Using MONITOR means we never interfere with damage modifiers —
 *   we just override the velocity that vanilla is about to write.
 *
 * Vanilla KB suppression — Paper 1.21.1:
 *   setKnockbackCancelled() does not exist on this API version.
 *   We schedule a 0-tick delayed task. It fires at the end of the same
 *   server tick, after Paper has already written vanilla KB into the
 *   victim's velocity. Our engine then overwrites that with the
 *   ping-compensated vector. Damage is committed before this task fires.
 *
 * Charge capture:
 *   getAttackCooldown() is read HERE at event time. The 0-tick delay
 *   runs after the server resets it to 1.0, so reading it there would
 *   break cooldown scaling entirely.
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

        // ── 1. Both must be players ──────────────────────────────────────
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity()  instanceof Player victim))   return;

        // ── 2. World enabled ─────────────────────────────────────────────
        if (!plugin.getKoalaConfig().isWorldEnabled(victim.getWorld().getName())) return;

        // ── 3. Bypass permission ─────────────────────────────────────────
        if (attacker.hasPermission("koalapvp.bypass")) return;

        // ── 4. Shield block — let vanilla handle push-back ───────────────
        if (victim.isBlocking()) return;

        // ── 5. Capture charge NOW — before the 0-tick delay resets it ────
        final float charge = attacker.getAttackCooldown();

        // ── 6. Fast cooldown gate — skip range check on weak hits ─────────
        if (plugin.getKoalaConfig().isCooldownEnabled()
                && charge < plugin.getKoalaConfig().getMinChargeThreshold()) {
            // Damage already committed; just don't apply KB
            return;
        }

        // ── 7. Hit validation — XZ range + lag compensation ──────────────
        if (!hitValidator.validate(attacker, victim)) {
            event.setCancelled(true);
            if (plugin.getKoalaConfig().isLogHits())
                Logger.debug("Hit cancelled (range): "
                        + attacker.getName() + " → " + victim.getName());
            return;
        }

        // ── 8. Schedule KB override for end of this tick ─────────────────
        // 0-tick delay: fires after Paper writes vanilla KB velocity.
        // Engine reads victim.getVelocity() at that point (which includes
        // vanilla KB), then overwrites with ping-compensated vector.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!victim.isOnline()) return;
            plugin.getKnockbackEngine().applyKnockback(attacker, victim, charge);
        });
    }
}
