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

        // Needs to run on the Bukkit main thread.
        Bukkit.getScheduler().runTask(EasyMcAdmin.getInstance(), () -> {
            try {
                UUID playerUUID = UUID.fromString(playerUUIDStr);
                Player p = Bukkit.getPlayer(playerUUID);

                if (p != null && p.isOnline()) {
                    // Trigger synchronization through the service.
                    // fullSync: true (backend requested explicit sync, so send full)
                    // sendPacket: true (event handler, so we want the packet sent)
                    EasyMcAdmin.getInstance().getServiceManager().getPlayerService()
                            .SendPlayerInventorySyncEvent(p, true, true);
                }
            } catch (Exception e) {
                EasyMcAdmin.getInstance().getLogger()
                        .warning("Player sync event handle error: " + playerUUIDStr + " - " + e.getMessage());
            }
        });

        return null;
    }
}
