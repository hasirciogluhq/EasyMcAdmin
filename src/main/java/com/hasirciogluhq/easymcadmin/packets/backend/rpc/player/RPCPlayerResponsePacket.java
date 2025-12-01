package com.hasirciogluhq.easymcadmin.packets.backend.rpc.player;

import java.util.UUID;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.PacketType;

public class RPCPlayerResponsePacket extends Packet {
    public RPCPlayerResponsePacket(JsonObject payload, Boolean online) {
        super(
                UUID.randomUUID().toString(),
                PacketType.RPC,
                payload,
                new JsonObject());

        JsonObject metadata = new JsonObject();
        metadata.addProperty("online", online);

        this.metadata = metadata;
    }
}
