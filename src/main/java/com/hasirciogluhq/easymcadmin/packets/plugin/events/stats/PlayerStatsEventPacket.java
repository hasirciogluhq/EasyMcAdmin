package com.hasirciogluhq.easymcadmin.packets.plugin.events.stats;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.PacketType;

import java.util.UUID;

/**
 * Append-only player stat event packet.
 * Each instance represents a single stat occurrence (join, quit, block break etc.).
 */
public class PlayerStatsEventPacket extends Packet {

    public PlayerStatsEventPacket(JsonObject event) {
        super(
                UUID.randomUUID().toString(),
                PacketType.EVENT,
                createMetadata(),
                createPayload(event));
    }

    private static JsonObject createMetadata() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("action", "player.stats.event");
        metadata.addProperty("requires_response", false);
        return metadata;
    }

    private static JsonObject createPayload(JsonObject event) {
        JsonObject payload = new JsonObject();
        payload.add("event", event);
        return payload;
    }
}
