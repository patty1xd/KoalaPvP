package dev.koala.koalapvp;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import dev.koala.koalapvp.command.KoalaPvPCommand;
import dev.koala.koalapvp.config.KoalaConfig;
import dev.koala.koalapvp.hit.HitLagCompensator;
import dev.koala.koalapvp.knockback.ClientGroundTracker;
import dev.koala.koalapvp.knockback.KnockbackEngine;
import dev.koala.koalapvp.listener.CombatListener;
import dev.koala.koalapvp.util.Logger;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.plugin.java.JavaPlugin;

public final class KoalaPvP extends JavaPlugin {

    private static KoalaPvP instance;
    private KoalaConfig         koalaConfig;
    private ClientGroundTracker groundTracker;
    private HitLagCompensator   hitLagCompensator;
    private KnockbackEngine     knockbackEngine;

    @Override
    public void onLoad() {
        // PacketEvents requires setAPI + load() during onLoad so packet
        // pipelines are wired in before any player joins.
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        koalaConfig = new KoalaConfig(this);
        koalaConfig.load();

        groundTracker     = new ClientGroundTracker(this);
        hitLagCompensator = new HitLagCompensator(this);
        knockbackEngine   = new KnockbackEngine(this, groundTracker);

        // Packet listeners (read client state + INTERACT_ENTITY off the netty pipeline).
        PacketEvents.getAPI().getEventManager()
                .registerListener(groundTracker,     PacketListenerPriority.NORMAL);
        PacketEvents.getAPI().getEventManager()
                .registerListener(hitLagCompensator, PacketListenerPriority.NORMAL);
        PacketEvents.getAPI().init();

        // Bukkit listeners (CombatListener pulls hitLagCompensator at construction,
        // so it must be created after hitLagCompensator above).
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(groundTracker, this);
        getServer().getPluginManager().registerEvents(hitLagCompensator, this);

        KoalaPvPCommand cmd = new KoalaPvPCommand(this);
        getCommand("koalapvp").setExecutor(cmd);
        getCommand("koalapvp").setTabCompleter(cmd);

        Logger.info("&aKoalaPvP &7v" + getDescription().getVersion() + " &aenabled.");
        Logger.info("&7Ping compensation: &e" + koalaConfig.isPingCompEnabled()
                + " &7| ground-trust-ticks: &e" + koalaConfig.getGroundTrustMaxTicks()
                + " &7| client-state via PacketEvents: &aon");
    }

    @Override
    public void onDisable() {
        try {
            PacketEvents.getAPI().terminate();
        } catch (Throwable ignored) {}
        if (groundTracker != null)     groundTracker.clear();
        if (hitLagCompensator != null) hitLagCompensator.clear();
        Logger.info("&cKoalaPvP disabled.");
    }

    public static KoalaPvP getInstance()        { return instance; }
    public KoalaConfig     getKoalaConfig()     { return koalaConfig; }
    public KnockbackEngine getKnockbackEngine() { return knockbackEngine; }
    public ClientGroundTracker getGroundTracker()    { return groundTracker; }
    public HitLagCompensator   getHitLagCompensator() { return hitLagCompensator; }
}
