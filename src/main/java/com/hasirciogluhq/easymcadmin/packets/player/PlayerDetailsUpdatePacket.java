package com.hasirciogluhq.easymcadmin.packets.player;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.PacketType;

import java.util.UUID;

/**
 * Player details update packet - EVENT type
 * Sent periodically with player location, ping, experience, etc. (no inventory)
 */
public class PlayerDetailsUpdatePacket extends Packet {

    public PlayerDetailsUpdatePacket(JsonObject playerData) {
        super(
                UUID.randomUUID().toString(),
                PacketType.EVENT,
                createMetadata(),
                createPayload(playerData));
    }

    private static JsonObject createMetadata() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("action", "player.updated");
        metadata.addProperty("requires_response", false);
        return metadata;
    }

    private static JsonObject createPayload(JsonObject playerData) {
        JsonObject payload = new JsonObject();
        payload.add("player", playerData);
        return payload;
    }
}
