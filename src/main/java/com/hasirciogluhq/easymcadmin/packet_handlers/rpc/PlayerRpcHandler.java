package com.hasirciogluhq.easymcadmin.packet_handlers.rpc;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.backend.rpc.player.RPCPlayerResponsePacket;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.RpcErrorPacket;
import com.hasirciogluhq.easymcadmin.serializers.player.PlayerDataSerializer;

public class PlayerRpcHandler {

    public static CompletableFuture<Packet> handlePlayerRequest(Packet packet) {
        CompletableFuture<Packet> future = new CompletableFuture<>();

        if (!packet.getPayload().has("player_uuid")) {
            future.complete(new RpcErrorPacket("missing field: player_uuid"));
            return future;
        }

        String playerUUIDStr = packet.getPayload().get("player_uuid").getAsString();

        Bukkit.getScheduler().runTask(EasyMcAdmin.getInstance(), () -> {
            try {
                UUID uuid = UUID.fromString(playerUUIDStr);
                Player p = Bukkit.getPlayer(uuid);

                if (p == null) {
                    future.complete(new RpcErrorPacket("player not found"));
                    return;
                }

                boolean isOnline = p.isOnline();
                JsonObject playerObj = isOnline
                        ? PlayerDataSerializer.getPlayerDetailsPayload(p)
                        : PlayerDataSerializer.getOfflinePlayerDetailsPayload(p);

                Packet res = new RPCPlayerResponsePacket(playerObj, isOnline);
                future.complete(res);

            } catch (Exception e) {
                future.complete(new RpcErrorPacket("internal error: " + e.getMessage()));
            }
        });

        return future;
    }

    public static CompletableFuture<Packet> handlePlayerInventoryRequest(Packet packet) {
        CompletableFuture<Packet> mainFuture = new CompletableFuture<>();

        // Missing UUID
        if (!packet.getPayload().has("player_uuid")) {
            mainFuture.complete(new RpcErrorPacket("missing field: player_uuid"));
            return mainFuture;
        }

        String uuidStr = packet.getPayload().get("player_uuid").getAsString();

        // Enter the main thread to find the player
        Bukkit.getScheduler().runTask(EasyMcAdmin.getInstance(), () -> {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Player p = Bukkit.getPlayer(uuid);

                // Player not found
                if (p == null) {
                    mainFuture.complete(new RpcErrorPacket("player not found"));
                    return;
                }

                // Start the process via PlayerService
                // fullSync: true (request-driven, so we send all data)
                // sendPacket: false (we will return as RPC, don't broadcast)
                EasyMcAdmin.getInstance().getServiceManager().getPlayerService()
                        .SendPlayerInventorySyncEvent(p, true, false)
                        .thenAccept(generatedPacket -> {
                            // Service prepared the packet; set it as the RPC response
                            if (generatedPacket != null) {
                                mainFuture.complete(generatedPacket);
                            } else {
                                mainFuture.complete(new RpcErrorPacket("failed to generate inventory data"));
                            }
                        })
                        .exceptionally(ex -> {
                            mainFuture.complete(new RpcErrorPacket("error in service: " + ex.getMessage()));
                            return null;
                        });

            } catch (Exception e) {
                mainFuture.complete(new RpcErrorPacket("internal error: " + e.getMessage()));
            }
        });

        return mainFuture;
    }
}
