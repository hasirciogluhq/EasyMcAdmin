package com.hasirciogluhq.easymcadmin.listeners;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.player.PlayerInventoryChangedPacket;
import com.hasirciogluhq.easymcadmin.player.serializers.InventorySerializer;

public class InventoryChangeListener implements Listener {

    private final EasyMcAdmin plugin;

    // Store previous inventory and ender chest states for diff calculation
    private final Map<UUID, JsonArray> previousInventories = new HashMap<>();
    private final Map<UUID, JsonArray> previousEnderChests = new HashMap<>();
    private final Map<UUID, String> previousInventoryHashes = new HashMap<>();
    private final Map<UUID, String> previousEnderChestHashes = new HashMap<>();

    public InventoryChangeListener(EasyMcAdmin plugin) {
        this.plugin = plugin;
    }

    // --- Utility: tek noktadan çağır ---
    private void fire(Player p, String reason, Boolean fullUpdateRequires) {
        Bukkit.getLogger().info("[InventoryChange] " + p.getName() + " changed inventory via " + reason);

        if (fullUpdateRequires == null)
            fullUpdateRequires = false;

        sendPlayerInventoryUpdate(p, fullUpdateRequires);
    }

    /**
     * Generates the player inventory update data.
     *
     * @param player   The player whose inventory data is to be generated.
     * @param fullSync If true, generates full inventory sync data; otherwise,
     *                 generates diff sync data.
     * @return A JsonObject containing the inventory data.
     */
    public JsonObject generatePlayerInventoryData(Player player, boolean fullSync) {
        UUID playerUUID = player.getUniqueId();

        // Serialize current inventory and ender chest
        JsonArray currentSerializedInventory = null;
        JsonArray currentSerializedEnderChest = null;

        PlayerInventory inventory = player.getInventory();
        if (inventory != null) {
            currentSerializedInventory = InventorySerializer.serializeInventory(inventory);
        }

        org.bukkit.inventory.Inventory enderChest = player.getEnderChest();
        if (enderChest != null) {
            currentSerializedEnderChest = InventorySerializer.serializeEnderChest(enderChest);
        }

        // Calculate hashes for both inventory and ender chest
        String inventoryHash = InventorySerializer.calculateInventoryHash(player.getInventory());
        String enderChestHash = InventorySerializer.calculateEnderChestHash(player.getEnderChest());

        JsonObject inventoryData = new JsonObject();
        inventoryData.addProperty("player_uuid", playerUUID.toString());

        if (fullSync) {
            // Full sync: send complete inventory and ender chest
            if (currentSerializedInventory != null) {
                inventoryData.add("inventory", currentSerializedInventory);
            }
            if (currentSerializedEnderChest != null) {
                inventoryData.add("ender_chest", currentSerializedEnderChest);
            }

            // Update stored states
            if (currentSerializedInventory != null) {
                previousInventories.put(playerUUID, currentSerializedInventory);
                previousInventoryHashes.put(playerUUID, inventoryHash);
            }
            if (currentSerializedEnderChest != null) {
                previousEnderChests.put(playerUUID, currentSerializedEnderChest);
                previousEnderChestHashes.put(playerUUID, enderChestHash);
            }
        } else {
            // Diff sync: send only changed slots
            JsonArray previousInventory = previousInventories.get(playerUUID);
            JsonArray previousEnderChest = previousEnderChests.get(playerUUID);
            String previousInventoryHash = previousInventoryHashes.get(playerUUID);
            String previousEnderChestHash = previousEnderChestHashes.get(playerUUID);

            // Check if inventory hash changed
            boolean inventoryChanged = !inventoryHash
                    .equals(previousInventoryHash != null ? previousInventoryHash : "");
            if (inventoryChanged && currentSerializedInventory != null) {
                JsonArray inventoryDiff = InventorySerializer.calculateDiff(previousInventory,
                        currentSerializedInventory);
                inventoryData.add("inventory", inventoryDiff);
                inventoryData.addProperty("inventory_prev_hash", previousInventoryHash);

                // Update stored state
                previousInventories.put(playerUUID, currentSerializedInventory);
                previousInventoryHashes.put(playerUUID, inventoryHash);
            }

            // Check if ender chest hash changed
            boolean enderChestChanged = !enderChestHash
                    .equals(previousEnderChestHash != null ? previousEnderChestHash : "");
            if (enderChestChanged && currentSerializedEnderChest != null) {
                JsonArray enderChestDiff = InventorySerializer.calculateDiff(previousEnderChest,
                        currentSerializedEnderChest);
                inventoryData.add("ender_chest", enderChestDiff);
                inventoryData.addProperty("ender_chest_prev_hash", previousEnderChestHash);

                // Update stored state
                previousEnderChests.put(playerUUID, currentSerializedEnderChest);
                previousEnderChestHashes.put(playerUUID, enderChestHash);
            }
        }

        return inventoryData;
    }

