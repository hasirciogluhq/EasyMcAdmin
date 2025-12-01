package com.hasirciogluhq.easymcadmin.packet_handlers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packet_handlers.rpc.GeneralRpcHandler;
import com.hasirciogluhq.easymcadmin.packet_handlers.rpc.PlayerRpcHandler;
import com.hasirciogluhq.easymcadmin.packets.backend.rpc.economy.EconomyConfigPacket;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.RpcErrorPacket;
import com.hasirciogluhq.easymcadmin.transport.TransportManager;

public class EventPacketHandler {
    private final TransportManager transportManager;

    private final Map<String, Function<Packet, Object>> eventHandlers = new HashMap<>();

    public EventPacketHandler(TransportManager tm) {
        this.transportManager = tm;
        registerHandlers();
    }

    public void handleEvent(Packet packet) {
        switch (packet.getAction()) {
            case "player.request_inventory_sync":
                // Handle inventory sync request from backend (hash mismatch detected)
                if (packet.getPayload().has("player_uuid")) {
                    String playerUUIDStr = packet.getPayload().get("player_uuid").getAsString();
                    try {
                        UUID playerUUID = UUID.fromString(playerUUIDStr);
                        // Use handlePlayerInventorySyncRequest method which calls
                        // sendPlayerInventoryUpdate
                        if (EasyMcAdmin.getInstance().getEventListenerManager().getPlayerListListener() != null) {
                            EasyMcAdmin.getInstance().getEventListenerManager().getPlayerListListener()
                                    .handlePlayerInventorySyncRequest(playerUUID);
                        }
                    } catch (IllegalArgumentException e) {
                        EasyMcAdmin.getInstance().getLogger()
                                .warning("Invalid player UUID in inventory sync request: " + playerUUIDStr);
                    }
                }
                break;

            case "server.set_economy_config":
                // Handle economy config update from backend
                handleEconomyConfig(packet);
                break;

            default:
                EasyMcAdmin.getInstance().getLogger().info("Unknown packet action: " + packet.getAction());
        }
    }

    private void handleEconomyConfig(Packet packet) {
        EconomyConfigPacket economyConfigPacket = new EconomyConfigPacket(packet);
        com.google.gson.JsonObject economyConfig = economyConfigPacket.getEconomyConfig();

        // Update economy manager with new config
        if (EasyMcAdmin.getInstance().getEconomyManager() != null) {
            EasyMcAdmin.getInstance().getEconomyManager().updateEconomyConfig(economyConfig);
        }
    }

    private void registerHandlers() {
        // ASYNC handlers
        eventHandlers.put("plugin.player.inventory.sync", PlayerRpcHandler::HandlePlayerRequestRPC);
    }

    public void handleEventPacket(Packet packet) {
        String action = packet.getAction();

        Function<Packet, Object> handler = eventHandlers.get(action);

        if (handler == null) {
            EasyMcAdmin.getInstance().getLogger()
                    .warning("No RPC handler found for action: " + action);
            sendError(packet, "unknown rpc action: " + action);
            return;
        }

        Object result;

        try {
            result = handler.apply(packet);
        } catch (Exception e) {
            sendError(packet, "rpc handler threw exception: " + e.getMessage());
            return;
        }

        // 1) ASYNC handler: CompletableFuture<Packet>
        if (result instanceof CompletableFuture<?>) {
            CompletableFuture<?> future = (CompletableFuture<?>) result;

            future.thenAccept(obj -> {
                if (obj instanceof Packet p) {
                    sendResponse(packet, p);
                } else {
                    sendError(packet, "async rpc returned invalid type");
                }
            });

            return;
        }

        // 2) SYNC handler: directly returned Packet
        if (result instanceof Packet p) {
            sendResponse(packet, p);
            return;
        }

        // 3) Invalid type
        sendError(packet, "rpc handler returned unsupported type: " + result.getClass().getName());
    }

    private void sendResponse(Packet original, Packet response) {
        try {
            transportManager.sendRpcResponsePacket(original, response);
        } catch (Exception e) {
            EasyMcAdmin.getInstance().getLogger()
                    .warning("Failed to send RPC response: " + e.getMessage());
        }
    }

    private void sendError(Packet original, String msg) {
        try {
            transportManager.sendRpcResponsePacket(original, new RpcErrorPacket(msg));
        } catch (Exception ignored) {
        }
    }
}
