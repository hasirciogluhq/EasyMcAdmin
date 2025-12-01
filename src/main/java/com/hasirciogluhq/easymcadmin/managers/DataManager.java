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

    // çoklu dosya -> JSON cache (memory)
    private final Map<String, Object> fileCache = new ConcurrentHashMap<>();

    // dosya lock'u (eş zamanlı dosya IO için gerekli)
    private final Object fileLock = new Object();

    public DataManager(EasyMcAdmin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        startAutoSave();
    }

    // -----------------------------------------------------
    // 📌 ENSURE LOADED (otomatik yükleyici + hata fırlatıcı)
    // -----------------------------------------------------
    private void ensureLoaded(String fileName) {
        // eğer memory'de varsa sıkıntı yok
        if (fileCache.containsKey(fileName))
            return;

        // memory'de yoksa yüklemeyi dene
        try {
            load(fileName);
        } catch (Exception e) {
            throw new RuntimeException("Data file '" + fileName + "' yüklenemedi!", e);
        }

        // hâlâ yoksa exception
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

                    // boş json oluştur
                    try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                        gson.toJson(new HashMap<>(), writer);
                    }
                }

                try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                    Map<String, Object> map = gson.fromJson(reader, Map.class);

                    if (map == null)
                        map = new HashMap<>();
                    fileCache.put(fileName, new ConcurrentHashMap<>(map));
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
    // ⏰ AUTO SAVE (2 dakikada bir)
    // -----------------------------------------------------
    private void startAutoSave() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (String fileName : fileCache.keySet()) {
                    save(fileName);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 120, 20L * 120);
    }

    // -----------------------------------------------------
    // ⚡ DATA ACCESS API
    // -----------------------------------------------------

    public Object getData(String fileName) {
        ensureLoaded(fileName);
        return fileCache.get(fileName);
    }
}
