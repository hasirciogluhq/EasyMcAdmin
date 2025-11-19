package com.hasirciogluhq.easymcadmin.commands;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main command handler for Easy MC Admin
 * Routes subcommands to appropriate handlers
 */
public class MainCommand implements CommandExecutor, TabCompleter {
    
    private final Map<String, SubCommand> subCommands;
    
    public MainCommand(EasyMcAdmin plugin) {
        this.subCommands = new HashMap<>();
        
        // Register subcommands
        registerSubCommand("setToken", new SetTokenSubCommand(plugin));
        // Add more subcommands here in the future
    }
    
    private void registerSubCommand(String name, SubCommand subCommand) {
        subCommands.put(name.toLowerCase(), subCommand);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("easymcadmin.use")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);
        
        if (subCommand == null) {
            sender.sendMessage("§cUnknown subcommand: " + args[0]);
            sender.sendMessage("§7Use /easymcadmin help for available commands.");
            return true;
        }
        
        // Check permission for subcommand
        if (!sender.hasPermission(subCommand.getPermission())) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        // Execute subcommand with remaining args
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        
        return subCommand.onCommand(sender, command, label, subArgs);
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Return list of available subcommands
            List<String> completions = new ArrayList<>();
            for (String subCmd : subCommands.keySet()) {
                if (sender.hasPermission(subCommands.get(subCmd).getPermission())) {
                    if (subCmd.toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(subCmd);
                    }
                }
            }
            return completions;
        }
        
        // Delegate tab completion to subcommand
        if (args.length > 1) {
            String subCommandName = args[0].toLowerCase();
            SubCommand subCommand = subCommands.get(subCommandName);
            
            if (subCommand != null && sender.hasPermission(subCommand.getPermission())) {
                String[] subArgs = new String[args.length - 1];
                System.arraycopy(args, 1, subArgs, 0, subArgs.length);
                return subCommand.onTabComplete(sender, command, alias, subArgs);
            }
        }
        
        return new ArrayList<>();
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== Easy MC Admin ===");
        sender.sendMessage("§7Available commands:");
        
        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            if (sender.hasPermission(entry.getValue().getPermission())) {
                sender.sendMessage("§e/easymcadmin " + entry.getKey() + " §7- " + entry.getValue().getDescription());
            }
        }
        
        sender.sendMessage("§7Use §e/easymcadmin <subcommand> help §7for more information.");
    }
}

