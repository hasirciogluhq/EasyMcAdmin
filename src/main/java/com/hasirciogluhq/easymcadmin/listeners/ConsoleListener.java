package com.hasirciogluhq.easymcadmin.listeners;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.ConsoleOutputPacket;
import com.hasirciogluhq.easymcadmin.websocket.WebSocketManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * Listens to server console output and forwards to backend via WebSocket
 */
public class ConsoleListener implements Listener {
    
    private final WebSocketManager webSocketManager;
    
    public ConsoleListener(EasyMcAdmin plugin, WebSocketManager webSocketManager, String serverId) {
        this.webSocketManager = webSocketManager;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        if (!webSocketManager.isConnected()) {
            return;
        }
        
        // Send command execution to backend via WebSocket packet
        String command = event.getCommand();
        ConsoleOutputPacket packet = new ConsoleOutputPacket("> " + command, "info");
        webSocketManager.sendPacket(packet);
    }
}

