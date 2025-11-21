package com.hasirciogluhq.easymcadmin.transport;

import com.hasirciogluhq.easymcadmin.packets.Packet;

public interface TransportListener {
    void onPacket(Packet packet);

    void onDisconnect();

    void onError(Exception e);

    void onConnect();
}
