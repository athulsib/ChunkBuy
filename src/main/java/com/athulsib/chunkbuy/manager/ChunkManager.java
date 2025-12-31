package com.athulsib.chunkbuy.manager;

import com.athulsib.chunkbuy.ChunkBuy;
import com.athulsib.chunkbuy.model.ChunkCoordinate;
import com.athulsib.chunkbuy.util.ConfigManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages chunk ownership and persistence.
 */
public class ChunkManager {
    private final ChunkBuy plugin;
    private final ConfigManager configManager;
    private final Map<UUID, Set<ChunkCoordinate>> playerChunks;
    private File chunksFile;
    private FileConfiguration chunksConfig;

    public ChunkManager(ChunkBuy plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerChunks = new ConcurrentHashMap<>();
        loadChunks();
    }

    /**
     * Loads chunk ownership data from file.
     */
    public void loadChunks() {
        chunksFile = new File(plugin.getDataFolder(), "chunks.yml");
        if (!chunksFile.exists()) {
            try {
                chunksFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create chunks.yml: " + e.getMessage());
                return;
            }
        }

        chunksConfig = YamlConfiguration.loadConfiguration(chunksFile);
        playerChunks.clear();

        for (String playerUuidStr : chunksConfig.getKeys(false)) {
            try {
                UUID playerUuid = UUID.fromString(playerUuidStr);
                List<String> chunkList = chunksConfig.getStringList(playerUuidStr + ".chunks");
                Set<ChunkCoordinate> chunks = new HashSet<>();

                for (String chunkStr : chunkList) {
                    String[] parts = chunkStr.split(",");
                    if (parts.length == 2) {
                        try {
                            int x = Integer.parseInt(parts[0]);
                            int z = Integer.parseInt(parts[1]);
                            chunks.add(new ChunkCoordinate(x, z));
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid chunk coordinate: " + chunkStr);
                        }
                    }
                }

                playerChunks.put(playerUuid, chunks);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in chunks.yml: " + playerUuidStr);
            }
        }

        plugin.getLogger().info("Loaded chunk ownership for " + playerChunks.size() + " players");
    }

    /**
     * Saves chunk ownership data to file.
     */
    public void saveChunks() {
        if (chunksConfig == null) {
            chunksConfig = new YamlConfiguration();
        }

        for (Map.Entry<UUID, Set<ChunkCoordinate>> entry : playerChunks.entrySet()) {
            List<String> chunkList = new ArrayList<>();
            for (ChunkCoordinate coord : entry.getValue()) {
                chunkList.add(coord.getX() + "," + coord.getZ());
            }
            chunksConfig.set(entry.getKey().toString() + ".chunks", chunkList);
        }

        try {
            chunksConfig.save(chunksFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save chunks.yml: " + e.getMessage());
        }
    }

    /**
     * Checks if a chunk is owned by a player.
     *
     * @param playerUuid The player's UUID
     * @param chunk      The chunk coordinate
     * @return True if the player owns the chunk
     */
    public boolean isChunkOwned(UUID playerUuid, ChunkCoordinate chunk) {
        Set<ChunkCoordinate> chunks = playerChunks.get(playerUuid);
        return chunks != null && chunks.contains(chunk);
    }

    /**
     * Checks if a chunk is owned by any player.
     *
     * @param chunk The chunk coordinate
     * @return The UUID of the owner, or null if unowned
     */
    public UUID getChunkOwner(ChunkCoordinate chunk) {
        for (Map.Entry<UUID, Set<ChunkCoordinate>> entry : playerChunks.entrySet()) {
            if (entry.getValue().contains(chunk)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Adds a chunk to a player's ownership.
     *
     * @param playerUuid The player's UUID
     * @param chunk      The chunk coordinate
     * @return True if the chunk was added (not already owned)
     */
    public boolean addChunk(UUID playerUuid, ChunkCoordinate chunk) {
        // Check if already owned by someone else
        UUID owner = getChunkOwner(chunk);
        if (owner != null && !owner.equals(playerUuid)) {
            return false;
        }

        playerChunks.computeIfAbsent(playerUuid, k -> new HashSet<>()).add(chunk);
        saveChunks();
        return true;
    }

    /**
     * Gets all chunks owned by a player.
     *
     * @param playerUuid The player's UUID
     * @return Set of chunk coordinates owned by the player
     */
    public Set<ChunkCoordinate> getPlayerChunks(UUID playerUuid) {
        return playerChunks.getOrDefault(playerUuid, Collections.emptySet());
    }

    /**
     * Gets the number of chunks owned by a player.
     *
     * @param playerUuid The player's UUID
     * @return The number of chunks owned
     */
    public int getPlayerChunkCount(UUID playerUuid) {
        return getPlayerChunks(playerUuid).size();
    }

    /**
     * Validates if a player can purchase a chunk.
     *
     * @param playerUuid The player's UUID
     * @param chunk      The chunk coordinate
     * @return Validation result message, or null if valid
     */
    public String validatePurchase(UUID playerUuid, ChunkCoordinate chunk) {
        // Check if already owned
        UUID owner = getChunkOwner(chunk);
        if (owner != null && !owner.equals(playerUuid)) {
            return "This chunk is already owned by another player.";
        }

        if (owner != null && owner.equals(playerUuid)) {
            if (!configManager.getConfig().getBoolean("chunks.allow-repurchase", false)) {
                return "You already own this chunk.";
            }
        }

        // Check max chunks limit
        int maxChunks = configManager.getMaxChunksPerPlayer();
        if (maxChunks > 0 && getPlayerChunkCount(playerUuid) >= maxChunks) {
            return "You have reached the maximum chunk limit (" + maxChunks + ").";
        }

        return null; // Valid
    }
}

