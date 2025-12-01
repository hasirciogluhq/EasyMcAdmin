package com.hasirciogluhq.easymcadmin.packets.backend.rpc.economy;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;

/**
 * Economy config packet - EVENT type
 * Received from backend with economy provider configurations
 */
public class EconomyConfigPacket {
    private final Packet packet;
    
    public EconomyConfigPacket(Packet packet) {
        this.packet = packet;
    }
    
    /**
     * Get economy config from packet payload
     * 
     * @return JsonObject with economy_config structure
     */
    public JsonObject getEconomyConfig() {
        if (packet.getPayload().has("economy_config")) {
            return packet.getPayload().get("economy_config").getAsJsonObject();
        }
        return new JsonObject();
    }
    
    /**
     * Get server ID from packet payload
     * 
     * @return Server ID, or -1 if not found
     */
    public long getServerId() {
        if (packet.getPayload().has("server_id")) {
            return packet.getPayload().get("server_id").getAsLong();
        }
        return -1;
    }
}

