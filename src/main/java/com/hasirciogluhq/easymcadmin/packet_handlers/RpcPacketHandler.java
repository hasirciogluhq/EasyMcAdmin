package com.hasirciogluhq.easymcadmin.packet_handlers;

import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.PacketType;
import com.hasirciogluhq.easymcadmin.packets.player.PlayerInventoryChangedPacket;
import com.hasirciogluhq.easymcadmin.packets.rpc.RpcErrorPacket;
import com.hasirciogluhq.easymcadmin.serializers.player.*;
import com.hasirciogluhq.easymcadmin.transport.TransportManager;

public class RpcPacketHandler {
    private TransportManager transportManager;

    public RpcPacketHandler(TransportManager tm) {
        this.transportManager = tm;
    }

    public void handleRpcRequest(Packet packet) {

        switch (packet.getAction()) {
            case "server.execute_console_command":
                if (packet.getPayload().has("command")) {
                    String command = packet.getPayload().get("command").getAsString();

                    Bukkit.getServer().getScheduler().runTask(EasyMcAdmin.getInstance(), () -> {
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                            // Send RPC response
                            com.google.gson.JsonObject responsePayload = new com.google.gson.JsonObject();
                            responsePayload.addProperty("output", "Command executed: " + command);

                            com.google.gson.JsonObject responseMetadata = new com.google.gson.JsonObject();
                            responseMetadata.addProperty("action", "console_command");

                            com.hasirciogluhq.easymcadmin.packets.GenericPacket responsePacket = new com.hasirciogluhq.easymcadmin.packets.GenericPacket(
                                    java.util.UUID.randomUUID().toString(),
                                    PacketType.RPC,
                                    responseMetadata,
                                    responsePayload);

                            transportManager.sendRpcResponsePacket(packet, responsePacket);
                        } catch (Exception e) {
                            // Send error response
                            com.google.gson.JsonObject errorPayload = new com.google.gson.JsonObject();
                            errorPayload.addProperty("error", "Failed to execute command: " + e.getMessage());

                            com.google.gson.JsonObject errorMetadata = new com.google.gson.JsonObject();
                            errorMetadata.addProperty("action", "console_command");

                            com.hasirciogluhq.easymcadmin.packets.GenericPacket errorResponsePacket = new com.hasirciogluhq.easymcadmin.packets.GenericPacket(
                                    java.util.UUID.randomUUID().toString(),
                                    PacketType.RPC,
                                    errorMetadata,
                                    errorPayload);

                            try {
                                transportManager.sendRpcResponsePacket(packet, errorResponsePacket);
                            } catch (java.io.IOException ioException) {
                                EasyMcAdmin.getInstance().getLogger()
                                        .warning("Failed to send error response: " + ioException.getMessage());
                            }
                        }
                    });
                }
                break;

            case "player.inventory.request":
                if (packet.getPayload().has("player_uuid")) {
                    Bukkit.getLogger().log(Level.INFO, "player.inventory.request RPC Received");
                    String playerUUIDStr = packet.getPayload().get("player_uuid").getAsString();

                    Bukkit.getServer().getScheduler().runTask(EasyMcAdmin.getInstance(), () -> {
                        try {
                            UUID playerUUID = UUID.fromString(playerUUIDStr);
                            // Use handlePlayerInventorySyncRequest method which calls
                            // sendPlayerInventoryUpdate
                            Bukkit.getLogger().log(Level.INFO, "UUID: " + playerUUIDStr);
                            if (EasyMcAdmin.getInstance().getEventListenerManager().getPlayerListListener() != null) {
                                Player p = Bukkit.getPlayer(playerUUID);
                                if (p == null) {
                                    RpcErrorPacket errPacket = new RpcErrorPacket("player not found");
                                    transportManager.sendRpcResponsePacket(packet, errPacket);
                                    return;
                                }

                                Boolean isOnline = (p != null && p.isOnline()) ? true : false;

                                if (!isOnline) {
                                    Bukkit.getLogger().log(Level.INFO, "Player is offline sending error");

                                    RpcErrorPacket errPacket = new RpcErrorPacket("player offline");
                                    transportManager.sendRpcResponsePacket(packet, errPacket);
                                    return;
                                }

                                String inventoryHash = PlayerInventorySerializer
                                        .calculateInventoryHash(p.getInventory());
                                String enderChestHash = PlayerInventorySerializer
                                        .calculateEnderChestHash(p.getEnderChest());
                                JsonObject inventoryData = EasyMcAdmin.getInstance().getEventListenerManager()
                                        .getInventoryChangeListener()
                                        .generatePlayerInventoryData(p, true);
                                PlayerInventoryChangedPacket playerInventoryRequestResponseRpc = new PlayerInventoryChangedPacket(
                                        inventoryHash, enderChestHash, true, inventoryData);

                                transportManager.sendRpcResponsePacket(packet, playerInventoryRequestResponseRpc);

                                // RpcErrorPacket errPacket = new RpcErrorPacket("player offline");
                                // responseRpcPacket = errPacket;
                                Bukkit.getLogger().log(Level.INFO, "Player response packet sent");
                            } else {
                                RpcErrorPacket errPacket = new RpcErrorPacket("internal error");
                                transportManager.sendRpcResponsePacket(packet, errPacket);
                            }

                        } catch (Exception e) {
                            // Send error response
                            Bukkit.getLogger().log(Level.INFO, "Player rpc handling internal error.." + e.getMessage());
                            RpcErrorPacket errPacket = new RpcErrorPacket("internal error");

                            try {
                                transportManager.sendRpcResponsePacket(packet, errPacket);
                            } catch (java.io.IOException ioException) {
                                EasyMcAdmin.getInstance().getLogger()
                                        .warning("Failed to send error response: " + ioException.getMessage());
                            }
                        }
                    });
                }
                break;

            default:
                break;
        }
    }
}
