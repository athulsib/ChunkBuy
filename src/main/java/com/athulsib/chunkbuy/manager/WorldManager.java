package com.athulsib.chunkbuy.manager;

import com.athulsib.chunkbuy.ChunkBuy;
import com.athulsib.chunkbuy.util.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages per-player void worlds.
 */
public class WorldManager {
    private final ChunkBuy plugin;
    private final ConfigManager configManager;

    public WorldManager(ChunkBuy plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * Gets or creates a player's world asynchronously.
     *
     * @param playerUUID The UUID of the player
     * @return CompletableFuture that completes with the player's world
     */
    public CompletableFuture<World> getOrCreatePlayerWorld(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            String worldName = configManager.getWorldNamePattern().replace("%player%", playerUUID.toString());
            World world = Bukkit.getWorld(worldName);

            if (world != null) {
                return world;
            }

            // Create new void world
            WorldCreator creator = new WorldCreator(worldName);
            creator.type(WorldType.FLAT);
            creator.generatorSettings("{\"layers\":[],\"biome\":\"minecraft:the_void\"}");
            creator.generateStructures(false);

            // World creation must happen on main thread
            return Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                World newWorld = creator.createWorld();
                if (newWorld != null) {
                    // Ensure initial spawn chunk exists
                    ensureInitialChunk(newWorld);
                }
                return newWorld;
            }).join();
        }, Bukkit.getAsyncScheduler().getExecutor(plugin));
    }

    /**
     * Gets a player's world synchronously (if already loaded).
     *
     * @param playerUUID The UUID of the player
     * @return The player's world, or null if not loaded
     */
    public World getPlayerWorld(UUID playerUUID) {
        String worldName = configManager.getWorldNamePattern().replace("%player%", playerUUID.toString());
        return Bukkit.getWorld(worldName);
    }

    /**
     * Ensures the initial chunk at 0,0 exists in the world.
     *
     * @param world The world to ensure the chunk in
     */
    private void ensureInitialChunk(World world) {
        if (world.getChunkAt(0, 0).isLoaded()) {
            return;
        }

        // Load chunk asynchronously
        world.getChunkAtAsync(0, 0).thenAccept(chunk -> {
            if (chunk != null) {
                plugin.getLogger().info("Initial chunk loaded for world: " + world.getName());
            }
        });
    }

    /**
     * Gets the world for a player, creating it if necessary.
     *
     * @param player The player
     * @return CompletableFuture that completes with the player's world
     */
    public CompletableFuture<World> getPlayerWorld(Player player) {
        return getOrCreatePlayerWorld(player.getUniqueId());
    }
}

