package com.hasirciogluhq.easymcadmin.listeners.player.stats;

import com.google.gson.JsonObject;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.managers.DispatcherManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.entity.EntityShootBowEvent;

/**
 * Event-driven stat emitter: event -> JSON -> dispatcher.
 * Sends append-only events for analytics without any diff logic.
 */

public class PlayerStatsEventListener implements Listener {

    private final EasyMcAdmin plugin;
    private final DispatcherManager dispatcherManager;

    public PlayerStatsEventListener(EasyMcAdmin plugin,
            DispatcherManager dispatcherManager
    // StatsDispatcher dispatcher
    ) {
        this.plugin = plugin;
        this.dispatcherManager = dispatcherManager;
    }

    private void fire(Player player, String category, String key, Number value, JsonObject metadata) {
        if (!plugin.getTransportManager().isConnected() || !plugin.getTransportManager().isAuthenticated()) {
            return;
        }

        JsonObject eventObj = new JsonObject();
        eventObj.addProperty("player_uuid", player.getUniqueId().toString());
        eventObj.addProperty("username", player.getName());
        eventObj.addProperty("category", category);
        eventObj.addProperty("key", key);
        if (value != null) {
            eventObj.addProperty("value", value);
        }
        eventObj.addProperty("timestamp", System.currentTimeMillis());
        if (metadata != null && metadata.size() > 0) {
            eventObj.add("metadata", metadata);
        }

        this.dispatcherManager.getPlayerStatsEventDispatcher().dispatch(eventObj);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            // Warm up cache shortly after join to avoid stale diffs
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> fire(player, "session", "join", 1, null),
                    1L);

            // Emit append-only event for joins
            ;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Emit append-only event for quits
        fire(event.getPlayer(), "session", "quit", -1, null);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        fire(player, "block_break", materialKey(event.getBlock().getType()), 1,
                withLocation(event.getBlock().getLocation()));
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        fire(player, "block_place", materialKey(event.getBlock().getType()), 1,
                withLocation(event.getBlock().getLocation()));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        fire(player, "death", "player_death", 1, null);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity target = event.getEntity();

        if (damager instanceof Player) {
            Player player = (Player) damager;
            String key = target.getType().name().toLowerCase();
            JsonObject meta = new JsonObject();
            meta.addProperty("damage", event.getFinalDamage());
            fire(player, "damage_dealt", key, event.getFinalDamage(), meta);
        }

        if (target instanceof Player) {
            Player player = (Player) target;
            String key = damager.getType().name().toLowerCase();
            JsonObject meta = new JsonObject();
            meta.addProperty("damage", event.getFinalDamage());
            fire(player, "damage_taken", key, event.getFinalDamage(), meta);
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        fire(player, "item_consume", materialKey(event.getItem().getType()), 1, null);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        fire(player, "item_drop", materialKey(event.getItemDrop().getItemStack().getType()), 1, null);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        Player player = (Player) event.getEntity();
        fire(player, "item_pickup", materialKey(event.getItem().getItemStack().getType()), 1, null);
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) event.getWhoClicked();
        fire(player, "craft", materialKey(event.getRecipe().getResult().getType()), 1, null);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        fire(player, "interact", event.getAction().name().toLowerCase(), 1, null);
    }

    @EventHandler
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        fire(player, "sprint_toggle", "sprint", event.isSprinting() ? 1 : 0, null);
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        fire(player, "sneak_toggle", "sneak", event.isSneaking() ? 1 : 0, null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        fire(player, "session", "join", 1, null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        fire(player, "session", "quit", 1, null);
    }

    @EventHandler
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        Player player = (Player) event.getEntity();
        String projectile = event.getProjectile() != null ? event.getProjectile().getType().name().toLowerCase()
                : "projectile";
        fire(player, "bow_shot", projectile, 1, null);
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        JsonObject meta = new JsonObject();
        meta.addProperty("cost", event.getExpLevelCost());
        JsonObject enchants = new JsonObject();
        for (var entry : event.getEnchantsToAdd().entrySet()) {
            Enchantment enchantment = entry.getKey();
            enchants.addProperty(enchantment.getKey().getKey(), entry.getValue());
        }
        meta.add("enchants", enchants);
        fire(player, "enchant", materialKey(event.getItem().getType()), 1, meta);
    }

    @EventHandler
    public void onVillagerTrade(VillagerAcquireTradeEvent event) {
        HumanEntity trader = event.getEntity().getTrader();
        if (trader instanceof Player player) {
            fire(player, "trade", materialKey(event.getRecipe().getResult().getType()), 1, null);
        }
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (!(event.getView().getPlayer() instanceof Player))
            return;
        Player player = (Player) event.getView().getPlayer();
        fire(player, "smithing_prepare", "smithing_table", 1, null);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player))
            return;
        Player player = (Player) event.getView().getPlayer();
        fire(player, "anvil_prepare", "anvil", 1, null);
    }

    @EventHandler
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!(event.getView().getPlayer() instanceof Player))
            return;
        Player player = (Player) event.getView().getPlayer();
        fire(player, "grindstone_prepare", "grindstone", 1, null);
    }

    private String materialKey(Material material) {
        return material != null ? material.getKey().getKey() : "unknown";
    }

    private JsonObject withLocation(Location location) {
        if (location == null)
            return null;
        JsonObject meta = new JsonObject();
        meta.addProperty("world", location.getWorld() != null ? location.getWorld().getName() : "unknown");
        meta.addProperty("x", location.getBlockX());
        meta.addProperty("y", location.getBlockY());
        meta.addProperty("z", location.getBlockZ());
        return meta;
    }
}
