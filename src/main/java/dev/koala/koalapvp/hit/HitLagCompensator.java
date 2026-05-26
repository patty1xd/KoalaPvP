package dev.koala.koalapvp.hit;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import dev.koala.koalapvp.KoalaPvP;
import dev.koala.koalapvp.util.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Source-engine-style lag-compensated hit validation.
 *
 * <h2>The problem</h2>
 * Bukkit's {@code EntityDamageByEntityEvent} fires AFTER the server has already
 * processed the attacker's swing. By that point the victim has moved (possibly
 * out of reach on the server's view) — so naive distance validation rejects
 * legit hits from high-ping attackers whose screen still showed the victim
 * within range when they clicked.
 *
 * <h2>The fix — rewind</h2>
 * We hook the {@code INTERACT_ENTITY} packet directly from PacketEvents, which
 * arrives on the netty thread before the damage event runs on the main thread.
 * For each swing we:
 *
 *   1. Look up the victim's position {@code rewindMs} ago, where
 *      {@code rewindMs ≈ attacker.ping} (capped at {@code max-rewind-ms}).
 *      That's the position the attacker actually saw on their screen.
 *   2. Distance-check the attacker against THAT historical position.
 *   3. If it passes, write an "approval" the main-thread HitValidator can read.
 *
 * Each player's last ~2 seconds of position packets are kept in a fixed-size
 * ring buffer; lookup is O(N) over ≤40 entries — negligible.
 *
 * <h2>Threading</h2>
 * PacketEvents fires on netty threads. We never touch Bukkit world state
 * here — everything reads from {@link ConcurrentHashMap}s populated either
 * from packet handlers (off-thread) or from {@link PlayerJoinEvent}/quit
 * (main thread). The main-thread {@link dev.koala.koalapvp.util.HitValidator}
 * reads the approval map via {@link #isHitApproved}.
 */
public final class HitLagCompensator implements PacketListener, Listener {

    /** Approximate ticks of history retained per player (40 ≈ 2 s). */
    private static final int HISTORY_SIZE = 40;
    /** How long an approval is honored by the main-thread validator. */
    private static final long APPROVAL_TTL_MS = 500;
    /** Hard ceiling on rewind window — protects against ping-spike abuse. */
    private static final long MAX_REWIND_MS = 200;

    private final KoalaPvP plugin;

    private final ConcurrentMap<UUID, PositionHistory> history     = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, UUID>         entityToUid = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Approval>        approvals   = new ConcurrentHashMap<>();

    public HitLagCompensator(KoalaPvP plugin) {
        this.plugin = plugin;
    }

    // ─── Packet handling ────────────────────────────────────────────────────

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getConnectionState() != ConnectionState.PLAY) return;
        PacketTypeCommon type = event.getPacketType();

        if (type == PacketType.Play.Client.PLAYER_POSITION
                || type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            recordPosition(event);
            return;
        }
        if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            validateHit(event);
        }
    }

    private void recordPosition(PacketReceiveEvent event) {
        UUID uuid = event.getUser().getUUID();
        if (uuid == null) return;
        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (!wrapper.hasPositionChanged()) return;
        Vector3d pos = wrapper.getLocation().getPosition();
        history.computeIfAbsent(uuid, k -> new PositionHistory())
               .record(System.currentTimeMillis(), pos.getX(), pos.getY(), pos.getZ());
    }

    private void validateHit(PacketReceiveEvent event) {
        UUID attackerId = event.getUser().getUUID();
        if (attackerId == null) return;

        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        UUID victimId = entityToUid.get(wrapper.getEntityId());
        if (victimId == null) return; // not a player we're tracking

        // attacker.getPing() is safe to read off-thread; clamp to MAX_REWIND_MS
        Player attacker = Bukkit.getPlayer(attackerId);
        if (attacker == null) return;
        int pingMs = Math.max(0, attacker.getPing());
        long rewindMs = Math.min(pingMs, MAX_REWIND_MS);
        long targetMs = System.currentTimeMillis() - rewindMs;

        PositionHistory victimHist  = history.get(victimId);
        PositionHistory attackerHist = history.get(attackerId);
        if (victimHist == null || attackerHist == null) return;

        double[] victimPast   = victimHist.nearest(targetMs);
        double[] attackerNow  = attackerHist.newest();
        if (victimPast == null || attackerNow == null) return;

        double dx = attackerNow[0] - victimPast[0];
        double dz = attackerNow[2] - victimPast[2];
        double dy = Math.abs(attackerNow[1] - victimPast[1]);
        double xz = Math.sqrt(dx * dx + dz * dz);

        double maxRange = plugin.getKoalaConfig().getMaxRange();
        boolean ok = xz <= maxRange && dy <= maxRange + 1.0;

        if (plugin.getKoalaConfig().isLogHits()) {
            Logger.debug(String.format(
                "Rewind | %s -> entity#%d  ping=%dms rewind=%dms  xz=%.2f dy=%.2f -> %s",
                attacker.getName(), wrapper.getEntityId(), pingMs, rewindMs, xz, dy,
                ok ? "APPROVED" : "no"
            ));
        }

        if (ok) {
            approvals.put(attackerId,
                    new Approval(victimId, System.currentTimeMillis() + APPROVAL_TTL_MS));
        }
    }

    // ─── Public read API (called from main thread by HitValidator) ──────────

    /**
     * @return true if the latest packet-level rewind check approved a hit
     *         from {@code attackerId} on {@code victimId} within the last
     *         {@value #APPROVAL_TTL_MS} ms.
     */
    public boolean isHitApproved(UUID attackerId, UUID victimId) {
        Approval a = approvals.get(attackerId);
        if (a == null) return false;
        if (System.currentTimeMillis() > a.expiresAt()) return false;
        return a.victim().equals(victimId);
    }

    // ─── Bukkit lifecycle (entity-ID ↔ UUID registry + cleanup) ─────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        entityToUid.put(p.getEntityId(), p.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        entityToUid.values().removeIf(v -> v.equals(id));
        history.remove(id);
        approvals.remove(id);
        approvals.entrySet().removeIf(e -> e.getValue().victim().equals(id));
    }

    public void clear() {
        history.clear();
        approvals.clear();
        entityToUid.clear();
    }

    // ─── Inner types ────────────────────────────────────────────────────────

    private record Approval(UUID victim, long expiresAt) {}

    /** Fixed-size ring buffer of (timestamp, x, y, z) samples per player. */
    private static final class PositionHistory {
        private final long[]   ts = new long[HISTORY_SIZE];
        private final double[] x  = new double[HISTORY_SIZE];
        private final double[] y  = new double[HISTORY_SIZE];
        private final double[] z  = new double[HISTORY_SIZE];
        private int head = 0;

        synchronized void record(long t, double xv, double yv, double zv) {
            ts[head] = t; x[head] = xv; y[head] = yv; z[head] = zv;
            head = (head + 1) % HISTORY_SIZE;
        }

        /** Nearest sample (by absolute time delta) to {@code targetMs}. */
        synchronized double[] nearest(long targetMs) {
            int bestIdx = -1;
            long bestDiff = Long.MAX_VALUE;
            for (int i = 0; i < HISTORY_SIZE; i++) {
                if (ts[i] == 0) continue;
                long diff = Math.abs(ts[i] - targetMs);
                if (diff < bestDiff) { bestDiff = diff; bestIdx = i; }
            }
            return bestIdx < 0 ? null : new double[]{x[bestIdx], y[bestIdx], z[bestIdx]};
        }

        /** Most-recent sample. */
        synchronized double[] newest() {
            int idx = (head - 1 + HISTORY_SIZE) % HISTORY_SIZE;
            if (ts[idx] == 0) return null;
            return new double[]{x[idx], y[idx], z[idx]};
        }
    }
}
