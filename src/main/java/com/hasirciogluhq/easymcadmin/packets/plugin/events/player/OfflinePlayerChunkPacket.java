package com.hasirciogluhq.easymcadmin.packets.plugin.events.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.PacketType;

import java.util.UUID;

/**
 * Player chunk packet - EVENT type
 * Sent when sending player list in chunks
 */
public class OfflinePlayerChunkPacket extends Packet {

    public OfflinePlayerChunkPacket(int chunkIndex, int totalChunks, boolean isLastChunk, JsonArray players) {
        super(
                UUID.randomUUID().toString(),
                PacketType.EVENT,
                createMetadata(),
                createPayload(chunkIndex, totalChunks, isLastChunk, players));
    }

    private static JsonObject createMetadata() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("action", "player.chunk");
        metadata.addProperty("requires_response", false);
        return metadata;
    }

    private static JsonObject createPayload(int chunkIndex, int totalChunks, boolean isLastChunk, JsonArray players) {
        JsonObject payload = new JsonObject();
        payload.addProperty("chunk_index", chunkIndex);
        payload.addProperty("total_chunks", totalChunks);
        payload.addProperty("is_last_chunk", isLastChunk);
        payload.add("players", players);
        return payload;
    }
}
