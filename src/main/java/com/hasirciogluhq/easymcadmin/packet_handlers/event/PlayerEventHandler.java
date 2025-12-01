package com.hasirciogluhq.easymcadmin.packet_handlers.event;

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

public class PlayerEventHandler {
    public static void HandlePlayerInventorySync(Packet packet) {
        if (!packet.getPayload().has("player_uuid")) {
            return;
        }

        // Bukkit ana thread'te çalışması gerekiyor
        Bukkit.getScheduler().runTask(EasyMcAdmin.getInstance(), () -> {
            try {
                String playerUUIDStr = packet.getPayload().get("player_uuid").getAsString();
                UUID playerUUID = UUID.fromString(playerUUIDStr);
                // Use handlePlayerInventorySyncRequest method which calls
                // sendPlayerInventoryUpdate
                if (EasyMcAdmin.getInstance().getEventListenerManager().getPlayerListListener() != null) {
                    EasyMcAdmin.getInstance().getEventListenerManager().getPlayerListListener()
                            .handlePlayerInventorySyncRequest(playerUUID);
                }

            } catch (Exception e) {
                EasyMcAdmin.getInstance().getLogger()
                        .warning("Invalid player UUID in inventory sync request: " + playerUUIDStr);
            }
        });
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
