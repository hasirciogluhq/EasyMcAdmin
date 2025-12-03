package com.hasirciogluhq.easymcadmin.metrics.server;

import com.sun.management.OperatingSystemMXBean;
import org.bukkit.Bukkit;

import java.lang.management.ManagementFactory;
import java.util.*;

/**
 * Collects server metrics including player info, memory, CPU, and TPS
 */
public class ServerMetricsCollector {

    /**
     * Collect all server metrics
     * 
     * @return Map containing all collected metrics
     */
    public static Map<String, Object> collect() {
        Map<String, Object> data = new HashMap<>();

        // Player info - only count, not list
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();

        // Memory info
        long maxMemory = Runtime.getRuntime().maxMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        // Memory metrics with total, used, and percentage
        long memoryTotalMB = maxMemory / 1024 / 1024;
        long memoryUsedMB = usedMemory / 1024 / 1024;
        double memoryUsedPercentage = maxMemory > 0 ? (usedMemory * 100.0 / maxMemory) : 0.0;

        // CPU info
        double cpuLoad = 0.0;
        try {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            cpuLoad = osBean.getProcessCpuLoad(); // range 0-1
            if (cpuLoad < 0) {
                cpuLoad = 0.0; // Negative values mean unavailable
            }
        } catch (Exception e) {
            // CPU metrics not available
            cpuLoad = 0.0;
        }
        
        // CPU metrics with total (100%), used, and percentage
        double cpuTotal = 100.0; // CPU is always 100% max
        double cpuUsed = cpuLoad * 100.0; // Convert to percentage
        double cpuUsedPercentage = cpuLoad * 100.0; // Same as used for CPU

        // TPS (Paper/Spigot) - use reflection since method may not exist in all
        // implementations
        double tps1m = -1.0;
        try {
            java.lang.reflect.Method getTPSMethod = Bukkit.getServer().getClass().getMethod("getTPS");
            Object tpsObj = getTPSMethod.invoke(Bukkit.getServer());
            if (tpsObj instanceof double[]) {
                double[] tps = (double[]) tpsObj;
                if (tps != null && tps.length > 0) {
                    tps1m = tps[0];
                }
            }
        } catch (Exception e) {
            // TPS not available (not Paper/Spigot or method doesn't exist)
            tps1m = -1.0;
        }
        
        // TPS metrics with total (20), used, and percentage
        double tpsTotal = 20.0; // TPS is always 20 max
        double tpsUsed = tps1m >= 0 ? tps1m : 0.0;
        double tpsUsedPercentage = tps1m >= 0 ? (tps1m * 100.0 / 20.0) : 0.0;

        // Version information
        String serverName = Bukkit.getServer().getName(); // Paper, CraftBukkit, etc.
        String bukkitVersion = Bukkit.getBukkitVersion(); // 1.21.1-R0.1-SNAPSHOT
        String minecraftVersion = getCleanMinecraftVersion(); // 1.21.1

        data.put("onlinePlayers", online);
        data.put("maxPlayers", max);
        
        // Memory metrics (detailed)
        data.put("memory_total_mb", memoryTotalMB);
        data.put("memory_used_mb", memoryUsedMB);
        data.put("memory_used_percentage", memoryUsedPercentage);
        
        // CPU metrics (detailed)
        data.put("cpu_total", cpuTotal);
        data.put("cpu_used", cpuUsed);
        data.put("cpu_used_percentage", cpuUsedPercentage);
        
        // TPS metrics (detailed)
        data.put("tps_total", tpsTotal);
        data.put("tps_used", tpsUsed);
        data.put("tps_used_percentage", tpsUsedPercentage);
        
        // Legacy fields for backward compatibility (deprecated)
        data.put("memoryUsedMB", memoryUsedMB);
        data.put("memoryMaxMB", memoryTotalMB);
        data.put("cpuUsage", cpuUsed);
        data.put("tps", tpsUsed);
        
        data.put("serverName", serverName);
        data.put("minecraftVersion", minecraftVersion);
        data.put("bukkitVersion", bukkitVersion);
        data.put("timestamp", System.currentTimeMillis());

        return data;
    }

    /**
     * Extract clean Minecraft version from Bukkit.getVersion()
     * Example: "git-Paper-424 (MC: 1.21.1)" -> "1.21.1"
     * 
     * @return Clean Minecraft version string
     */
    private static String getCleanMinecraftVersion() {
        try {
            String raw = Bukkit.getVersion(); // e.g., "git-Paper-424 (MC: 1.21.1)"
            int start = raw.indexOf("MC: ");
            if (start == -1) {
                return "unknown";
            }

            start += 4; // Skip "MC: "
            int end = raw.indexOf(")", start);
            if (end == -1) {
                end = raw.length();
            }
            return raw.substring(start, end).trim();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
