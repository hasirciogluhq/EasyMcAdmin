package com.hasirciogluhq.easymcadmin.listeners;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.PacketType;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.security.MessageDigest;
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
    private Economy economy = null;
    private Permission permission = null;

    // ============================================================================
    // CONSTRUCTOR & SETUP
    // ============================================================================

    public PlayerListListener(EasyMcAdmin plugin) {
        this.plugin = plugin;
        setupEconomy();
        setupPermissions();
        startPlayerStateUpdateTask();
    }

    /**
     * Setup Vault Economy if available
     */
    private void setupEconomy() {
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found, economy features will be disabled");
            return;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().info("No economy plugin found, economy features will be disabled");
            return;
        }

        economy = rsp.getProvider();
        plugin.getLogger().info("Economy plugin found: " + economy.getName());
    }
    
    /**
     * Setup Vault Permissions if available
     */
    private void setupPermissions() {
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found, permission features will be disabled");
            return;
        }

        RegisteredServiceProvider<Permission> rsp = Bukkit.getServer().getServicesManager().getRegistration(Permission.class);
        if (rsp == null) {
            plugin.getLogger().info("No permission plugin found, permission features will be disabled");
            return;
        }

        permission = rsp.getProvider();
        plugin.getLogger().info("Permission plugin found: " + permission.getName());
    }

    /**
     * Start periodic task to update online players' location and details
     * Updates every 3-5 seconds (60-100 ticks)
     * Sends location, ping, experience - NO inventory
     */
    private void startPlayerStateUpdateTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!plugin.getWebSocketManager().isConnected()) {
                return;
            }

            // Send location and details updates (no inventory)
            for (Player player : Bukkit.getOnlinePlayers()) {
                sendPlayerDetailsUpdate(player);
            }
        }, 60L, 60L); // Start after 3 seconds, repeat every 3 seconds
    }

    // ============================================================================
    // EVENT HANDLERS
    // ============================================================================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        sendPlayerJoin(player);
        // Send full sync on join
        sendPlayerInventoryUpdate(player, true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        sendPlayerLeft(player);
    }
    
    /**
     * Handle inventory events - send update when inventory changes
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            // Send inventory update after a short delay to ensure inventory is updated
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                sendPlayerInventoryUpdate(player, false);
            }, 1L);
        }
    }
    
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            sendPlayerInventoryUpdate(player, false);
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            sendPlayerInventoryUpdate(player, false);
        }
    }
    
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        // Send inventory update after a short delay to ensure inventory is updated
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sendPlayerInventoryUpdate(player, false);
        }, 1L);
    }
    
    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            // Send inventory update after a short delay to ensure inventory is updated
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                sendPlayerInventoryUpdate(player, false);
            }, 1L);
        }
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        // Send inventory update immediately - inventory is already updated at this point
        sendPlayerInventoryUpdate(player, false);
    }
    
    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        // Send inventory update immediately - inventory is already updated at this point
        sendPlayerInventoryUpdate(player, false);
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        // Only trigger on right-click with items (food, blocks, etc.)
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || 
            event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            if (event.getItem() != null) {
                // Send inventory update immediately - inventory changes happen synchronously
                sendPlayerInventoryUpdate(player, false);
            }
        }
    }

    // ============================================================================
    // MAIN PUBLIC FUNCTIONS - Player Updates
    // ============================================================================

    /**
     * Send player join event with full details and inventory
     * Action: player.join
     */
    public void sendPlayerJoin(Player player) {
        if (!plugin.getWebSocketManager().isConnected()) {
            return;
        }

        try {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("action", "player.join");
            metadata.addProperty("requires_response", false);

            JsonObject payload = new JsonObject();
            JsonObject playerObj = getPlayerDetailsPayload(player);
            playerObj.addProperty("online", true);
            
            // Add inventory, ender chest data for join events
            addPlayerInventoryData(playerObj, player);

            payload.add("player", playerObj);

            Packet packet = new Packet(
                    UUID.randomUUID().toString(),
                    PacketType.EVENT,
                    metadata,
                    payload) {
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
            plugin.getLogger().warning("Failed to send player join event: " + e.getMessage());
        }
    }

    /**
     * Send player left event with full details
     * Action: player.left
     */
    public void sendPlayerLeft(Player player) {
        if (!plugin.getWebSocketManager().isConnected()) {
            return;
        }

        try {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("action", "player.left");
            metadata.addProperty("requires_response", false);

            JsonObject payload = new JsonObject();
            JsonObject playerObj = getPlayerDetailsPayload(player);
            playerObj.addProperty("online", false);

            payload.add("player", playerObj);

            Packet packet = new Packet(
                    UUID.randomUUID().toString(),
                    PacketType.EVENT,
                    metadata,
                    payload) {
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
            plugin.getLogger().warning("Failed to send player left event: " + e.getMessage());
        }
    }

    /**
     * Send player inventory update (only inventory data)
     * @param fullSync If true, sends full inventory sync (includes ender chest), if false sends diff sync
     * Used for inventory events (click, open, close, etc.) and full sync requests
     * Action: player.inventory_update
     */
    public void sendPlayerInventoryUpdate(Player player, boolean fullSync) {
        if (!plugin.getWebSocketManager().isConnected()) {
            return;
        }

        UUID playerUUID = player.getUniqueId();
        String inventoryHash = calculateInventoryHash(player.getInventory());
        String enderChestHash = fullSync ? calculateEnderChestHash(player.getEnderChest()) : "";

        try {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("action", "player.inventory_update");
            metadata.addProperty("requires_response", false);
            metadata.addProperty("inventory_hash", inventoryHash);
            if (fullSync && !enderChestHash.isEmpty()) {
                metadata.addProperty("ender_chest_hash", enderChestHash);
            }
            metadata.addProperty("full_sync", fullSync);

            JsonObject payload = new JsonObject();
            JsonObject playerObj = new JsonObject();
            playerObj.addProperty("uuid", playerUUID.toString());
            
            // Send inventory
            PlayerInventory inventory = player.getInventory();
            if (inventory != null) {
                playerObj.add("inventory", serializeInventory(inventory));
            }
            
            // Send ender chest only if full sync
            if (fullSync) {
                org.bukkit.inventory.Inventory enderChest = player.getEnderChest();
                if (enderChest != null) {
                    playerObj.add("ender_chest", serializeEnderChest(enderChest));
                }
            }

            payload.add("player", playerObj);

            Packet packet = new Packet(
                    UUID.randomUUID().toString(),
                    PacketType.EVENT,
                    metadata,
                    payload) {
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
            plugin.getLogger().warning("Failed to send player inventory update: " + e.getMessage());
        }
    }
    
    /**
     * Send player details update (all player info except inventory)
     * Used for periodic ticker updates
     * Action: player.details_update
     */
    public void sendPlayerDetailsUpdate(Player player) {
        if (!plugin.getWebSocketManager().isConnected()) {
            return;
        }

        try {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("action", "player.details_update");
            metadata.addProperty("requires_response", false);

            JsonObject payload = new JsonObject();
            JsonObject playerObj = getPlayerDetailsPayload(player);
            payload.add("player", playerObj);

            Packet packet = new Packet(
                    UUID.randomUUID().toString(),
                    PacketType.EVENT,
                    metadata,
                    payload) {
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
            plugin.getLogger().warning("Failed to send player details update: " + e.getMessage());
        }
    }

    /**
     * Send all offline players in chunks after server is ready
     * Called when WebSocket connection is established
     * Also sends full sync for all online players
     */
    public void sendAllOfflinePlayers() {
        if (initialSyncDone || !plugin.getWebSocketManager().isConnected()) {
            return;
        }

        initialSyncDone = true;

        // First, send full sync for all online players
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            sendPlayerInventoryUpdate(onlinePlayer, true);
        }

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
     * Handle inventory sync request from backend
     * Called when backend detects hash mismatch
     */
    public void handlePlayerInventorySyncRequest(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            sendPlayerInventoryUpdate(player, true);
        }
    }

    /**
     * Send a chunk of players
     * Action: player.chunk
     */
    private void sendPlayerChunk(List<OfflinePlayer> players, int chunkIndex, int totalChunks, boolean isLastChunk) {
        if (!plugin.getWebSocketManager().isConnected()) {
            return;
        }

        try {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("action", "player.chunk");
            metadata.addProperty("requires_response", false);

            JsonObject payload = new JsonObject();
            payload.addProperty("chunk_index", chunkIndex);
            payload.addProperty("total_chunks", totalChunks);
            payload.addProperty("is_last_chunk", isLastChunk);

            JsonArray playerArray = new JsonArray();

            for (OfflinePlayer offlinePlayer : players) {
                JsonObject playerObj;
                
                // Use common function for both online and offline players
                if (offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
                    Player player = offlinePlayer.getPlayer();
                    playerObj = getPlayerDetailsPayload(player);
                } else {
                    // Offline player - use common function for offline players
                    playerObj = getOfflinePlayerDetailsPayload(offlinePlayer);
                }

                playerArray.add(playerObj);
            }
            payload.add("players", playerArray);

            Packet packet = new Packet(
                    UUID.randomUUID().toString(),
                    PacketType.EVENT,
                    metadata,
                    payload) {
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

    // ============================================================================
    // HELPER FUNCTIONS - Player Data Payloads
    // ============================================================================

    /**
     * Get player details payload (common function for details and chunk)
     * Includes: username, display_name, player_list_name, ping, first_played, last_played,
     * balance, currency, location, experience, groups
     */
    private JsonObject getPlayerDetailsPayload(Player player) {
        JsonObject playerObj = new JsonObject();
        playerObj.addProperty("uuid", player.getUniqueId().toString());
        playerObj.addProperty("username", player.getName());
        playerObj.addProperty("online", true);
        playerObj.addProperty("display_name", player.getDisplayName());
        playerObj.addProperty("player_list_name", player.getPlayerListName());
        playerObj.addProperty("ping", player.getPing());
        playerObj.addProperty("first_played", player.getFirstPlayed());
        playerObj.addProperty("last_played", player.getLastPlayed());
        
        // Get economy balance if available
        Double balance = getPlayerBalance(player);
        if (balance != null) {
            playerObj.addProperty("balance", balance);
        }
        String currencyName = getCurrencyName();
        if (currencyName != null) {
            playerObj.addProperty("currency", currencyName);
        }
        
        // Send location
        Location loc = player.getLocation();
        if (loc != null) {
            playerObj.add("location", serializeLocation(loc));
        }
        
        // Send experience
        JsonObject expObj = new JsonObject();
        expObj.addProperty("level", player.getLevel());
        expObj.addProperty("exp", player.getExp());
        expObj.addProperty("total_exp", player.getTotalExperience());
        playerObj.add("experience", expObj);
        
        // Get and send permission groups/ranks
        String primaryGroup = getPlayerPrimaryGroup(player);
        if (primaryGroup != null) {
            playerObj.addProperty("primary_group", primaryGroup);
        }
        
        String[] groups = getPlayerGroups(player);
        if (groups != null && groups.length > 0) {
            JsonArray groupsArray = new JsonArray();
            for (String group : groups) {
                groupsArray.add(group);
            }
            playerObj.add("groups", groupsArray);
        }
        
        return playerObj;
    }

    /**
     * Get offline player details payload (for offline players in chunk)
     * Includes all fields same as online player, but with N/A for unavailable data
     */
    private JsonObject getOfflinePlayerDetailsPayload(OfflinePlayer offlinePlayer) {
        JsonObject playerObj = new JsonObject();
        playerObj.addProperty("uuid", offlinePlayer.getUniqueId().toString());
        playerObj.addProperty("username", offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown");
        playerObj.addProperty("online", false);
        
        // Display name - N/A for offline players
        playerObj.addProperty("display_name", "N/A");
        
        // Player list name - N/A for offline players
        playerObj.addProperty("player_list_name", "N/A");
        
        // Ping - N/A for offline players
        playerObj.addProperty("ping", -1);
        
        playerObj.addProperty("first_played", offlinePlayer.getFirstPlayed());
        playerObj.addProperty("last_played", offlinePlayer.getLastPlayed());
        
        // Get economy balance if available
        Double balance = getPlayerBalance(offlinePlayer);
        if (balance != null) {
            playerObj.addProperty("balance", balance);
        } else {
            playerObj.addProperty("balance", 0.0);
        }
        
        String currencyName = getCurrencyName();
        if (currencyName != null) {
            playerObj.addProperty("currency", currencyName);
        } else {
            playerObj.addProperty("currency", "N/A");
        }
        
        // Location - N/A for offline players
        JsonObject locObj = new JsonObject();
        locObj.addProperty("world", "N/A");
        locObj.addProperty("x", 0.0);
        locObj.addProperty("y", 0.0);
        locObj.addProperty("z", 0.0);
        locObj.addProperty("yaw", 0.0);
        locObj.addProperty("pitch", 0.0);
        playerObj.add("location", locObj);
        
        // Experience - N/A for offline players
        JsonObject expObj = new JsonObject();
        expObj.addProperty("level", 0);
        expObj.addProperty("exp", 0.0);
        expObj.addProperty("total_exp", 0);
        playerObj.add("experience", expObj);
        
        // Groups - try to get from permission plugin if available
        // For offline players, we can try to get groups from permission plugin
        String primaryGroup = getOfflinePlayerPrimaryGroup(offlinePlayer);
        if (primaryGroup != null) {
            playerObj.addProperty("primary_group", primaryGroup);
        } else {
            playerObj.addProperty("primary_group", "N/A");
        }
        
        String[] groups = getOfflinePlayerGroups(offlinePlayer);
        if (groups != null && groups.length > 0) {
            JsonArray groupsArray = new JsonArray();
            for (String group : groups) {
                groupsArray.add(group);
            }
            playerObj.add("groups", groupsArray);
        } else {
            // Empty array if no groups
            playerObj.add("groups", new JsonArray());
        }
        
        return playerObj;
    }

    /**
     * Add player inventory, ender chest, experience, and location data to player object
     */
    private void addPlayerInventoryData(JsonObject playerObj, Player player) {
        if (player == null) {
            return;
        }
        
        // Inventory
        PlayerInventory inventory = player.getInventory();
        if (inventory != null) {
            playerObj.add("inventory", serializeInventory(inventory));
        }
        
        // Ender Chest
        org.bukkit.inventory.Inventory enderChest = player.getEnderChest();
        if (enderChest != null) {
            playerObj.add("ender_chest", serializeEnderChest(enderChest));
        }
        
        // Experience
        JsonObject expObj = new JsonObject();
        expObj.addProperty("level", player.getLevel());
        expObj.addProperty("exp", player.getExp());
        expObj.addProperty("total_exp", player.getTotalExperience());
        playerObj.add("experience", expObj);
        
        // Location
        Location loc = player.getLocation();
        if (loc != null) {
            playerObj.add("location", serializeLocation(loc));
        }
    }

    // ============================================================================
    // HELPER FUNCTIONS - Vault Integration
    // ============================================================================

    /**
     * Get player balance from economy plugin
     * 
     * @param player Player to get balance for
     * @return Balance amount, or null if economy is not available
     */
    private Double getPlayerBalance(OfflinePlayer player) {
        if (economy == null || player == null) {
            return null;
        }

        try {
            return economy.getBalance(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get balance for player " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Get currency name from economy plugin
     * 
     * @return Currency name, or null if economy is not available
     */
    private String getCurrencyName() {
        if (economy == null) {
            return null;
        }
        
        try {
            return economy.currencyNameSingular();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get player's primary group (rank)
     * 
     * @param player Player to get group for
     * @return Primary group name, or null if permission plugin is not available
     */
    private String getPlayerPrimaryGroup(Player player) {
        if (permission == null || player == null) {
            return null;
        }
        
        try {
            String world = player.getWorld() != null ? player.getWorld().getName() : null;
            String group = permission.getPrimaryGroup(world, player);
            return group != null && !group.isEmpty() ? group : null;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get primary group for player " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get all player groups (including ranks)
     * 
     * @param player Player to get groups for
     * @return Array of group names, or null if permission plugin is not available
     */
    private String[] getPlayerGroups(Player player) {
        if (permission == null || player == null) {
            return null;
        }
        
        try {
            String world = player.getWorld() != null ? player.getWorld().getName() : null;
            String[] groups = permission.getPlayerGroups(world, player);
            return groups != null && groups.length > 0 ? groups : null;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get groups for player " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Get offline player's primary group (rank)
     * 
     * @param offlinePlayer OfflinePlayer to get group for
     * @return Primary group name, or null if permission plugin is not available
     */
    private String getOfflinePlayerPrimaryGroup(OfflinePlayer offlinePlayer) {
        if (permission == null || offlinePlayer == null) {
            return null;
        }
        
        try {
            // Try to get primary group for offline player
            // Some permission plugins support this, some don't
            String group = permission.getPrimaryGroup(null, offlinePlayer);
            return group != null && !group.isEmpty() ? group : null;
        } catch (Exception e) {
            // Many permission plugins don't support offline player groups
            return null;
        }
    }
    
    /**
     * Get all offline player groups (including ranks)
     * 
     * @param offlinePlayer OfflinePlayer to get groups for
     * @return Array of group names, or null if permission plugin is not available
     */
    private String[] getOfflinePlayerGroups(OfflinePlayer offlinePlayer) {
        if (permission == null || offlinePlayer == null) {
            return null;
        }
        
        try {
            // Try to get groups for offline player
            // Some permission plugins support this, some don't
            String[] groups = permission.getPlayerGroups(null, offlinePlayer);
            return groups != null && groups.length > 0 ? groups : null;
        } catch (Exception e) {
            // Many permission plugins don't support offline player groups
            return null;
        }
    }

    // ============================================================================
    // HELPER FUNCTIONS - Serialization
    // ============================================================================

    /**
     * Serialize player location to JSON object
     */
    private JsonObject serializeLocation(Location loc) {
        JsonObject locObj = new JsonObject();
        if (loc == null) {
            return locObj;
        }
        
        locObj.addProperty("world", loc.getWorld() != null ? loc.getWorld().getName() : "unknown");
        locObj.addProperty("x", loc.getX());
        locObj.addProperty("y", loc.getY());
        locObj.addProperty("z", loc.getZ());
        locObj.addProperty("yaw", loc.getYaw());
        locObj.addProperty("pitch", loc.getPitch());
        
        return locObj;
    }

    /**
     * Serialize player inventory to JSON array
     * MC inventory slots:
     * 0-8: Hotbar (quick access)
     * 9-35: Main inventory (27 slots)
     * 36: Boots
     * 37: Leggings
     * 38: Chestplate
     * 39: Helmet
     * 40: Offhand
     */
    private JsonArray serializeInventory(PlayerInventory inventory) {
        JsonArray invArray = new JsonArray();
        if (inventory == null) {
            return invArray;
        }
        
        // Get all contents in correct order
        ItemStack[] storageContents = inventory.getStorageContents(); // Slots 0-35
        ItemStack[] armorContents = inventory.getArmorContents(); // Armor array: boots, leggings, chestplate, helmet
        ItemStack offhand = inventory.getItemInOffHand(); // Offhand slot
        
        // Add storage contents (0-35: hotbar + main inventory)
        for (ItemStack item : storageContents) {
            JsonObject itemObj = serializeItemStack(item);
            invArray.add(itemObj);
        }
        
        // Add armor slots (36-39)
        // armorContents array: [boots, leggings, chestplate, helmet]
        // But in MC, slots are: 36=boots, 37=leggings, 38=chestplate, 39=helmet
        if (armorContents != null && armorContents.length >= 4) {
            invArray.add(serializeItemStack(armorContents[0])); // Boots (slot 36)
            invArray.add(serializeItemStack(armorContents[1])); // Leggings (slot 37)
            invArray.add(serializeItemStack(armorContents[2])); // Chestplate (slot 38)
            invArray.add(serializeItemStack(armorContents[3])); // Helmet (slot 39)
        } else {
            // Add empty armor slots if armor array is null or incomplete
            for (int i = 0; i < 4; i++) {
                JsonObject emptyItem = new JsonObject();
                emptyItem.addProperty("type", "AIR");
                emptyItem.addProperty("amount", 0);
                invArray.add(emptyItem);
            }
        }
        
        // Add offhand (slot 40)
        invArray.add(serializeItemStack(offhand));
        
        return invArray;
    }
    
    /**
     * Serialize ender chest inventory to JSON array
     */
    private JsonArray serializeEnderChest(org.bukkit.inventory.Inventory enderChest) {
        JsonArray invArray = new JsonArray();
        if (enderChest == null) {
            return invArray;
        }
        
        ItemStack[] contents = enderChest.getStorageContents();
        for (ItemStack item : contents) {
            JsonObject itemObj = serializeItemStack(item);
            invArray.add(itemObj);
        }
        return invArray;
    }
    
    /**
     * Serialize ItemStack to JSON object
     */
    private JsonObject serializeItemStack(ItemStack item) {
        JsonObject itemObj = new JsonObject();
        if (item == null || item.getType() == Material.AIR) {
            itemObj.addProperty("type", "AIR");
            itemObj.addProperty("amount", 0);
            return itemObj;
        }
        
        itemObj.addProperty("type", item.getType().name());
        itemObj.addProperty("amount", item.getAmount());
        
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            itemObj.addProperty("display_name", item.getItemMeta().getDisplayName());
        }
        
        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            JsonArray loreArray = new JsonArray();
            for (String line : item.getItemMeta().getLore()) {
                loreArray.add(line);
            }
            itemObj.add("lore", loreArray);
        }
        
        // Use ItemMeta for durability if available (getDurability is deprecated)
        if (item.hasItemMeta() && item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable) {
            org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) item.getItemMeta();
            if (damageable.hasDamage()) {
                itemObj.addProperty("durability", damageable.getDamage());
            }
        }
        
        return itemObj;
    }

    // ============================================================================
    // HELPER FUNCTIONS - Hash Calculation
    // ============================================================================

    /**
     * Calculate hash of inventory for diff detection
     */
    private String calculateInventoryHash(PlayerInventory inventory) {
        if (inventory == null) {
            return "";
        }
        
        try {
            JsonArray invArray = serializeInventory(inventory);
            String inventoryJson = invArray.toString();
            
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(inventoryJson.getBytes("UTF-8"));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to calculate inventory hash: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * Calculate hash of ender chest for diff detection
     */
    private String calculateEnderChestHash(org.bukkit.inventory.Inventory enderChest) {
        if (enderChest == null) {
            return "";
        }
        
        try {
            JsonArray ecArray = serializeEnderChest(enderChest);
            String enderChestJson = ecArray.toString();
            
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(enderChestJson.getBytes("UTF-8"));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to calculate ender chest hash: " + e.getMessage());
            return "";
        }
    }
}
