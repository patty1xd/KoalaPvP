package dev.koala.koalapvp.knockback;

import dev.koala.koalapvp.KoalaPvP;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies a KnockbackProfile to a victim player.
 *
 * Smoothing strategy (velocity stacking fix):
 *   We do NOT use getVelocity().add() on subsequent ticks. By the time tick 1
 *   fires, gravity and player movement have already modified the victim's
 *   velocity — adding on top would stack and produce unpredictable results.
 *   Instead we store the remaining velocity budget and SET it directly each
 *   tick, overwriting whatever the physics engine has done. This keeps KB
 *   purely deterministic regardless of TPS or player movement.
 *
 * Anti-desync:
 *   Any pending smoothing task for a player is cancelled before a new one
 *   starts. This prevents old and new KB vectors accumulating on rapid hits.
 */
public final class KnockbackEngine {

    private final KoalaPvP plugin;
    private final KnockbackCalculator calculator;

    // Pending smoothing tasks keyed by victim UUID
    private final Map<UUID, BukkitRunnable> pendingTasks = new ConcurrentHashMap<>();

    public KnockbackEngine(KoalaPvP plugin) {
        this.plugin     = plugin;
        this.calculator = new KnockbackCalculator(plugin.getKoalaConfig());
    }

    /**
     * Compute and apply knockback for a hit.
     * Must be called from the main thread.
     *
     * @param charge attack cooldown fraction captured at event time
     */
    public void applyKnockback(Player attacker, Player victim, float charge) {
        KnockbackProfile profile = calculator.compute(attacker, victim, charge);
        if (!profile.isValid()) return;
        apply(victim, profile);
    }

    private void apply(Player victim, KnockbackProfile profile) {
        // Cancel any in-flight task first — anti-desync, no stacking
        cancelPending(victim.getUniqueId());

        final Vector total = profile.getVelocity();
        final int    ticks = profile.getSmoothingTicks();

        // Always zero existing velocity before our push
        victim.setVelocity(new Vector(0, 0, 0));

        if (ticks <= 1) {
            victim.setVelocity(total);
            return;
        }

        // Smoothing: divide total into N equal slices.
        // Each slice is the velocity for that specific tick — we SET, not ADD.
        // perTick carries the horizontal push; vertical only on tick 0 so the
        // player gets the full lift immediately and doesn't get double-lifted.
        final Vector horizPerTick = new Vector(
                total.getX() / ticks,
                0,
                total.getZ() / ticks
        );

        // Tick 0 — immediate: full vertical + first horizontal slice
        victim.setVelocity(new Vector(
                horizPerTick.getX(),
                total.getY(),          // full Y on first tick only
                horizPerTick.getZ()
        ));

        // Ticks 1..N-1 — horizontal continuation only, SET not ADD
        final int remaining = ticks - 1;
        BukkitRunnable task = new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (!victim.isOnline() || count >= remaining) {
                    pendingTasks.remove(victim.getUniqueId());
                    cancel();
                    return;
                }
                // Preserve current Y (gravity is doing its job), replace XZ only
                double currentY = victim.getVelocity().getY();
                victim.setVelocity(new Vector(
                        horizPerTick.getX(),
                        currentY,
                        horizPerTick.getZ()
                ));
                count++;
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
        pendingTasks.put(victim.getUniqueId(), task);
    }

    /** Cancel any in-flight smoothing task for this player. */
    public void cancelPending(UUID uuid) {
        BukkitRunnable old = pendingTasks.remove(uuid);
        if (old != null) {
            try { old.cancel(); } catch (Exception ignored) {}
        }
    }

    public KnockbackCalculator getCalculator() {
        return calculator;
    }
}
