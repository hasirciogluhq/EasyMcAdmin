package com.hasirciogluhq.easymcadmin.managers;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.dispatchers.player.stats.PlayerStatsDispatcher;
import com.hasirciogluhq.easymcadmin.dispatchers.player.stats.PlayerStatsEventDispatcher;
import com.hasirciogluhq.easymcadmin.interfaces.player.stats.PlayerStatsDispatcherInterface;
import com.hasirciogluhq.easymcadmin.interfaces.player.stats.PlayerStatsEventDispatcherInterface;

public class DispatcherManager {
    private PlayerStatsDispatcherInterface playerStatsDispatcher;
    private PlayerStatsEventDispatcherInterface playerStatsEventDispatcher;
    // private EasyMcAdmin plugin;

    public DispatcherManager(EasyMcAdmin ema) {
        // this.plugin = plugin;
        this.playerStatsDispatcher = new PlayerStatsDispatcher(ema);
        this.playerStatsEventDispatcher = new PlayerStatsEventDispatcher(ema);
    }

    public PlayerStatsDispatcherInterface getPlayerStatsDispatcher() {
        return playerStatsDispatcher;
    }

    public PlayerStatsEventDispatcherInterface getPlayerStatsEventDispatcher() {
        return playerStatsEventDispatcher;
    }
}
