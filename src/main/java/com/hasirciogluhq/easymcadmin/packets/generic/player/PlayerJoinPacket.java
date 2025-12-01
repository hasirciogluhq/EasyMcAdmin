package com.hasirciogluhq.easymcadmin.packets.generic.player;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.PacketType;

import java.util.UUID;

/**
 * Player join packet - EVENT type
 * Sent when a player joins the server
 */
public class PlayerJoinPacket extends Packet {
    
    public PlayerJoinPacket(JsonObject playerData) {
        super(
            UUID.randomUUID().toString(),
            PacketType.EVENT,
            createMetadata(),
            createPayload(playerData)
        );
    }
    
    private static JsonObject createMetadata() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("action", "plugin.player.join");
        return metadata;
    }
    
    private static JsonObject createPayload(JsonObject playerData) {
        JsonObject payload = new JsonObject();
        payload.add("player", playerData);
        return payload;
    }
}

