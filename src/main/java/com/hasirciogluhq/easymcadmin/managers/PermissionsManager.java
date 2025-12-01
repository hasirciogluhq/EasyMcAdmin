package com.hasirciogluhq.easymcadmin.managers;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PermissionsManager {

    private final EasyMcAdmin plugin;

    private LuckPerms luckPerms;
    private boolean lpEnabled = false;

    private Permission vaultPerms;
    private boolean vaultEnabled = false;

    public PermissionsManager(EasyMcAdmin plugin) {
        this.plugin = plugin;
        setupPermissions();
    }

    // ------------------------------------------------------------
    // INIT LOGIC
    // ------------------------------------------------------------
    private void setupPermissions() {
        // Try LuckPerms first
        try {
            luckPerms = LuckPermsProvider.get();
            lpEnabled = true;
            plugin.getLogger().info("LuckPerms detected. FULL permission features enabled.");
            return;
        } catch (Exception ignore) {
        }

        // Try Vault fallback
        RegisteredServiceProvider<Permission> rsp = Bukkit.getServer().getServicesManager()
                .getRegistration(Permission.class);

        if (rsp != null) {
            vaultPerms = rsp.getProvider();
            if (vaultPerms != null) {
                vaultEnabled = true;
                plugin.getLogger().info("Vault permissions hooked. LIMITED permission support enabled.");
                return;
            }
        }

        plugin.getLogger().warning("No permission plugin found. Only Bukkit .hasPermission() works.");
    }

    // ------------------------------------------------------------
    // LP USER LOADING
    // ------------------------------------------------------------
    private CompletableFuture<User> loadUserAsync(UUID uuid) {
        return luckPerms.getUserManager().loadUser(uuid);
    }

    private User getUserIfLoaded(UUID uuid) {
        return luckPerms.getUserManager().getUser(uuid);
    }

    // ------------------------------------------------------------
    // UNIVERSAL PERMISSION CHECK
    // ------------------------------------------------------------
    public boolean hasPermission(OfflinePlayer player, String perm) {

        // LP supports offline
        if (lpEnabled) {
            User user = getUserIfLoaded(player.getUniqueId());
            if (user != null) {
                return user.getCachedData().getPermissionData().checkPermission(perm).asBoolean();
            }
        }

        // Vault supports ONLY online
        if (vaultEnabled && player.isOnline()) {
            return vaultPerms.playerHas(null, player.getPlayer(), perm);
        }

        // Bukkit fallback
        return player.isOnline() && player.getPlayer().hasPermission(perm);
    }

    // ------------------------------------------------------------
    // PERMISSION ADD
    // ------------------------------------------------------------
    public CompletableFuture<Boolean> addPermission(OfflinePlayer player, String perm) {

        if (!lpEnabled) {
            // Vault only supports online players
            if (vaultEnabled && player.isOnline()) {
                return CompletableFuture.completedFuture(
                        vaultPerms.playerAdd(null, player.getPlayer(), perm));
            }
            return CompletableFuture.completedFuture(false);
        }

        return loadUserAsync(player.getUniqueId()).thenApply(user -> {
            Node node = PermissionNode.builder(perm).value(true).build();
            user.data().add(node);
            luckPerms.getUserManager().saveUser(user);
            return true;
        });
    }

    // ------------------------------------------------------------
    // PERMISSION ADD WITH EXPIRY
    // ------------------------------------------------------------
    public CompletableFuture<Boolean> addPermissionTimed(OfflinePlayer player, String perm, long seconds) {

        if (!lpEnabled)
            return CompletableFuture.completedFuture(false);

        return loadUserAsync(player.getUniqueId()).thenApply(user -> {
            Node node = PermissionNode.builder(perm)
                    .expiry(Duration.ofSeconds(seconds))
                    .value(true)
                    .build();

            user.data().add(node);
            luckPerms.getUserManager().saveUser(user);
            return true;
        });
    }

    // ------------------------------------------------------------
    // PERMISSION REMOVE
    // ------------------------------------------------------------
    public CompletableFuture<Boolean> removePermission(OfflinePlayer player, String perm) {

        if (!lpEnabled) {
            if (vaultEnabled && player.isOnline()) {
                return CompletableFuture.completedFuture(
                        vaultPerms.playerRemove(null, player.getPlayer(), perm));
            }
            return CompletableFuture.completedFuture(false);
        }

        return loadUserAsync(player.getUniqueId()).thenApply(user -> {
            user.getNodes(NodeType.PERMISSION).stream()
                    .filter(n -> n.getKey().equalsIgnoreCase(perm))
                    .forEach(n -> user.data().remove(n));

            luckPerms.getUserManager().saveUser(user);
            return true;
        });
    }

    // ------------------------------------------------------------
    // PERMISSION UPDATE (remove old + add new)
    // ------------------------------------------------------------
    public CompletableFuture<Boolean> updatePermission(OfflinePlayer player, String oldPerm, String newPerm) {
        return removePermission(player, oldPerm)
                .thenCompose(v -> addPermission(player, newPerm));
    }

    // ------------------------------------------------------------
    // GROUP GET LIST
    // ------------------------------------------------------------
    public CompletableFuture<List<String>> getGroups(OfflinePlayer player) {

        if (lpEnabled) {
            return loadUserAsync(player.getUniqueId()).thenApply(user -> {
                List<String> groups = new ArrayList<>();
                user.getNodes(NodeType.INHERITANCE).forEach(n -> groups.add(n.getGroupName()));
                return groups;
            });
        }

        if (vaultEnabled && player.isOnline()) {
            return CompletableFuture.completedFuture(
                    Arrays.asList(vaultPerms.getPlayerGroups(player.getPlayer())));
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    // ------------------------------------------------------------
    // GROUP ADD
    // ------------------------------------------------------------
    public CompletableFuture<Boolean> addGroup(OfflinePlayer player, String group) {

        if (!lpEnabled) {
            if (vaultEnabled && player.isOnline()) {
                return CompletableFuture.completedFuture(
                        vaultPerms.playerAddGroup(null, player.getPlayer(), group));
            }
            return CompletableFuture.completedFuture(false);
        }

        return loadUserAsync(player.getUniqueId()).thenApply(user -> {
            Node node = InheritanceNode.builder(group).build();
            user.data().add(node);
            luckPerms.getUserManager().saveUser(user);
            return true;
        });
    }

    // ------------------------------------------------------------
    // GROUP REMOVE
    // ------------------------------------------------------------
    public CompletableFuture<Boolean> removeGroup(OfflinePlayer player, String group) {

        if (!lpEnabled) {
            if (vaultEnabled && player.isOnline()) {
                return CompletableFuture.completedFuture(
                        vaultPerms.playerRemoveGroup(null, player.getPlayer(), group));
            }
            return CompletableFuture.completedFuture(false);
        }

        return loadUserAsync(player.getUniqueId()).thenApply(user -> {
            user.getNodes(NodeType.INHERITANCE).stream()
                    .map(n -> (InheritanceNode) n)
                    .filter(n -> n.getGroupName().equalsIgnoreCase(group))
                    .forEach(n -> user.data().remove(n));

            luckPerms.getUserManager().saveUser(user);
            return true;
        });
    }

    // ------------------------------------------------------------
    // PRIMARY GROUP GET
    // ------------------------------------------------------------
    public CompletableFuture<String> getPrimaryGroup(OfflinePlayer player) {

        if (lpEnabled) {
            return loadUserAsync(player.getUniqueId()).thenApply(User::getPrimaryGroup);
        }

        if (vaultEnabled && player.isOnline()) {
            return CompletableFuture.completedFuture(
                    vaultPerms.getPrimaryGroup(player.getPlayer()));
        }

        return CompletableFuture.completedFuture("default");
    }

    // ------------------------------------------------------------
    // PRIMARY GROUP SET
    // ------------------------------------------------------------
    public CompletableFuture<Boolean> setPrimaryGroup(OfflinePlayer player, String group) {

        if (!lpEnabled)
            return CompletableFuture.completedFuture(false);

        return loadUserAsync(player.getUniqueId()).thenApply(user -> {
            user.setPrimaryGroup(group);
            luckPerms.getUserManager().saveUser(user);
            return true;
        });
    }
}
