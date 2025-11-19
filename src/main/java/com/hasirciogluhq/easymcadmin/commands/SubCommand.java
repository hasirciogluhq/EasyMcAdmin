package com.hasirciogluhq.easymcadmin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * Interface for subcommands
 */
public interface SubCommand extends TabCompleter {
    
    /**
     * Execute the subcommand
     * 
     * @param sender Command sender
     * @param command Command object
     * @param label Command label
     * @param args Command arguments (without subcommand name)
     * @return true if command was handled successfully
     */
    boolean onCommand(CommandSender sender, Command command, String label, String[] args);
    
    /**
     * Get the permission required for this subcommand
     * 
     * @return Permission string
     */
    String getPermission();
    
    /**
     * Get the description of this subcommand
     * 
     * @return Description string
     */
    String getDescription();
}

