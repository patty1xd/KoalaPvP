package dev.koala.koalapvp.knockback;

import dev.koala.koalapvp.KoalaPvP;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies a computed KnockbackProfile to the victim.
 *
 * No smoothing loop — the KnockbackSync approach works by correctly
 * computing what the velocity should be in one shot, including preserved
 * momentum and ping-compensated ground state. Spreading it over ticks
 * was a workaround for inaccurate calculation; with accurate calculation
 * it adds latency and unpredictability instead of helping.
 *
 * Anti-stacking: we track in-flight tasks per UUID. If a second hit lands
 * before the 0-tick delay from the first has fired (extremely fast, but
 * possible under packet bursts), we cancel the pending one so only the
 * latest KB vector applies.
 */
public final class KnockbackEngine {

    private final KoalaPvP plugin;
    private final KnockbackCalculator calculator;

    // Tracks pending 0-tick task IDs to cancel stale ones on rapid hits
    private final Map<UUID, Integer> pendingTaskIds = new ConcurrentHashMap<>();

    public KnockbackEngine(KoalaPvP plugin) {
        this.plugin     = plugin;
        this.calculator = new KnockbackCalculator(plugin.getKoalaConfig());
    }

    /**
     * Compute and apply knockback for a hit.
     * Must be called from the main thread (inside the 0-tick delayed task).
     *
     * @param charge attack cooldown fraction captured at event time
     */
    public void applyKnockback(Player attacker, Player victim, float charge) {
        KnockbackProfile profile = calculator.compute(attacker, victim, charge);
        if (!profile.isValid()) return;

        // Cancel any still-pending task for this victim (anti-stack)
        cancelPending(victim.getUniqueId());

        // Apply immediately — calculator already built the correct final vector
        victim.setVelocity(profile.getVelocity());
    }

    public void cancelPending(UUID uuid) {
        Integer taskId = pendingTaskIds.remove(uuid);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    public KnockbackCalculator getCalculator() {
        return calculator;
    }
}
