package com.hasirciogluhq.easymcadmin.listeners;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.player.PlayerLeftPacket;
import com.hasirciogluhq.easymcadmin.serializers.player.*;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 */
public class PlayerListListener implements Listener {
    private final EasyMcAdmin plugin;
    private Economy economy = null;
    private Permission permission = null;

    // ============================================================================
    // CONSTRUCTOR & SETUP
    // ============================================================================

    public PlayerListListener(EasyMcAdmin plugin) {
        this.plugin = plugin;
        startPlayerStateUpdateTask();
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
                plugin.getServiceManager().getPlayerService().sendPlayerDetails(player);
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
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (p == null)
            return;

        plugin.getServiceManager().getPlayerService().handleJoin(p);
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
}
