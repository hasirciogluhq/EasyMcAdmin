package com.hasirciogluhq.easymcadmin.packets.plugin.events.inventory;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.PacketType;

import java.util.UUID;

/**
 * Player inventory update packet - EVENT type
 * Sent when player inventory changes
 */
public class PlayerInventoryChangedPacket extends Packet {
    
    public PlayerInventoryChangedPacket(String inventoryHash, String enderChestHash, boolean fullSync, JsonObject inventoryData) {
        super(
            UUID.randomUUID().toString(),
            PacketType.EVENT,
            createMetadata(inventoryHash, enderChestHash, fullSync),
            createPayload(inventoryData)
        );
    }
    
    private static JsonObject createMetadata(String inventoryHash, String enderChestHash, boolean fullSync) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("action", "player.inventory.changed");
        metadata.addProperty("inventory_hash", inventoryHash);
        // Always include ender chest hash if available (for validation)
        if (enderChestHash != null && !enderChestHash.isEmpty()) {
            metadata.addProperty("ender_chest_hash", enderChestHash);
        }
        metadata.addProperty("full_sync", fullSync);
        return metadata;
    }
    
    private static JsonObject createPayload(JsonObject inventoryData) {
        return inventoryData;
    }
}

