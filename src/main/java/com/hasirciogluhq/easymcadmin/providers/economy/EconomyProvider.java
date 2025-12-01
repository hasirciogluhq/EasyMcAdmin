package com.hasirciogluhq.easymcadmin.providers.economy;

import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;

/**
 * Interface for economy providers
 * All economy providers must implement this interface
 */
public interface EconomyProvider {
    
    /**
     * Get provider name
     * 
     * @return Provider name (e.g., "essentials", "iconomy", "myplugin_custom")
     */
    String getName();
    
    /**
     * Check if provider is enabled
     * 
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();
    
    /**
     * Get currency name for this provider
     * 
     * @return Currency name, or null if not available
     */
    String getCurrencyName();
    
    /**
     * Check if provider supports Vault hooks
     * 
     * @return true if supports Vault, false otherwise
     */
    boolean supportsVault();
    
    /**
     * Get player balance
     * 
     * @param player Player to get balance for
     * @return Balance amount, or null if error
     */
    BigDecimal getBalance(OfflinePlayer player);
    
    /**
     * Check if provider is available/loaded
     * 
     * @return true if available, false otherwise
     */
    boolean isAvailable();
}

