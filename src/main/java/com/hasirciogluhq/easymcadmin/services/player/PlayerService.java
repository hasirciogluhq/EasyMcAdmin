package com.hasirciogluhq.easymcadmin.services.player;

import org.bukkit.entity.Player;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.transport.TransportManager;

public class PlayerService {
    private TransportManager transportManager;

    public PlayerService(EasyMcAdmin ema) {
        this.transportManager = ema.getTransportManager();
    }

    public void SendPlayerInventoryEvent(Player p) {
        if (!p.isOnline())
            return;
    }
}
