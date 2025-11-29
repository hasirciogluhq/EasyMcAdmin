package com.hasirciogluhq.easymcadmin.managers;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.listeners.PlayerListListener;
import com.hasirciogluhq.easymcadmin.listeners.player.inventory.InventoryChangeListener;
import com.hasirciogluhq.easymcadmin.listeners.player.stats.PlayerStatsEventListener;
import com.hasirciogluhq.easymcadmin.listeners.player.stats.PlayerStatsListener;

public class EventListenerManager {
    private final EasyMcAdmin plugin;

    private PlayerListListener playerListListener;
    private InventoryChangeListener inventoryChangeListener;
    // private PlayerStatsListener playerStatsListener;
    private PlayerStatsEventListener playerStatsEventListener;
    DispatcherManager dispatcherManager;

    public EventListenerManager(EasyMcAdmin ema, DispatcherManager dp) {
        this.plugin = ema;
        this.dispatcherManager = dp;
        this.playerListListener = new PlayerListListener(ema);
        this.inventoryChangeListener = new InventoryChangeListener(ema);
        // this is disabled until fix the sync problem.
        // this.playerStatsListener = new PlayerStatsListener(ema, this.dispatcherManager);
        this.playerStatsEventListener = new PlayerStatsEventListener(ema, this.dispatcherManager);
    }

    public void RegisterAllListeners() {
        PluginManager pm = Bukkit.getServer().getPluginManager();

        pm.registerEvents(playerListListener, this.plugin);
        pm.registerEvents(inventoryChangeListener, this.plugin);
        // pm.registerEvents(playerStatsListener, this.plugin);
        pm.registerEvents(playerStatsEventListener, this.plugin);
    }

    public PlayerListListener getPlayerListListener() {
        return playerListListener;
    }

    public InventoryChangeListener getInventoryChangeListener() {
        return inventoryChangeListener;
    }

    public PlayerStatsListener getPlayerStatsListener() {
        return null;
    }

    public PlayerStatsEventListener getPlayerStatsEventListener() {
        return playerStatsEventListener;
    }
}
