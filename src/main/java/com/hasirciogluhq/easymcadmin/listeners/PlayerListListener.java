package com.hasirciogluhq.easymcadmin.listeners;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.PacketType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Listens to player join/quit events and sends player list updates
 * Also sends all offline players in chunks after server startup
 */
public class PlayerListListener implements Listener {
    
    private final EasyMcAdmin plugin;
    private static final int CHUNK_SIZE = 20;
    private boolean initialSyncDone = false;
    
    public PlayerListListener(EasyMcAdmin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Send all offline players in chunks after server is ready
     * Called when WebSocket connection is established
     */
    public void sendAllOfflinePlayers() {
        if (initialSyncDone || !plugin.getWebSocketManager().isConnected()) {
            return;
        }
        
        initialSyncDone = true;
        
        // Get all offline players (including online ones)
        OfflinePlayer[] allPlayers = Bukkit.getOfflinePlayers();
        
        if (allPlayers.length == 0) {
            return;
        }
        
        // Send in chunks
        List<OfflinePlayer> chunk = new ArrayList<>();
        for (int i = 0; i < allPlayers.length; i++) {
            chunk.add(allPlayers[i]);
            
            if (chunk.size() >= CHUNK_SIZE || i == allPlayers.length - 1) {
                final List<OfflinePlayer> chunkToSend = new ArrayList<>(chunk);
                final int chunkIndex = (i / CHUNK_SIZE) + 1;
                final int totalChunks = (allPlayers.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
                final boolean isLastChunk = (i == allPlayers.length - 1);
                
                // Send chunk with delay to avoid overwhelming the connection
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    sendPlayerChunk(chunkToSend, chunkIndex, totalChunks, isLastChunk);
                }, (chunkIndex - 1) * 2L); // 2 ticks delay between chunks
                
                chunk.clear();
            }
        }
    }
    
    /**
     * Send a chunk of players
     */
    private void sendPlayerChunk(List<OfflinePlayer> players, int chunkIndex, int totalChunks, boolean isLastChunk) {
        if (!plugin.getWebSocketManager().isConnected()) {
            return;
        }
        
        try {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("action", "player_events");
            metadata.addProperty("event_type", "chunk");
            metadata.addProperty("requires_response", false);
            
            JsonObject payload = new JsonObject();
            payload.addProperty("chunk_index", chunkIndex);
            payload.addProperty("total_chunks", totalChunks);
            payload.addProperty("is_last_chunk", isLastChunk);
            
            JsonArray playerArray = new JsonArray();
            for (OfflinePlayer offlinePlayer : players) {
                JsonObject playerObj = new JsonObject();
                playerObj.addProperty("uuid", offlinePlayer.getUniqueId().toString());
                playerObj.addProperty("username", offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown");
                playerObj.addProperty("online", offlinePlayer.isOnline());
                playerObj.addProperty("first_played", offlinePlayer.getFirstPlayed());
                playerObj.addProperty("last_played", offlinePlayer.getLastPlayed());
                
                // If online, get additional info
                if (offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
                    Player player = offlinePlayer.getPlayer();
                    playerObj.addProperty("display_name", player.getDisplayName());
                    playerObj.addProperty("player_list_name", player.getPlayerListName());
                    playerObj.addProperty("ping", player.getPing());
                }
                
                playerArray.add(playerObj);
            }
            payload.add("players", playerArray);
            
            Packet packet = new Packet(
                UUID.randomUUID().toString(),
                PacketType.EVENT,
                metadata,
                payload
            ) {
                @Override
                public JsonObject toJson() {
                    JsonObject json = new JsonObject();
                    json.addProperty("packet_id", getPacketId());
                    json.addProperty("packet_type", getPacketType().name());
                    json.add("metadata", getMetadata());
                    json.add("payload", getPayload());
                    json.addProperty("timestamp", getTimestamp());
                    return json;
                }
            };
            
            plugin.getWebSocketManager().sendPacket(packet);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send player chunk: " + e.getMessage());
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        sendPlayerEvent(player, "join");
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        sendPlayerEvent(player, "left");
    }
    
    /**
     * Send player join/left event
     */
    private void sendPlayerEvent(Player player, String eventType) {
        if (!plugin.getWebSocketManager().isConnected()) {
            return;
        }
        
        try {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("action", "player_events");
            metadata.addProperty("event_type", eventType);
            metadata.addProperty("requires_response", false);
            
            JsonObject payload = new JsonObject();
            JsonObject playerObj = new JsonObject();
            playerObj.addProperty("uuid", player.getUniqueId().toString());
            playerObj.addProperty("username", player.getName());
            playerObj.addProperty("online", eventType.equals("join"));
            playerObj.addProperty("display_name", player.getDisplayName());
            playerObj.addProperty("player_list_name", player.getPlayerListName());
            playerObj.addProperty("ping", player.getPing());
            playerObj.addProperty("first_played", player.getFirstPlayed());
            playerObj.addProperty("last_played", player.getLastPlayed());
            
            payload.add("player", playerObj);
            
            Packet packet = new Packet(
                UUID.randomUUID().toString(),
                PacketType.EVENT,
                metadata,
                payload
            ) {
                @Override
                public JsonObject toJson() {
                    JsonObject json = new JsonObject();
                    json.addProperty("packet_id", getPacketId());
                    json.addProperty("packet_type", getPacketType().name());
                    json.add("metadata", getMetadata());
                    json.add("payload", getPayload());
                    json.addProperty("timestamp", getTimestamp());
                    return json;
                }
            };
            
            plugin.getWebSocketManager().sendPacket(packet);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send player event: " + e.getMessage());
        }
    }
}

