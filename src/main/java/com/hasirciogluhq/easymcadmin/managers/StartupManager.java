package com.hasirciogluhq.easymcadmin.managers;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.generic.auth.GenericAuthPacket;
import com.hasirciogluhq.easymcadmin.packets.generic.auth.GenericAuthPacketResponse;
import com.hasirciogluhq.easymcadmin.transport.TransportManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    // Explicit startup state machine
    private enum State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        AUTHENTICATED,
        SYNCING,
        READY
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.DISCONNECTED);

    // Connection attempt guard (only one concurrent connect attempt)
    private final AtomicBoolean connectingFlag = new AtomicBoolean(false);

    // Auth attempt guard (only one concurrent auth RPC outstanding)
    private final AtomicBoolean authInProgress = new AtomicBoolean(false);

    // One-shot marker for the initial sync (exactly-once)
    private final AtomicBoolean syncStarted = new AtomicBoolean(false);

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
        // Periodic watcher: if connected but not authenticated, try auth; if already
        // authenticated and we haven't started sync, ensure we transition to AUTHENTICATED
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // If transport reports authenticated, ensure our state reflects that
                    if (transportManager.isAuthenticated()) {
                        // Try to move to AUTHENTICATED if we are CONNECTED (or CONNECTING)
                        State prev = state.get();
                        if (prev == State.CONNECTED || prev == State.CONNECTING) {
                            boolean moved = state.compareAndSet(prev, State.AUTHENTICATED);
                            if (moved) {
                                plugin.getLogger().info("StartupManager: observed transport authenticated (watcher), transitioning to AUTHENTICATED");
                                // ensure transportManager authenticated flag set (idempotent)
                                transportManager.setAuthenticated(true);
                                startSyncIfNeeded();
                            }
                        } else if (prev == State.AUTHENTICATED) {
                            // we may have missed starting sync previously
                            startSyncIfNeeded();
                        }
                    } else {
                        // Not authenticated: if not connected, ensure we transition to DISCONNECTED
                        if (!transportManager.isConnected()) {
                            State prev = state.getAndSet(State.DISCONNECTED);
                            if (prev != State.DISCONNECTED) {
                                plugin.getLogger().info("StartupManager: detected transport disconnected, scheduling reconnect");
                                // clear any ongoing guards so reconnect may proceed
                                authInProgress.set(false);
                                connectingFlag.set(false);
                                // schedule reconnect attempt
                                attemptConnectWithBackoff();
                            }
                        } else {
                            // Connected but not authenticated: try an auth attempt if appropriate
                            State s = state.get();
                            if (s == State.CONNECTED || s == State.CONNECTING) {
                                plugin.getLogger().fine("StartupManager: watcher initiating auth attempt");
                                attemptAuth();
                            }
                        }
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("StartupManager auth-check task error: " + t.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L * authInterval, 20L * authInterval);
    }

    private void attemptConnectWithBackoff() {
        // If already connected, reflect state and return
        if (transportManager.isConnected()) {
            state.set(State.CONNECTED);
            return;
        }

        // Only one concurrent connect attempt
        if (!connectingFlag.compareAndSet(false, true)) return;

        int maxBackoff = Math.max(5, plugin.getConfig().getInt("startup.connect_max_backoff_seconds", 60));

        plugin.getLogger().info("StartupManager: attempting connect (backoff aware)");

        // Attempt connect asynchronously to avoid blocking main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // mark state as CONNECTING if we were DISCONNECTED
                state.updateAndGet(s -> (s == State.DISCONNECTED) ? State.CONNECTING : s);

                transportManager.connect();
                plugin.getLogger().info("StartupManager: transport.connect() returned");

                // reflect connected state
                state.updateAndGet(s -> (s == State.CONNECTING || s == State.DISCONNECTED) ? State.CONNECTED : s);

                // Try auth immediately after connect (in addition to the periodic auth retry).
                try {
                    attemptAuth();
                } catch (Throwable ignored) {
                }

                // We will also ensure sync starts when auth completes via startSyncIfNeeded()
                connectingFlag.set(false);
                // reset backoff
                connectDelaySeconds = Math.max(1, plugin.getConfig().getInt("startup.connect_initial_delay_seconds", 1));
            } catch (Exception e) {
                plugin.getLogger().warning("StartupManager: transport connect failed: " + e.getMessage());
                connectingFlag.set(false);
                // Only schedule reconnect if we are not already authenticated/ready
                State cur = state.get();
                if (cur != State.READY && cur != State.SYNCING && cur != State.AUTHENTICATED) {
                    state.set(State.DISCONNECTED);
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
            }
        });
    }

    private void attemptAuth() {
        // Only attempt auth when connected and not already authenticated
        if (!transportManager.isConnected()) return;

        State cur = state.get();
        if (cur != State.CONNECTED && cur != State.CONNECTING) return;

        if (transportManager.isAuthenticated()) {
            // Transport already says authenticated; ensure state transition and start sync
            boolean moved = state.compareAndSet(State.CONNECTED, State.AUTHENTICATED) || state.compareAndSet(State.CONNECTING, State.AUTHENTICATED);
            if (moved || state.get() == State.AUTHENTICATED) {
                transportManager.setAuthenticated(true);
                startSyncIfNeeded();
            }
            return;
        }

        // Only one outstanding auth RPC
        if (!authInProgress.compareAndSet(false, true)) return;

        try {
            GenericAuthPacket authPacket = new GenericAuthPacket(plugin.getConfig().getString("server.token", ""));
            CompletableFuture<com.hasirciogluhq.easymcadmin.packets.generic.Packet> fut = transportManager.sendRpcRequestPacket(authPacket);

            fut.orTimeout(10, TimeUnit.SECONDS).whenComplete((resp, thr) -> {
                authInProgress.set(false);
                if (thr != null) {
                    plugin.getLogger().warning("StartupManager: auth request failed: " + thr.getMessage());
                    return;
                }

                try {
                    if (resp != null) {
                        GenericAuthPacketResponse gar = new GenericAuthPacketResponse(resp);
                        if (gar.isSuccess()) {
                            plugin.getLogger().info("StartupManager: auth success via auth RPC");
                            // Mark transport manager as authenticated (idempotent)
                            transportManager.setAuthenticated(true);
                            plugin.setServerId(gar.getServerId());

                            // Transition to AUTHENTICATED only once
                            boolean transitioned = state.compareAndSet(State.CONNECTED, State.AUTHENTICATED) || state.compareAndSet(State.CONNECTING, State.AUTHENTICATED) || state.get() == State.AUTHENTICATED;
                            if (transitioned) {
                                startSyncIfNeeded();
                            }
                        } else {
                            plugin.getLogger().warning("StartupManager: auth rejected: " + gar.getMessage());
                        }
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("StartupManager: error parsing auth response: " + ex.getMessage());
                }
            });
        } catch (Exception e) {
            authInProgress.set(false);
            plugin.getLogger().warning("StartupManager: exception while attempting auth: " + e.getMessage());
        }
    }

    /**
     * Start the initial sync exactly once. This transitions the state from
     * AUTHENTICATED -> SYNCING -> READY and invokes the plugin hook when done.
     */
    private void startSyncIfNeeded() {
        // Ensure we only start sync once
        if (!syncStarted.compareAndSet(false, true)) {
            return;
        }

        // Only proceed if we're in AUTHENTICATED state (or already SYNCING/READY)
        boolean moved = state.compareAndSet(State.AUTHENTICATED, State.SYNCING) || state.get() == State.SYNCING || state.get() == State.READY;
        if (!moved) {
            // If not in authenticated state, we shouldn't start sync now. Reset syncStarted
            syncStarted.set(false);
            return;
        }

        plugin.getLogger().info("StartupManager: running initial synchronization (online -> offline)");

        // Run online sync asynchronously and chain offline sync afterwards
        CompletableFuture<Void> onlineFuture = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    serviceManager.getPlayerService().syncOnlinePlayers();
                    onlineFuture.complete(null);
                } catch (Throwable t) {
                    onlineFuture.completeExceptionally(t);
                }
            }
        }.runTaskAsynchronously(plugin);

        CompletableFuture<Void> chain = onlineFuture.thenCompose(v -> {
            CompletableFuture<Void> offlineFuture = new CompletableFuture<>();
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        serviceManager.getPlayerService().syncOfflinePlayers();
                        offlineFuture.complete(null);
                    } catch (Throwable t) {
                        offlineFuture.completeExceptionally(t);
                    }
                }
            }.runTaskAsynchronously(plugin);
            return offlineFuture;
        });

        chain.whenComplete((v, thr) -> {
            if (thr != null) {
                plugin.getLogger().warning("StartupManager: initial synchronization failed: " + thr.getMessage());
            }

            state.set(State.READY);
            plugin.getLogger().info("StartupManager: initial synchronization complete, normal processing enabled");
            // Notify plugin that transport is connected and authenticated and initial sync done
            try {
                plugin.onTransportConnectedAndAuthenticated();
            } catch (Throwable t) {
                plugin.getLogger().warning("StartupManager: error while invoking onTransportConnectedAndAuthenticated: " + t.getMessage());
            }
        });
    }
}
