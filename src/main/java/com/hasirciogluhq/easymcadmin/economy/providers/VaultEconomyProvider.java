package com.hasirciogluhq.easymcadmin.economy.providers;

import com.hasirciogluhq.easymcadmin.economy.EconomyProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;

/**
 * Vault-based economy provider
 * Uses Vault API to access economy plugins
 */
public class VaultEconomyProvider implements EconomyProvider {
    
    private final String name;
    private final boolean enabled;
    private final String currencyName;
    private final boolean supportsVault;
    private Economy economy;
    
    public VaultEconomyProvider(String name, boolean enabled, String currencyName, boolean supportsVault) {
        this.name = name;
        this.enabled = enabled;
        this.currencyName = currencyName;
        this.supportsVault = supportsVault;
        this.economy = null;
        
        // Try to get Vault economy
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                this.economy = rsp.getProvider();
            }
        }
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
        if (economy != null) {
            try {
                return economy.currencyNameSingular();
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
        if (!isEnabled() || economy == null || player == null) {
            return null;
        }
        
        try {
            double balance = economy.getBalance(player);
            return BigDecimal.valueOf(balance);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public boolean isAvailable() {
        return economy != null;
    }
}

