package com.hasirciogluhq.easymcadmin.services.player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.managers.EconomyManager;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.player.PlayerDetailsUpdatePacket;
import com.hasirciogluhq.easymcadmin.packets.generic.player.PlayerJoinPacket;
import com.hasirciogluhq.easymcadmin.packets.plugin.events.economy.PlayerEconomyUpdatedPacket;
import com.hasirciogluhq.easymcadmin.packets.plugin.events.player.OfflinePlayerChunkPacket;
import com.hasirciogluhq.easymcadmin.serializers.player.PlayerDataSerializer;
import com.hasirciogluhq.easymcadmin.transport.TransportManager;
import java.util.concurrent.CompletableFuture;
import com.hasirciogluhq.easymcadmin.packets.backend.rpc.inventory.PlayerInventoryResponse;
import com.hasirciogluhq.easymcadmin.serializers.player.PlayerInventorySerializer;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Inventory;

public class PlayerService {
    private TransportManager transportManager;
    private EasyMcAdmin plugin;
    private final Map<UUID, JsonArray> previousInventories = new HashMap<>();
    private final Map<UUID, JsonArray> previousEnderChests = new HashMap<>();
    private final Map<UUID, String> previousInventoryHashes = new HashMap<>();
    private final Map<UUID, String> previousEnderChestHashes = new HashMap<>();

    public PlayerService(EasyMcAdmin ema) {
        this.transportManager = ema.getTransportManager();
    }

