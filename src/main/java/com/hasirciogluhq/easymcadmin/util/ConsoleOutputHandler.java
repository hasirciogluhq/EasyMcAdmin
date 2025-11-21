package com.hasirciogluhq.easymcadmin.util;

import com.hasirciogluhq.easymcadmin.packets.ConsoleOutputPacket;
import com.hasirciogluhq.easymcadmin.transport.TransportManager;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

public class ConsoleOutputHandler extends AbstractAppender {

    private final TransportManager transportManager;
    private static boolean sending = false;

    public ConsoleOutputHandler(EasyMcAdmin plugin, TransportManager transportManager) {
        super("EasyMcAdminAppender", null, null, false, Property.EMPTY_ARRAY);
        this.transportManager = transportManager;
    }

    @Override
    public void append(LogEvent event) {
        if (!transportManager.isConnected() || !transportManager.isAuthenticated())
            return;

        if (sending)
            return; // Recursion guard
        sending = true;

        try {
            String message = event.getMessage().getFormattedMessage();
            if (message == null || message.trim().isEmpty())
                return;

            String level = event.getLevel().name().toLowerCase();

            ConsoleOutputPacket packet = new ConsoleOutputPacket(
                    message,
                    level,
                    "console",
                    detectType(message));

            transportManager.sendPacket(packet);

        } catch (Throwable ignored) {

        } finally {
            sending = false;
        }
    }

    private String detectType(String msg) {
        String m = msg.toLowerCase();

        if (m.contains("exception") || m.contains("error"))
            return "error";
        if (m.startsWith("> "))
            return "command";
        if (m.contains("starting") || m.contains("done") || m.contains("stopping"))
            return "server";

        return "log";
    }
}
