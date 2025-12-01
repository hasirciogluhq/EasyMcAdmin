package com.hasirciogluhq.easymcadmin.packets.generic.player;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.PacketType;

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
        metadata.addProperty("action", "plugin.player.updated");
        return metadata;
    }

    private static JsonObject createPayload(JsonObject playerData) {
        JsonObject payload = new JsonObject();
        payload.add("player", playerData);
        return payload;
    }
}
