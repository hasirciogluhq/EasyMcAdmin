package com.hasirciogluhq.easymcadmin.commands;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand to set authentication token
 */
public class SetTokenSubCommand implements SubCommand {

    private final EasyMcAdmin plugin;

    public SetTokenSubCommand(EasyMcAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("[EasyMcAdmin] §cUsage: /easymcadmin setToken <token>");
            sender.sendMessage("[EasyMcAdmin] §7Set the authentication token for the plugin.");
            return true;
        }

        if (args.length == 1) {
            String token = args[0];

            if (token.length() < 32) {
                sender.sendMessage("[EasyMcAdmin] §cError: Token must be at least 32 characters long.");
                return true;
            }

            // Set token
            // plugin.getTransportManager().setToken(token);
            plugin.getConfig().set("server.token", token);
            plugin.saveConfig();

            sender.sendMessage("[EasyMcAdmin] §aToken set successfully!");
            sender.sendMessage("[EasyMcAdmin] §7Attempting to connect...");

            // Try to connect immediately
            try {
                plugin.getTransportManager().connect();
            } catch (IOException e) {
                sender.sendMessage("[EasyMcAdmin] §cFailed to connect: " + e.getMessage());
                return true;
            }

            // Check connection status after a short delay
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (plugin.getTransportManager().isConnected()) {
                        sender.sendMessage("[EasyMcAdmin] §aSuccessfully connected to backend server!");
                    } else {
                        sender.sendMessage("[EasyMcAdmin] §cFailed to connect. The plugin will retry automatically.");
                    }
                }
            }.runTaskLater(plugin, 40L); // 2 seconds delay

            return true;
        }

        sender.sendMessage("[EasyMcAdmin] §cUsage: /easymcadmin setToken <token>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return new ArrayList<>(); // No suggestions for token
        }
        return new ArrayList<>();
    }

    @Override
    public String getPermission() {
        return "easymcadmin.admin";
    }

    @Override
    public String getDescription() {
        return "Set the authentication token for the plugin";
    }
}
