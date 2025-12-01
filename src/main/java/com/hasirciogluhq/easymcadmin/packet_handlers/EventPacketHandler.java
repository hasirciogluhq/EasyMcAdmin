package com.hasirciogluhq.easymcadmin.packet_handlers;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packet_handlers.event.PlayerEventHandler;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.transport.TransportManager;

public class EventPacketHandler {
    private final TransportManager transportManager;

    private final Map<String, Function<Packet, Object>> eventHandlers = new HashMap<>();

    public EventPacketHandler(TransportManager tm) {
        this.transportManager = tm;
        registerHandlers();
    }

    private void registerHandlers() {
        eventHandlers.put("plugin.player.inventory.sync", PlayerEventHandler::handlePlayerInventorySync);
    }

    public void handle(Packet packet) {
        String action = packet.getAction();

        Function<Packet, Object> handler = eventHandlers.get(action);

        if (handler == null) {
            EasyMcAdmin.getInstance().getLogger()
                    .info("Dropping event packet without handler: " + action);
            return;
        }

        Object result;

        try {
            result = handler.apply(packet);
        } catch (Exception e) {
            EasyMcAdmin.getInstance().getLogger()
                    .log(Level.SEVERE, "Error handling event action: " + action, e);
            return;
        }
    }
}
