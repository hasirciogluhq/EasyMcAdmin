package com.hasirciogluhq.easymcadmin.dispatchers.player.stats;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.interfaces.player.stats.PlayerStatsEventDispatcherInterface;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.plugin.events.stats.PlayerStatsEventPacket;

public class PlayerStatsEventDispatcher implements PlayerStatsEventDispatcherInterface {
    private final EasyMcAdmin plugin;

    public PlayerStatsEventDispatcher(EasyMcAdmin pl) {
        this.plugin = pl;
    }

    @Override
    public void dispatch(JsonObject event) {
        Packet packet = new PlayerStatsEventPacket(event);
        plugin.getTransportManager().sendPacketAsync(packet);
    }
}
