package com.hasirciogluhq.easymcadmin.packets.plugin.events.console;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.PacketType;

import java.util.UUID;

/**
 * Console output packet - EVENT type
 * Sent from plugin to server as one-way notification
 */
public class ConsoleOutputPacket extends Packet {
    
    public ConsoleOutputPacket(String message, String level, String kind, String type) {
        super(
            UUID.randomUUID().toString(), // packet_id
            PacketType.EVENT, // Always EVENT - one-way notification
            createMetadata(), // metadata
            createPayload(message, level, kind, type) // payload
        );
    }
    
    private static JsonObject createMetadata() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("action", "console_output");
        metadata.addProperty("requires_response", false); // EVENT packets don't need response
        return metadata;
    }
    
    private static JsonObject createPayload(String message, String level, String kind, String type) {
        JsonObject payload = new JsonObject();
        payload.addProperty("message", message);
        payload.addProperty("level", level);
        payload.addProperty("kind", kind != null ? kind : "unknown");
        payload.addProperty("type", type != null ? type : "log");
        return payload;
    }
    
    public String getMessage() {
        return payload.get("message").getAsString();
    }
    
    public String getLevel() {
        return payload.get("level").getAsString();
    }
    
    public String getKind() {
        return payload.has("kind") ? payload.get("kind").getAsString() : "unknown";
    }
    
    public String getType() {
        return payload.has("type") ? payload.get("type").getAsString() : "log";
    }
}

