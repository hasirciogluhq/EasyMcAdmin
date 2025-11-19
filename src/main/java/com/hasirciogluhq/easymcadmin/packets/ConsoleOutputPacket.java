package com.hasirciogluhq.easymcadmin.packets;

import com.google.gson.JsonObject;
import java.util.UUID;

/**
 * Console output packet - EVENT type
 * Sent from plugin to server as one-way notification
 */
public class ConsoleOutputPacket extends Packet {
    
    public ConsoleOutputPacket(String message, String level) {
        super(
            UUID.randomUUID().toString(), // packet_id
            PacketType.EVENT, // Always EVENT - one-way notification
            createMetadata(), // metadata
            createPayload(message, level) // payload
        );
    }
    
    private static JsonObject createMetadata() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("action", "console_output");
        metadata.addProperty("requires_response", false); // EVENT packets don't need response
        return metadata;
    }
    
    private static JsonObject createPayload(String message, String level) {
        JsonObject payload = new JsonObject();
        payload.addProperty("message", message);
        payload.addProperty("level", level);
        return payload;
    }
    
    public String getMessage() {
        return payload.get("message").getAsString();
    }
    
    public String getLevel() {
        return payload.get("level").getAsString();
    }
}

