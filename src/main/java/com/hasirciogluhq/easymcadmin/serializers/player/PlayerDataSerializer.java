package com.hasirciogluhq.easymcadmin.serializers.player;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.serializers.LocationSerializer;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Utility class for serializing player data to JSON payloads
 */
public class PlayerDataSerializer {

    /**
     * Get player details payload (common function for details and chunk)
     * Includes: username, display_name, player_list_name, ping, first_played,
     * last_played, location, experience, game_mode, health, food_level, saturation,
     * fire_ticks, air_ticks
     * 
     * Note: balance, currency, groups, primary_group are temporarily commented out
     * as they are not part of the backend Player entity structure
     * 
     * @param player Player to serialize
     * @return JsonObject with player data
     */
    public static JsonObject getPlayerDetailsPayload(Player player) {
        JsonObject playerObj = new JsonObject();
        playerObj.addProperty("uuid", player.getUniqueId().toString());
        playerObj.addProperty("username", player.getName());
        playerObj.addProperty("online", player.isOnline());
        playerObj.addProperty("display_name", player.getDisplayName());
        playerObj.addProperty("player_list_name", player.getPlayerListName());
        try {
            // Works on 1.17+
            playerObj.addProperty("ping", player.getPing());
        } catch (NoSuchMethodError e) {
            // Fallback for older versions
            try {
                Object entityPlayer = player.getClass().getMethod("getHandle").invoke(player);
                int ping = (int) entityPlayer.getClass().getField("ping").get(entityPlayer);
                playerObj.addProperty("ping", ping);
            } catch (Exception ex) {
                playerObj.addProperty("ping", -1);
            }
        }
        playerObj.addProperty("first_played", player.getFirstPlayed());
        playerObj.addProperty("last_played", System.currentTimeMillis());
        playerObj.addProperty("last_seen", System.currentTimeMillis());
        // last_played is only set on join, not in details updates
        // Don't set it here - it will be set in sendPlayerJoin() if needed

        // TODO: Economy balance - will be handled separately via player_balances table
        // Double balance = VaultIntegration.getPlayerBalance(player);
        // if (balance != null) {
        // playerObj.addProperty("balance", balance);
        // }
        // String currencyName = VaultIntegration.getCurrencyName();
        // if (currencyName != null) {
        // playerObj.addProperty("currency", currencyName);
        // }

        // Send location
        if (player.getLocation() != null) {
            playerObj.add("location", LocationSerializer.serialize(player.getLocation()));
        }

        // Send experience
        JsonObject expObj = new JsonObject();
        expObj.addProperty("level", player.getLevel());
        expObj.addProperty("exp", player.getExp());
        expObj.addProperty("total_exp", player.getTotalExperience());
        playerObj.add("experience", expObj);

        // TODO: Permission groups - will be handled separately if needed
        // String primaryGroup = VaultIntegration.getPlayerPrimaryGroup(player);
        // if (primaryGroup != null) {
        // playerObj.addProperty("primary_group", primaryGroup);
        // }
        // String[] groups = VaultIntegration.getPlayerGroups(player);
        // if (groups != null && groups.length > 0) {
        // JsonArray groupsArray = new JsonArray();
        // for (String group : groups) {
        // groupsArray.add(group);
        // }
        // playerObj.add("groups", groupsArray);
        // }

        // Send game mode
        playerObj.addProperty("game_mode", player.getGameMode().name());

        // Send health and food stats
        playerObj.addProperty("health", player.getHealth());
        playerObj.addProperty("food_level", player.getFoodLevel());
        playerObj.addProperty("saturation", player.getSaturation());
        playerObj.addProperty("fire_ticks", player.getFireTicks());
        playerObj.addProperty("air_ticks", player.getRemainingAir());

        return playerObj;
    }

