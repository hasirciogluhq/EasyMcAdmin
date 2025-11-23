package com.hasirciogluhq.easymcadmin.transport;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.PacketType;
import com.hasirciogluhq.easymcadmin.packets.auth.GenericAuthPacket;
import com.hasirciogluhq.easymcadmin.packets.auth.GenericAuthPacketResponse;
import com.hasirciogluhq.easymcadmin.packets.economy.EconomyConfigPacket;
import com.hasirciogluhq.easymcadmin.rpc.RpcStore;

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
        // Handle RPC packets first
        if (packet.getPacketType() == PacketType.RPC) {
            // If not authenticated, check if it's auth response
            if (!manager.isAuthenticated()) {
                String action = packet.getMetadata().has("action")
                        ? packet.getMetadata().get("action").getAsString()
                        : "";
                if ("plugin.auth.response".equals(action)) {
                    // Auth response - handle via RPC store
                    try {
                        RpcStore.getRpcStore().handlePacket(packet);
                        return;
                    } catch (Exception e) {
                        EasyMcAdmin.getInstance().getLogger().warning("Failed to handle auth RPC response: " + e.getMessage());
                        onAuthFailure("Failed to handle auth response");
                        return;
                    }
                }
            }
            
            // Handle other RPC packets
            try {
                RpcStore.getRpcStore().handlePacket(packet);
                return; // RPC handled, don't process as regular packet
            } catch (Exception e) {
                EasyMcAdmin.getInstance().getLogger().warning("Failed to handle RPC packet: " + e.getMessage());
                // Continue to regular packet handling if RPC handling fails
            }
        }

        String action = packet.getMetadata().has("action")
                ? packet.getMetadata().get("action").getAsString()
                : "";

        if (!manager.isAuthenticated()) {
            // Only allow auth response packets when not authenticated
            return;
        }

        // Handle different packet types
        switch (action) {
            case "console_command":
                // Execute command from backend
                if (packet.getPayload().has("command")) {
                    String command = packet.getPayload().get("command").getAsString();
                    
                    // If RPC packet, execute command and send response
                    if (packet.getPacketType() == PacketType.RPC) {
                        Bukkit.getServer().getScheduler().runTask(EasyMcAdmin.getInstance(), () -> {
                            try {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                                
                                // Send RPC response
                                com.google.gson.JsonObject responseJson = new com.google.gson.JsonObject();
                                responseJson.addProperty("packet_id", java.util.UUID.randomUUID().toString());
                                responseJson.addProperty("packet_type", "RPC");
                                
                                com.google.gson.JsonObject responseMetadata = new com.google.gson.JsonObject();
                                responseMetadata.addProperty("action", "console_command");
                                responseJson.add("metadata", responseMetadata);
                                
                                com.google.gson.JsonObject responsePayload = new com.google.gson.JsonObject();
                                responsePayload.addProperty("output", "Command executed: " + command);
                                responseJson.add("payload", responsePayload);
                                
                                com.hasirciogluhq.easymcadmin.packets.GenericPacket responsePacket = 
                                    new com.hasirciogluhq.easymcadmin.packets.GenericPacket(responseJson);
                                
                                manager.sendRpcResponsePacket(packet, responsePacket);
                            } catch (Exception e) {
                                // Send error response
                                com.google.gson.JsonObject errorJson = new com.google.gson.JsonObject();
                                errorJson.addProperty("packet_id", java.util.UUID.randomUUID().toString());
                                errorJson.addProperty("packet_type", "RPC");
                                
                                com.google.gson.JsonObject errorMetadata = new com.google.gson.JsonObject();
                                errorMetadata.addProperty("action", "console_command");
                                errorJson.add("metadata", errorMetadata);
                                
                                com.google.gson.JsonObject errorPayload = new com.google.gson.JsonObject();
                                errorPayload.addProperty("error", "Failed to execute command: " + e.getMessage());
                                errorJson.add("payload", errorPayload);
                                
                                com.hasirciogluhq.easymcadmin.packets.GenericPacket errorResponsePacket = 
                                    new com.hasirciogluhq.easymcadmin.packets.GenericPacket(errorJson);
                                
                                try {
                                    manager.sendRpcResponsePacket(packet, errorResponsePacket);
                                } catch (java.io.IOException ioException) {
                                    EasyMcAdmin.getInstance().getLogger().warning("Failed to send error response: " + ioException.getMessage());
                                }
                            }
                        });
                    } else {
                        // EVENT packet - just execute, no response
                        Bukkit.getServer().getScheduler().runTask(EasyMcAdmin.getInstance(), () -> {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                        });
                    }
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
        // Send auth RPC request, onTransportConnectedAndAuthenticated will be called
        // after successful authentication in onAuthSuccess()
        Packet authPacket = new GenericAuthPacket(
                EasyMcAdmin.getInstance().getConfig().getString("server.token", "1234567890"));

        Bukkit.getServer().getScheduler().runTaskLater(EasyMcAdmin.getInstance(), () -> {
            try {
                // Send RPC request and wait for response
                manager.sendRpcRequestPacket(authPacket)
                    .thenAccept(responsePacket -> {
                        // Handle auth response
                        onAuthResponse(responsePacket);
                    })
                    .exceptionally(throwable -> {
                        EasyMcAdmin.getInstance().getLogger()
                                .warning("Auth request failed: " + throwable.getMessage());
                        onAuthFailure(throwable.getMessage());
                        return null;
                    });
            } catch (Exception e) {
                EasyMcAdmin.getInstance().getLogger()
                        .warning("Error while sending auth packet: " + e.getMessage());
                onAuthFailure(e.getMessage());
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
            // EasyMcAdmin.getInstance().getLogger().info("Economy config updated from
            // backend");
        }
    }
}
