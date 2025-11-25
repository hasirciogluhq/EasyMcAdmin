package com.hasirciogluhq.easymcadmin.listeners.player.stats;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.managers.DispatcherManager;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.plugin.events.stats.PlayerStatsPacket;
import com.hasirciogluhq.easymcadmin.serializers.player.PlayerStatsSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically scans player statistics, detects diffs, and pushes updates.
 */
public class PlayerStatsListener implements Listener {

    private final EasyMcAdmin plugin;
    // Local cache for diffing stats between ticks
    private final Map<UUID, JsonObject> previousStats = new ConcurrentHashMap<>();
    private final Map<UUID, String> previousHashes = new ConcurrentHashMap<>();
    private final DispatcherManager dispatcherManager;

    public PlayerStatsListener(EasyMcAdmin plugin, DispatcherManager dispatcherManager) {
        this.plugin = plugin;
        this.dispatcherManager = dispatcherManager;
        startTask();
    }

    private void startTask() {
        // Async ticker to scan all online players for stat changes
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!plugin.getTransportManager().isConnected() || !plugin.getTransportManager().isAuthenticated()) {
                return;
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    scanPlayer(player);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed stats scan for " + player.getName() + ": " + e.getMessage());
                }
            }
        }, 60L, 60L);
    }

    // events
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        previousStats.remove(uuid);
        previousHashes.remove(uuid);
    }

    private void scanPlayer(Player player) {
        if (!plugin.getTransportManager().isConnected() || !plugin.getTransportManager().isAuthenticated()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        JsonObject payload = PlayerStatsSerializer.getPlayerStatsPayload(player);
        JsonObject stats = payload.getAsJsonObject("stats");
        String currentHash = calculateHash(stats);
        String previousHash = previousHashes.get(uuid);

        if (previousHash == null) {
            sendPacket(player, payload, stats, currentHash, null, true);
            return;
        }

        if (!previousHash.equals(currentHash)) {
            JsonObject previous = previousStats.get(uuid);
            JsonObject diff = calculateDiff(previous, stats);
            if (diff.size() == 0) {
                return;
            }
            sendPacket(player, payload, diff, currentHash, previousHash, false);
        }
    }

    private void sendPacket(
            Player player, JsonObject basePayload, JsonObject statsData, String hash, String prevHash,
            boolean fullSync) {

        UUID uuid = player.getUniqueId();

        JsonObject playerData = new JsonObject();
        playerData.addProperty("uuid", uuid.toString());
        playerData.addProperty("username", player.getName());
        playerData.add("stats", statsData);

        // Packet packet = new PlayerStatsPacket(hash, fullSync, prevHash, playerData);

        this.dispatcherManager.getPlayerStatsDispatcher().dispatch(hash, hash, fullSync, playerData);

        previousStats.put(uuid, fullSync ? statsData.deepCopy() : merge(previousStats.get(uuid), statsData));
        previousHashes.put(uuid, hash);
    }

    private JsonObject calculateDiff(JsonObject previous, JsonObject current) {
        if (previous == null) {
            return current.deepCopy();
        }

        JsonObject diff = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : current.entrySet()) {
            String key = entry.getKey();
            JsonElement newValue = entry.getValue();
            JsonElement oldValue = previous.get(key);

            if (oldValue == null) {
                diff.add(key, newValue);
                continue;
            }

            if (newValue.isJsonObject() && oldValue.isJsonObject()) {
                JsonObject childDiff = calculateDiff(oldValue.getAsJsonObject(), newValue.getAsJsonObject());
                if (childDiff.size() > 0) {
                    diff.add(key, childDiff);
                }
            } else if (!newValue.equals(oldValue)) {
                diff.add(key, newValue);
            }
        }
        return diff;
    }

    private JsonObject merge(JsonObject base, JsonObject diff) {
        if (base == null) {
            return diff.deepCopy();
        }

        JsonObject merged = base.deepCopy();
        for (Map.Entry<String, JsonElement> entry : diff.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonObject() && merged.has(key) && merged.get(key).isJsonObject()) {
                merged.add(key, merge(merged.getAsJsonObject(key), value.getAsJsonObject()));
            } else {
                merged.add(key, value);
            }
        }
        return merged;
    }

    private String calculateHash(JsonObject obj) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(obj.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(obj.toString().hashCode());
        }
    }
}
