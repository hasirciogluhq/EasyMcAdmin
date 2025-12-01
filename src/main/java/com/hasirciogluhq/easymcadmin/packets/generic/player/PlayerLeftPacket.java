package com.hasirciogluhq.easymcadmin.packets.generic.player;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.PacketType;

import java.util.UUID;

/**
 * Player left packet - EVENT type
 * Sent when a player leaves the server
 */
public class PlayerLeftPacket extends Packet {
    
    public PlayerLeftPacket(JsonObject playerData) {
        super(
            UUID.randomUUID().toString(),
            PacketType.EVENT,
            createMetadata(),
            createPayload(playerData)
        );
    }
    
    private static JsonObject createMetadata() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("action", "plugin.player.left");
        return metadata;
    }
    
    private static JsonObject createPayload(JsonObject playerData) {
        JsonObject payload = new JsonObject();
        payload.add("player", playerData);
        return payload;
    }
}

