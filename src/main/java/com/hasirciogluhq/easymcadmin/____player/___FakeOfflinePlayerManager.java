package com.hasirciogluhq.easymcadmin.____player;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.*;
import java.util.UUID;

public class ___FakeOfflinePlayerManager {

    // // ---------------- PUBLIC API ---------------- //

    // public static Player load(OfflinePlayer offline) {
    //     try {
    //         Object mcServer = getMinecraftServer();
    //         Object worldServer = getWorldServer(mcServer);

    //         Object playerDataManager = getPlayerDataManager(worldServer, mcServer);

    //         // Create GameProfile
    //         Object gameProfile = createGameProfile(offline.getUniqueId(), offline.getName());

    //         // Create EntityPlayer
    //         Object entityPlayer = createEntityPlayer(mcServer, worldServer, gameProfile);

    //         // Load NBT playerdata
    //         loadNBTData(playerDataManager, entityPlayer, offline.getUniqueId());

    //         // Inject dummy connection
    //         injectDummyConnection(entityPlayer);

    //         // Wrap CraftPlayer
    //         Player bukkitPlayer = wrapBukkit(entityPlayer);

    //         return bukkitPlayer;

    //     } catch (Throwable e) {
    //         e.printStackTrace();
    //         return null;
    //     }
    // }

    // public static void unload(Player p) {
    //     try {
    //         Object entity = getHandle(p);

    //         // Call entity.discard() or remove()
    //         tryInvoke(entity, "discard");
    //         tryInvoke(entity, "remove");

    //         // Remove from world entity list
    //         Object world = entity.getClass().getMethod("getWorld").invoke(entity);
    //         tryInvoke(world, "removeEntity", entity);

    //         // Remove from player list
    //         Object playerList = getPlayerList();
    //         tryRemoveFromPlayerList(playerList, entity);
    //     } catch (Throwable t) {
    //         t.printStackTrace();
    //     }
    // }

    // // ---------------- INTERNAL IMPLEMENTATION ---------------- //

    // private static Object getMinecraftServer() throws Exception {
    //     Class<?> craftServer = Class.forName(Bukkit.getServer().getClass().getName());
    //     Method getServer = craftServer.getMethod("getServer");
    //     return getServer.invoke(Bukkit.getServer());
    // }

    // private static Object getWorldServer(Object mcServer) throws Exception {
    //     try {
    //         // Modern Paper mapping (1.17+)
    //         Method m = mcServer.getClass().getMethod("overworld");
    //         return m.invoke(mcServer);
    //     } catch (NoSuchMethodException ignored) {
    //     }

    //     // Eski spigot mapping fallback (1.8 - 1.16)
    //     for (Method m : mcServer.getClass().getMethods()) {
    //         if (m.getReturnType().getSimpleName().contains("WorldServer")) {
    //             return m.invoke(mcServer);
    //         }
    //     }

    //     throw new IllegalStateException("Could not find world server");
    // }

    // private static Object getPlayerDataManager(Object worldServer, Object mcServer) throws Exception {

    //     // 1) Modern Paper 1.21.3+ mapping
    //     try {
    //         Field f = mcServer.getClass().getDeclaredField("playerDataStorage");
    //         f.setAccessible(true);
    //         Object storage = f.get(mcServer);
    //         if (storage != null)
    //             return storage;
    //     } catch (NoSuchFieldException ignored) {
    //     }

    //     // 2) Fallback: getPlayerDataStorage() (1.20.5 - 1.21.2)
    //     try {
    //         Method m = mcServer.getClass().getMethod("getPlayerDataStorage");
    //         Object storage = m.invoke(mcServer);
    //         if (storage != null)
    //             return storage;
    //     } catch (Exception ignored) {
    //     }

    //     // 3) Older: worldServer.getPlayerData()
    //     try {
    //         Method m = worldServer.getClass().getMethod("getPlayerData");
    //         Object storage = m.invoke(worldServer);
    //         if (storage != null)
    //             return storage;
    //     } catch (Exception ignored) {
    //     }

    //     // 4) Last fallback: any field containing PlayerDataStorage
    //     for (Field f : mcServer.getClass().getDeclaredFields()) {
    //         if (f.getType().getSimpleName().contains("PlayerDataStorage")) {
    //             f.setAccessible(true);
    //             return f.get(mcServer);
    //         }
    //     }

    //     throw new IllegalStateException("Cannot find PlayerDataStorage on this Paper version");
    // }

    // // Create GameProfile(UUID, name)
    // private static Object createGameProfile(UUID uuid, String name) throws Exception {
    //     Class<?> gp = Class.forName("com.mojang.authlib.GameProfile");
    //     return gp.getConstructor(UUID.class, String.class).newInstance(uuid, name);
    // }

    // private static Object createEntityPlayer(Object mcServer, Object worldServer, Object gameProfile) throws Exception {
    //     Class<?> epClass = getNMS("EntityPlayer");

    //     // new EntityPlayer(MinecraftServer, WorldServer, GameProfile)
    //     for (Constructor<?> c : epClass.getConstructors()) {
    //         if (c.getParameterCount() == 3) {
    //             return c.newInstance(mcServer, worldServer, gameProfile);
    //         }
    //     }

    //     // 1.17+ uses different signatures
    //     Constructor<?> c = epClass.getDeclaredConstructors()[0];
    //     c.setAccessible(true);
    //     return c.newInstance(mcServer, worldServer, gameProfile);
    // }

