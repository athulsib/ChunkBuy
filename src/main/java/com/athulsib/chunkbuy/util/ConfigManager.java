package com.athulsib.chunkbuy.util;

import com.athulsib.chunkbuy.ChunkBuy;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

/**
 * Manages plugin configuration.
 */
public class ConfigManager {
    private final ChunkBuy plugin;
    private FileConfiguration config;

    public ConfigManager(ChunkBuy plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public void reload() {
        loadConfig();
    }

    public String getWorldNamePattern() {
        return config.getString("world.name-pattern", "chunkbuy_%player%");
    }

    public double getDefaultChunkPrice() {
        return config.getDouble("economy.default-chunk-price", 1000.0);
    }

    public int getMaxChunksPerPlayer() {
        return config.getInt("chunks.max-per-player", -1); // -1 means unlimited
    }

    public boolean isAsyncGenerationEnabled() {
        return config.getBoolean("performance.async-generation", true);
    }

    public int getChunksPerTick() {
        return config.getInt("performance.chunks-per-tick", 1);
    }

    public int getGuiViewDistance() {
        return config.getInt("gui.view-distance", 2); // 2 chunks in each direction
    }

    public FileConfiguration getConfig() {
        return config;
    }
}

