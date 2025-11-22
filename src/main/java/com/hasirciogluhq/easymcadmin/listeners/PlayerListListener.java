package com.hasirciogluhq.easymcadmin.listeners;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.player.PlayerJoinPacket;
import com.hasirciogluhq.easymcadmin.packets.player.PlayerLeftPacket;
import com.hasirciogluhq.easymcadmin.packets.player.PlayerInventoryUpdatePacket;
import com.hasirciogluhq.easymcadmin.packets.player.PlayerDetailsUpdatePacket;
import com.hasirciogluhq.easymcadmin.packets.player.PlayerChunkPacket;
import com.hasirciogluhq.easymcadmin.packets.player.PlayerBalanceUpdatePacket;
import com.hasirciogluhq.easymcadmin.player.PlayerDataSerializer;
import com.hasirciogluhq.easymcadmin.player.serializers.InventorySerializer;
import com.hasirciogluhq.easymcadmin.economy.EconomyManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // Store previous inventory and ender chest states for diff calculation
    private final Map<UUID, JsonArray> previousInventories = new HashMap<>();
    private final Map<UUID, JsonArray> previousEnderChests = new HashMap<>();
    private final Map<UUID, String> previousInventoryHashes = new HashMap<>();
    private final Map<UUID, String> previousEnderChestHashes = new HashMap<>();

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

        RegisteredServiceProvider<Permission> rsp = Bukkit.getServer().getServicesManager()
                .getRegistration(Permission.class);
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
            if (!plugin.getTransportManager().isConnected()) {
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

        // Clean up stored states when player quits
        UUID playerUUID = player.getUniqueId();
        previousInventories.remove(playerUUID);
        previousEnderChests.remove(playerUUID);
        previousInventoryHashes.remove(playerUUID);
        previousEnderChestHashes.remove(playerUUID);
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
        // Send inventory update immediately - inventory is already updated at this
        // point
        sendPlayerInventoryUpdate(player, false);
    }

    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        // Send inventory update immediately - inventory is already updated at this
        // point
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
        if (!plugin.getTransportManager().isConnected() || !plugin.getTransportManager().isAuthenticated()) {
            return;
        }

        try {
            JsonObject playerObj = PlayerDataSerializer.getPlayerDetailsPayload(player);
            playerObj.addProperty("online", true);

            // Set last_played to current time when player joins
            playerObj.addProperty("last_played", System.currentTimeMillis());

            // Add inventory, ender chest data for join events
            PlayerDataSerializer.addPlayerInventoryData(playerObj, player);

            Packet packet = new PlayerJoinPacket(playerObj);
            plugin.getTransportManager().sendPacket(packet);

            // Send balance updates separately
            sendPlayerBalanceUpdate(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send player join event: " + e.getMessage());
        }
    }

    /**
     * Send player left event with full details
     * Action: player.left
     */
    public void sendPlayerLeft(Player player) {
        if (!plugin.getTransportManager().isConnected() || !plugin.getTransportManager().isAuthenticated()) {
            return;
        }

        try {
            JsonObject playerObj = PlayerDataSerializer.getPlayerDetailsPayload(player);
            playerObj.addProperty("online", false);

            Packet packet = new PlayerLeftPacket(playerObj);
            plugin.getTransportManager().sendPacket(packet);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send player left event: " + e.getMessage());
        }
    }

    /**
     * Send player inventory update (only inventory data)
     * 
     * @param fullSync If true, sends full inventory sync (includes ender chest), if
     *                 false sends diff sync
     *                 Used for inventory events (click, open, close, etc.) and full
     *                 sync requests
     *                 Action: player.inventory_update
     */
    public void sendPlayerInventoryUpdate(Player player, boolean fullSync) {
        if (!plugin.getTransportManager().isConnected() || !plugin.getTransportManager().isAuthenticated()) {
            return;
        }

        UUID playerUUID = player.getUniqueId();

        // Serialize current inventory and ender chest
        JsonArray currentInventory = null;
        JsonArray currentEnderChest = null;

        PlayerInventory inventory = player.getInventory();
        if (inventory != null) {
            currentInventory = InventorySerializer.serializeInventory(inventory);
        }

        org.bukkit.inventory.Inventory enderChest = player.getEnderChest();
        if (enderChest != null) {
            currentEnderChest = InventorySerializer.serializeEnderChest(enderChest);
        }

        // Calculate hashes for both inventory and ender chest
        String inventoryHash = InventorySerializer.calculateInventoryHash(player.getInventory());
        String enderChestHash = InventorySerializer.calculateEnderChestHash(player.getEnderChest());

        try {
            JsonObject playerObj = new JsonObject();
            playerObj.addProperty("uuid", playerUUID.toString());

            if (fullSync) {
                // Full sync: send complete inventory and ender chest
                if (currentInventory != null) {
                    playerObj.add("inventory", currentInventory);
                }
                if (currentEnderChest != null) {
                    playerObj.add("ender_chest", currentEnderChest);
                }

                // Update stored states
                if (currentInventory != null) {
                    previousInventories.put(playerUUID, currentInventory);
                    previousInventoryHashes.put(playerUUID, inventoryHash);
                }
                if (currentEnderChest != null) {
                    previousEnderChests.put(playerUUID, currentEnderChest);
                    previousEnderChestHashes.put(playerUUID, enderChestHash);
                }
            } else {
                // Diff sync: send only changed slots
                JsonArray previousInventory = previousInventories.get(playerUUID);
                JsonArray previousEnderChest = previousEnderChests.get(playerUUID);
                String previousInventoryHash = previousInventoryHashes.get(playerUUID);
                String previousEnderChestHash = previousEnderChestHashes.get(playerUUID);

                // Check if inventory hash changed
                boolean inventoryChanged = !inventoryHash
                        .equals(previousInventoryHash != null ? previousInventoryHash : "");
                if (inventoryChanged && currentInventory != null) {
                    JsonArray inventoryDiff = InventorySerializer.calculateDiff(previousInventory, currentInventory);
                    playerObj.add("inventory", inventoryDiff);

                    // Update stored state
                    previousInventories.put(playerUUID, currentInventory);
                    previousInventoryHashes.put(playerUUID, inventoryHash);
                }

                // Check if ender chest hash changed
                boolean enderChestChanged = !enderChestHash
                        .equals(previousEnderChestHash != null ? previousEnderChestHash : "");
                if (enderChestChanged && currentEnderChest != null) {
                    JsonArray enderChestDiff = InventorySerializer.calculateDiff(previousEnderChest, currentEnderChest);
                    playerObj.add("ender_chest", enderChestDiff);

                    // Update stored state
                    previousEnderChests.put(playerUUID, currentEnderChest);
                    previousEnderChestHashes.put(playerUUID, enderChestHash);
                }
            }

            Packet packet = new PlayerInventoryUpdatePacket(inventoryHash, enderChestHash, fullSync, playerObj);
            plugin.getTransportManager().sendPacket(packet);
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
        if (!plugin.getTransportManager().isConnected() || !plugin.getTransportManager().isAuthenticated()) {
            return;
        }

        try {
            JsonObject playerObj = PlayerDataSerializer.getPlayerDetailsPayload(player);
            Packet packet = new PlayerDetailsUpdatePacket(playerObj);
            plugin.getTransportManager().sendPacket(packet);

            // Send balance updates separately
            sendPlayerBalanceUpdate(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send player details update: " + e.getMessage());
        }
    }

    /**
     * Send player balance update for all enabled economy providers
     * Action: player.balance_update
     * 
     * @param player Player to send balance for
     */
    public void sendPlayerBalanceUpdate(Player player) {
        if (!plugin.getTransportManager().isConnected() || !plugin.getTransportManager().isAuthenticated()) {
            return;
        }

        EconomyManager economyManager = plugin.getEconomyManager();
        if (economyManager == null) {
            return;
        }

        try {
            // Get all balances from enabled providers
            List<EconomyManager.PlayerBalanceEntry> balances = economyManager.getPlayerBalances(player);
            
            if (balances.isEmpty()) {
                return;
            }

            // Create player balance data object
            JsonObject playerBalanceData = new JsonObject();
            playerBalanceData.addProperty("uuid", player.getUniqueId().toString());
            playerBalanceData.addProperty("username", player.getName());
            playerBalanceData.addProperty("online", true);

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

            // Send balance update packet
            Packet packet = new PlayerBalanceUpdatePacket(playerBalanceData);
            plugin.getTransportManager().sendPacket(packet);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send player balance update: " + e.getMessage());
        }
    }

    /**
     * Send balance update for offline player
     * 
     * @param offlinePlayer OfflinePlayer to send balance for
     */
    public void sendOfflinePlayerBalanceUpdate(OfflinePlayer offlinePlayer) {
        if (!plugin.getTransportManager().isConnected() || !plugin.getTransportManager().isAuthenticated()) {
            return;
        }

        EconomyManager economyManager = plugin.getEconomyManager();
        if (economyManager == null) {
            return;
        }

        try {
            // Get all balances from enabled providers
            List<EconomyManager.PlayerBalanceEntry> balances = economyManager.getPlayerBalances(offlinePlayer);
            
            if (balances.isEmpty()) {
                return;
            }

            // Create player balance data object
            JsonObject playerBalanceData = new JsonObject();
            playerBalanceData.addProperty("uuid", offlinePlayer.getUniqueId().toString());
            playerBalanceData.addProperty("username", offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown");
            playerBalanceData.addProperty("online", false);

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

            // Send balance update packet
            Packet packet = new PlayerBalanceUpdatePacket(playerBalanceData);
            plugin.getTransportManager().sendPacket(packet);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send offline player balance update: " + e.getMessage());
        }
    }

    /**
     * Send all offline players in chunks after server is ready
     * Called when WebSocket connection is established
     * Also sends full sync for all online players
     */
    public void sendAllOfflinePlayers() {
        if (initialSyncDone || !plugin.getTransportManager().isConnected()) {
            return;
        }

        initialSyncDone = true;

        // First, send full sync for all online players
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            sendPlayerInventoryUpdate(onlinePlayer, true);
            sendPlayerBalanceUpdate(onlinePlayer);
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
        if (!plugin.getTransportManager().isConnected() || !plugin.getTransportManager().isAuthenticated()) {
            return;
        }

        try {
            JsonArray playerArray = new JsonArray();

            for (OfflinePlayer offlinePlayer : players) {
                JsonObject playerObj;

                // Use common function for both online and offline players
                if (offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
                    Player player = offlinePlayer.getPlayer();
                    playerObj = PlayerDataSerializer.getPlayerDetailsPayload(player);
                } else {
                    // Offline player - use common function for offline players
                    playerObj = PlayerDataSerializer.getOfflinePlayerDetailsPayload(offlinePlayer);
                }

                playerArray.add(playerObj);
            }

            // Send balance updates for all players in chunk (after chunk is sent)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (OfflinePlayer offlinePlayer : players) {
                    if (offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
                        sendPlayerBalanceUpdate(offlinePlayer.getPlayer());
                    } else {
                        sendOfflinePlayerBalanceUpdate(offlinePlayer);
                    }
                }
            }, 1L); // 1 tick delay after chunk

            Packet packet = new PlayerChunkPacket(chunkIndex, totalChunks, isLastChunk, playerArray);
            plugin.getTransportManager().sendPacket(packet);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send player chunk: " + e.getMessage());
        }
    }



    // ============================================================================
    // HELPER FUNCTIONS - Vault Integration
    // ============================================================================
    // TODO: Vault integration methods are temporarily commented out
    // Economy balance and groups will be handled separately via player_balances table
    // These methods are kept for future use if needed
    
    // /**
    //  * Get player balance from economy plugin
    //  * 
    //  * @param player Player to get balance for
    //  * @return Balance amount, or null if economy is not available
    //  */
    // private Double getPlayerBalance(OfflinePlayer player) {
    //     if (economy == null || player == null) {
    //         return null;
    //     }
    //     try {
    //         return economy.getBalance(player);
    //     } catch (Exception e) {
    //         plugin.getLogger().warning("Failed to get balance for player " + player.getName() + ": " + e.getMessage());
    //         return null;
    //     }
    // }
    // 
    // /**
    //  * Get currency name from economy plugin
    //  * 
    //  * @return Currency name, or null if economy is not available
    //  */
    // private String getCurrencyName() {
    //     if (economy == null) {
    //         return null;
    //     }
    //     try {
    //         return economy.currencyNameSingular();
    //     } catch (Exception e) {
    //         return null;
    //     }
    // }
    // 
    // /**
    //  * Get player's primary group (rank)
    //  * 
    //  * @param player Player to get group for
    //  * @return Primary group name, or null if permission plugin is not available
    //  */
    // private String getPlayerPrimaryGroup(Player player) {
    //     if (permission == null || player == null) {
    //         return null;
    //     }
    //     try {
    //         String world = player.getWorld() != null ? player.getWorld().getName() : null;
    //         String group = permission.getPrimaryGroup(world, player);
    //         return group != null && !group.isEmpty() ? group : null;
    //     } catch (Exception e) {
    //         plugin.getLogger().warning("Failed to get primary group for player " + player.getName() + ": " + e.getMessage());
    //         return null;
    //     }
    // }
    // 
    // /**
    //  * Get all player groups (including ranks)
    //  * 
    //  * @param player Player to get groups for
    //  * @return Array of group names, or null if permission plugin is not available
    //  */
    // private String[] getPlayerGroups(Player player) {
    //     if (permission == null || player == null) {
    //         return null;
    //     }
    //     try {
    //         String world = player.getWorld() != null ? player.getWorld().getName() : null;
    //         String[] groups = permission.getPlayerGroups(world, player);
    //         return groups != null && groups.length > 0 ? groups : null;
    //     } catch (Exception e) {
    //         plugin.getLogger().warning("Failed to get groups for player " + player.getName() + ": " + e.getMessage());
    //         return null;
    //     }
    // }
    // 
    // /**
    //  * Get offline player's primary group (rank)
    //  * 
    //  * @param offlinePlayer OfflinePlayer to get group for
    //  * @return Primary group name, or null if permission plugin is not available
    //  */
    // private String getOfflinePlayerPrimaryGroup(OfflinePlayer offlinePlayer) {
    //     if (permission == null || offlinePlayer == null) {
    //         return null;
    //     }
    //     try {
    //         String group = permission.getPrimaryGroup(null, offlinePlayer);
    //         return group != null && !group.isEmpty() ? group : null;
    //     } catch (Exception e) {
    //         return null;
    //     }
    // }
    // 
    // /**
    //  * Get all offline player groups (including ranks)
    //  * 
    //  * @param offlinePlayer OfflinePlayer to get groups for
    //  * @return Array of group names, or null if permission plugin is not available
    //  */
    // private String[] getOfflinePlayerGroups(OfflinePlayer offlinePlayer) {
    //     if (permission == null || offlinePlayer == null) {
    //         return null;
    //     }
    //     try {
    //         String[] groups = permission.getPlayerGroups(null, offlinePlayer);
    //         return groups != null && groups.length > 0 ? groups : null;
    //     } catch (Exception e) {
    //         return null;
    //     }
    // }

}
