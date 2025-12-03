package com.hasirciogluhq.easymcadmin.packet_handlers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packet_handlers.rpc.GeneralRpcHandler;
import com.hasirciogluhq.easymcadmin.packet_handlers.rpc.PlayerRpcHandler;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.RpcErrorPacket;
import com.hasirciogluhq.easymcadmin.transport.TransportManager;

public class RpcPacketHandler {

    private final TransportManager transportManager;

    private final Map<String, Function<Packet, Object>> rpcHandlers = new HashMap<>();

    public RpcPacketHandler(TransportManager tm) {
        this.transportManager = tm;
        registerHandlers();
    }

    private void registerHandlers() {
        // ASYNC handlers
        rpcHandlers.put("plugin.player.request", PlayerRpcHandler::handlePlayerRequest);
        rpcHandlers.put("plugin.player.inventory.request", PlayerRpcHandler::handlePlayerInventoryRequest);
        rpcHandlers.put("plugin.server.console.execute", GeneralRpcHandler::handleConsoleCommandExecute);

        // rpcHandlers.put("plugin.ping", MyHandler::handlePingSync); // Returns Packet
    }

    public void handle(Packet packet) {
        String action = packet.getAction();

        Function<Packet, Object> handler = rpcHandlers.get(action);

        if (handler == null) {
            EasyMcAdmin.getInstance().getLogger()
                    .warning("No RPC handler found for action: " + action);
            sendError(packet, "unknown rpc action: " + action);
            return;
        }

        Object result;

        try {
            // This try-catch only captures errors while CALLING the handler function
            // synchronously.
            // It does not catch errors inside the Future.
            result = handler.apply(packet);
        } catch (Exception e) {
            sendError(packet, "rpc handler threw exception: " + e.getMessage());
            return;
        }

        // 1) ASYNC handler: CompletableFuture<Packet>
        if (result instanceof CompletableFuture<?>) {
            CompletableFuture<?> future = (CompletableFuture<?>) result;

            future.thenAccept(obj -> {
                // Runs if the operation completes SUCCESSFULLY
                if (obj instanceof Packet p) {
                    sendResponse(packet, p);
                } else {
                    sendError(packet, "async rpc returned invalid type");
                }
            }).exceptionally(ex -> {
                // [!] ADDED PART: Runs if the operation FAILS (throws an exception)
                EasyMcAdmin.getInstance().getLogger().warning("Async RPC Error for " + action + ": " + ex.getMessage());
                sendError(packet, "async rpc internal error: " + ex.getMessage());
                return null;
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
