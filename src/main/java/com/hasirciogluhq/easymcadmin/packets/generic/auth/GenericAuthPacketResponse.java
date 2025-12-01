package com.hasirciogluhq.easymcadmin.packets.generic.auth;

import com.hasirciogluhq.easymcadmin.packets.generic.Packet;

public class GenericAuthPacketResponse extends Packet {

    public GenericAuthPacketResponse(Packet packet) {
        super(packet.getPacketId(), packet.getPacketType(), packet.getMetadata(), packet.getPayload());
    }

    public boolean isSuccess() {
        return payload.has("success") && payload.get("success").getAsBoolean();
    }

    public String getMessage() {
        return payload.has("message") ? payload.get("message").getAsString() : "";
    }

    public String getServerId() {
        return payload.has("server_id") ? payload.get("server_id").getAsString() : "";
    }
}
