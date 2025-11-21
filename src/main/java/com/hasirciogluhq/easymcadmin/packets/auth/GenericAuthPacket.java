package com.hasirciogluhq.easymcadmin.packets.auth;

import java.util.UUID;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.PacketType;

public class GenericAuthPacket extends Packet {

    public GenericAuthPacket(String token) {
        super(
                UUID.randomUUID().toString(),
                PacketType.EVENT,
                createMetadata(),
                createPayload(token));
    }

    private static JsonObject createMetadata() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("action", "plugin.auth.request");
        return metadata;
    }

    private static JsonObject createPayload(String token) {
        JsonObject payload = new JsonObject();
        payload.addProperty("token", token);
        return payload;
    }
}
