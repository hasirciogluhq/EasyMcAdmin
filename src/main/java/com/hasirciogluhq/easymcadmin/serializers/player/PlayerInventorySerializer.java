package com.hasirciogluhq.easymcadmin.serializers.player;

import com.google.gson.*;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.security.MessageDigest;
import java.util.Objects;

public class PlayerInventorySerializer {

    // ============================================================
    // SERIALIZE PLAYER INVENTORY
    // ============================================================

    public static JsonArray serializeInventory(PlayerInventory inventory) {
        JsonArray invArray = new JsonArray();
        if (inventory == null)
            return invArray;

        ItemStack[] storage = inventory.getStorageContents(); // 0–35
        ItemStack[] armor = inventory.getArmorContents(); // boots..helmet
        ItemStack offhand = inventory.getItemInOffHand(); // 40

        // storage slots (0–35)
        for (int i = 0; i < storage.length; i++) {
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("slot", i);
            wrapper.add("item", serializeItemStack(storage[i]));
            invArray.add(wrapper);
        }

        // armor slots (36–39)
        if (armor != null && armor.length >= 4) {
            for (int i = 0; i < 4; i++) {
                JsonObject wrapper = new JsonObject();
                wrapper.addProperty("slot", 36 + i);
                wrapper.add("item", serializeItemStack(armor[i]));
                invArray.add(wrapper);
            }
        }

        // offhand slot (40)
        JsonObject offWrap = new JsonObject();
        offWrap.addProperty("slot", 40);
        offWrap.add("item", serializeItemStack(offhand));
        invArray.add(offWrap);

        return invArray;
    }

    // ============================================================
    // SERIALIZE ENDER CHEST (slots 0–26)
    // ============================================================

    public static JsonArray serializeEnderChest(Inventory ender) {
        JsonArray arr = new JsonArray();
        if (ender == null)
            return arr;

        ItemStack[] content = ender.getStorageContents();

        for (int i = 0; i < content.length; i++) {
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("slot", i);
            wrapper.add("item", serializeItemStack(content[i]));
            arr.add(wrapper);
        }

        return arr;
    }

    // ============================================================
    // SERIALIZE SINGLE ITEM
    // ============================================================

    public static JsonObject serializeItemStack(ItemStack item) {
        JsonObject obj = new JsonObject();

        if (item == null || item.getType() == Material.AIR) {
            obj.addProperty("type", "AIR");
            obj.addProperty("amount", 0);
            return obj;
        }

        obj.addProperty("type", item.getType().name());
        obj.addProperty("amount", item.getAmount());

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName())
            obj.addProperty("display_name", item.getItemMeta().getDisplayName());

        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            JsonArray lore = new JsonArray();
            for (String l : item.getItemMeta().getLore())
                lore.add(l);
            obj.add("lore", lore);
        }

        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            JsonArray enchArr = new JsonArray();
            item.getItemMeta().getEnchants().forEach((ench, level) -> {
                JsonObject enchObj = new JsonObject();
                enchObj.addProperty("name", ench.getKey().getKey());
                enchObj.addProperty("level", level);
                enchArr.add(enchObj);
            });
            obj.add("enchantments", enchArr);
        }

        if (item.hasItemMeta() && item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable) {
            org.bukkit.inventory.meta.Damageable dmg = (org.bukkit.inventory.meta.Damageable) item.getItemMeta();
            if (dmg.hasDamage())
                obj.addProperty("durability", dmg.getDamage());
        }

        return obj;
    }

    // ============================================================
    // HASH GENERATOR (PLAYER INVENTORY)
    // ============================================================

    public static String calculateInventoryHash(PlayerInventory inv) {
        try {
            JsonArray arr = serializeInventory(inv);
            return md5(arr.toString());
        } catch (Exception e) {
            return "";
        }
    }

    // ============================================================
    // HASH GENERATOR (ENDER CHEST)
    // ============================================================

    public static String calculateEnderChestHash(Inventory ender) {
        try {
            JsonArray arr = serializeEnderChest(ender);
            return md5(arr.toString());
        } catch (Exception e) {
            return "";
        }
    }

    private static String md5(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ============================================================
    // TRUE DIFF SYSTEM (PLAYER + ENDER CHEST SUPPORT)
    // ============================================================

    public static JsonArray calculateDiff(JsonArray previous, JsonArray current) {
        JsonArray diffList = new JsonArray();

        if (previous == null || current == null)
            return diffList;

        int max = Math.min(previous.size(), current.size());

        for (int i = 0; i < max; i++) {
            JsonObject prevItem = previous.get(i).getAsJsonObject();
            JsonObject currItem = current.get(i).getAsJsonObject();

            if (!Objects.equals(prevItem.toString(), currItem.toString())) {

                JsonObject diffObj = new JsonObject();

                // DIŞ WRAPPER SLOT
                diffObj.addProperty("slot", currItem.get("slot").getAsInt());

                // İÇ ITEM DATA — **slot içermeyen item objesi**
                diffObj.add("item", currItem.getAsJsonObject("item"));

                diffList.add(diffObj);
            }
        }

        return diffList;
    }

}
