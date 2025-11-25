package com.hasirciogluhq.easymcadmin.packets.plugin.events;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.PacketType;

import java.util.UUID;

public class PlayerStatsUpdateEventPacket extends Packet {

    public PlayerStatsUpdateEventPacket(String statsHash, boolean fullSync, String previousHash, JsonObject playerData) {
        super(
                UUID.randomUUID().toString(),
                PacketType.EVENT,
                createMetadata(statsHash, fullSync, previousHash),
                createPayload(playerData));
    }

    private static JsonObject createMetadata(String statsHash, boolean fullSync, String previousHash) {
        // Include hashes to let backend validate or request resyncs
        JsonObject metadata = new JsonObject();
        metadata.addProperty("action", "player.stats.updated");
        metadata.addProperty("requires_response", false);
        metadata.addProperty("stats_hash", statsHash);
        metadata.addProperty("full_sync", fullSync);
        if (previousHash != null && !previousHash.isEmpty()) {
            metadata.addProperty("previous_hash", previousHash);
        }
        return metadata;
    }

    private static JsonObject createPayload(JsonObject playerData) {
        JsonObject payload = new JsonObject();
        payload.add("player", playerData);
        return payload;
    }
}
