package com.hasirciogluhq.easymcadmin.rpc;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.Packet;
import com.hasirciogluhq.easymcadmin.packets.PacketType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * RPC Store - manages RPC handlers and their timeouts
 * Similar to Go backend's rpc/store.go
 */
public class RpcStore {
    private static RpcStore instance;
    private final Map<String, RpcHandler> handlers;
    private final ReentrantReadWriteLock lock;
    private BukkitRunnable cleanupTask;

    private RpcStore() {
        this.handlers = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Initialize RPC store (singleton pattern)
     * 
     * @return RpcStore instance
     */
    public static synchronized RpcStore initRpc() {
        if (instance == null) {
            instance = new RpcStore();
        }
        return instance;
    }

    /**
     * Get RPC store instance
     * 
     * @return RpcStore instance
     * @throws IllegalStateException if store is not initialized
     */
    public static RpcStore getRpcStore() {
        if (instance == null) {
            throw new IllegalStateException("RPC store not initialized");
        }
        return instance;
    }

    /**
     * Start the cleanup task that removes expired handlers
     * 
     * @param plugin Plugin instance for scheduling tasks
     */
    public void start(EasyMcAdmin plugin) {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }

        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (lock.writeLock().tryLock()) {
                    try {
                        handlers.entrySet().removeIf(entry -> entry.getValue().isExpired());
                    } finally {
                        lock.writeLock().unlock();
                    }
                }
            }
        };
        cleanupTask.runTaskTimerAsynchronously(plugin, 0L, 2L); // Every 100ms (2 ticks)
    }

    /**
     * Stop the cleanup task
     */
    public void stop() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    /**
     * Handle incoming RPC packet
     * 
     * @param packet RPC response packet
     * @throws IllegalArgumentException if packet is not RPC or handler not found
     */
    public void handlePacket(Packet packet) {
        if (packet.getPacketType() != PacketType.RPC) {
            throw new IllegalArgumentException("Packet is not an RPC packet");
        }

        if (!packet.getMetadata().has("rpc_id")) {
            throw new IllegalArgumentException("RPC packet missing rpc_id in metadata");
        }

        String rpcId = packet.getMetadata().get("rpc_id").getAsString();
        RpcHandler handler = getHandlerByRpcId(rpcId);
        
        if (handler == null) {
            throw new IllegalArgumentException("Handler not found for rpc_id: " + rpcId);
        }

        handler.getHandler().accept(packet);
    }

    /**
     * Register a handler for an RPC request
     * 
     * @param packet Request packet
     * @param handler RPC handler
     * @return Packet ID (used as RPC ID)
     */
    public String registerHandler(Packet packet, RpcHandler handler) {
        lock.writeLock().lock();
        try {
            handlers.put(packet.getPacketId(), handler);
            return packet.getPacketId();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get handler by packet ID
     * 
     * @param packetId Packet ID
     * @return RpcHandler or null if not found
     */
    public RpcHandler getHandlerForPacketId(String packetId) {
        lock.readLock().lock();
        try {
            return handlers.get(packetId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get handler by RPC ID (from metadata)
     * 
     * @param rpcId RPC ID from metadata
     * @return RpcHandler or null if not found
     */
    public RpcHandler getHandlerByRpcId(String rpcId) {
        lock.readLock().lock();
        try {
            return handlers.get(rpcId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Remove handler for a packet
     * 
     * @param packet Packet to remove handler for
     * @throws IllegalArgumentException if handler not found
     */
    public void removeHandler(Packet packet) {
        lock.writeLock().lock();
        try {
            if (!handlers.containsKey(packet.getPacketId())) {
                throw new IllegalArgumentException("Handler not found");
            }
            handlers.remove(packet.getPacketId());
        } finally {
            lock.writeLock().unlock();
        }
    }
}

