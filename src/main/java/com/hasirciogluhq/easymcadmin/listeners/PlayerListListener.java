package com.hasirciogluhq.easymcadmin.listeners;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.player.PlayerJoinPacket;
import com.hasirciogluhq.easymcadmin.packets.player.PlayerLeftPacket;
import com.hasirciogluhq.easymcadmin.serializers.player.*;
import com.hasirciogluhq.easymcadmin.packets.player.PlayerDetailsUpdatePacket;
import com.hasirciogluhq.easymcadmin.packets.player.PlayerChunkPacket;
import com.hasirciogluhq.easymcadmin.packets.player.PlayerBalanceUpdatePacket;
import com.hasirciogluhq.easymcadmin.economy.EconomyManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
    // MAIN PUBLIC FUNCTIONS - Player Updates
    // ============================================================================

    /**
     * Send player join event with full details and inventory
     * Action: player.join
     */
    @EventHandler
    public void sendPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getTransportManager().isConnected() || !plugin.getTransportManager().isAuthenticated()) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null)
            return;

        try {
            JsonObject playerObj = PlayerDataSerializer.getPlayerDetailsPayload(player);
            playerObj.addProperty("online", true);

            // Set last_played to current time when player joins
            playerObj.addProperty("last_played", System.currentTimeMillis());
            playerObj.addProperty("last_seen", System.currentTimeMillis());

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
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!plugin.getTransportManager().isConnected() || !plugin.getTransportManager().isAuthenticated()) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null)
            return;

        try {
            JsonObject playerObj = PlayerDataSerializer.getPlayerDetailsPayload(player);
            playerObj.addProperty("online", false);

            Packet packet = new PlayerLeftPacket(playerObj);
            plugin.getTransportManager().sendPacket(packet);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send player left event: " + e.getMessage());
        }
    }

    // ... existing code ...

    // ... existing code ...

    /**
     * Send player details update (all player info except inventory)
     * Used for periodic ticker updates
     * Action: player.updated
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
            playerBalanceData.addProperty("username",
                    offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown");
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
    public void syncAllPlayers() {
        if (initialSyncDone || !plugin.getTransportManager().isConnected()) {
            return;
        }

        initialSyncDone = true;

        // First, send full sync for all online players
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            plugin.getEventListenerManager().getInventoryChangeListener().sendPlayerInventoryUpdate(onlinePlayer, true);
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
            plugin.getEventListenerManager().getInventoryChangeListener().sendPlayerInventoryUpdate(player, true);
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
}