    // private static void loadNBTData(Object dataManager, Object entityPlayer, UUID uuid) throws Exception {
    //     File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
    //     File playerFile = new File(worldFolder, "playerdata/" + uuid + ".dat");

    //     if (!playerFile.exists())
    //         return;

    //     Object nbt = readCompressedNBT(playerFile);

    //     tryInvoke(dataManager, "load", entityPlayer);
    //     tryInvoke(entityPlayer, "load", nbt);
    //     tryInvoke(entityPlayer, "read", nbt);
    //     tryInvoke(entityPlayer, "a", nbt);
    // }

    // private static Object readCompressedNBT(File f) throws Exception {
    //     Class<?> nbtClass = getNMS("NBTCompressedStreamTools");

    //     for (Method m : nbtClass.getMethods()) {
    //         if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == FileInputStream.class) {
    //             return m.invoke(null, new FileInputStream(f));
    //         }
    //     }

    //     // Mojang-mapped
    //     Method m = nbtClass.getMethod("readCompressed", java.io.InputStream.class);
    //     return m.invoke(null, new FileInputStream(f));
    // }

    // // Inject dummy network connection
    // private static void injectDummyConnection(Object entityPlayer) throws Exception {
    //     Class<?> ncClass = getNMS("PlayerConnection");

    //     Object netManager = makeDummyNetManager();
    //     Object pc = Proxy.newProxyInstance(
    //             ncClass.getClassLoader(),
    //             new Class[] { ncClass },
    //             (proxy, method, args) -> null);

    //     try {
    //         Field f = entityPlayer.getClass().getField("playerConnection");
    //         f.set(entityPlayer, pc);
    //         return;
    //     } catch (Exception ignored) {
    //     }

    //     Field[] fs = entityPlayer.getClass().getFields();
    //     for (Field f : fs) {
    //         if (f.getType().getSimpleName().contains("PlayerConnection")) {
    //             f.setAccessible(true);
    //             f.set(entityPlayer, pc);
    //             return;
    //         }
    //     }
    // }

    // private static Object makeDummyNetManager() throws Exception {
    //     Class<?> nmClass = getNMS("NetworkManager");

    //     for (Constructor<?> c : nmClass.getConstructors()) {
    //         if (c.getParameterCount() == 1) {
    //             return c.newInstance(getEnum(nmClass.getPackage().getName() + ".EnumProtocolDirection", "CLIENTBOUND"));
    //         }
    //     }

    //     return UnsafeAllocator.create(nmClass);
    // }

    // private static Player wrapBukkit(Object entityPlayer) throws Exception {
    //     Class<?> cpClass = Class.forName(Bukkit.getServer().getClass().getPackage().getName() + ".entity.CraftPlayer");
    //     Constructor<?> cc = cpClass.getDeclaredConstructors()[0];
    //     cc.setAccessible(true);

    //     return (Player) cc.newInstance(Bukkit.getServer(), entityPlayer);
    // }

    // private static Object getHandle(Player p) throws Exception {
    //     Method m = p.getClass().getMethod("getHandle");
    //     return m.invoke(p);
    // }

    // private static Object getPlayerList() throws Exception {
    //     Object server = getMinecraftServer();

    //     for (Method m : server.getClass().getMethods()) {
    //         if (m.getReturnType().getSimpleName().contains("PlayerList")) {
    //             return m.invoke(server);
    //         }
    //     }

    //     // Field alternative
    //     for (Field f : server.getClass().getFields()) {
    //         if (f.getType().getSimpleName().contains("PlayerList")) {
    //             return f.get(server);
    //         }
    //     }

    //     return null;
    // }

    // private static void tryRemoveFromPlayerList(Object playerList, Object entityPlayer) {
    //     try {
    //         Field f = playerList.getClass().getField("players");
    //         f.setAccessible(true);
    //         ((java.util.List<?>) f.get(playerList)).remove(entityPlayer);
    //     } catch (Throwable ignored) {
    //     }
    // }

    // // ---------------- REFLECTION HELPERS ---------------- //

    // private static void tryInvoke(Object o, String name, Object... args) {
    //     if (o == null)
    //         return;
    //     for (Method m : o.getClass().getMethods()) {
    //         if (!m.getName().equals(name))
    //             continue;
    //         if (m.getParameterCount() != args.length)
    //             continue;
    //         try {
    //             m.invoke(o, args);
    //             return;
    //         } catch (Throwable ignored) {
    //         }
    //     }
    // }

    // private static Class<?> getNMS(String name) throws Exception {
    //     String v = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
    //     try {
    //         return Class.forName("net.minecraft.server." + v + "." + name);
    //     } catch (ClassNotFoundException ignored) {
    //     }

    //     // Mojang-mapped
    //     return Class.forName("net.minecraft." + name.toLowerCase());
    // }

    // private static Object getEnum(String className, String constant) throws Exception {
    //     Class<?> c = Class.forName(className);
    //     return Enum.valueOf((Class<Enum>) c.asSubclass(Enum.class), constant);
    // }

    // // UnsafeAllocator
    // public static class UnsafeAllocator {
    //     public static Object create(Class<?> cls) throws Exception {
    //         Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    //         f.setAccessible(true);
    //         sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
    //         return unsafe.allocateInstance(cls);
    //     }
    // }
}
