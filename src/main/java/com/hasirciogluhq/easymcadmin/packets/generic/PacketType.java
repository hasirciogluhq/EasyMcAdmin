package com.hasirciogluhq.easymcadmin.packets.generic;

/**
 * Packet types - determines if packet is EVENT or RPC
 */
public enum PacketType {
    /**
     * Event packet - one-way notification, no response expected
     */
    EVENT,
    
    /**
     * RPC packet - request/response pattern, expects response
     */
    RPC
}

