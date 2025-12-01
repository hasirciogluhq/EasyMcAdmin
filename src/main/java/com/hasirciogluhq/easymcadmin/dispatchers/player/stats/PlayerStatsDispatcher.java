package com.hasirciogluhq.easymcadmin.dispatchers.player.stats;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.interfaces.player.stats.PlayerStatsDispatcherInterface;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.plugin.events.stats.PlayerStatsPacket;

public class PlayerStatsDispatcher implements PlayerStatsDispatcherInterface {
    private final EasyMcAdmin plugin;

    public PlayerStatsDispatcher(EasyMcAdmin pl) {
        this.plugin = pl;
    }

    @Override
    public void dispatch(String statsHash, String previousHash, Boolean fullSync, JsonObject event) {
        Packet packet = new PlayerStatsPacket(statsHash, fullSync, previousHash, event);
        try {
            plugin.getTransportManager().sendPacket(packet);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send player stat event: " + e.getMessage());
        }
    }
}
