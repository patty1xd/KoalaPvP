package dev.koala.koalapvp.util;

import dev.koala.koalapvp.config.KoalaConfig;
import org.bukkit.entity.Player;

public final class HitValidator {

    private final KoalaConfig cfg;

    public HitValidator(KoalaConfig cfg) { this.cfg = cfg; }

    public boolean validate(Player attacker, Player victim) {
        if (!cfg.isHitValidationEnabled()) return true;

        double distance    = attacker.getLocation().distance(victim.getLocation());
        double allowedRange = cfg.getMaxRange();

        if (cfg.isLagCompensation()) {
            allowedRange += lagBonus(attacker) + lagBonus(victim);
        }

        boolean ok = distance <= allowedRange;
        if (!ok && cfg.isLogHits()) {
            Logger.debug(String.format(
                "HitValidator REJECT | %s→%s dist=%.2f max=%.2f",
                attacker.getName(), victim.getName(), distance, allowedRange));
        }
        return ok;
    }

    private double lagBonus(Player player) {
        int ping = player.getPing();
        double bonus = ping / cfg.getLagCompMsPerBlock();
        return Math.min(bonus, cfg.getLagCompMaxBonus());
    }
}
