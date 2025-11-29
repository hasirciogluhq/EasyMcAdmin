package com.hasirciogluhq.easymcadmin.serializers.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.security.MessageDigest;

/**
 * Utility class for serializing player inventories to JSON
 * Includes serialization, hash calculation, and diff calculation
 */
public class PlayerInventorySerializer {
    
    // ============================================================================
    // SERIALIZATION
    // ============================================================================
    
    /**
     * Serialize player inventory to JSON array
     * MC inventory slots:
     * 0-8: Hotbar (quick access)
     * 9-35: Main inventory (27 slots)
     * 36: Boots
     * 37: Leggings
     * 38: Chestplate
     * 39: Helmet
     * 40: Offhand
     * 
     * @param inventory PlayerInventory to serialize
     * @return JsonArray with all inventory slots
     */
    public static JsonArray serializeInventory(PlayerInventory inventory) {
        JsonArray invArray = new JsonArray();
        if (inventory == null) {
            return invArray;
        }

        // Get all contents in correct order
        ItemStack[] storageContents = inventory.getStorageContents(); // Slots 0-35
        ItemStack[] armorContents = inventory.getArmorContents(); // Armor array: boots, leggings, chestplate, helmet
        ItemStack offhand = inventory.getItemInOffHand(); // Offhand slot

        // Add storage contents (0-35: hotbar + main inventory)
        for (ItemStack item : storageContents) {
            JsonObject itemObj = serializeItemStack(item);
            invArray.add(itemObj);
        }

        // Add armor slots (36-39)
        // armorContents array: [boots, leggings, chestplate, helmet]
        // But in MC, slots are: 36=boots, 37=leggings, 38=chestplate, 39=helmet
        if (armorContents != null && armorContents.length >= 4) {
            invArray.add(serializeItemStack(armorContents[0])); // Boots (slot 36)
            invArray.add(serializeItemStack(armorContents[1])); // Leggings (slot 37)
            invArray.add(serializeItemStack(armorContents[2])); // Chestplate (slot 38)
            invArray.add(serializeItemStack(armorContents[3])); // Helmet (slot 39)
        } else {
            // Add empty armor slots if armor array is null or incomplete
            for (int i = 0; i < 4; i++) {
                JsonObject emptyItem = new JsonObject();
                emptyItem.addProperty("type", "AIR");
                emptyItem.addProperty("amount", 0);
                invArray.add(emptyItem);
            }
        }

        // Add offhand (slot 40)
        invArray.add(serializeItemStack(offhand));

        return invArray;
    }

    /**
     * Serialize ender chest inventory to JSON array
     * 
     * @param enderChest Ender chest inventory to serialize
     * @return JsonArray with all ender chest slots
     */
    public static JsonArray serializeEnderChest(org.bukkit.inventory.Inventory enderChest) {
        JsonArray invArray = new JsonArray();
        if (enderChest == null) {
            return invArray;
        }

        ItemStack[] contents = enderChest.getStorageContents();
        for (ItemStack item : contents) {
            JsonObject itemObj = serializeItemStack(item);
            invArray.add(itemObj);
        }
        return invArray;
    }

