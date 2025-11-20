package com.hasirciogluhq.easymcadmin.metrics;

import com.sun.management.OperatingSystemMXBean;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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

        // Player info
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        List<String> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.add(player.getName());
        }

        // Memory info
        long maxMemory = Runtime.getRuntime().maxMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;

        // CPU info
        double cpuLoad = 0.0;
        try {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            cpuLoad = osBean.getProcessCpuLoad(); // 0-1 arası
            if (cpuLoad < 0) {
                cpuLoad = 0.0; // Negative values mean unavailable
            }
        } catch (Exception e) {
            // CPU metrics not available
            cpuLoad = 0.0;
        }

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

        // Version information
        String serverName = Bukkit.getServer().getName(); // Paper, CraftBukkit, etc.
        String bukkitVersion = Bukkit.getBukkitVersion(); // 1.21.1-R0.1-SNAPSHOT
        String minecraftVersion = getCleanMinecraftVersion(); // 1.21.1

        data.put("onlinePlayers", online);
        data.put("maxPlayers", max);
        data.put("playerList", players);
        data.put("memoryUsedMB", usedMemory / 1024 / 1024);
        data.put("memoryMaxMB", maxMemory / 1024 / 1024);
        data.put("cpuUsage", cpuLoad * 100.0);
        data.put("tps", tps1m);
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
