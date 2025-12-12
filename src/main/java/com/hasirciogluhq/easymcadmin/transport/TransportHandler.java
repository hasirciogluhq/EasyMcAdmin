package com.hasirciogluhq.easymcadmin.transport;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packet_handlers.EventPacketHandler;
import com.hasirciogluhq.easymcadmin.packet_handlers.RpcPacketHandler;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.packets.generic.auth.GenericAuthPacket;
import com.hasirciogluhq.easymcadmin.packets.generic.auth.GenericAuthPacketResponse;
import com.hasirciogluhq.easymcadmin.rpc.RpcStore;

import org.bukkit.Bukkit;

import java.io.IOException;

public class TransportHandler implements TransportListener {
    private TransportManager manager;
    private RpcPacketHandler rpcPacketHandler;
    private EventPacketHandler eventPacketHandler;

    public TransportHandler(TransportManager manager) {
        this.manager = manager;
        this.rpcPacketHandler = new RpcPacketHandler(this.manager);
        this.eventPacketHandler = new EventPacketHandler(this.manager);
    }

    @Override
    public void onPacket(Packet packet) {
        // Handle RPC packets
        if (packet.isRpcResponse()) {
            try {
                RpcStore.getRpcStore().handlePacket(packet);
                return;
            } catch (Exception e) {
                EasyMcAdmin.getInstance().getLogger().warning("Failed to handle rpc response: " + e.getMessage());
                return;
            }
        }

        if (packet.isRpcRequest()) {
            rpcPacketHandler.handle(packet);
        }

        if (!manager.isAuthenticated()) {
            EasyMcAdmin.getInstance().getLogger()
                    .warning("Transport not authenticated, packets are skipped.");
            return;
        }

        // Handle different packet types (EVENT packets)

        if (packet.IsEvent()) {
            eventPacketHandler.handle(packet);
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
        // Transport connected. Authentication and any retries are handled by StartupManager.
        EasyMcAdmin.getInstance().getLogger().info("Transport connected (handler notified)");
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
}
