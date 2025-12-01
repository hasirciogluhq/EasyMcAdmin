package com.hasirciogluhq.easymcadmin.packets.backend.rpc.console;

import com.hasirciogluhq.easymcadmin.packets.generic.Packet;

// server.execute_console_command
public class ServerExecuteCommandRpc extends Packet {
    /**
     * Create a GenericPacket with explicit parameters
     * 
     * @param packetId   Packet ID
     * @param packetType Packet type (EVENT or RPC)
     * @param metadata   Metadata object
     * @param payload    Payload object
     */
    public ServerExecuteCommandRpc(Packet packet) {
        super(packet.getPacketId(), packet.getPacketType(), packet.getMetadata(), packet.getPayload());
    }

    public String getCommand() {
        return payload.get("command").toString();
    }
}
