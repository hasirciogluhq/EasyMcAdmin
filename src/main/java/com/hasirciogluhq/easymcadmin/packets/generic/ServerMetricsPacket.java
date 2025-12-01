package com.hasirciogluhq.easymcadmin.packets.generic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server metrics packet - EVENT type
 * Sent periodically with server metrics (CPU, memory, TPS, etc.)
 */
public class ServerMetricsPacket extends Packet {
    
    public ServerMetricsPacket(Map<String, Object> metrics) {
        super(
            UUID.randomUUID().toString(),
            PacketType.EVENT,
            createMetadata(),
            createPayload(metrics)
        );
    }
    
    private static JsonObject createMetadata() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("action", "server_metrics");
        metadata.addProperty("requires_response", false);
        return metadata;
    }
    
    private static JsonObject createPayload(Map<String, Object> metrics) {
        JsonObject payload = new JsonObject();
        
        for (Map.Entry<String, Object> entry : metrics.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                payload.addProperty(entry.getKey(), (String) value);
            } else if (value instanceof Integer) {
                payload.addProperty(entry.getKey(), (Integer) value);
            } else if (value instanceof Long) {
                payload.addProperty(entry.getKey(), (Long) value);
            } else if (value instanceof Double) {
                payload.addProperty(entry.getKey(), (Double) value);
            } else if (value instanceof Float) {
                payload.addProperty(entry.getKey(), (Float) value);
            } else if (value instanceof Boolean) {
                payload.addProperty(entry.getKey(), (Boolean) value);
            } else if (value instanceof List) {
                JsonArray array = new JsonArray();
                for (Object item : (List<?>) value) {
                    if (item instanceof String) {
                        array.add((String) item);
                    } else if (item instanceof Number) {
                        if (item instanceof Integer) {
                            array.add((Integer) item);
                        } else if (item instanceof Long) {
                            array.add((Long) item);
                        } else if (item instanceof Double) {
                            array.add((Double) item);
                        }
                    }
                }
                payload.add(entry.getKey(), array);
            }
        }
        
        return payload;
    }
}

