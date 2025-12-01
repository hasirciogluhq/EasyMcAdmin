package com.hasirciogluhq.easymcadmin.packets.generic.player;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.PacketType;

import java.util.UUID;

/**
 * Player balance update packet - EVENT type
 * Sent when player balances need to be updated
 */
public class PlayerBalanceUpdatePacket extends Packet {
    
    public PlayerBalanceUpdatePacket(JsonObject playerData) {
        super(
            UUID.randomUUID().toString(),
            PacketType.EVENT,
            createMetadata(),
            createPayload(playerData)
        );
    }
    
    private static JsonObject createMetadata() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("action", "player.balance_update");
        metadata.addProperty("requires_response", false);
        return metadata;
    }
    
    private static JsonObject createPayload(JsonObject playerData) {
        JsonObject payload = new JsonObject();
        payload.add("player", playerData);
        return payload;
    }
}

