package com.hasirciogluhq.easymcadmin.economy.providers;

import com.hasirciogluhq.easymcadmin.economy.EconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Custom economy provider
 * Uses reflection to call custom plugin methods
 */
public class CustomEconomyProvider implements EconomyProvider {
    
    private final String name;
    private final boolean enabled;
    private final String currencyName;
    private final boolean supportsVault;
    private final CustomProviderConfig config;
    
    private Plugin targetPlugin;
    private Class<?> targetClass;
    private Map<String, Method> functionCache;
    
    public CustomEconomyProvider(String name, boolean enabled, String currencyName, boolean supportsVault, CustomProviderConfig config) {
        this.name = name;
        this.enabled = enabled;
        this.currencyName = currencyName;
        this.supportsVault = supportsVault;
        this.config = config;
        this.functionCache = new HashMap<>();
        
        initializeProvider();
    }
    
    private void initializeProvider() {
        if (config == null) {
            return;
        }
        
        // Get plugin
        targetPlugin = Bukkit.getServer().getPluginManager().getPlugin(config.getPluginName());
        if (targetPlugin == null) {
            return;
        }
        
        // Load class
        try {
            ClassLoader classLoader = targetPlugin.getClass().getClassLoader();
            targetClass = classLoader.loadClass(config.getJavaClassName());
        } catch (ClassNotFoundException e) {
            return;
        }
        
        // Cache methods
        cacheMethods();
    }
    
    private void cacheMethods() {
        if (targetClass == null || config.getFunctions() == null) {
            return;
        }
        
        Method[] methods = targetClass.getMethods();
        Map<String, String> functionMappings = config.getFunctions();
        
        for (Map.Entry<String, String> entry : functionMappings.entrySet()) {
            String requiredFunction = entry.getKey(); // e.g., "getBalance"
            String customFunctionName = entry.getValue(); // e.g., "getPlayerBalance"
            
            // Find method by name
            for (Method method : methods) {
                if (method.getName().equals(customFunctionName)) {
                    functionCache.put(requiredFunction, method);
                    break;
                }
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
        return currencyName;
    }
    
    @Override
    public boolean supportsVault() {
        return supportsVault;
    }
    
    @Override
    public BigDecimal getBalance(OfflinePlayer player) {
        if (!isEnabled() || targetClass == null || player == null) {
            return null;
        }
        
        Method getBalanceMethod = functionCache.get("getBalance");
        if (getBalanceMethod == null) {
            return null;
        }
        
        try {
            // Get instance (static or instance method)
            Object instance = getInstance();
            
            // Prepare parameters based on config
            Object[] params = prepareParameters(player, getBalanceMethod);
            
            // Invoke method
            Object result = getBalanceMethod.invoke(instance, params);
            
            // Convert result to BigDecimal
            if (result instanceof Number) {
                return BigDecimal.valueOf(((Number) result).doubleValue());
            } else if (result instanceof String) {
                return new BigDecimal((String) result);
            }
        } catch (Exception e) {
            // Method invocation failed
        }
        
        return null;
    }
    
    private Object getInstance() {
        // Try to get instance from plugin
        // This depends on the plugin's architecture
        try {
            // Try static method or singleton pattern
            Method getInstance = targetClass.getMethod("getInstance");
            return getInstance.invoke(null);
        } catch (Exception e) {
            // Try plugin instance
            if (targetPlugin != null) {
                return targetPlugin;
            }
        }
        return null;
    }
    
    private Object[] prepareParameters(OfflinePlayer player, Method method) {
        if (config.getParameters() == null) {
            return new Object[0];
        }
        
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] params = new Object[paramTypes.length];
        
        Map<String, String> parameterMappings = config.getParameters();
        int paramIndex = 0;
        
        for (Map.Entry<String, String> entry : parameterMappings.entrySet()) {
            if (paramIndex >= paramTypes.length) {
                break;
            }
            
            String paramType = entry.getValue(); // "uuid", "username", "amount"
            Class<?> expectedType = paramTypes[paramIndex];
            
            if (paramType.equals("uuid")) {
                if (expectedType == UUID.class) {
                    params[paramIndex] = player.getUniqueId();
                } else if (expectedType == String.class) {
                    params[paramIndex] = player.getUniqueId().toString();
                }
            } else if (paramType.equals("username")) {
                if (expectedType == String.class) {
                    params[paramIndex] = player.getName();
                } else if (expectedType == OfflinePlayer.class) {
                    params[paramIndex] = player;
                }
            } else if (paramType.equals("amount")) {
                // Amount is typically not used in getBalance, but included for completeness
                params[paramIndex] = 0.0;
            }
            
            paramIndex++;
        }
        
        return params;
    }
    
    @Override
    public boolean isAvailable() {
        return targetPlugin != null && targetPlugin.isEnabled() && targetClass != null;
    }
    
    /**
     * Custom provider configuration
     */
    public static class CustomProviderConfig {
        private final String pluginId;
        private final String pluginName;
        private final String javaClassName;
        private final String javaClassPath;
        private final Map<String, String> functions; // required function -> custom function name
        private final Map<String, String> parameters; // param ID -> param type
        
        public CustomProviderConfig(String pluginId, String pluginName, String javaClassName, String javaClassPath,
                                   Map<String, String> functions, Map<String, String> parameters) {
            this.pluginId = pluginId;
            this.pluginName = pluginName;
            this.javaClassName = javaClassName;
            this.javaClassPath = javaClassPath;
            this.functions = functions;
            this.parameters = parameters;
        }
        
        public String getPluginId() { return pluginId; }
        public String getPluginName() { return pluginName; }
        public String getJavaClassName() { return javaClassName; }
        public String getJavaClassPath() { return javaClassPath; }
        public Map<String, String> getFunctions() { return functions; }
        public Map<String, String> getParameters() { return parameters; }
    }
}

