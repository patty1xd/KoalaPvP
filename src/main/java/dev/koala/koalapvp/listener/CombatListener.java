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

public final class CombatListener implements Listener {

    private final KoalaPvP plugin;
    private final HitValidator hitValidator;

    public CombatListener(KoalaPvP plugin) {
        this.plugin       = plugin;
        this.hitValidator = new HitValidator(plugin.getKoalaConfig());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {

        // 1. Both must be players
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity()  instanceof Player victim))   return;

        // 2. World enabled?
        if (!plugin.getKoalaConfig().isWorldEnabled(victim.getWorld().getName())) return;

        // 3. Bypass permission
        if (attacker.hasPermission("koalapvp.bypass")) return;

        // 4. Cooldown gate
        if (plugin.getKoalaConfig().isCooldownEnabled()) {
            float charge = attacker.getAttackCooldown();
            if (charge < plugin.getKoalaConfig().getMinChargeThreshold()) return;
        }

        // 5. Shield block — preserve vanilla push-back
        if (victim.isBlocking()) return;

        // 6. Hit validation (range + lag compensation)
        if (!hitValidator.validate(attacker, victim)) {
            event.setCancelled(true);
            if (plugin.getKoalaConfig().isLogHits())
                Logger.debug("Hit cancelled (range): " + attacker.getName() + " → " + victim.getName());
            return;
        }

        // 7. Cancel vanilla KB flag (damage still lands this tick)
        event.setKnockbackCancelled(true);

        // 8. Apply custom KB — same tick as damage (tick 0 of smoothing)
        plugin.getKnockbackEngine().applyKnockback(attacker, victim);
    }
}
