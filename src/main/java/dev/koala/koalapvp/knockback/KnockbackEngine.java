package dev.koala.koalapvp.knockback;

import dev.koala.koalapvp.KoalaPvP;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KnockbackEngine {

    private final KoalaPvP plugin;
    private final KnockbackCalculator calculator;
    private final Map<UUID, BukkitRunnable> pendingTasks = new ConcurrentHashMap<>();

    public KnockbackEngine(KoalaPvP plugin) {
        this.plugin     = plugin;
        this.calculator = new KnockbackCalculator(plugin.getKoalaConfig());
    }

    public void applyKnockback(Player attacker, Player victim) {
        KnockbackProfile profile = calculator.compute(attacker, victim);
        if (!profile.isValid()) return;
        apply(victim, profile);
    }

    private void apply(Player victim, KnockbackProfile profile) {
        cancelPending(victim.getUniqueId());

        Vector total = profile.getVelocity();
        int    ticks = profile.getSmoothingTicks();

        if (plugin.getKoalaConfig().isResetExistingVelocity()) {
            victim.setVelocity(new Vector(0, 0, 0));
        }

        if (ticks <= 1) {
            victim.setVelocity(total);
            return;
        }

        Vector perTick = total.clone().multiply(1.0 / ticks);

        // Tick 0 — immediate, same tick as damage
        victim.setVelocity(victim.getVelocity().add(perTick));

        final int remaining = ticks - 1;
        BukkitRunnable task = new BukkitRunnable() {
            int count = 0;
            @Override public void run() {
                if (!victim.isOnline() || count >= remaining) {
                    pendingTasks.remove(victim.getUniqueId());
                    cancel();
                    return;
                }
                victim.setVelocity(victim.getVelocity().add(perTick));
                count++;
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
        pendingTasks.put(victim.getUniqueId(), task);
    }

    public void cancelPending(UUID uuid) {
        BukkitRunnable old = pendingTasks.remove(uuid);
        if (old != null) try { old.cancel(); } catch (Exception ignored) {}
    }

    public KnockbackCalculator getCalculator() { return calculator; }
}
