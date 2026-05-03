package dev.koala.koalapvp.listener;

import dev.koala.koalapvp.KoalaPvP;
import dev.koala.koalapvp.util.HitValidator;
import dev.koala.koalapvp.util.Logger;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Intercepts every player-vs-player damage event.
 *
 * Priority: HIGHEST — runs after armour/absorption modifiers are resolved.
 *
 * Vanilla KB suppression — Paper 1.21.1:
 *   setKnockbackCancelled() does NOT exist on this API version.
 *   We schedule a 0-tick delayed task. It fires after Paper has already
 *   written vanilla KB into the victim's velocity. We zero that velocity
 *   then apply our own deterministic vector. Damage is committed before
 *   the task fires, so damage and KB land in the same tick client-side.
 *
 * Cooldown:
 *   getAttackCooldown() is captured HERE at event time. By the time the
 *   0-tick task runs the server has already reset it to 1.0, so reading
 *   it there would make cooldown scaling completely non-functional.
 */
public final class CombatListener implements Listener {

    private final KoalaPvP plugin;
    private final HitValidator hitValidator;

    public CombatListener(KoalaPvP plugin) {
        this.plugin       = plugin;
        this.hitValidator = new HitValidator(plugin.getKoalaConfig());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {

        // ── 1. Both must be players ──────────────────────────────────────
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity()  instanceof Player victim))   return;

        // ── 2. World enabled? ────────────────────────────────────────────
        if (!plugin.getKoalaConfig().isWorldEnabled(victim.getWorld().getName())) return;

        // ── 3. Bypass permission ─────────────────────────────────────────
        if (attacker.hasPermission("koalapvp.bypass")) return;

        // ── 4. Shield block — let vanilla handle the push-back ───────────
        if (victim.isBlocking()) return;

        // ── 5. Capture charge NOW — before the 0-tick task, before reset ─
        final float charge = attacker.getAttackCooldown();

        // ── 6. Cooldown gate (fast-reject before range check) ────────────
        if (plugin.getKoalaConfig().isCooldownEnabled()
                && charge < plugin.getKoalaConfig().getMinChargeThreshold()) {
            // Damage still lands, we just apply no KB
            return;
        }

        // ── 7. Hit validation (XZ range + lag compensation) ──────────────
        if (!hitValidator.validate(attacker, victim)) {
            event.setCancelled(true);
            if (plugin.getKoalaConfig().isLogHits())
                Logger.debug("Hit cancelled (range): "
                        + attacker.getName() + " → " + victim.getName());
            return;
        }

        // ── 8. Override vanilla KB at end of this tick ───────────────────
        // 0-tick delay fires after Paper writes its vanilla KB velocity.
        // We zero it, then set our own. charge captured above is passed in.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!victim.isOnline()) return;
            plugin.getKnockbackEngine().applyKnockback(attacker, victim, charge);
        });
    }
}
