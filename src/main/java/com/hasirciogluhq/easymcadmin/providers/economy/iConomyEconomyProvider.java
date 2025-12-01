package com.hasirciogluhq.easymcadmin.providers.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;

/**
 * iConomy economy provider
 * Directly accesses iConomy API
 */
public class iConomyEconomyProvider implements EconomyProvider {
    
    private final String name;
    private final boolean enabled;
    private final String currencyName;
    private final boolean supportsVault;
    private Plugin iconomyPlugin;
    
    public iConomyEconomyProvider(String name, boolean enabled, String currencyName, boolean supportsVault) {
        this.name = name;
        this.enabled = enabled;
        this.currencyName = currencyName;
        this.supportsVault = supportsVault;
        this.iconomyPlugin = Bukkit.getServer().getPluginManager().getPlugin("iConomy");
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled && isAvailable();
    }
    
    @Override
    public String getCurrencyName() {
        if (currencyName != null && !currencyName.isEmpty()) {
            return currencyName;
        }
        // Try to get from iConomy config
        if (iconomyPlugin != null) {
            try {
                // iConomy API access
                return null; // iConomy doesn't have a standard currency name method
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
    
    @Override
    public boolean supportsVault() {
        return supportsVault;
    }
    
    @Override
    public BigDecimal getBalance(OfflinePlayer player) {
        if (!isEnabled() || iconomyPlugin == null || player == null) {
            return null;
        }
        
        try {
            // iConomy API access
            // This would need iConomy API implementation
            // For now, return null as iConomy API is not standard
        } catch (Exception e) {
            // iConomy API not available or error
        }
        
        return null;
    }
    
    @Override
    public boolean isAvailable() {
        return iconomyPlugin != null && iconomyPlugin.isEnabled();
    }
}

