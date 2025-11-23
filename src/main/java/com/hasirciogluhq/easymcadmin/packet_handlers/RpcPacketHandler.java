package com.hasirciogluhq.easymcadmin.packet_handlers;

import org.bukkit.Bukkit;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.PacketType;
import com.hasirciogluhq.easymcadmin.rpc.RpcStore;
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

            default:
                break;
        }
    }
}
