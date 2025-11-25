package com.hasirciogluhq.easymcadmin.metrics;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.metrics.server.ServerMetricsCollector;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.ServerMetricsPacket;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;

/**
 * Schedules and sends server metrics packets via Transport
 * Sends metrics every 3 seconds (60 ticks) after initial send
 */
public class MetricsScheduler {

    private final EasyMcAdmin plugin;
    private final TransportSender sender;
    private BukkitRunnable task;
    private boolean isRunning;

    /**
     * Interface for sending packets via Transport
     */
    public interface TransportSender {
        void sendPacket(Packet packet);

        boolean isConnected();

        boolean isAuthenticated();
    }

    /**
     * Create a new MetricsScheduler
     * 
     * @param plugin Plugin instance
     * @param sender Transport sender interface
     */
    public MetricsScheduler(EasyMcAdmin plugin, TransportSender sender) {
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
                if (!plugin.isEnabled() || !sender.isConnected() || !sender.isAuthenticated()) {
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
        if (!sender.isConnected() || !sender.isAuthenticated()) {
            return;
        }

        try {
            Map<String, Object> metrics = ServerMetricsCollector.collect();
            Packet packet = new ServerMetricsPacket(metrics);
            sender.sendPacket(packet);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send metrics: " + e.getMessage());
        }
    }
}
