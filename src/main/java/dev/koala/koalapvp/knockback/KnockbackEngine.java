package dev.koala.koalapvp.knockback;

import dev.koala.koalapvp.KoalaPvP;
import dev.koala.koalapvp.util.Logger;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * KnockbackSync-style ground-state compensation.
 *
 * <h2>What vanilla gets wrong</h2>
 * The server lags by {@code ping/50} ticks behind the client. When a high-ping
 * player who is mid-air on their own screen gets hit, the server still reports
 * them as grounded and vanilla applies the grounded knockback formula:
 *
 *     v.y += 0.4 + (knockback enchant * 0.5)
 *
 * That y-bonus is wrong for an airborne target and makes high-ping players
 * "float" on hits. Real KnockbackSync subtracts it back out.
 *
 * <h2>How we detect the desync</h2>
 * Every movement packet the client sends carries its own {@code onGround}
 * flag. {@link ClientGroundTracker} reads that bit via PacketEvents and keeps
 * the latest value per player. The engine compares it to the server's
 * {@code Player.isOnGround()} at hit time:
 *
 *   • server onGround = true  AND  client onGround = false  →  desync, fix it
 *   • anything else                                          →  vanilla was right
 *
 * This is the same signal CASELOAD7000/Axionize KbSync uses — no heuristic,
 * no ping-shifted history, the actual client-reported flag.
 */
public final class KnockbackEngine {

    private final KoalaPvP plugin;
    private final ClientGroundTracker tracker;

    public KnockbackEngine(KoalaPvP plugin, ClientGroundTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    /**
     * Apply the compensation. Called from a 1-tick-delayed task after vanilla
     * KB has been written into {@code victim}'s velocity.
     *
     * @param attacker    the player who landed the hit (used for Knockback enchant)
     * @param victim      the player who was hit
     * @param wasOnGround server-side ground state captured at event time
     * @param preVelocity victim velocity captured BEFORE vanilla applied KB
     */
    public void compensate(Player attacker, Player victim, boolean wasOnGround, Vector preVelocity) {
        if (!victim.isOnline()) return;
        if (!plugin.getKoalaConfig().isPingCompEnabled()) return;
        if (victim.hasPermission("koalapvp.bypass")) return;

        // Sanity-bound the ping (Paper returns -1 before first ping pong).
        int ping = Math.max(0, victim.getPing());
        int pingTicks = Math.min(
            ping / Math.max(1, plugin.getKoalaConfig().getMsPerTick()),
            plugin.getKoalaConfig().getMaxStaleTicks()
        );

        // Low ping → server state is trustworthy → vanilla is correct as-is.
        if (pingTicks <= plugin.getKoalaConfig().getGroundTrustMaxTicks()) return;

        // Only fix the case where the server-vs-client ground state actually
        // disagreed at hit time. If server thought airborne, the y-bonus was
        // never applied, nothing to subtract.
        if (!wasOnGround) return;
        if (tracker.isClientOnGround(victim)) return; // client agrees → no desync

        // Subtract the exact bonus vanilla just stamped onto velocity so the
        // result matches the airborne formula the client computed locally.
        double yBonus = computeYBonus(attacker);

        Vector cur = victim.getVelocity();
        double appliedY = cur.getY() - preVelocity.getY();

        // Defensive: only patch if vanilla actually applied roughly that bonus.
        // Anything smaller and we'd be subtracting a y-kick that wasn't there.
        if (appliedY < yBonus * 0.5) return;

        cur.setY(cur.getY() - yBonus);
        victim.setVelocity(cur);

        if (plugin.getKoalaConfig().isLogHits()) {
            Logger.debug(String.format(
                "PingComp | %s ping=%dms stale=%dt yBonus=-%.2f appliedY=%.2f",
                victim.getName(), ping, pingTicks, yBonus, appliedY
            ));
        }
    }

    /**
     * Vanilla's grounded y-bonus = 0.4 base + (0.5 × Knockback enchant level
     * on the attacker's main-hand weapon). Sprint knockback does not add y.
     */
    private double computeYBonus(Player attacker) {
        double bonus = 0.4;
        if (attacker == null) return bonus;
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon == null) return bonus;
        try {
            int level = weapon.getEnchantmentLevel(Enchantment.KNOCKBACK);
            bonus += level * 0.5;
        } catch (Throwable ignored) {
            // Enchantment.KNOCKBACK may be renamed on newer Paper; fall back to 0.4.
        }
        return bonus;
    }
}
