package com.hasirciogluhq.easymcadmin.packet_handlers;

import java.util.UUID;


import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.economy.EconomyConfigPacket;
import com.hasirciogluhq.easymcadmin.transport.TransportManager;

public class EventPacketHandler {
    private TransportManager transportManager;

    public EventPacketHandler(TransportManager tm) {
        this.transportManager = tm;
    }

    public void handleEvent(Packet packet) {
        switch (packet.getAction()) {
            case "player.request_inventory_sync":
                // Handle inventory sync request from backend (hash mismatch detected)
                if (packet.getPayload().has("player_uuid")) {
                    String playerUUIDStr = packet.getPayload().get("player_uuid").getAsString();
                    try {
                        UUID playerUUID = UUID.fromString(playerUUIDStr);
                        // Use handlePlayerInventorySyncRequest method which calls
                        // sendPlayerInventoryUpdate
                        if (EasyMcAdmin.getInstance().getPlayerListListener() != null) {
                            EasyMcAdmin.getInstance().getPlayerListListener()
                                    .handlePlayerInventorySyncRequest(playerUUID);
                        }
                    } catch (IllegalArgumentException e) {
                        EasyMcAdmin.getInstance().getLogger()
                                .warning("Invalid player UUID in inventory sync request: " + playerUUIDStr);
                    }
                }
                break;

            case "server.set_economy_config":
                // Handle economy config update from backend
                handleEconomyConfig(packet);
                break;

            default:
                EasyMcAdmin.getInstance().getLogger().info("Unknown packet action: " + packet.getAction());
        }
    }

    private void handleEconomyConfig(Packet packet) {
        EconomyConfigPacket economyConfigPacket = new EconomyConfigPacket(packet);
        com.google.gson.JsonObject economyConfig = economyConfigPacket.getEconomyConfig();

        // Update economy manager with new config
        if (EasyMcAdmin.getInstance().getEconomyManager() != null) {
            EasyMcAdmin.getInstance().getEconomyManager().updateEconomyConfig(economyConfig);
        }
    }
}
