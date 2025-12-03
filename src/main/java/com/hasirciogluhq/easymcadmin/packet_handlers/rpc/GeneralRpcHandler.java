package com.hasirciogluhq.easymcadmin.packet_handlers.rpc;

import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.backend.rpc.economy.EconomyConfigPacket;
import com.hasirciogluhq.easymcadmin.packets.generic.GenericPacket;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.PacketType;
import com.hasirciogluhq.easymcadmin.packets.generic.RpcErrorPacket;

public class GeneralRpcHandler {

    /**
     * This handler only returns a Packet.
     * - On error, it returns an RpcErrorPacket
     * - On success, it returns a GenericPacket
     * 
     * The main RPC router will send it with sendRpcResponsePacket.
     */
    public static CompletableFuture<Packet> handleConsoleCommandExecute(Packet packet) {
        CompletableFuture<Packet> future = new CompletableFuture<>();

        if (!packet.getPayload().has("command")) {
            future.complete(new RpcErrorPacket("Missing 'command' in payload"));
            return future;
        }

        String command = packet.getPayload().get("command").getAsString();

        // Run on the Bukkit main thread
        Bukkit.getScheduler().runTask(EasyMcAdmin.getInstance(), () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                // Success packet
                JsonObject responsePayload = new JsonObject();
                responsePayload.addProperty("output", "Command executed: " + command);

                JsonObject metadata = new JsonObject();
                metadata.addProperty("action", "plugin.server.console.execute");

                Packet response = new GenericPacket(
                        UUID.randomUUID().toString(),
                        PacketType.RPC,
                        metadata,
                        responsePayload);

                future.complete(response);

            } catch (Exception e) {
                // Error packet
                RpcErrorPacket err = new RpcErrorPacket("Failed to execute command: " + e.getMessage());
                future.complete(err);
            }
        });

        return future;
    }

    public static Packet handleEconomyConfigSet(Packet packet) {
        EconomyConfigPacket economyConfigPacket = new EconomyConfigPacket(packet);
        com.google.gson.JsonObject economyConfig = economyConfigPacket.getEconomyConfig();

        // Update economy manager with new config
        if (EasyMcAdmin.getInstance().getEconomyManager() != null) {
            EasyMcAdmin.getInstance().getEconomyManager().updateEconomyConfig(economyConfig);
        }

        return EconomyConfigPacket.generateResponse(true);
    }
}
