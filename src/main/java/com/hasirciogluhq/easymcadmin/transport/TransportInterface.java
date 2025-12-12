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

    /**
     * Inform the transport about current authentication state. Implementations
     * may use this to filter incoming/outgoing packets when unauthenticated.
     * Default implementation is a no-op for transports that don't need it.
     */
    default void setAuthenticated(boolean authenticated) {
        // no-op by default
    }
}
