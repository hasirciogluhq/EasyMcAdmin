package com.hasirciogluhq.easymcadmin.packets.generic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Generic Packet implementation for deserializing packets from JSON
 * Used when receiving packets from transport layer
 */
public class GenericPacket extends Packet {
    
    /**
     * Create a GenericPacket from JSON string
     * 
     * @param jsonString JSON string representation of the packet
     */
    public GenericPacket(String jsonString) {
        super(null, null, null, null);
        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
        
        this.packetId = json.has("packet_id") ? json.get("packet_id").getAsString() : null;
        this.packetType = json.has("packet_type") 
            ? PacketType.valueOf(json.get("packet_type").getAsString()) 
            : PacketType.EVENT;
        this.metadata = json.has("metadata") && json.get("metadata").isJsonObject()
            ? json.get("metadata").getAsJsonObject()
            : new JsonObject();
        this.payload = json.has("payload") && json.get("payload").isJsonObject()
            ? json.get("payload").getAsJsonObject()
            : new JsonObject();
        this.timestamp = json.has("timestamp") ? json.get("timestamp").getAsLong() : System.currentTimeMillis() / 1000;
    }
    
    /**
     * Create a GenericPacket from JsonObject
     * 
     * @param json JsonObject representation of the packet
     */
    public GenericPacket(JsonObject json) {
        super(null, null, null, null);
        
        this.packetId = json.has("packet_id") ? json.get("packet_id").getAsString() : null;
        this.packetType = json.has("packet_type") 
            ? PacketType.valueOf(json.get("packet_type").getAsString()) 
            : PacketType.EVENT;
        this.metadata = json.has("metadata") && json.get("metadata").isJsonObject()
            ? json.get("metadata").getAsJsonObject()
            : new JsonObject();
        this.payload = json.has("payload") && json.get("payload").isJsonObject()
            ? json.get("payload").getAsJsonObject()
            : new JsonObject();
        this.timestamp = json.has("timestamp") ? json.get("timestamp").getAsLong() : System.currentTimeMillis() / 1000;
    }
    
    /**
     * Create a GenericPacket with explicit parameters
     * 
     * @param packetId Packet ID
     * @param packetType Packet type (EVENT or RPC)
     * @param metadata Metadata object
     * @param payload Payload object
     */
    public GenericPacket(String packetId, PacketType packetType, JsonObject metadata, JsonObject payload) {
        super(packetId, packetType, metadata, payload);
    }
}

