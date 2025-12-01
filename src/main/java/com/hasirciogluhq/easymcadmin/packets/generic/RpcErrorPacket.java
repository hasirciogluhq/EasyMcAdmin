package com.hasirciogluhq.easymcadmin.packets.generic;

import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * Console output packet - EVENT type
 * Sent from plugin to server as one-way notification
 */
public class RpcErrorPacket extends Packet {

    public RpcErrorPacket(String error) {
        super(
                UUID.randomUUID().toString(), // packet_id
                PacketType.RPC, // Always EVENT - one-way notification
                new JsonObject(), // metadata
                createPayload(error) // payload
        );
    }

    private static JsonObject createPayload(String error) {
        JsonObject payload = new JsonObject();
        payload.addProperty("error", error);
        return payload;
    }
}