    /**
     * Sends the player inventory update packet.
     *
     * @param inventoryHash  The hash of the player's inventory.
     * @param enderChestHash The hash of the player's ender chest.
     * @param fullSync       Indicates whether it's a full sync or a diff sync.
     * @param inventoryData  The JsonObject containing the inventory data.
     */
    private void sendPlayerInventoryPacket(String inventoryHash, String enderChestHash, boolean fullSync,
            JsonObject inventoryData) {
        Packet packet = new PlayerInventoryChangedPacket(inventoryHash, enderChestHash, fullSync,
                inventoryData);
        try {
            plugin.getTransportManager().sendPacket(packet);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    // functions
    /**
     * Send player inventory update (only inventory data)
     *
     * @param player   The player whose inventory needs to be updated.
     * @param fullSync If true, sends full inventory sync (includes ender chest), if
     *                 false sends diff sync.
     *                 Used for inventory events (click, open, close, etc.) and full
     *                 sync requests.
     *                 Action: player.inventory_update
     */
    public void sendPlayerInventoryUpdate(Player player, boolean fullSync) {
        if (!plugin.getTransportManager().isConnected() || !plugin.getTransportManager().isAuthenticated()) {
            return;
        }

        // Calculate hashes for both inventory and ender chest
        String inventoryHash = InventorySerializer.calculateInventoryHash(player.getInventory());
        String enderChestHash = InventorySerializer.calculateEnderChestHash(player.getEnderChest());

        try {
            JsonObject inventoryData = generatePlayerInventoryData(player, fullSync);
            sendPlayerInventoryPacket(inventoryHash, enderChestHash, fullSync, inventoryData);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send player inventory update: " + e.getMessage());
        }
    }

    // --- Inventory Click (shift, drag, taşıma vs) ---
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p)
            fire(p, "InventoryClick", false);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p)
            fire(p, "InventoryDrag", false);
    }

    // --- Consume (yemek, potion) ---
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        fire(e.getPlayer(), "Consume", false);
    }

    // --- Interact (durability, bucket fill, pearl, fire, vb) ---
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getPlayer() != null)
            fire(e.getPlayer(), "Interact", false);
    }

    // --- Block break: tool durability azalır ---
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        fire(e.getPlayer(), "BlockBreak", false);
    }

    // --- Block place: item amount azalır ---
    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        fire(e.getPlayer(), "BlockPlace", false);
    }

    // --- Death -> full inventory gider ---
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        fire(e.getEntity(), "Death", true);
    }

    // --- Respawn -> envanter resetlenebilir ---
    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        fire(e.getPlayer(), "Respawn", true);
    }

    // --- Entity damage: sword durability, bow durability vb ---
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p)
            fire(p, "DamageDealt", false);
        if (e.getEntity() instanceof Player p)
            fire(p, "DamageTaken", false);
    }

    // --- Bow shoot / crossbow shoot ---
    @EventHandler
    public void onBow(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player p)
            fire(p, "ShootBow", false);
    }

    // --- Swap main/off hand ---
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        fire(e.getPlayer(), "SwapHand", false);
    }

    // --- Crafting ---
    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (e.getWhoClicked() instanceof Player p)
            fire(p, "Craft", false);
    }

    // --- Smithing / Anvil / Grindstone ---
    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        if (e.getView().getPlayer() instanceof Player p)
            fire(p, "AnvilPrepare", false);
    }

    // --- Enchant ---
    @EventHandler
    public void onEnchant(EnchantItemEvent e) {
        fire(e.getEnchanter(), "Enchant", false);
    }

    // --- Join: pluginler item verebilir ---
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        fire(e.getPlayer(), "Join", true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Clean up stored states when player quits
        UUID playerUUID = player.getUniqueId();
        previousInventories.remove(playerUUID);
        previousEnderChests.remove(playerUUID);
        previousInventoryHashes.remove(playerUUID);
        previousEnderChestHashes.remove(playerUUID);

        fire(player, "Quit", true);
    }


    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            fire(player, "Inventory Open", false);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            fire(player, "Inventory Close", false);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        // Send inventory update after a short delay to ensure inventory is updated
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            fire(player, "Item Drop", false);
        }, 1L);
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            // Send inventory update after a short delay to ensure inventory is updated
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                fire(player, "Item Pick Up ", false);
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        // Send inventory update immediately - inventory is already updated at this
        // point
        fire(player, "Item Consume", false);
    }
}