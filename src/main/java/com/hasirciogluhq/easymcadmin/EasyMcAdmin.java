package com.hasirciogluhq.easymcadmin;

import com.hasirciogluhq.easymcadmin.commands.MainCommand;
import com.hasirciogluhq.easymcadmin.metrics.MetricsScheduler;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.util.ConsoleOutputHandler;
import com.hasirciogluhq.easymcadmin.websocket.WebSocketManager;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;

import java.util.UUID;

/**
 * Easy MC Admin - Universal Minecraft Server Management Plugin
 * 
 * @author hasirciogluhq
 * @version 1.0.0
 */
public class EasyMcAdmin extends JavaPlugin {

    private static EasyMcAdmin instance;
    private WebSocketManager webSocketManager;
    private String serverId;
    private ConsoleOutputHandler consoleHandler;
    private MetricsScheduler metricsScheduler;
    private com.hasirciogluhq.easymcadmin.listeners.PlayerListListener playerListListener;

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

        // Initialize WebSocket Manager
        webSocketManager = new WebSocketManager(this);

        // Setup packet handler for incoming packets from backend
        webSocketManager.setPacketHandler(this::handleIncomingPacket);

        // Initialize metrics scheduler
        metricsScheduler = new MetricsScheduler(this, new MetricsScheduler.WebSocketSender() {
            @Override
            public void sendPacket(Packet packet) {
                webSocketManager.sendPacket(packet);
            }

            @Override
            public boolean isConnected() {
                return webSocketManager.isConnected();
            }
        });

        // Setup console output handler to intercept server logs
        setupConsoleHandler();

        // Register commands
        registerCommands();

        // Register event listeners
        registerListeners();

        // Start automatic connection task (1 second interval)
        startConnectionTask();

        getLogger().info("Easy MC Admin has been enabled!");
        getLogger().info("Version: " + getDescription().getVersion());
        getLogger().info("Server ID: " + serverId);

        // Try initial connection if token is set
        if (getConfig().getString("server.token", "").isEmpty()) {
            getLogger().warning("No token set! Use /easymcadmin setToken <token> to set authentication token.");
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

        // Disconnect WebSocket
        if (webSocketManager != null) {
            webSocketManager.disconnect();
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
     * Handle incoming packets from backend
     * 
     * @param packet Incoming packet
     */
    private void handleIncomingPacket(Packet packet) {
        String action = packet.getMetadata().has("action")
                ? packet.getMetadata().get("action").getAsString()
                : "";

        // Handle different packet types
        switch (action) {
            case "console_command":
                // Execute command from backend
                if (packet.getPayload().has("command")) {
                    String command = packet.getPayload().get("command").getAsString();
                    getServer().getScheduler().runTask(this, () -> {
                        getServer().dispatchCommand(getServer().getConsoleSender(), command);
                    });
                }
                break;

            case "request_full_sync":
                // Handle full sync request from backend (hash mismatch detected)
                if (packet.getPayload().has("player_uuid")) {
                    String playerUUIDStr = packet.getPayload().get("player_uuid").getAsString();
                    try {
                        UUID playerUUID = UUID.fromString(playerUUIDStr);
                        org.bukkit.entity.Player player = getServer().getPlayer(playerUUID);
                        if (player != null && player.isOnline()) {
                            // Force full sync for this player
                            if (playerListListener != null) {
                                playerListListener.sendPlayerUpdate(player, true);
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        getLogger().warning("Invalid player UUID in full sync request: " + playerUUIDStr);
                    }
                }
                break;

            default:
                getLogger().info("Unknown packet action: " + action);
        }

    }


    /**
     * Setup console output handler to capture server logs
     */

    private void setupConsoleHandler() {
        try {
            consoleHandler = new ConsoleOutputHandler(this, webSocketManager);
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
                if (!getConfig().getBoolean("websocket.enabled", true)) {
                    return;
                }

                // Only try to connect if not already connected
                if (!webSocketManager.isConnected()) {
                    String token = getConfig().getString("server.token", "");
                    if (token != null && !token.isEmpty()) {
                        // Connect WebSocket
                        webSocketManager.connect();
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
     * Register event listeners
     * Note: Console output is handled by ConsoleOutputHandler, not event listeners
     */
    private void registerListeners() {
        // Register player list listener
        playerListListener = new com.hasirciogluhq.easymcadmin.listeners.PlayerListListener(this);
        getServer().getPluginManager().registerEvents(playerListListener, this);
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
     * Get the WebSocket Manager
     * 
     * @return WebSocketManager instance
     */
    public WebSocketManager getWebSocketManager() {
        return webSocketManager;
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
     * Start metrics scheduler when WebSocket connection is established
     */
    public void onWebSocketConnected() {
        if (metricsScheduler != null && !metricsScheduler.isRunning()) {
            metricsScheduler.start();
        }
        
        // Send all offline players in chunks after connection is established
        // Wait a bit for server to be fully ready
        if (playerListListener != null) {
            getServer().getScheduler().runTaskLater(this, () -> {
                playerListListener.sendAllOfflinePlayers();
            }, 60L); // Wait 3 seconds (60 ticks) for server to be ready
        }
    }
}
