package com.hasirciogluhq.easymcadmin.transport;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.auth.GenericAuthPacket;
import com.hasirciogluhq.easymcadmin.packets.auth.GenericAuthPacketResponse;
import com.hasirciogluhq.easymcadmin.packets.economy.EconomyConfigPacket;

import org.bukkit.Bukkit;

import java.io.IOException;
import java.util.UUID;

public class TransportHandler implements TransportListener {
    private TransportManager manager;

    public TransportHandler(TransportManager manager) {
        this.manager = manager;
    }

    @Override
    public void onPacket(Packet packet) {
        String action = packet.getMetadata().has("action")
                ? packet.getMetadata().get("action").getAsString()
                : "";

        if (!manager.isAuthenticated()) {
            if (packet.getMetadata().has("action")
                    && packet.getMetadata().get("action").getAsString().equals("plugin.auth.response")) {
                onAuthResponse(packet);
            }
            return;
        }

        // Handle different packet types
        switch (action) {
            case "console_command":
                // Execute command from backend
                if (packet.getPayload().has("command")) {
                    String command = packet.getPayload().get("command").getAsString();
                    Bukkit.getServer().getScheduler().runTask(EasyMcAdmin.getInstance(), () -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    });
                }
                break;

            case "player.request_inventory_sync":
                // Handle inventory sync request from backend (hash mismatch detected)
                if (packet.getPayload().has("player_uuid")) {
                    String playerUUIDStr = packet.getPayload().get("player_uuid").getAsString();
                    try {
                        UUID playerUUID = UUID.fromString(playerUUIDStr);
                        // Use handlePlayerInventorySyncRequest method which calls
                        // sendPlayerInventoryUpdate
                        if (EasyMcAdmin.getInstance().getPlayerListListener() != null) {
                            EasyMcAdmin.getInstance().getPlayerListListener()
                                    .handlePlayerInventorySyncRequest(playerUUID);
                        }
                    } catch (IllegalArgumentException e) {
                        EasyMcAdmin.getInstance().getLogger()
                                .warning("Invalid player UUID in inventory sync request: " + playerUUIDStr);
                    }
                }
                break;

            case "server.economy_config":
                // Handle economy config update from backend
                handleEconomyConfig(packet);
                break;

            default:
                EasyMcAdmin.getInstance().getLogger().info("Unknown packet action: " + action);
        }
    }

    private void onAuthResponse(Packet packet) {
        GenericAuthPacketResponse authResponse = new GenericAuthPacketResponse(packet);
        if (authResponse.isSuccess()) {
            EasyMcAdmin.getInstance().setServerId(authResponse.getServerId());
            onAuthSuccess();
        } else {
            EasyMcAdmin.getInstance().getLogger()
                    .warning("Failed to authenticate: " + authResponse.getMessage());
            onAuthFailure(authResponse.getMessage());
        }
    }

    private void onAuthSuccess() {
        manager.setAuthenticated(true);
        EasyMcAdmin.getInstance().getLogger().info("Transport authenticated");
        EasyMcAdmin.getInstance().onTransportConnectedAndAuthenticated();
    }

    private void onAuthFailure(String message) {
        manager.setAuthenticated(false);
        EasyMcAdmin.getInstance().getLogger().warning("Failed to authenticate: " + message);
    }

    @Override
    public void onDisconnect() {
        manager.setAuthenticated(false);
        EasyMcAdmin.getInstance().getLogger().info("Transport disconnected");
    }

    @Override
    public void onConnect() {
        // Send auth packet first, onTransportConnectedAndAuthenticated will be called
        // after successful authentication in onAuthSuccess()
        Packet authPacket = new GenericAuthPacket(
                EasyMcAdmin.getInstance().getConfig().getString("server.token", "1234567890"));

        Bukkit.getServer().getScheduler().runTaskLater(EasyMcAdmin.getInstance(), () -> {
            try {
                manager.sendPacket(authPacket);
            } catch (IOException e) {
                EasyMcAdmin.getInstance().getLogger()
                        .warning("Error while sending auth packet: " + e.getMessage());
            }
        }, 6L);

    }

    @Override
    public void onError(Exception e) {
        EasyMcAdmin.getInstance().getLogger().warning("Transport error: " + e.getMessage());
        if (e instanceof IOException) {
            // Connection error, disconnect
            try {
                manager.disconnect();
            } catch (IOException ioException) {
                // Ignore disconnect errors
            }
        }
    }

    private void handleEconomyConfig(Packet packet) {
        EconomyConfigPacket economyConfigPacket = new EconomyConfigPacket(packet);
        com.google.gson.JsonObject economyConfig = economyConfigPacket.getEconomyConfig();
        
        // Update economy manager with new config
        if (EasyMcAdmin.getInstance().getEconomyManager() != null) {
            EasyMcAdmin.getInstance().getEconomyManager().updateEconomyConfig(economyConfig);
            // EasyMcAdmin.getInstance().getLogger().info("Economy config updated from backend");
        }
    }
}
