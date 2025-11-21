package com.hasirciogluhq.easymcadmin.packets.player;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.PacketType;

import java.util.UUID;

/**
 * Player inventory update packet - EVENT type
 * Sent when player inventory changes
 */
public class PlayerInventoryUpdatePacket extends Packet {
    
    public PlayerInventoryUpdatePacket(String inventoryHash, String enderChestHash, boolean fullSync, JsonObject playerData) {
        super(
            UUID.randomUUID().toString(),
            PacketType.EVENT,
            createMetadata(inventoryHash, enderChestHash, fullSync),
            createPayload(playerData)
        );
    }
    
    private static JsonObject createMetadata(String inventoryHash, String enderChestHash, boolean fullSync) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("action", "player.inventory_update");
        metadata.addProperty("requires_response", false);
        metadata.addProperty("inventory_hash", inventoryHash);
        // Always include ender chest hash if available (for validation)
        if (enderChestHash != null && !enderChestHash.isEmpty()) {
            metadata.addProperty("ender_chest_hash", enderChestHash);
        }
        metadata.addProperty("full_sync", fullSync);
        return metadata;
    }
    
    private static JsonObject createPayload(JsonObject playerData) {
        JsonObject payload = new JsonObject();
        payload.add("player", playerData);
        return payload;
    }
}

