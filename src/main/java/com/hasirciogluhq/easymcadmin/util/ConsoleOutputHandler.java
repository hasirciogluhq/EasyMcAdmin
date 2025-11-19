package com.hasirciogluhq.easymcadmin.util;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.ConsoleOutputPacket;
import com.hasirciogluhq.easymcadmin.websocket.WebSocketManager;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Custom log handler that intercepts console output and sends to backend via WebSocket
 */
public class ConsoleOutputHandler extends Handler {
    
    private final WebSocketManager webSocketManager;
    private final EasyMcAdmin plugin;
    
    public ConsoleOutputHandler(EasyMcAdmin plugin, WebSocketManager webSocketManager) {
        this.plugin = plugin;
        this.webSocketManager = webSocketManager;
    }
    
    @Override
    public void publish(LogRecord record) {
        if (!plugin.getConfig().getBoolean("websocket.enabled", true)) {
            return;
        }
        
        if (!webSocketManager.isConnected()) {
            return;
        }
        
        // Format message
        String message = getFormatter().formatMessage(record);
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        
        // Map log level
        String level = mapLogLevel(record.getLevel());
        
        // Create and send console output packet
        ConsoleOutputPacket packet = new ConsoleOutputPacket(message, level);
        webSocketManager.sendPacket(packet);
    }
    
    @Override
    public void flush() {
        // Nothing to flush
    }
    
    @Override
    public void close() throws SecurityException {
        // Nothing to close
    }
    
    /**
     * Map Java log level to plugin log level
     */
    private String mapLogLevel(java.util.logging.Level level) {
        if (level == java.util.logging.Level.SEVERE) {
            return "error";
        } else if (level == java.util.logging.Level.WARNING) {
            return "warn";
        } else if (level == java.util.logging.Level.INFO) {
            return "info";
        } else if (level == java.util.logging.Level.FINE || 
                   level == java.util.logging.Level.FINER || 
                   level == java.util.logging.Level.FINEST) {
            return "debug";
        }
        return "info";
    }
}

