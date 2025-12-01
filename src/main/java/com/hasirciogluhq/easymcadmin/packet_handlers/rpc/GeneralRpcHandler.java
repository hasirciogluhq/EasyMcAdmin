package com.hasirciogluhq.easymcadmin.packet_handlers.rpc;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.generic.GenericPacket;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.PacketType;
import com.hasirciogluhq.easymcadmin.packets.generic.RpcErrorPacket;

public class GeneralRpcHandler {

    /**
     * Bu handler sadece Packet döndürür.
     * - Hata varsa RpcErrorPacket döner
     * - Başarı varsa GenericPacket döner
     * 
     * Ana RPC router bunu sendRpcResponsePacket ile gönderecek.
     */
    public static CompletableFuture<Packet> handleConsoleCommandExecute(Packet packet) {
        CompletableFuture<Packet> future = new CompletableFuture<>();

        if (!packet.getPayload().has("command")) {
            future.complete(new RpcErrorPacket("Missing 'command' in payload"));
            return future;
        }

        String command = packet.getPayload().get("command").getAsString();

        // Bukkit main thread üzerinde çalıştır
        Bukkit.getScheduler().runTask(EasyMcAdmin.getInstance(), () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                // Success packet
                JsonObject responsePayload = new JsonObject();
                responsePayload.addProperty("output", "Command executed: " + command);

                JsonObject metadata = new JsonObject();
                metadata.addProperty("action", "console_command");

                Packet response = new GenericPacket(
                        UUID.randomUUID().toString(),
                        PacketType.RPC,
                        metadata,
                        responsePayload
                );

                future.complete(response);

            } catch (Exception e) {
                // Error packet
                RpcErrorPacket err = new RpcErrorPacket("Failed to execute command: " + e.getMessage());
                future.complete(err);
            }
        });

        return future;
    }
}
