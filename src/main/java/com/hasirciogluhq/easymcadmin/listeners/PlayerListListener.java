package com.hasirciogluhq.easymcadmin.listeners;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.PacketType;
import net.milkbowl.vault.economy.Economy;
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
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.RegisteredServiceProvider;

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

    public PlayerListListener(EasyMcAdmin plugin) {
        this.plugin = plugin;
        setupEconomy();
        startPlayerStateUpdateTask();
    }

    /**
     * Start periodic task to update online players' full state
     * Updates ping, display name, balance, and all other player data
     * Updates every 5 seconds (100 ticks)
     */
    private void startPlayerStateUpdateTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!plugin.getWebSocketManager().isConnected()) {
                return;
            }

            // Update all online players' full state
            for (Player player : Bukkit.getOnlinePlayers()) {
                sendPlayerUpdate(player);
            }
        }, 100L, 100L); // Start after 5 seconds, repeat every 5 seconds
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
            String currencyName = getCurrencyName();

            for (OfflinePlayer offlinePlayer : players) {
                JsonObject playerObj = new JsonObject();
                playerObj.addProperty("uuid", offlinePlayer.getUniqueId().toString());
                playerObj.addProperty("username",
                        offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown");
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

                // Get economy balance if available
                Double balance = getPlayerBalance(offlinePlayer);
                if (balance != null) {
                    playerObj.addProperty("balance", balance);
                }
                if (currencyName != null) {
                    playerObj.addProperty("currency", currencyName);
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
     * Send player update event (for balance changes, ping updates, etc.)
     */
    public void sendPlayerUpdate(Player player) {
        if (!plugin.getWebSocketManager().isConnected()) {
            return;
        }

        try {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("action", "player_events");
            metadata.addProperty("event_type", "update");
            metadata.addProperty("requires_response", false);

            JsonObject payload = new JsonObject();
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
            
            // Add inventory, ender chest, experience, and location data
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
            plugin.getLogger().warning("Failed to send player update: " + e.getMessage());
        }
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

            // Get economy balance if available
            Double balance = getPlayerBalance(player);
            if (balance != null) {
                playerObj.addProperty("balance", balance);
            }
            String currencyName = getCurrencyName();
            if (currencyName != null) {
                playerObj.addProperty("currency", currencyName);
            }
            
            // Add inventory, ender chest, experience, and location data
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
            plugin.getLogger().warning("Failed to send player event: " + e.getMessage());
        }
    }
    
    /**
     * Handle inventory events - send update when inventory changes
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            // Send update after a short delay to ensure inventory is updated
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                sendPlayerUpdate(player);
            }, 1L);
        }
    }
    
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            sendPlayerUpdate(player);
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            sendPlayerUpdate(player);
        }
    }
    
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        // Send update after a short delay to ensure inventory is updated
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sendPlayerUpdate(player);
        }, 1L);
    }
    
    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            // Send update after a short delay to ensure inventory is updated
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                sendPlayerUpdate(player);
            }, 1L);
        }
    }
}
