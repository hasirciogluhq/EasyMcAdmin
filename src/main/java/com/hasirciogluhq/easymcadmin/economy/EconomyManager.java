package com.hasirciogluhq.easymcadmin.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.economy.providers.CustomEconomyProvider;
import com.hasirciogluhq.easymcadmin.economy.providers.VaultEconomyProvider;
import com.hasirciogluhq.easymcadmin.economy.providers.iConomyEconomyProvider;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages multiple economy providers
 * Handles provider registration, balance collection, and updates
 */
public class EconomyManager {
    
    private final Map<String, EconomyProvider> providers;
    
    public EconomyManager() {
        this.providers = new HashMap<>();
    }
    
    /**
     * Update economy configuration from backend
     * 
     * @param economyConfig Economy config JSON object
     */
    public void updateEconomyConfig(JsonObject economyConfig) {
        providers.clear();
        
        if (!economyConfig.has("providers")) {
            return;
        }
        
        JsonArray providersArray = economyConfig.getAsJsonArray("providers");
        for (int i = 0; i < providersArray.size(); i++) {
            JsonObject providerObj = providersArray.get(i).getAsJsonObject();
            
            String name = providerObj.has("name") ? providerObj.get("name").getAsString() : "";
            boolean enabled = providerObj.has("enabled") && providerObj.get("enabled").getAsBoolean();
            String currencyName = providerObj.has("currency_name") && !providerObj.get("currency_name").isJsonNull()
                    ? providerObj.get("currency_name").getAsString() : null;
            boolean supportsVault = providerObj.has("supports_vault") && providerObj.get("supports_vault").getAsBoolean();
            
            if (name.isEmpty()) {
                continue;
            }
            
            EconomyProvider provider = createProvider(providerObj, name, enabled, currencyName, supportsVault);
            if (provider != null) {
                providers.put(name, provider);
            }
        }
    }
    
    /**
     * Create economy provider from config
     * If supports_vault is true, always use VaultEconomyProvider
     * Otherwise, use specific provider implementations or custom reflection-based provider
     */
    private EconomyProvider createProvider(JsonObject providerObj, String name, boolean enabled, String currencyName, boolean supportsVault) {
        String nameLower = name.toLowerCase();
        
        // If provider supports Vault, always use Vault (Vault hooks into Essentials, iConomy, etc.)
        if (supportsVault) {
            return new VaultEconomyProvider(name, enabled, currencyName, supportsVault);
        }
        
        // If no Vault support, check for specific providers
        if (nameLower.equals("iconomy")) {
            return new iConomyEconomyProvider(name, enabled, currencyName, supportsVault);
        } else if (name.endsWith("_custom")) {
            // Custom provider without Vault support - use reflection
            if (!providerObj.has("config")) {
                return null;
            }
            
            JsonObject configObj = providerObj.getAsJsonObject("config");
            
            // Parse plugin info
            String pluginId = configObj.has("plugin_id") ? configObj.get("plugin_id").getAsString() : "";
            String pluginName = configObj.has("plugin_name") ? configObj.get("plugin_name").getAsString() : "";
            String javaClassName = configObj.has("java_class_name") ? configObj.get("java_class_name").getAsString() : "";
            String javaClassPath = configObj.has("java_class_path") ? configObj.get("java_class_path").getAsString() : "";
            
            // Parse functions
            Map<String, String> functions = new HashMap<>();
            if (configObj.has("functions")) {
                JsonObject functionsObj = configObj.getAsJsonObject("functions");
                for (String key : functionsObj.keySet()) {
                    functions.put(key, functionsObj.get(key).getAsString());
                }
            }
            
            // Parse parameters
            Map<String, String> parameters = new HashMap<>();
            if (configObj.has("parameters")) {
                JsonObject paramsObj = configObj.getAsJsonObject("parameters");
                for (String key : paramsObj.keySet()) {
                    parameters.put(key, paramsObj.get(key).getAsString());
                }
            }
            
            CustomEconomyProvider.CustomProviderConfig customConfig = new CustomEconomyProvider.CustomProviderConfig(
                    pluginId, pluginName, javaClassName, javaClassPath, functions, parameters);
            
            return new CustomEconomyProvider(name, enabled, currencyName, supportsVault, customConfig);
        }
        
        // Unknown provider without Vault support - not supported
        return null;
    }
    
    /**
     * Get all balances for a player from all enabled providers
     * 
     * @param player Player to get balances for
     * @return List of balance entries (provider -> amount)
     */
    public List<PlayerBalanceEntry> getPlayerBalances(OfflinePlayer player) {
        List<PlayerBalanceEntry> balances = new ArrayList<>();
        
        for (EconomyProvider provider : providers.values()) {
            if (!provider.isEnabled()) {
                continue;
            }
            
            BigDecimal balance = provider.getBalance(player);
            if (balance != null) {
                balances.add(new PlayerBalanceEntry(
                        provider.getName(),
                        balance,
                        provider.getCurrencyName()
                ));
            }
        }
        
        return balances;
    }
    
    /**
     * Get all enabled providers
     * 
     * @return List of enabled providers
     */
    public List<EconomyProvider> getEnabledProviders() {
        List<EconomyProvider> enabled = new ArrayList<>();
        for (EconomyProvider provider : providers.values()) {
            if (provider.isEnabled()) {
                enabled.add(provider);
            }
        }
        return enabled;
    }
    
    /**
     * Player balance entry
     */
    public static class PlayerBalanceEntry {
        private final String provider;
        private final BigDecimal amount;
        private final String currencyName;
        
        public PlayerBalanceEntry(String provider, BigDecimal amount, String currencyName) {
            this.provider = provider;
            this.amount = amount;
            this.currencyName = currencyName;
        }
        
        public String getProvider() { return provider; }
        public BigDecimal getAmount() { return amount; }
        public String getCurrencyName() { return currencyName; }
    }
}

