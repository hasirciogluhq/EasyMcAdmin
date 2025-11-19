package com.hasirciogluhq.easymcadmin.websocket;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.URI;
import java.util.logging.Level;
import java.util.function.Consumer;

/**
 * WebSocket Manager
 * Manages WebSocket connection lifecycle and packet handling
 */
public class WebSocketManager {
    
    private final EasyMcAdmin plugin;
    private PluginWebSocketClient client;
    private boolean isEnabled;
    private String token;
    private Consumer<Packet> packetHandler;
    
    public WebSocketManager(EasyMcAdmin plugin) {
        this.plugin = plugin;
        this.isEnabled = plugin.getConfig().getBoolean("websocket.enabled", true);
        this.token = plugin.getConfig().getString("server.token", "");
    }
    
    /**
     * Initialize and connect WebSocket
     */
    public void connect() {
        if (!isEnabled) {
            plugin.getLogger().info("WebSocket is disabled in config");
            return;
        }
        
        if (token == null || token.isEmpty()) {
            plugin.getLogger().warning("No token set! Use /easymcadmin setToken <token> to set authentication token.");
            return;
        }
        
        // If already connected, don't create a new client
        if (client != null && client.isOpen()) {
            return;
        }
        
        // Clean up old client if exists but not connected
        if (client != null) {
            try {
                client.shouldReconnect = false;
                if (client.reconnectTask != null) {
                    client.reconnectTask.cancel();
                    client.reconnectTask = null;
                }
                if (client.isOpen()) {
                    client.close();
                }
            } catch (Exception e) {
                // Ignore errors when cleaning up old client
            }
            client = null;
        }
        
        String url = plugin.getConfig().getString("websocket.url", "ws://localhost:8080/ws/plugin");
        
        try {
            URI serverUri = new URI(url + "?token=" + token);
            client = new PluginWebSocketClient(serverUri, plugin, token);
            
            // Set packet handler
            if (packetHandler != null) {
                client.setPacketHandler(packetHandler);
            }
            
            // Connect in async task to avoid blocking main thread
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        client.connect();
                        // Connection status will be logged in onOpen/onClose
                    } catch (Exception e) {
                        // Silently handle connection errors - don't spam console
                        plugin.getLogger().log(Level.FINE, "Failed to connect WebSocket (silent):", e);
                    }
                }
            }.runTaskAsynchronously(plugin);
            
        } catch (Exception e) {
            // Silently handle URL errors - don't spam console
            plugin.getLogger().log(Level.FINE, "Invalid WebSocket URL (silent):", e);
        }
    }
    
    /**
     * Disconnect WebSocket
     */
    public void disconnect() {
        if (client != null) {
            client.disconnect();
            client = null;
            // Disconnection status will be logged in onClose
        }
    }
    
    /**
     * Check if WebSocket is connected
     * 
     * @return true if connected
     */
    public boolean isConnected() {
        return client != null && client.isOpen();
    }
    
    /**
     * Send a packet through WebSocket
     * 
     * @param packet Packet to send
     */
    public void sendPacket(Packet packet) {
        if (client != null && client.isOpen()) {
            client.sendPacket(packet);
        }
        // Silently fail if not connected - don't spam console
    }
    
    /**
     * Send RPC packet and wait for response
     * 
     * @param packet RPC packet
     * @param responseHandler Response handler
     */
    public void sendRPCPacket(Packet packet, java.util.function.Consumer<com.google.gson.JsonObject> responseHandler) {
        if (client != null && client.isOpen()) {
            client.sendRPCPacket(packet, responseHandler);
        }
        // Silently fail if not connected - don't spam console
    }
    
    /**
     * Set packet handler for incoming packets
     * 
     * @param handler Packet handler
     */
    public void setPacketHandler(Consumer<Packet> handler) {
        this.packetHandler = handler;
        if (client != null) {
            client.setPacketHandler(handler);
        }
    }
    
    /**
     * Get the WebSocket client instance
     * 
     * @return PluginWebSocketClient instance
     */
    public PluginWebSocketClient getClient() {
        return client;
    }
    
    /**
     * Set token and reconnect
     * 
     * @param token Authentication token
     */
    public void setToken(String token) {
        this.token = token;
        if (client != null) {
            disconnect();
        }
        plugin.getConfig().set("server.token", token);
        plugin.saveConfig();
    }
    
    /**
     * Reconnect by creating a new WebSocket client
     * This is necessary because WebSocketClient objects are not reusable
     */
    public void reconnect() {
        // Disconnect old client if exists
        if (client != null) {
            try {
                // Prevent old client from trying to reconnect
                client.shouldReconnect = false;
                // Cancel any pending reconnect tasks
                if (client.reconnectTask != null) {
                    client.reconnectTask.cancel();
                    client.reconnectTask = null;
                }
                // Close the connection
                if (client.isOpen()) {
                    client.close();
                }
            } catch (Exception e) {
                // Ignore errors when closing old client
                plugin.getLogger().log(Level.FINE, "Error closing old WebSocket client", e);
            }
            client = null;
        }
        
        // Create new connection
        connect();
    }
    
    /**
     * Enable or disable WebSocket
     * 
     * @param enabled Enable/disable flag
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        if (!enabled && client != null) {
            disconnect();
        } else if (enabled && (client == null || !client.isOpen())) {
            connect();
        }
    }
}

