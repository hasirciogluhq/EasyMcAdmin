package com.hasirciogluhq.easymcadmin.transport;

import java.io.IOException;

import org.bukkit.Bukkit;

import com.hasirciogluhq.easymcadmin.packets.Packet;

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
        if (!isAuthenticated() && !packet.isAuthPacket()) {
            Bukkit.getLogger().warning("[EasyMcAdmin] Not authenticated and not an auth packet, skipping packet");
            return;
        }

        if (isAuthenticated() && packet.isAuthPacket()) {
            Bukkit.getLogger().warning("[EasyMcAdmin] Authenticated and is an auth packet, skipping packet");
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
}
