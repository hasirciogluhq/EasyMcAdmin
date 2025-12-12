package com.hasirciogluhq.easymcadmin.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.scheduler.BukkitRunnable;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DataManager {

    private final EasyMcAdmin plugin;
    private final Gson gson;

    // multiple files -> JSON cache (memory)
    private BukkitRunnable autoSaveTask;
    private final Map<String, Object> fileCache = new ConcurrentHashMap<>();

    // file lock (required for concurrent file IO)
    private final Object fileLock = new Object();
    // pending debounced save tasks per file
    private final Map<String, BukkitRunnable> pendingSaveTasks = new ConcurrentHashMap<>();

    public DataManager(EasyMcAdmin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        startAutoSave(); // Start auto save with configurable interval
    }

    // -----------------------------------------------------
    // 📌 ENSURE LOADED (auto loader + exception thrower)
    // -----------------------------------------------------
    private void ensureLoaded(String fileName) {
        // if it's already in memory, no problem
        if (fileCache.containsKey(fileName))
            return;

        // if it's not in memory, try loading it
        try {
            load(fileName);
        } catch (Exception e) {
            throw new RuntimeException("Data file '" + fileName + "' yüklenemedi!", e);
        }

        // if still missing, throw exception
        if (!fileCache.containsKey(fileName)) {
            throw new RuntimeException("Data file '" + fileName + "' cache'e eklenemedi!");
        }
    }

    // -----------------------------------------------------
    // 📌 SYNC LOAD
    // -----------------------------------------------------
    public void load(String fileName) {
        synchronized (fileLock) {
            try {
                File file = new File(plugin.getDataFolder(), fileName);

                if (!file.exists()) {
                    plugin.getDataFolder().mkdirs();
                    file.createNewFile();

                    // create an empty json
                    try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                        gson.toJson(new HashMap<>(), writer);
                    }
                }

                try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                    Map<String, Object> map = gson.fromJson(reader, Map.class);

                    if (map == null)
                        map = new HashMap<>();
                    // Wrap the loaded map into an AutoSavingMap so direct mutations auto-save
                    AutoSavingMap<String, Object> asm = new AutoSavingMap<>(fileName, this);
                    // Put entries with nested wrapping (lists/maps)
                    for (Map.Entry<String, Object> e : map.entrySet()) {
                        asm.put(e.getKey(), wrapValue(e.getValue(), fileName));
                    }
                    fileCache.put(fileName, asm);
                }

            } catch (Exception e) {
                throw new RuntimeException("Data file '" + fileName + "' yüklenirken hata oluştu!", e);
            }
        }
    }

    // -----------------------------------------------------
    // 📌 ASYNC LOAD
    // -----------------------------------------------------
    public void loadAsync(String fileName, Runnable callback) {
        new BukkitRunnable() {
            @Override
            public void run() {
                load(fileName);
                if (callback != null)
                    callback.run();
            }
        }.runTaskAsynchronously(plugin);
    }

    // -----------------------------------------------------
    // 📌 SYNC SAVE
    // -----------------------------------------------------
    public void save(String fileName) {
        synchronized (fileLock) {
            try {
                File file = new File(plugin.getDataFolder(), fileName);

                Object data = fileCache.getOrDefault(fileName, new ConcurrentHashMap<>());

                try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                    gson.toJson(data, writer);
                }

            } catch (Exception e) {
                throw new RuntimeException("Data file '" + fileName + "' kaydedilirken hata oluştu!", e);
            }
        }
    }

    // -----------------------------------------------------
    // 📌 ASYNC SAVE
    // -----------------------------------------------------
    public void saveAsync(String fileName) {
        new BukkitRunnable() {
            @Override
            public void run() {
                save(fileName);
            }
        }.runTaskAsynchronously(plugin);
    }

    // -----------------------------------------------------
    // ⏰ AUTO SAVE (every 2 minutes)
    // -----------------------------------------------------
    private void startAutoSave() {
        int intervalSeconds = 120;
        try {
            intervalSeconds = Math.max(5, plugin.getConfig().getInt("data.autosave_interval_seconds", 120));
        } catch (Exception ignored) {
            intervalSeconds = 120;
        }

        final int finalInterval = intervalSeconds;
        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (String fileName : fileCache.keySet()) {
                    try {
                        save(fileName);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to autosave '" + fileName + "': " + e.getMessage());
                    }
                }
            }
        };
        autoSaveTask.runTaskTimerAsynchronously(plugin, 20L * finalInterval, 20L * finalInterval);
    }

    public void stopAutoSave() {
        if (autoSaveTask != null) {
            try {
                autoSaveTask.cancel();
            } catch (Exception ignored) {
            }
            autoSaveTask = null;
        }
        // cancel any pending debounced saves
        for (BukkitRunnable r : pendingSaveTasks.values()) {
            try {
                r.cancel();
            } catch (Exception ignored) {
            }
        }
        pendingSaveTasks.clear();
    }

    /**
     * Force save all cached data synchronously.
     */
    public void saveAll() {
        synchronized (fileLock) {
            // cancel pending debounced saves and save synchronously
            for (String fileName : fileCache.keySet()) {
                cancelPendingSave(fileName);
                save(fileName);
            }
        }
    }

    /**
     * Schedule a debounced save for the given file. Repeated calls within the
     * debounce window will cancel the previous scheduled save and schedule a new
     * one, effectively aggregating rapid updates.
     */
    public void scheduleDebouncedSave(String fileName) {
        // read debounce from config (milliseconds)
        int debounceMs = 1000;
        try {
            debounceMs = Math.max(50, plugin.getConfig().getInt("data.save_debounce_millis", 1000));
        } catch (Exception ignored) {
        }

        // convert to ticks (50 ms per tick)
        long ticks = Math.max(1L, debounceMs / 50L);

        // cancel previous task if exists
        cancelPendingSave(fileName);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    save(fileName);
                } catch (Exception e) {
                    plugin.getLogger().warning("Debounced save failed for '" + fileName + "': " + e.getMessage());
                } finally {
                    pendingSaveTasks.remove(fileName);
                }
            }
        };

        pendingSaveTasks.put(fileName, task);
        task.runTaskLaterAsynchronously(plugin, ticks);
    }

    private void cancelPendingSave(String fileName) {
        BukkitRunnable prev = pendingSaveTasks.remove(fileName);
        if (prev != null) {
            try {
                prev.cancel();
            } catch (Exception ignored) {
            }
        }
    }
    // -----------------------------------------------------
    // ⚡ DATA ACCESS API
    // -----------------------------------------------------

    public Object getData(String fileName) {
        ensureLoaded(fileName);
        return fileCache.get(fileName);
    }

    /**
     * Internal live map that auto-saves on mutating operations.
     */
    private static class AutoSavingMap<K, V> extends ConcurrentHashMap<K, V> {
        private final String fileName;
        private final DataManager manager;

        AutoSavingMap(String fileName, DataManager manager) {
            super();
            this.fileName = fileName;
            this.manager = manager;
        }

        @Override
        public V put(K key, V value) {
            V v = super.put(key, value);
            try {
                manager.scheduleDebouncedSave(fileName);
            } catch (Exception ignored) {
            }
            return v;
        }

        @Override
        public void putAll(Map<? extends K, ? extends V> m) {
            super.putAll(m);
            try {
                manager.scheduleDebouncedSave(fileName);
            } catch (Exception ignored) {
            }
        }

        @Override
        public V remove(Object key) {
            V v = super.remove(key);
            try {
                manager.scheduleDebouncedSave(fileName);
            } catch (Exception ignored) {
            }
            return v;
        }

        @Override
        public void clear() {
            super.clear();
            try {
                manager.scheduleDebouncedSave(fileName);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * List implementation that triggers saveAsync on mutating operations.
     */
    private static class AutoSavingList<E> extends ArrayList<E> {
        private final String fileName;
        private final DataManager manager;

        AutoSavingList(String fileName, DataManager manager) {
            super();
            this.fileName = fileName;
            this.manager = manager;
        }

        @Override
        public boolean add(E e) {
            boolean r = super.add(e);
            try {
                manager.scheduleDebouncedSave(fileName);
            } catch (Exception ignored) {
            }
            return r;
        }

        @Override
        public void add(int index, E element) {
            super.add(index, element);
            try {
                manager.scheduleDebouncedSave(fileName);
            } catch (Exception ignored) {
            }
        }

        @Override
        public boolean addAll(Collection<? extends E> c) {
            boolean r = super.addAll(c);
            try {
                manager.scheduleDebouncedSave(fileName);
            } catch (Exception ignored) {
            }
            return r;
        }

        @Override
        public E remove(int index) {
            E v = super.remove(index);
            try {
                manager.scheduleDebouncedSave(fileName);
            } catch (Exception ignored) {
            }
            return v;
        }

        @Override
        public boolean remove(Object o) {
            boolean r = super.remove(o);
            try {
                manager.scheduleDebouncedSave(fileName);
            } catch (Exception ignored) {
            }
            return r;
        }

        @Override
        public void clear() {
            super.clear();
            try {
                manager.scheduleDebouncedSave(fileName);
            } catch (Exception ignored) {
            }
        }

        @Override
        public E set(int index, E element) {
            E v = super.set(index, element);
            try {
                manager.scheduleDebouncedSave(fileName);
            } catch (Exception ignored) {
            }
            return v;
        }
    }

    public void setData(String fileName, Object data) {
        ensureLoaded(fileName);
        if (data instanceof Map) {
            AutoSavingMap<String, Object> asm = new AutoSavingMap<>(fileName, this);
            Map m = (Map) data;
            for (Object ok : m.keySet()) {
                String k = String.valueOf(ok);
                asm.put(k, wrapValue(m.get(ok), fileName));
            }
            fileCache.put(fileName, asm);
        } else {
            fileCache.put(fileName, data != null ? data : new ConcurrentHashMap<>());
        }
        scheduleDebouncedSave(fileName);
    }

    /**
     * Wrap nested List/Map values into auto-saving wrappers so mutations persist.
     */
    @SuppressWarnings("unchecked")
    private Object wrapValue(Object value, String fileName) {
        if (value instanceof Map) {
            AutoSavingMap<String, Object> asm = new AutoSavingMap<>(fileName, this);
            Map m = (Map) value;
            for (Object ok : m.keySet()) {
                String k = String.valueOf(ok);
                asm.put(k, wrapValue(m.get(ok), fileName));
            }
            return asm;
        }

        if (value instanceof List) {
            AutoSavingList<Object> asl = new AutoSavingList<>(fileName, this);
            List list = (List) value;
            for (Object item : list) {
                asl.add(wrapValue(item, fileName));
            }
            return asl;
        }

        return value;
    }

    /*
     * Returns the data directly as a Map.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDataMap(String fileName) {
        Object d = getData(fileName);
        if (d instanceof Map) {
            return (Map<String, Object>) d;
        }
        // If the stored value isn't a map, replace it with an empty AutoSavingMap
        AutoSavingMap<String, Object> asm = new AutoSavingMap<>(fileName, this);
        fileCache.put(fileName, asm);
        return asm;
    }

    /**
     * Returns the value for a specific key.
     */
    public Object get(String fileName, String key) {
        Map<String, Object> data = getDataMap(fileName);
        return data.get(key);
    }

    /**
     * Writes a value for a key and updates the cache.
     * (Does not write to disk immediately; auto-save or saveAsync is needed)
     */
    public void set(String fileName, String key, Object value) {
        Map<String, Object> data = getDataMap(fileName);
        data.put(key, value);
        // Persist change asynchronously so callers don't need to call save explicitly.
        try {
            scheduleDebouncedSave(fileName);
        } catch (Exception ignored) {
        }
    }

    /**
     * Safely retrieves a list of strings.
     */
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String fileName, String key) {
        Object val = get(fileName, key);
        if (val instanceof List) {
            return (List<String>) val;
        }
        return new ArrayList<>();
    }
}
