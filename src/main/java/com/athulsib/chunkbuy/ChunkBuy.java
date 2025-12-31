package com.athulsib.chunkbuy;

import com.athulsib.chunkbuy.command.ChunkBuyCommand;
import com.athulsib.chunkbuy.gui.ChunkPurchaseGUI;
import com.athulsib.chunkbuy.manager.ChunkManager;
import com.athulsib.chunkbuy.manager.WorldManager;
import com.athulsib.chunkbuy.util.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkBuy extends JavaPlugin {

    private ConfigManager configManager;
    private ChunkManager chunkManager;
    private WorldManager worldManager;
    private ChunkPurchaseGUI purchaseGUI;

    @Override
    public void onEnable() {
        // Initialize configuration
        configManager = new ConfigManager(this);

        // Initialize managers
        chunkManager = new ChunkManager(this, configManager);
        worldManager = new WorldManager(this, configManager);

        // Initialize GUI
        purchaseGUI = new ChunkPurchaseGUI(this, chunkManager, worldManager, configManager);

        // Register command
        ChunkBuyCommand command = new ChunkBuyCommand(this, chunkManager, worldManager, configManager, purchaseGUI);
        org.bukkit.command.PluginCommand chunkBuyCommand = getCommand("chunkbuy");
        if (chunkBuyCommand != null) {
            chunkBuyCommand.setExecutor(command);
            chunkBuyCommand.setTabCompleter(command);
        } else {
            getLogger().warning("Failed to register chunkbuy command - command not found in plugin.yml");
        }

        getLogger().info("ChunkBuy has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save chunk data
        if (chunkManager != null) {
            chunkManager.saveChunks();
        }

        getLogger().info("ChunkBuy has been disabled!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ChunkManager getChunkManager() {
        return chunkManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }
}
