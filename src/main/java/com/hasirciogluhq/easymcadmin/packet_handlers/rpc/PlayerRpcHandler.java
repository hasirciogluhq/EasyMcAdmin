package com.hasirciogluhq.easymcadmin.packet_handlers.rpc;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.backend.rpc.inventory.PlayerInventoryResponse;
import com.hasirciogluhq.easymcadmin.packets.backend.rpc.player.RPCPlayerResponsePacket;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.RpcErrorPacket;
import com.hasirciogluhq.easymcadmin.serializers.player.PlayerDataSerializer;
import com.hasirciogluhq.easymcadmin.serializers.player.PlayerInventorySerializer;

public class PlayerRpcHandler {
    public static CompletableFuture<Packet> HandlePlayerRequestRPC(Packet packet) {
        CompletableFuture<Packet> future = new CompletableFuture<>();

        if (!packet.getPayload().has("player_uuid")) {
            future.complete(new RpcErrorPacket("missing field: player_uuid"));
            return future;
        }

        String playerUUIDStr = packet.getPayload().get("player_uuid").getAsString();

        // Bukkit ana thread'te çalışması gerekiyor
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
        CompletableFuture<Packet> future = new CompletableFuture<>();

        // Missing UUID
        if (!packet.getPayload().has("player_uuid")) {
            future.complete(new RpcErrorPacket("missing field: player_uuid"));
            return future;
        }

        String uuidStr = packet.getPayload().get("player_uuid").getAsString();

        Bukkit.getScheduler().runTask(EasyMcAdmin.getInstance(), () -> {
            try {
                UUID uuid = UUID.fromString(uuidStr);

                // Listener check
                if (EasyMcAdmin.getInstance().getEventListenerManager().getInventoryChangeListener() == null) {
                    future.complete(new RpcErrorPacket("internal error"));
                    return;
                }

                Player p = Bukkit.getPlayer(uuid);

                // Player not found
                if (p == null) {
                    future.complete(new RpcErrorPacket("player not found"));
                    return;
                }

                boolean online = p.isOnline();

                // Offline error
                if (!online) {
                    future.complete(new RpcErrorPacket("player offline"));
                    return;
                }

                // Calculate hashes
                String invHash = PlayerInventorySerializer.calculateInventoryHash(p.getInventory());
                String enderHash = PlayerInventorySerializer.calculateEnderChestHash(p.getEnderChest());

                // Inventory data payload
                JsonObject inventoryData = EasyMcAdmin.getInstance()
                        .getEventListenerManager()
                        .getInventoryChangeListener()
                        .generatePlayerInventoryData(p, true);

                // Build response packet
                Packet res = new PlayerInventoryResponse(invHash, enderHash, true, inventoryData);

                future.complete(res);

            } catch (Exception e) {
                future.complete(new RpcErrorPacket("internal error: " + e.getMessage()));
            }
        });

        return future;
    }
}
