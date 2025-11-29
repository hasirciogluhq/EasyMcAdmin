package com.hasirciogluhq.easymcadmin;

import com.hasirciogluhq.easymcadmin.commands.MainCommand;
import com.hasirciogluhq.easymcadmin.economy.EconomyManager;
import com.hasirciogluhq.easymcadmin.managers.DispatcherManager;
import com.hasirciogluhq.easymcadmin.managers.EventListenerManager;
import com.hasirciogluhq.easymcadmin.metrics.MetricsScheduler;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.rpc.RpcStore;
import com.hasirciogluhq.easymcadmin.transport.TransportHandler;
import com.hasirciogluhq.easymcadmin.transport.TransportInterface;
import com.hasirciogluhq.easymcadmin.transport.TransportManager;
import com.hasirciogluhq.easymcadmin.transport.tcp.TcpTransport;
import com.hasirciogluhq.easymcadmin.util.ConsoleOutputHandler;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;

import java.io.IOException;
import java.util.UUID;

/**
 * Easy MC Admin - Universal Minecraft Server Management Plugin
 * 
 * @author hasirciogluhq
 * @version 1.0.0
 */
public class EasyMcAdmin extends JavaPlugin {

    private static EasyMcAdmin instance;
    private String serverId;
    private ConsoleOutputHandler consoleHandler;
    private MetricsScheduler metricsScheduler;
    private TransportManager transportManager;
    private TransportInterface transport;
    private EventListenerManager eventListenerManager;
    private DispatcherManager dispatcherManager;

    private EconomyManager economyManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config if it doesn't exist
        saveDefaultConfig();

        // Get or generate server ID
        serverId = getConfig().getString("server.id", "");
        if (serverId == null || serverId.isEmpty()) {
            // Generate a unique server ID based on server properties
            serverId = UUID.randomUUID().toString();
            getConfig().set("server.id", serverId);
            saveConfig();
            getLogger().info("Generated new server ID: " + serverId);
        }

        // Initialize RPC Store
        RpcStore.initRpc().start(this);

        // Initialize Transport Manager
        transport = new TcpTransport(this, getConfig().getString("transport.host", "localhost"),
                getConfig().getInt("transport.port", 8798));

        transportManager = new TransportManager(transport);

        dispatcherManager = new DispatcherManager(this);
        eventListenerManager = new EventListenerManager(this, dispatcherManager);

        // Setup packet handler for incoming packets from backend
        transport.setTransportListener(new TransportHandler(transportManager));

        // Initialize metrics scheduler
        metricsScheduler = new MetricsScheduler(this, new MetricsScheduler.TransportSender() {
            @Override
            public void sendPacket(Packet packet) {
                try {
                    transportManager.sendPacket(packet);
                } catch (IOException e) {
                    getLogger().warning("Failed to send packet to transport: " + e.getMessage());
                }
            }

            @Override
            public boolean isConnected() {
                return transportManager.isConnected();
            }

            @Override
            public boolean isAuthenticated() {
                return transportManager.isAuthenticated();
            }
        });

        // Setup console output handler to intercept server logs
        setupConsoleHandler();

        // Register commands
        registerCommands();

        // Initialize economy manager
        economyManager = new EconomyManager();

        // Register event listeners
        this.eventListenerManager.RegisterAllListeners();

        // Start automatic connection task (20 ticks interval)
        startConnectionTask();

        getLogger().info("Easy MC Admin has been enabled!");
        getLogger().info("Version: " + getDescription().getVersion());
        getLogger().info("Server ID: " + serverId);

        // Try initial connection if token is set
        if (getConfig().getString("server.token", "").isEmpty()) {
            getLogger().warning(
                    "No token set! Use /easymcadmin setToken <token> to set authentication token.");
        } else {
            getLogger().info("Token found, attempting to connect...");
        }
    }

    @Override
    public void onDisable() {
        // Stop metrics scheduler
        if (metricsScheduler != null) {
            metricsScheduler.stop();
        }

        // Stop RPC store cleanup task
        try {
            RpcStore.getRpcStore().stop();
        } catch (Exception e) {
            // Ignore if not initialized
        }

        // Disconnect Transport
        if (transportManager != null) {
            try {
                transportManager.disconnect();
            } catch (IOException e) {
                getLogger().warning("Failed to disconnect from transport: " + e.getMessage());
            }
        }

        // Remove console interceptor
        try {
            if (consoleHandler != null) {
                Logger root = (Logger) LogManager.getRootLogger();
                root.removeAppender(consoleHandler);
            }
        } catch (Throwable ignored) {
        }

        getLogger().info("Easy MC Admin has been disabled!");
    }

    /**
     * Setup console output handler to capture server logs
     */

    private void setupConsoleHandler() {
        try {
            consoleHandler = new ConsoleOutputHandler(this, transportManager);
            consoleHandler.start();

            Logger root = (Logger) LogManager.getRootLogger();
            root.addAppender(consoleHandler);

            getLogger().info("ConsoleOutputHandler (Log4j2 Appender) registered.");

        } catch (Exception e) {
            getLogger().warning("Failed to setup console handler: " + e.getMessage());
        }
    }

    /**
     * Register plugin commands
     */
    private void registerCommands() {
        // Register main command with subcommand support
        MainCommand mainCommand = new MainCommand(this);
        getCommand("easymcadmin").setExecutor(mainCommand);
        getCommand("easymcadmin").setTabCompleter(mainCommand);
    }

    /**
     * Start automatic connection task (tries to connect every 1 second)
     */
    private void startConnectionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!getConfig().getBoolean("transport.enabled", true)) {
                    return;
                }

                // Only try to connect if not already connected
                if (!transportManager.isConnected()) {
                    String token = getConfig().getString("server.token", "");
                    if (token != null && !token.isEmpty()) {
                        // Connect Transport
                        try {
                            transportManager.connect();
                        } catch (IOException e) {
                            getLogger().warning("Failed to connect to transport: " + e.getMessage());
                        }
                    }
                } else {
                    // If connected, ensure metrics scheduler is running
                    if (metricsScheduler != null && !metricsScheduler.isRunning()) {
                        metricsScheduler.start();
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L); // Every 1 second (20 ticks)
    }

    /**
     * Get the player list listener
     * 
     * @return PlayerListListener instance
     */
    public EventListenerManager getEventListenerManager() {
        return this.eventListenerManager;
    }

    /**
     * Get the economy manager
     * 
     * @return EconomyManager instance
     */
    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    /**
     * Get the plugin instance
     * 
     * @return EasyMcAdmin instance
     */
    public static EasyMcAdmin getInstance() {
        return instance;
    }

    /**
     * Get the Transport Manager
     * 
     * @return TransportManager instance
     */
    public TransportManager getTransportManager() {
        return transportManager;
    }

    /**
     * Get the server ID
     * 
     * @return Server ID
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * Set the server ID
     * 
     * @param serverId Server ID
     */
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    /**
     * Start metrics scheduler when Transport connection is established
     */
    public void onTransportConnectedAndAuthenticated() {
        if (metricsScheduler != null && !metricsScheduler.isRunning()) {
            metricsScheduler.start();
        }

        // Send all offline players in chunks after connection is established
        // Wait a bit for server to be fully ready
        if (getEventListenerManager().getPlayerListListener() != null) {
            getServer().getScheduler().runTaskLater(this, () -> {
                getEventListenerManager().getPlayerListListener().syncAllPlayers();
            }, 60L); // Wait 3 seconds (60 ticks) for server to be ready
        }
    }
}
