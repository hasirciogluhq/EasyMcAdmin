package com.hasirciogluhq.easymcadmin.metrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.PacketType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Schedules and sends server metrics packets via WebSocket
 * Sends metrics every 3 seconds (60 ticks) after initial send
 */
public class MetricsScheduler {

    private final EasyMcAdmin plugin;
    private final WebSocketSender sender;
    private BukkitRunnable task;
    private boolean isRunning;

    /**
     * Interface for sending packets via WebSocket
     */
    public interface WebSocketSender {
        void sendPacket(Packet packet);
        boolean isConnected();
    }

    /**
     * Create a new MetricsScheduler
     * 
     * @param plugin Plugin instance
     * @param sender WebSocket sender interface
     */
    public MetricsScheduler(EasyMcAdmin plugin, WebSocketSender sender) {
        this.plugin = plugin;
        this.sender = sender;
        this.isRunning = false;
    }

    /**
     * Start sending metrics
     * Sends immediately, then every 3 seconds (60 ticks)
     */
    public void start() {
        if (isRunning) {
            return;
        }

        isRunning = true;

        // Send initial metrics immediately
        sendMetrics();

        // Schedule periodic sending (every 3 seconds = 60 ticks)
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isEnabled() || !sender.isConnected()) {
                    stop();
                    return;
                }
                sendMetrics();
            }
        };
        task.runTaskTimer(plugin, 60L, 60L); // Start after 3 seconds, repeat every 3 seconds
    }

    /**
     * Stop sending metrics
     */
    public void stop() {
        isRunning = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Check if scheduler is running
     * 
     * @return true if running
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Collect and send metrics packet
     */
    private void sendMetrics() {
        if (!sender.isConnected()) {
            return;
        }

        try {
            Map<String, Object> metrics = ServerMetricsCollector.collect();
            
            // Create metadata
            JsonObject metadata = new JsonObject();
            metadata.addProperty("action", "server_metrics");
            metadata.addProperty("requires_response", false);
            
            // Create payload from map
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
            
            // Create packet
            Packet packet = new Packet(
                UUID.randomUUID().toString(),
                PacketType.EVENT,
                metadata,
                payload
            ) {
                @Override
                public JsonObject toJson() {
                    JsonObject json = new JsonObject();
                    json.addProperty("packet_id", getPacketId());
                    json.addProperty("packet_type", getPacketType().name());
                    json.add("metadata", getMetadata());
                    json.add("payload", getPayload());
                    json.addProperty("timestamp", getTimestamp());
                    return json;
                }
            };
            
            sender.sendPacket(packet);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send metrics: " + e.getMessage());
        }
    }
}

