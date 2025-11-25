package com.hasirciogluhq.easymcadmin.packets.rpc;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.PacketType;

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
