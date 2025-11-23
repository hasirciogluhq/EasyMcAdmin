package com.hasirciogluhq.easymcadmin.rpc;

import com.hasirciogluhq.easymcadmin.packets.Packet;
import java.util.function.Consumer;

/**
 * RPC Handler - stores handler function, timeout, and creation time
 */
public class RpcHandler {
    private final Consumer<Packet> handler;
    private final long timeout; // in milliseconds
    private final long createdAt; // timestamp in milliseconds

    public RpcHandler(Consumer<Packet> handler, long timeout, long createdAt) {
        this.handler = handler;
        this.timeout = timeout;
        this.createdAt = createdAt;
    }

    public Consumer<Packet> getHandler() {
        return handler;
    }

    public long getTimeout() {
        return timeout;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt > timeout;
    }
}