    /**
     * Get offline player details payload (for offline players in chunk)
     * Includes all fields same as online player, but with N/A for unavailable data
     * 
     * Note: balance, currency, groups, primary_group are temporarily commented out
     * as they are not part of the backend Player entity structure
     * 
     * @param offlinePlayer OfflinePlayer to serialize
     * @return JsonObject with player data
     */
    public static JsonObject getOfflinePlayerDetailsPayload(OfflinePlayer offlinePlayer) {
        JsonObject playerObj = new JsonObject();
        playerObj.addProperty("uuid", offlinePlayer.getUniqueId().toString());
        playerObj.addProperty("username", offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown");
        playerObj.addProperty("online", false);

        // Display name - N/A for offline players
        playerObj.addProperty("display_name", "N/A");

        // Player list name - N/A for offline players
        playerObj.addProperty("player_list_name", "N/A");

        // Ping - N/A for offline players
        playerObj.addProperty("ping", -1);

        playerObj.addProperty("first_played", offlinePlayer.getFirstPlayed());
        playerObj.addProperty("last_played", offlinePlayer.getLastPlayed());

        // TODO: Economy balance - will be handled separately via player_balances table
        // Double balance = VaultIntegration.getPlayerBalance(offlinePlayer);
        // if (balance != null) {
        // playerObj.addProperty("balance", balance);
        // } else {
        // playerObj.addProperty("balance", 0.0);
        // }
        // String currencyName = VaultIntegration.getCurrencyName();
        // if (currencyName != null) {
        // playerObj.addProperty("currency", currencyName);
        // } else {
        // playerObj.addProperty("currency", "N/A");
        // }

        // Location - N/A for offline players
        playerObj.add("location", LocationSerializer.createEmpty());

        // Experience - N/A for offline players
        JsonObject expObj = new JsonObject();
        expObj.addProperty("level", 0);
        expObj.addProperty("exp", 0.0);
        expObj.addProperty("total_exp", 0);
        playerObj.add("experience", expObj);

        // TODO: Permission groups - will be handled separately if needed
        // String primaryGroup =
        // VaultIntegration.getOfflinePlayerPrimaryGroup(offlinePlayer);
        // if (primaryGroup != null) {
        // playerObj.addProperty("primary_group", primaryGroup);
        // } else {
        // playerObj.addProperty("primary_group", "N/A");
        // }
        // String[] groups = VaultIntegration.getOfflinePlayerGroups(offlinePlayer);
        // if (groups != null && groups.length > 0) {
        // JsonArray groupsArray = new JsonArray();
        // for (String group : groups) {
        // groupsArray.add(group);
        // }
        // playerObj.add("groups", groupsArray);
        // } else {
        // // Empty array if no groups
        // playerObj.add("groups", new JsonArray());
        // }

        // Health and food stats - N/A for offline players
        playerObj.addProperty("health", 0.0);
        playerObj.addProperty("food_level", 0);
        playerObj.addProperty("saturation", 0.0);
        playerObj.addProperty("fire_ticks", 0);
        playerObj.addProperty("air_ticks", 0);

        return playerObj;
    }

    /**
     * Add player inventory, ender chest, experience, and location data to player
     * object
     * Used for join events
     * 
     * @param playerObj Player JSON object to add data to
     * @param player    Player to get data from
     */
    public static void addPlayerInventoryData(JsonObject playerObj, Player player) {
        if (player == null) {
            return;
        }

        // Inventory
        if (player.getInventory() != null) {
            playerObj.add("inventory", PlayerInventorySerializer.serializeInventory(player.getInventory()));
        }

        // Ender Chest
        if (player.getEnderChest() != null) {
            playerObj.add("ender_chest", PlayerInventorySerializer.serializeEnderChest(player.getEnderChest()));
        }

        // Experience
        JsonObject expObj = new JsonObject();
        expObj.addProperty("level", player.getLevel());
        expObj.addProperty("exp", player.getExp());
        expObj.addProperty("total_exp", player.getTotalExperience());
        playerObj.add("experience", expObj);

        // Location
        if (player.getLocation() != null) {
            playerObj.add("location", LocationSerializer.serialize(player.getLocation()));
        }
    }
}