    /**
     * Synchronizes the player's inventory or prepares the packet.
     * 
     * @param p          Player
     * @param fullSync   Full synchronization (true) or only changes (false)
     * @param sendPacket Should the packet be sent inside this function? (true for
     *                   events, false for RPC return)
     * @return A Future containing the prepared packet.
     */
    public CompletableFuture<Packet> SendPlayerInventorySyncEvent(Player p, boolean fullSync, boolean sendPacket) {
        CompletableFuture<Packet> future = new CompletableFuture<>();

        if (!p.isOnline()) {
            future.complete(null);
            return future;
        }

        // 1. Switch to the main thread for Bukkit API operations
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Connection check (only important if sending a packet; not a blocker for data
                // prep)
                if (sendPacket && (!plugin.getTransportManager().isConnected()
                        || !plugin.getTransportManager().isAuthenticated())) {
                    future.complete(null);
                    return;
                }

                // --- Data preparation and diff calculation (main thread) ---
                String inventoryHash = PlayerInventorySerializer.calculateInventoryHash(p.getInventory());
                String enderChestHash = PlayerInventorySerializer.calculateEnderChestHash(p.getEnderChest());

                // Build JSON data via helper function
                JsonObject inventoryData = generatePlayerInventoryData(p, fullSync);

                // Create packet
                Packet packet = new PlayerInventoryResponse(inventoryHash, enderChestHash, fullSync, inventoryData);

                // 2. If the packet will be sent, send it on an async thread
                if (sendPacket) {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            plugin.getTransportManager().sendPacket(packet);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to send player inventory update: " + e.getMessage());
                        }
                    });
                }

                // 3. Return the packet (for RPC or other uses)
                future.complete(packet);

            } catch (Exception e) {
                plugin.getLogger().warning("Error generating inventory sync: " + e.getMessage());
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Builds player inventory data (moved from listener).
     * Must be run on the main thread.
     */
    private JsonObject generatePlayerInventoryData(Player player, boolean fullSync) {
        UUID playerUUID = player.getUniqueId();

        // Serialize current inventory and ender chest
        JsonArray currentSerializedInventory = null;
        JsonArray currentSerializedEnderChest = null;

        PlayerInventory inventory = player.getInventory();
        if (inventory != null) {
            currentSerializedInventory = PlayerInventorySerializer.serializeInventory(inventory);
        }

        Inventory enderChest = player.getEnderChest();
        if (enderChest != null) {
            currentSerializedEnderChest = PlayerInventorySerializer.serializeEnderChest(enderChest);
        }

        // Calculate hashes
        String inventoryHash = PlayerInventorySerializer.calculateInventoryHash(player.getInventory());
        String enderChestHash = PlayerInventorySerializer.calculateEnderChestHash(player.getEnderChest());

        JsonObject inventoryData = new JsonObject();
        inventoryData.addProperty("player_uuid", playerUUID.toString());

        if (fullSync) {
            // Full sync
            if (currentSerializedInventory != null) {
                inventoryData.add("inventory", currentSerializedInventory);
                previousInventories.put(playerUUID, currentSerializedInventory);
                previousInventoryHashes.put(playerUUID, inventoryHash);
            }
            if (currentSerializedEnderChest != null) {
                inventoryData.add("ender_chest", currentSerializedEnderChest);
                previousEnderChests.put(playerUUID, currentSerializedEnderChest);
                previousEnderChestHashes.put(playerUUID, enderChestHash);
            }
        } else {
            // Diff sync
            JsonArray previousInventory = previousInventories.get(playerUUID);
            JsonArray previousEnderChest = previousEnderChests.get(playerUUID);
            String previousInventoryHash = previousInventoryHashes.get(playerUUID);
            String previousEnderChestHash = previousEnderChestHashes.get(playerUUID);

            // Inventory Diff
            boolean inventoryChanged = !inventoryHash
                    .equals(previousInventoryHash != null ? previousInventoryHash : "");
            if (inventoryChanged && currentSerializedInventory != null) {
                // If no previous record exists, sending full data might be safer, but we
                // calculate a diff here
                JsonArray inventoryDiff = PlayerInventorySerializer.calculateDiff(previousInventory,
                        currentSerializedInventory);
                inventoryData.add("inventory", inventoryDiff);
                inventoryData.addProperty("inventory_prev_hash", previousInventoryHash);

                previousInventories.put(playerUUID, currentSerializedInventory);
                previousInventoryHashes.put(playerUUID, inventoryHash);
            }

            // Ender Chest Diff
            boolean enderChestChanged = !enderChestHash
                    .equals(previousEnderChestHash != null ? previousEnderChestHash : "");
            if (enderChestChanged && currentSerializedEnderChest != null) {
                JsonArray enderChestDiff = PlayerInventorySerializer.calculateDiff(previousEnderChest,
                        currentSerializedEnderChest);
                inventoryData.add("ender_chest", enderChestDiff);
                inventoryData.addProperty("ender_chest_prev_hash", previousEnderChestHash);

                previousEnderChests.put(playerUUID, currentSerializedEnderChest);
                previousEnderChestHashes.put(playerUUID, enderChestHash);
            }
        }

        return inventoryData;
    }

    public void sendPlayerDetails(Player p) {
        if (!plugin.getTransportManager().isConnected() || !plugin.getTransportManager().isAuthenticated()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            JsonObject playerObj = PlayerDataSerializer.getPlayerDetailsPayload(p);

            try {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {

                        Packet packet = new PlayerDetailsUpdatePacket(playerObj);
                        plugin.getTransportManager().sendPacket(packet);

                        // Send balance updates separately
                        // sendPlayerBalanceUpdate(player);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to send player details update: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send player details update: " + e.getMessage());
            }
        });
    }

    public void sendPlayerBalances(Player p) {
        if (!plugin.getTransportManager().isConnected() || !plugin.getTransportManager().isAuthenticated()) {
            return;
        }

        EconomyManager economyManager = plugin.getEconomyManager();
        if (economyManager == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Get all balances from enabled providers
                List<EconomyManager.PlayerBalanceEntry> balances = economyManager.getPlayerBalances(p);

                if (balances.isEmpty()) {
                    return;
                }

                String username = null;
                String uuid = null;
                Boolean online = null;

                if (p.isOnline()) {
                    username = p.getName();
                    online = p.isOnline();
                    uuid = p.getUniqueId().toString();
                } else {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(p.getUniqueId());

                    username = offlinePlayer.getName();
                    online = false;
                    uuid = offlinePlayer.getUniqueId().toString();
                }

                // Create player balance data object
                JsonObject playerBalanceData = new JsonObject();
                playerBalanceData.addProperty("uuid", uuid);
                playerBalanceData.addProperty("username", username);
                playerBalanceData.addProperty("online", online);

                // Add balances array
                JsonArray balancesArray = new JsonArray();
                for (EconomyManager.PlayerBalanceEntry entry : balances) {
                    JsonObject balanceObj = new JsonObject();
                    balanceObj.addProperty("provider", entry.getProvider());
                    balanceObj.addProperty("amount", entry.getAmount().toString());
                    if (entry.getCurrencyName() != null) {
                        balanceObj.addProperty("currency_name", entry.getCurrencyName());
                    }
                    balancesArray.add(balanceObj);
                }
                playerBalanceData.add("balances", balancesArray);

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        // Send balance update packet
                        Packet packet = new PlayerEconomyUpdatedPacket(playerBalanceData);
                        plugin.getTransportManager().sendPacket(packet);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to send player balance update: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send player balance update: " + e.getMessage());
            }
        });
    }

    public void syncOfflinePlayers() {
        final int CHUNK_SIZE = 20;

        Bukkit.getScheduler().runTask(plugin, () -> {

            OfflinePlayer[] allPlayers = Bukkit.getOfflinePlayers();

            if (allPlayers.length == 0) {
                return;
            }

            List<OfflinePlayer> chunk = new ArrayList<>();

            for (int i = 0; i < allPlayers.length; i++) {

                chunk.add(allPlayers[i]);

                boolean isLast = (i == allPlayers.length - 1);

                if (chunk.size() >= CHUNK_SIZE || isLast) {
                    final List<OfflinePlayer> chunkToSend = new ArrayList<>(chunk);
                    final int chunkIndex = (i / CHUNK_SIZE) + 1;
                    final int totalChunks = (allPlayers.length + CHUNK_SIZE - 1) / CHUNK_SIZE;

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        sendOfflinePlayerChunk(chunkToSend, chunkIndex, totalChunks, isLast);
                    }, (chunkIndex - 1) * 2L); // 2 tick delay

                    chunk.clear();
                }
            }
        });
    }

    public void syncOnlinePlayers() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();

            for (Player onlinePlayer : onlinePlayers) {
                this.sendPlayerDetails(onlinePlayer);
            }
        });
    }

    // event handlers
    // All event handlers already start on the main thread, so no main thread run is
    // needed
    public void handleJoin(Player p) {
        JsonObject playerObj = PlayerDataSerializer.getPlayerDetailsPayload(p);
        playerObj.addProperty("online", true);

        // Set last_seen to current time when player joins
        playerObj.addProperty("first_seen", p.getFirstPlayed());
        playerObj.addProperty("last_seen", System.currentTimeMillis());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Packet packet = new PlayerJoinPacket(playerObj);
            try {
                plugin.getTransportManager().sendPacket(packet);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });

        // Send balance updates separately
        // sendPlayerBalanceUpdate(player);
    }

    // helpers
    // Cache cleanup when the player leaves (don't forget to call this)
    public void clearPlayerInventoryCache(UUID uuid) {
        previousInventories.remove(uuid);
        previousEnderChests.remove(uuid);
        previousInventoryHashes.remove(uuid);
        previousEnderChestHashes.remove(uuid);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object o) {
        if (o instanceof Map)
            return (Map<String, Object>) o;
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<String> safeList(Object o) {
        if (o instanceof List)
            return (List<String>) o;
        return new ArrayList<>();
    }

    /**
     * Send a chunk of players
     * Action: plugin.player.offline.chunk
     */
    private void sendOfflinePlayerChunk(List<OfflinePlayer> players, int chunkIndex, int totalChunks,
            boolean isLastChunk) {

        if (!plugin.getTransportManager().isConnected() || !plugin.getTransportManager().isAuthenticated()) {
            return;
        }

        try {
            JsonArray playerArray = new JsonArray();

            // JSON file → type-safe map
            Map<String, Object> data = safeMap(plugin.getDataManager().getData("synced_user_offline_users.json"));

            // "players" array
            List<String> syncedUuids = safeList(data.get("players"));

            // If the array doesn't exist in the file → create from scratch
            data.put("players", syncedUuids);

            for (OfflinePlayer op : players) {

                UUID uuid = op.getUniqueId();

                // ❗ Sadece OFFLINE oyuncular takip edilecek
                if (!op.isOnline()) {
                    if (!syncedUuids.contains(uuid.toString())) {
                        syncedUuids.add(uuid.toString());
                        // plugin.getDataManager().saveAsync("synced_user_offline_users.json"); //
                        // Enable if desired
                    }
                }

                JsonObject json;

                if (op.isOnline() && op.getPlayer() != null) {
                    json = PlayerDataSerializer.getPlayerDetailsPayload(op.getPlayer());
                } else {
                    json = PlayerDataSerializer.getOfflinePlayerDetailsPayload(op);
                }

                playerArray.add(json);
            }

            // Optional: send balances for online players
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (OfflinePlayer op : players) {
                    if (op.isOnline()) {
                        sendPlayerBalances(op.getPlayer());
                    }
                }
            }, 1L);

            Packet packet = new OfflinePlayerChunkPacket(chunkIndex, totalChunks, isLastChunk, playerArray);
            plugin.getTransportManager().sendPacket(packet);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send offline player chunk: " + e.getMessage());
        }
    }
}
