package com.hasirciogluhq.easymcadmin;

import com.hasirciogluhq.easymcadmin.commands.MainCommand;
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

        getLogger().info("Received packet: " + action + " (type: " + packet.getPacketType() + ")");

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
            case "server_status_request":
                // Send server status back
                sendServerStatus();
                break;
            default:
                getLogger().info("Unknown packet action: " + action);
        }
    }

    /**
     * Send server status to backend
     */
    private void sendServerStatus() {
        com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
        payload.addProperty("server_id", serverId);
        payload.addProperty("online_players", getServer().getOnlinePlayers().size());
        payload.addProperty("max_players", getServer().getMaxPlayers());
        payload.addProperty("version", getServer().getVersion());

        // Safely get TPS if available; otherwise, omit or use a fallback
        double tps = -1.0;
        try {
            Object tpsObj = getServer().getClass().getMethod("getTPS").invoke(getServer());
            if (tpsObj instanceof double[]) {
                tps = ((double[]) tpsObj)[0];
            }
        } catch (Exception e) {
            // Method does not exist or error occurred
            tps = -1.0;
        }
        payload.addProperty("tps", tps);

        com.google.gson.JsonObject metadata = new com.google.gson.JsonObject();
        metadata.addProperty("action", "server_status");
        metadata.addProperty("requires_response", false);

        Packet statusPacket = new Packet(
                java.util.UUID.randomUUID().toString(),
                com.hasirciogluhq.easymcadmin.packets.PacketType.EVENT,
                metadata,
                payload) {
            @Override
            public com.google.gson.JsonObject toJson() {
                com.google.gson.JsonObject json = new com.google.gson.JsonObject();
                json.addProperty("packet_id", getPacketId());
                json.addProperty("packet_type", getPacketType().name());
                json.add("metadata", getMetadata());
                json.add("payload", getPayload());
                json.addProperty("timestamp", getTimestamp());
                return json;
            }
        };

        webSocketManager.sendPacket(statusPacket);
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
                }
            }
        }.runTaskTimer(this, 0L, 20L); // Every 1 second (20 ticks)
    }

    /**
     * Register event listeners
     * Note: Console output is handled by ConsoleOutputHandler, not event listeners
     */
    private void registerListeners() {
        // All console output is captured by ConsoleOutputHandler
        // No event listeners needed for console output
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
}
