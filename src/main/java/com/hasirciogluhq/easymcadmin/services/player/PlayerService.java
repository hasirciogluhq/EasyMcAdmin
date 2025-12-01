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
import com.hasirciogluhq.easymcadmin.packets.plugin.events.economy.PlayerBalancesUpdatedPacket;
import com.hasirciogluhq.easymcadmin.packets.plugin.events.player.OfflinePlayerChunkPacket;
import com.hasirciogluhq.easymcadmin.serializers.player.PlayerDataSerializer;
import com.hasirciogluhq.easymcadmin.transport.TransportManager;

public class PlayerService {
    private TransportManager transportManager;
    private EasyMcAdmin plugin;

    public PlayerService(EasyMcAdmin ema) {
        this.transportManager = ema.getTransportManager();
    }

    public void SendPlayerInventorySyncEvent(Player p) {
        if (!p.isOnline())
            return;
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
                        Packet packet = new PlayerBalancesUpdatedPacket(playerBalanceData);
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
    // Tüm event handler lar zaten main thread dan başlıyor o yüzden main
    // thread run'a gerek yok
    public void handleJoin(Player p) {
        JsonObject playerObj = PlayerDataSerializer.getPlayerDetailsPayload(p);
        playerObj.addProperty("online", true);

        // Set last_seen to current time when player joins
        playerObj.addProperty("first_seen", p.getFirstPlayed());
        playerObj.addProperty("last_seen", System.currentTimeMillis());

        // Add inventory, ender chest data for join events
        PlayerDataSerializer.addPlayerInventoryData(playerObj, p);

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

            // Eğer array yoksa dosyada → sıfırdan oluştur
            data.put("players", syncedUuids);

            for (OfflinePlayer op : players) {

                UUID uuid = op.getUniqueId();

                // ❗ Sadece OFFLINE oyuncular takip edilecek
                if (!op.isOnline()) {
                    if (!syncedUuids.contains(uuid.toString())) {
                        syncedUuids.add(uuid.toString());
                        // plugin.getDataManager().saveAsync("synced_user_offline_users.json"); //
                        // İstersen aç
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

            // Optional: Online oyuncular için balance send
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