    /**
     * Serialize ItemStack to JSON object
     * 
     * @param item ItemStack to serialize
     * @return JsonObject with item data
     */
    public static JsonObject serializeItemStack(ItemStack item) {
        JsonObject itemObj = new JsonObject();
        if (item == null || item.getType() == Material.AIR) {
            itemObj.addProperty("type", "AIR");
            itemObj.addProperty("amount", 0);
            return itemObj;
        }

        itemObj.addProperty("type", item.getType().name());
        itemObj.addProperty("amount", item.getAmount());

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            itemObj.addProperty("display_name", item.getItemMeta().getDisplayName());
        }

        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            JsonArray loreArray = new JsonArray();
            for (String line : item.getItemMeta().getLore()) {
                loreArray.add(line);
            }
            itemObj.add("lore", loreArray);
        }

        // Add enchantments
        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            JsonArray enchantArray = new JsonArray();
            for (org.bukkit.enchantments.Enchantment enchant : item.getItemMeta().getEnchants().keySet()) {
                JsonObject enchantObj = new JsonObject();
                enchantObj.addProperty("name", enchant.getKey().getKey());
                enchantObj.addProperty("level", item.getItemMeta().getEnchantLevel(enchant));
                enchantArray.add(enchantObj);
            }
            itemObj.add("enchantments", enchantArray);
        }

        // Use ItemMeta for durability if available (getDurability is deprecated)
        if (item.hasItemMeta() && item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable) {
            org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) item.getItemMeta();
            if (damageable.hasDamage()) {
                itemObj.addProperty("durability", damageable.getDamage());
            }
        }

        return itemObj;
    }
    
    // ============================================================================
    // HASH CALCULATION
    // ============================================================================
    
    /**
     * Calculate hash of inventory for diff detection
     * 
     * @param inventory PlayerInventory to hash
     * @return MD5 hash string, or empty string if error
     */
    public static String calculateInventoryHash(PlayerInventory inventory) {
        if (inventory == null) {
            return "";
        }

        try {
            JsonArray invArray = serializeInventory(inventory);
            String inventoryJson = invArray.toString();

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(inventoryJson.getBytes("UTF-8"));

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Calculate hash of ender chest for diff detection
     * 
     * @param enderChest Ender chest inventory to hash
     * @return MD5 hash string, or empty string if error
     */
    public static String calculateEnderChestHash(org.bukkit.inventory.Inventory enderChest) {
        if (enderChest == null) {
            return "";
        }

        try {
            JsonArray ecArray = serializeEnderChest(enderChest);
            String enderChestJson = ecArray.toString();

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(enderChestJson.getBytes("UTF-8"));

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    // ============================================================================
    // DIFF CALCULATION
    // ============================================================================
    
    /**
     * Calculate diff between two inventory arrays (only changed slots)
     * Returns a JsonArray with all slots, but only changed slots have actual item data
     * Unchanged slots have null to maintain slot indices
     * 
     * @param previous Previous inventory state
     * @param current Current inventory state
     * @return JsonArray with only changed slots
     */
    public static JsonArray calculateDiff(JsonArray previous, JsonArray current) {
        JsonArray diff = new JsonArray();

        if (previous == null || previous.size() == 0) {
            // No previous state, return full inventory
            return current != null ? current : new JsonArray();
        }

        if (current == null || current.size() == 0) {
            // Current is empty, return array with empty items for all previous slots
            for (int i = 0; i < previous.size(); i++) {
                JsonObject emptyItem = new JsonObject();
                emptyItem.addProperty("type", "AIR");
                emptyItem.addProperty("amount", 0);
                diff.add(emptyItem);
            }
            return diff;
        }

        // Find the maximum size
        int maxSize = Math.max(previous.size(), current.size());

        // Compare each slot
        for (int i = 0; i < maxSize; i++) {
            JsonObject prevItem = i < previous.size() && !previous.get(i).isJsonNull()
                    ? previous.get(i).getAsJsonObject()
                    : null;
            JsonObject currItem = i < current.size() && !current.get(i).isJsonNull()
                    ? current.get(i).getAsJsonObject()
                    : null;

            // Check if slot changed
            boolean changed = false;
            if (prevItem == null && currItem == null) {
                // Both null, no change - add null to maintain slot index
                diff.add(com.google.gson.JsonNull.INSTANCE);
                continue;
            } else if (prevItem == null || currItem == null) {
                // One is null, other is not - changed
                changed = true;
            } else {
                // Compare item properties
                String prevType = prevItem.has("type") ? prevItem.get("type").getAsString() : "AIR";
                String currType = currItem.has("type") ? currItem.get("type").getAsString() : "AIR";
                int prevAmount = prevItem.has("amount") ? prevItem.get("amount").getAsInt() : 0;
                int currAmount = currItem.has("amount") ? currItem.get("amount").getAsInt() : 0;

                if (!prevType.equals(currType) || prevAmount != currAmount) {
                    changed = true;
                } else {
                    // Compare other properties (display_name, lore, enchantments, durability)
                    String prevDisplayName = prevItem.has("display_name") ? prevItem.get("display_name").getAsString()
                            : null;
                    String currDisplayName = currItem.has("display_name") ? currItem.get("display_name").getAsString()
                            : null;
                    if ((prevDisplayName == null) != (currDisplayName == null) ||
                            (prevDisplayName != null && !prevDisplayName.equals(currDisplayName))) {
                        changed = true;
                    } else {
                        // Compare durability
                        int prevDurability = prevItem.has("durability") ? prevItem.get("durability").getAsInt() : -1;
                        int currDurability = currItem.has("durability") ? currItem.get("durability").getAsInt() : -1;
                        if (prevDurability != currDurability) {
                            changed = true;
                        }
                    }
                }
            }

            if (changed) {
                // Slot changed, include current item in diff
                if (currItem != null) {
                    diff.add(currItem);
                } else {
                    // Item removed, add empty item
                    JsonObject emptyItem = new JsonObject();
                    emptyItem.addProperty("type", "AIR");
                    emptyItem.addProperty("amount", 0);
                    diff.add(emptyItem);
                }
            } else {
                // Slot unchanged, add null to maintain slot index
                diff.add(com.google.gson.JsonNull.INSTANCE);
            }
        }

        return diff;
    }
}

