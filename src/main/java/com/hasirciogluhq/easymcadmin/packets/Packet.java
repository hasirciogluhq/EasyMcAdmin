package com.hasirciogluhq.easymcadmin.packets;

import com.google.gson.JsonObject;

/**
 * Base class for packets between plugin and server
 * Every packet has:
 * - packet_id: Unique identifier for the packet
 * - packet_type: EVENT or RPC (determined by metadata)
 * - metadata: Additional information to determine packet behavior
 * - payload: Actual data
 */
public abstract class Packet {

    protected String packetId;
    protected PacketType packetType;
    protected JsonObject metadata;
    protected JsonObject payload;
    protected long timestamp;

    public Packet(String packetId, PacketType packetType, JsonObject metadata, JsonObject payload) {
        this.packetId = packetId;
        this.packetType = packetType;
        this.metadata = metadata != null ? metadata : new JsonObject();
        this.payload = payload != null ? payload : new JsonObject();
        this.timestamp = System.currentTimeMillis() / 1000;
    }

    /**
     * Convert packet to JSON
     * Server will use packet_id and metadata to determine if it's EVENT or RPC
     * 
     * @return JsonObject representation
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("packet_id", packetId);
        json.addProperty("packet_type", packetType.name());
        json.add("metadata", metadata);
        json.add("payload", payload);
        json.addProperty("timestamp", timestamp);
        return json;
    }

    public boolean IsRpc() {
        return packetType.equals(PacketType.RPC);
    }

    public boolean isRpcRequest() {
        return packetType == PacketType.RPC &&
                (!metadata.has("rpc_id") || metadata.get("rpc_id").getAsString().isEmpty());
    }

    public boolean isRpcResponse() {
        return packetType == PacketType.RPC &&
                (metadata.has("rpc_id") && !metadata.get("rpc_id").getAsString().isEmpty());
    }

    public boolean IsEvent() {
        return packetType.equals(PacketType.EVENT);
    }

    public String getAction() {
        return metadata.has("action") ? metadata.get("action").getAsString() : "";
    }

    /**
     * Get packet ID
     * 
     * @return Packet ID
     */
    public String getPacketId() {
        return packetId;
    }

    /**
     * Get packet type (EVENT or RPC)
     * 
     * @return Packet type
     */
    public PacketType getPacketType() {
        return packetType;
    }

    /**
     * Get metadata
     * 
     * @return Metadata object
     */
    public JsonObject getMetadata() {
        return metadata;
    }

    /**
     * Get payload
     * 
     * @return Payload object
     */
    public JsonObject getPayload() {
        return payload;
    }

    /**
     * Get timestamp
     * 
     * @return Timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    public boolean isAuthPacket() {
        return metadata.has("action") && metadata.get("action").getAsString().equals("plugin.auth.request");
    }
}
