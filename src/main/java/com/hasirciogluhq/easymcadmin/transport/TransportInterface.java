package com.hasirciogluhq.easymcadmin.transport;

import com.hasirciogluhq.easymcadmin.packets.generic.Packet;

public interface TransportInterface {
    void connect();

    void disconnect();

    boolean isConnected();

    /**
     * Enqueue a packet for sending. Implementations SHOULD avoid blocking the caller
     * thread; writes may be performed asynchronously by a dedicated sender thread.
     *
     * @param packet Packet to send
     */
    void sendPacket(Packet packet);

    void setTransportListener(TransportListener transportListener);
}
