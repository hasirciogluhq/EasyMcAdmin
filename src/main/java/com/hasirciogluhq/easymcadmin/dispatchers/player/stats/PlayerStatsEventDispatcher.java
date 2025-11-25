package com.hasirciogluhq.easymcadmin.dispatchers.player.stats;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.interfaces.player.stats.PlayerStatsEventDispatcherInterface;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.plugin.events.stats.PlayerStatsEventPacket;

public class PlayerStatsEventDispatcher implements PlayerStatsEventDispatcherInterface {
    private final EasyMcAdmin plugin;

    public PlayerStatsEventDispatcher(EasyMcAdmin pl) {
        this.plugin = pl;
    }

    @Override
    public void dispatch(JsonObject event) {
        Packet packet = new PlayerStatsEventPacket(event);
        try {
            plugin.getTransportManager().sendPacket(packet);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send player stat event: " + e.getMessage());
        }
    }
}
