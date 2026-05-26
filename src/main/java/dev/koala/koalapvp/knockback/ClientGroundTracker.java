package dev.koala.koalapvp.knockback;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import dev.koala.koalapvp.KoalaPvP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Reads the {@code onGround} flag the CLIENT puts in every movement packet
 * and stores the most-recent value per player.
 *
 * The vanilla client sends one of these ~20 times per second:
 *   • PLAYER_FLYING               (no movement, just status — every tick)
 *   • PLAYER_POSITION             (x/y/z + onGround)
 *   • PLAYER_ROTATION             (yaw/pitch + onGround)
 *   • PLAYER_POSITION_AND_ROTATION (both)
 *
 * All four wrap {@link WrapperPlayClientPlayerFlying} in PacketEvents and
 * expose {@code isOnGround()}. That bit IS what the client believes about
 * its own ground state — no estimation, no heuristic. The engine compares
 * it against the server's {@code Player.isOnGround()} to detect the
 * "server grounded / client airborne" desync that vanilla KB gets wrong.
 *
 * Thread safety: PacketEvents fires {@code onPacketReceive} on netty I/O
 * threads. We write through {@link ConcurrentHashMap} so the main thread
 * can safely read during damage events.
 */
public final class ClientGroundTracker implements PacketListener, Listener {

    private final KoalaPvP plugin;
    private final ConcurrentMap<UUID, Boolean> clientOnGround = new ConcurrentHashMap<>();

    public ClientGroundTracker(KoalaPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Ignore packets from the 1.20.2+ Configuration phase — they're a
        // different enum branch and casting them to Play.Client throws CCE.
        if (event.getConnectionState() != ConnectionState.PLAY) return;

        // Compare by reference (no cast) so we don't blow up if PacketEvents
        // ever surfaces another packet-type family here.
        PacketTypeCommon type = event.getPacketType();
        if (type != PacketType.Play.Client.PLAYER_FLYING
                && type != PacketType.Play.Client.PLAYER_POSITION
                && type != PacketType.Play.Client.PLAYER_ROTATION
                && type != PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            return;
        }

        UUID uuid = event.getUser().getUUID();
        if (uuid == null) return;
        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        clientOnGround.put(uuid, wrapper.isOnGround());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clientOnGround.remove(event.getPlayer().getUniqueId());
    }

    /**
     * @return the most-recent {@code onGround} flag the client sent, or the
     *         server's value as a fallback when no packet has arrived yet
     *         (e.g. the very first tick after join).
     */
    public boolean isClientOnGround(Player player) {
        Boolean v = clientOnGround.get(player.getUniqueId());
        return v != null ? v : player.isOnGround();
    }

    /** Called once on plugin disable so quit listener cleanup is fully drained. */
    public void clear() {
        clientOnGround.clear();
    }
}
