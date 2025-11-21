package com.hasirciogluhq.easymcadmin.transport;

import com.hasirciogluhq.easymcadmin.packets.Packet;

public interface TransportInterface {
    void connect();

    void disconnect();

    boolean isConnected();

    void sendPacket(Packet packet);

    void setTransportListener(TransportListener transportListener);
}
