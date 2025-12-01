package com.hasirciogluhq.easymcadmin.packet_handlers.event;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;

public class PlayerEventHandler {
    public static Object handlePlayerInventorySync(Packet packet) {
        if (!packet.getPayload().has("player_uuid")) {
            return null;
        }

        String playerUUIDStr = packet.getPayload().get("player_uuid").getAsString();
        UUID playerUUID = UUID.fromString(playerUUIDStr);

        // Bukkit ana thread'te çalışması gerekiyor.
        Bukkit.getScheduler().runTask(EasyMcAdmin.getInstance(), () -> {
            try {
                Player p = Bukkit.getPlayer(playerUUID);
                if (EasyMcAdmin.getInstance().getServiceManager().getPlayerService() != null) {
                    EasyMcAdmin.getInstance().getServiceManager().getPlayerService()
                            .SendPlayerInventorySyncEvent(p);
                }
            } catch (Exception e) {
                EasyMcAdmin.getInstance().getLogger()
                        .warning("Player sync event handle error: " + playerUUIDStr);
            }
        });

        return null;
    }
}
