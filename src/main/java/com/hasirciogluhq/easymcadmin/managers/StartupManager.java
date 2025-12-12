package com.hasirciogluhq.easymcadmin.managers;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.generic.auth.GenericAuthPacket;
import com.hasirciogluhq.easymcadmin.packets.generic.auth.GenericAuthPacketResponse;
import com.hasirciogluhq.easymcadmin.transport.TransportManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * StartupManager orchestrates plugin startup phases:
 *  - load (connection setup with backoff)
 *  - auth checks with controlled retries
 *  - initial sync (send all online then offline players)
 *  - enable normal processing
 */
public class StartupManager {
    private final EasyMcAdmin plugin;
    private final TransportManager transportManager;
    private final ServiceManager serviceManager;

    private volatile boolean connecting = false;
    private volatile boolean started = false;

    // Backoff state
    private int connectDelaySeconds;

    public StartupManager(EasyMcAdmin plugin) {
        this.plugin = plugin;
        this.transportManager = plugin.getTransportManager();
        this.serviceManager = plugin.getServiceManager();

        this.connectDelaySeconds = Math.max(1, plugin.getConfig().getInt("startup.connect_initial_delay_seconds", 1));
    }

    public void start() {
        load();
    }

    private void load() {
        plugin.getLogger().info("StartupManager: load() - starting connection flow");
        // Readiness: attempt connect with backoff if not connected
        attemptConnectWithBackoff();

        // Schedule periodic auth check (will send auth if connected but unauthenticated)
        int authInterval = Math.max(5, plugin.getConfig().getInt("startup.auth_retry_interval_seconds", 10));
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (transportManager.isConnected() && !transportManager.isAuthenticated()) {
                        plugin.getLogger().info("StartupManager: attempting auth check (scheduled)");
                        attemptAuth();
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("StartupManager auth-check task error: " + t.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L * authInterval, 20L * authInterval);
    }

    private void attemptConnectWithBackoff() {
        if (connecting || transportManager.isConnected()) return;
        connecting = true;

        int maxBackoff = Math.max(5, plugin.getConfig().getInt("startup.connect_max_backoff_seconds", 60));

        plugin.getLogger().info("StartupManager: attempting initial connect (backoff aware)");

        // Attempt connect asynchronously to avoid blocking main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                transportManager.connect();
                plugin.getLogger().info("StartupManager: transport.connect() returned");
                // Try auth immediately after connect (in addition to the periodic auth retry).
                // This reduces the chance that waitForAuthThenSync() times out before any auth
                // attempt is made.
                try {
                    attemptAuth();
                } catch (Throwable ignored) {
                }
                // Continue to wait for auth and run initial sync when it becomes available.
                waitForAuthThenSync();
                connecting = false;
                // reset backoff
                connectDelaySeconds = Math.max(1, plugin.getConfig().getInt("startup.connect_initial_delay_seconds", 1));
            } catch (Exception e) {
                plugin.getLogger().warning("StartupManager: transport connect failed: " + e.getMessage());
                connecting = false;
                // schedule next attempt with exponential backoff
                int nextDelay = Math.min(maxBackoff, Math.max(1, connectDelaySeconds * 2));
                connectDelaySeconds = nextDelay;
                plugin.getLogger().info("StartupManager: scheduling reconnect in " + nextDelay + "s");
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        attemptConnectWithBackoff();
                    }
                }.runTaskLaterAsynchronously(plugin, 20L * nextDelay);
            }
        });
    }

    private void attemptAuth() {
        if (!transportManager.isConnected()) return;

        try {
            GenericAuthPacket authPacket = new GenericAuthPacket(plugin.getConfig().getString("server.token", ""));
            CompletableFuture<?> fut = transportManager.sendRpcRequestPacket(authPacket);
            fut.orTimeout(10, TimeUnit.SECONDS).whenComplete((resp, thr) -> {
                if (thr != null) {
                    plugin.getLogger().warning("StartupManager: auth request failed: " + thr.getMessage());
                    return;
                }

                try {
                    if (resp instanceof com.hasirciogluhq.easymcadmin.packets.generic.Packet) {
                        GenericAuthPacketResponse gar = new GenericAuthPacketResponse((com.hasirciogluhq.easymcadmin.packets.generic.Packet) resp);
                        if (gar.isSuccess()) {
                            plugin.getLogger().info("StartupManager: auth success via direct auth attempt");
                            transportManager.setAuthenticated(true);
                            plugin.setServerId(gar.getServerId());
                            onAuthenticated();
                        } else {
                            plugin.getLogger().warning("StartupManager: auth rejected: " + gar.getMessage());
                        }
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("StartupManager: error parsing auth response: " + ex.getMessage());
                }
            });
        } catch (Exception e) {
            plugin.getLogger().warning("StartupManager: exception while attempting auth: " + e.getMessage());
        }
    }

    private void waitForAuthThenSync() {
        // Poll for auth state with timeout. If auth becomes true, proceed to sync.
        int maxWaitSeconds = Math.max(5, plugin.getConfig().getInt("startup.auth_wait_max_seconds", 30));

        plugin.getLogger().info("StartupManager: waiting up to " + maxWaitSeconds + "s for authentication");

        new BukkitRunnable() {
            int waited = 0;

            @Override
            public void run() {
                if (transportManager.isAuthenticated()) {
                    plugin.getLogger().info("StartupManager: detected authenticated state");
                    this.cancel();
                    onAuthenticated();
                    return;
                }

                if (!transportManager.isConnected()) {
                    plugin.getLogger().warning("StartupManager: lost connection while waiting for auth");
                    this.cancel();
                    attemptConnectWithBackoff();
                    return;
                }

                waited += 1;
                if (waited >= maxWaitSeconds) {
                    plugin.getLogger().warning("StartupManager: auth not completed within " + maxWaitSeconds + "s, will rely on periodic auth attempts");
                    this.cancel();
                    return;
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L, 20L);
    }

    private void onAuthenticated() {
        // Ensure we only run initial sync once
        if (started) return;
        started = true;

        plugin.getLogger().info("StartupManager: running initial synchronization (online -> offline)");

        // Send online players first
        try {
            serviceManager.getPlayerService().syncOnlinePlayers();
        } catch (Exception e) {
            plugin.getLogger().warning("StartupManager: failed to sync online players: " + e.getMessage());
        }

        // Then send offline chunks
        try {
            serviceManager.getPlayerService().syncOfflinePlayers();
        } catch (Exception e) {
            plugin.getLogger().warning("StartupManager: failed to sync offline players: " + e.getMessage());
        }

        // Normal mode can now run — other systems can rely on this signal
        plugin.getLogger().info("StartupManager: initial synchronization complete, normal processing enabled");
        plugin.onTransportConnectedAndAuthenticated();
    }
}
