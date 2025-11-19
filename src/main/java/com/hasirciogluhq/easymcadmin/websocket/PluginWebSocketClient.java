package com.hasirciogluhq.easymcadmin.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import org.bukkit.scheduler.BukkitRunnable;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * WebSocket Client for Easy MC Admin
 * Handles bidirectional communication with backend via WebSocket
 */
public class PluginWebSocketClient extends WebSocketClient {
    
    private final EasyMcAdmin plugin;
    private final Gson gson;
    private final String token;
    boolean shouldReconnect; // Package-private for WebSocketManager access
    private int reconnectDelay;
    BukkitRunnable reconnectTask; // Package-private for WebSocketManager access
    private boolean wasConnected; // Track if connection was ever established
    
    // RPC response handlers (packet_id -> callback)
    private final ConcurrentMap<String, Consumer<JsonObject>> rpcHandlers = new ConcurrentHashMap<>();
    
    // Packet handler
    private Consumer<Packet> packetHandler;
    
    public PluginWebSocketClient(URI serverUri, EasyMcAdmin plugin, String token) {
        super(serverUri);
        this.plugin = plugin;
        this.gson = new Gson();
        this.token = token;
        this.shouldReconnect = plugin.getConfig().getBoolean("websocket.auto-reconnect", true);
        this.reconnectDelay = plugin.getConfig().getInt("websocket.reconnect-delay", 5);
    }
    
    @Override
    public void onOpen(ServerHandshake handshake) {
        wasConnected = true;
        plugin.getLogger().info("[EasyMcAdmin] WebSocket connected");
        
        // Send authentication packet
        JsonObject authPacket = new JsonObject();
        authPacket.addProperty("packet_id", java.util.UUID.randomUUID().toString());
        authPacket.addProperty("packet_type", "EVENT");
        JsonObject authMetadata = new JsonObject();
        authMetadata.addProperty("action", "authenticate");
        authPacket.add("metadata", authMetadata);
        JsonObject authPayload = new JsonObject();
        authPayload.addProperty("token", token);
        authPayload.addProperty("version", plugin.getDescription().getVersion());
        authPacket.add("payload", authPayload);
        authPacket.addProperty("timestamp", System.currentTimeMillis() / 1000);
        
        send(authPacket.toString());
    }
    
    @Override
    public void onMessage(String message) {
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            
            // Parse packet
            String packetId = json.get("packet_id").getAsString();
            String packetType = json.get("packet_type").getAsString();
            JsonObject metadata = json.getAsJsonObject("metadata");
            JsonObject payload = json.getAsJsonObject("payload");
            
            // Create packet object
            Packet packet = new Packet(packetId, 
                com.hasirciogluhq.easymcadmin.packets.PacketType.valueOf(packetType),
                metadata, payload) {
                @Override
                public JsonObject toJson() {
                    return json;
                }
            };
            
            // Handle RPC response
            if (packetType.equals("RPC") && rpcHandlers.containsKey(packetId)) {
                Consumer<JsonObject> handler = rpcHandlers.remove(packetId);
                handler.accept(payload);
                return;
            }
            
            // Handle incoming packet
            if (packetHandler != null) {
                packetHandler.accept(packet);
            }
            
        } catch (Exception e) {
            // Silently handle message errors - don't spam console
            plugin.getLogger().log(Level.FINE, "Error handling WebSocket message (silent):", e);
        }
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        // Only log disconnect if we were actually connected
        // This prevents spam when old clients are closed during reconnect
        if (wasConnected) {
            plugin.getLogger().info("[EasyMcAdmin] WebSocket disconnected");
            wasConnected = false; // Reset flag
        }
        
        // Clear RPC handlers
        rpcHandlers.clear();
        
        // Attempt to reconnect if enabled
        if (shouldReconnect && plugin.isEnabled()) {
            scheduleReconnect();
        }
    }
    
    @Override
    public void onError(Exception ex) {
        // Silently handle errors - don't spam console
        // Only log at fine level for debugging if needed
        plugin.getLogger().log(Level.FINE, "WebSocket error (silent):", ex);
    }
    
    /**
     * Send a packet to the server
     * 
     * @param packet Packet to send
     */
    public void sendPacket(Packet packet) {
        if (isOpen()) {
            send(packet.toJson().toString());
        }
        // Silently fail if not connected - don't spam console
    }
    
    /**
     * Send RPC packet and wait for response
     * 
     * @param packet RPC packet to send
     * @param responseHandler Handler for response
     */
    public void sendRPCPacket(Packet packet, Consumer<JsonObject> responseHandler) {
        if (!isOpen()) {
            // Silently fail if not connected - don't spam console
            return;
        }
        
        // Register response handler
        rpcHandlers.put(packet.getPacketId(), responseHandler);
        
        // Send packet
        send(packet.toJson().toString());
        
        // Timeout handler (remove after 30 seconds)
        new BukkitRunnable() {
            @Override
            public void run() {
                rpcHandlers.remove(packet.getPacketId());
                // Silently handle timeout - don't spam console
            }
        }.runTaskLater(plugin, 20L * 30); // 30 seconds
    }
    
    /**
     * Set packet handler for incoming packets
     * 
     * @param handler Packet handler
     */
    public void setPacketHandler(Consumer<Packet> handler) {
        this.packetHandler = handler;
    }
    
    /**
     * Schedule a reconnection attempt
     * Note: This will notify WebSocketManager to create a new client
     */
    private void scheduleReconnect() {
        if (reconnectTask != null) {
            reconnectTask.cancel();
        }
        
        reconnectTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isOpen() && plugin.isEnabled()) {
                    // Silently reconnect - don't spam console
                    // Notify WebSocketManager to reconnect (create new client)
                    plugin.getWebSocketManager().reconnect();
                }
            }
        };
        
        reconnectTask.runTaskLater(plugin, reconnectDelay * 20L); // Convert seconds to ticks
    }
    
    /**
     * Close the connection and stop reconnecting
     */
    public void disconnect() {
        shouldReconnect = false;
        if (reconnectTask != null) {
            reconnectTask.cancel();
        }
        close();
    }
}

