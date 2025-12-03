package com.hasirciogluhq.easymcadmin.listeners.player.inventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;

public class InventoryChangeListener implements Listener {

    private final EasyMcAdmin plugin;

    public InventoryChangeListener(EasyMcAdmin plugin) {
        this.plugin = plugin;
    }

    /**
     * Central trigger method.
     * All logic now lives in PlayerService's SendPlayerInventorySyncEvent method.
     * 
     * @param p                  Player
     * @param reason             Reason for logging (optional)
     * @param fullUpdateRequires Is a full update required (true) or is a diff enough
     *                           (false)?
     */
    private void fire(Player p, String reason, Boolean fullUpdateRequires) {
        if (fullUpdateRequires == null)
            fullUpdateRequires = false;

        // We call PlayerService.
        // Parametreler: (Player, isFullSync, shouldSendPacket)
        // We set shouldSendPacket = true because this is an event listener and we want
        // the packet to be sent.
        plugin.getServiceManager().getPlayerService().SendPlayerInventorySyncEvent(p, fullUpdateRequires, true);
    }

    // --- Inventory Click (shift, drag, moving etc) ---
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getWhoClicked() instanceof Player p)
                fire(p, "InventoryClick", false);
        }, 1L);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getWhoClicked() instanceof Player p)
                fire(p, "InventoryDrag", false);
        }, 1L);
    }

    // --- Consume (yemek, potion) ---
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        // Inventory updates on consumption; delay might be unnecessary but keep it for
        // safety
        fire(e.getPlayer(), "Consume", false);
    }

    // --- Interact (durability, bucket fill, pearl, fire, etc) ---
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer() != null)
                fire(e.getPlayer(), "Interact", false);
        }, 1L);
    }

    // --- Block break: tool durability decreases ---
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        fire(e.getPlayer(), "BlockBreak", false);
    }

    // --- Block place: item amount decreases ---
    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        fire(e.getPlayer(), "BlockPlace", false);
    }

    // --- Death -> full inventory is lost ---
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        fire(e.getEntity(), "Death", true);
    }

    // --- Respawn -> inventory may reset ---
    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        fire(e.getPlayer(), "Respawn", true);
    }

    // --- Entity damage: sword durability, bow durability etc ---
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

    // --- Join: plugins may give items ---
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        fire(e.getPlayer(), "Join", true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Send the final state on quit (marked as full sync for a clean state)
        fire(player, "Quit", true);

        // Clear the cache on the service
        plugin.getServiceManager().getPlayerService().clearPlayerInventoryCache(player.getUniqueId());
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            fire(player, "Item Drop", false);
        }, 1L);
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                fire(player, "Item Pick Up ", false);
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        fire(player, "Item Consume", false);
    }
}
