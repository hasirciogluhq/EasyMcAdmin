package com.hasirciogluhq.easymcadmin.transport;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.rpc.RpcHandler;
import com.hasirciogluhq.easymcadmin.rpc.RpcStore;

public class TransportManager {
    private final TransportInterface transport;
    private boolean isAuthenticated = false;

    public TransportManager(TransportInterface transport) {
        this.transport = transport;
        this.isAuthenticated = false;
    }

    public void connect() throws IOException {
        transport.connect();
    }

    public void disconnect() throws IOException {
        transport.disconnect();
    }

    public boolean isConnected() {
        return transport.isConnected();
    }

    public void sendPacket(Packet packet) throws IOException {
        // Only allow auth packets if not authenticated
        if (!isAuthenticated() && !packet.isAuthPacket()) {
            return;
        }

        // Don't allow auth packets if already authenticated
        if (isAuthenticated() && packet.isAuthPacket()) {
            return;
        }

        try {
            transport.sendPacket(packet);
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to send packet", e);
        }
    }

    public void setTransportListener(TransportListener transportListener) {
        transport.setTransportListener(transportListener);
    }

    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    public void setAuthenticated(boolean isAuthenticated) {
        this.isAuthenticated = isAuthenticated;
    }

    /**
     * Send RPC request packet and wait for response
     * Similar to Go backend's SendRpcRequestPacket
     * 
     * @param packet RPC request packet
     * @return CompletableFuture that completes with response packet or timeout exception
     */
    public CompletableFuture<Packet> sendRpcRequestPacket(Packet packet) {
        CompletableFuture<Packet> future = new CompletableFuture<>();
        
        // Register handler in RPC store
        RpcStore rpcStore = RpcStore.getRpcStore();
        rpcStore.registerHandler(packet, new RpcHandler(
            responsePacket -> {
                rpcStore.removeHandler(packet);
                future.complete(responsePacket);
            },
            30000, // 30 seconds timeout
            System.currentTimeMillis()
        ));

        // Send the packet
        try {
            sendPacket(packet);
        } catch (IOException e) {
            rpcStore.removeHandler(packet);
            future.completeExceptionally(e);
            return future;
        }

        // Set timeout
        future.orTimeout(10, TimeUnit.SECONDS).whenComplete((result, throwable) -> {
            if (throwable instanceof TimeoutException) {
                rpcStore.removeHandler(packet);
                future.completeExceptionally(new TimeoutException("RPC request timed out"));
            }
        });

        return future;
    }

    /**
     * Send RPC response packet
     * Similar to Go backend's SendRpcResponsePacket
     * 
     * @param requestPacket Original request packet
     * @param responsePacket Response packet to send
     * @throws IOException if sending fails
     */
    public void sendRpcResponsePacket(Packet requestPacket, Packet responsePacket) throws IOException {
        // Set RPC ID in response packet metadata
        responsePacket.getMetadata().addProperty("rpc_id", requestPacket.getPacketId());
        sendPacket(responsePacket);
    }
}
