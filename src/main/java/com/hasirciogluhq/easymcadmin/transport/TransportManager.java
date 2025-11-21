package com.hasirciogluhq.easymcadmin.transport;

import java.io.IOException;

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
}
