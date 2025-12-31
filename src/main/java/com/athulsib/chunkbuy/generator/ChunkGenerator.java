package com.athulsib.chunkbuy.generator;

import com.athulsib.chunkbuy.ChunkBuy;
import com.athulsib.chunkbuy.model.ChunkCoordinate;
import com.athulsib.chunkbuy.util.ConfigManager;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Generates random biome chunks using FAWE for copying.
 */
public class ChunkGeneratorService {
    private final ChunkBuy plugin;
    private final ConfigManager configManager;
    private final Random random;

    public ChunkGeneratorService(ChunkBuy plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.random = new Random();
    }

    /**
     * Generates a random biome chunk at the target location.
     *
     * @param targetWorld The world to paste the chunk into
     * @param targetChunk The target chunk coordinate
     * @return CompletableFuture that completes when the chunk is generated
     */
    public CompletableFuture<Boolean> generateChunk(World targetWorld, ChunkCoordinate targetChunk) {
        return CompletableFuture.supplyAsync(() -> {
            World tempWorld = null;
            try {
                // Create temporary world with random biome
                tempWorld = createTempWorld();
                if (tempWorld == null) {
                    plugin.getLogger().severe("Failed to create temporary world");
                    return false;
                }

                // Generate chunk at 0,0 in temp world
                if (!generateChunkInTempWorld(tempWorld)) {
                    plugin.getLogger().severe("Failed to generate chunk in temp world");
                    return false;
                }

                // Copy chunk from temp world to target location using FAWE
                return copyChunkWithFAWE(tempWorld, targetWorld, targetChunk);
            } catch (Exception e) {
                plugin.getLogger().severe("Error generating chunk: " + e.getMessage());
                e.printStackTrace();
                return false;
            } finally {
                // Clean up temp world
                if (tempWorld != null) {
                    cleanupTempWorld(tempWorld);
                }
            }
        }, Bukkit.getAsyncScheduler().getExecutor(plugin));
    }

    /**
     * Creates a temporary world with a random biome.
     */
    private World createTempWorld() {
        String tempWorldName = "chunkbuy_temp_" + System.currentTimeMillis();
        org.bukkit.WorldCreator creator = new org.bukkit.WorldCreator(tempWorldName);
        creator.type(org.bukkit.WorldType.NORMAL);
        creator.generateStructures(true);

        // Use a random biome generator
        creator.generator(new RandomBiomeChunkGenerator());

        // World creation must happen on main thread
        return Bukkit.getScheduler().callSyncMethod(plugin, () -> {
            try {
                return creator.createWorld();
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to create temp world: " + e.getMessage());
                return null;
            }
        }).join();
    }

    /**
     * Generates a chunk at 0,0 in the temporary world.
     */
    private boolean generateChunkInTempWorld(World tempWorld) {
        // Use Paper's async chunk loading
        return tempWorld.getChunkAtAsync(0, 0).thenApply(chunk -> {
            if (chunk == null) {
                return false;
            }

            // Force generation by accessing blocks
            int chunkX = chunk.getX() << 4;
            int chunkZ = chunk.getZ() << 4;

            // Access a few blocks to ensure chunk is generated
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    tempWorld.getBlockAt(chunkX + x, 64, chunkZ + z);
                }
            }

            return true;
        }).join();
    }

    /**
     * Copies a chunk from temp world to target location using FAWE.
     */
    private boolean copyChunkWithFAWE(World tempWorld, World targetWorld, ChunkCoordinate targetChunk) {
        try {
            // Check if FAWE is available
            if (!plugin.getServer().getPluginManager().isPluginEnabled("FastAsyncWorldEdit")) {
                plugin.getLogger().warning("FAWE not available, using fallback method");
                return copyChunkDirect(tempWorld, targetWorld, targetChunk);
            }

            // Convert Bukkit worlds to WorldEdit worlds
            com.sk89q.worldedit.world.World weTempWorld = BukkitAdapter.asWorld(tempWorld);
            com.sk89q.worldedit.world.World weTargetWorld = BukkitAdapter.asWorld(targetWorld);

            // Define source region (chunk at 0,0 in temp world)
            int chunkX = 0;
            int chunkZ = 0;
            BlockVector3 sourceMin = BlockVector3.at(chunkX << 4, -64, chunkZ << 4);
            BlockVector3 sourceMax = BlockVector3.at((chunkX << 4) + 15, 319, (chunkZ << 4) + 15);

            // Define target location
            int targetChunkX = targetChunk.getX();
            int targetChunkZ = targetChunk.getZ();
            BlockVector3 targetMin = BlockVector3.at(targetChunkX << 4, -64, targetChunkZ << 4);

            // Use EditSession to copy blocks directly (FAWE optimized)
            try (EditSession sourceSession = WorldEdit.getInstance().newEditSession(weTempWorld)) {
                try (EditSession targetSession = WorldEdit.getInstance().newEditSession(weTargetWorld)) {
                    // Copy blocks from source to target using FAWE's optimized methods
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = -64; y <= 319; y++) {
                                BlockVector3 sourcePos = sourceMin.add(x, y + 64, z);
                                BlockVector3 targetPos = targetMin.add(x, y + 64, z);
                                
                                try {
                                    // Try using BlockState (newer WorldEdit)
                                    com.sk89q.worldedit.world.block.BlockState block = sourceSession.getBlock(sourcePos);
                                    targetSession.setBlock(targetPos, block);
                                } catch (Exception e) {
                                    // Fallback to BaseBlock (older WorldEdit)
                                    com.sk89q.worldedit.blocks.BaseBlock block = sourceSession.getLazyBlock(sourcePos).toBaseBlock();
                                    targetSession.setBlock(targetPos, block);
                                }
                            }
                        }
                    }
                    targetSession.flushQueue();
                }
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("FAWE copy failed: " + e.getMessage());
            e.printStackTrace();
            // Fallback to direct block copying
            return copyChunkDirect(tempWorld, targetWorld, targetChunk);
        }
    }

    /**
     * Fallback method using direct block copying (no FAWE).
     */
    private boolean copyChunkDirect(World tempWorld, World targetWorld, ChunkCoordinate targetChunk) {
        try {
            // Direct block copying as fallback
            int sourceChunkX = 0;
            int sourceChunkZ = 0;
            int targetChunkX = targetChunk.getX();
            int targetChunkZ = targetChunk.getZ();

            int sourceWorldX = sourceChunkX << 4;
            int sourceWorldZ = sourceChunkZ << 4;
            int targetWorldX = targetChunkX << 4;
            int targetWorldZ = targetChunkZ << 4;

            // Copy blocks directly
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = -64; y <= 319; y++) {
                        org.bukkit.block.Block sourceBlock = tempWorld.getBlockAt(
                                sourceWorldX + x, y, sourceWorldZ + z);
                        org.bukkit.block.Block targetBlock = targetWorld.getBlockAt(
                                targetWorldX + x, y, targetWorldZ + z);

                        targetBlock.setType(sourceBlock.getType());
                        targetBlock.setBlockData(sourceBlock.getBlockData());
                    }
                }
            }

            // Copy biome data
            org.bukkit.Chunk sourceChunk = tempWorld.getChunkAt(sourceChunkX, sourceChunkZ);
            org.bukkit.Chunk targetChunkObj = targetWorld.getChunkAt(targetChunkX, targetChunkZ);

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = -64; y <= 319; y++) {
                        org.bukkit.block.Biome biome = sourceChunk.getBiome(x, y, z);
                        targetChunkObj.setBiome(x, y, z, biome);
                    }
                }
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Direct chunk copy failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Cleans up the temporary world.
     */
    private void cleanupTempWorld(World tempWorld) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Bukkit.unloadWorld(tempWorld, false);
                File worldFolder = tempWorld.getWorldFolder();
                if (worldFolder.exists()) {
                    deleteDirectory(worldFolder);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to cleanup temp world: " + e.getMessage());
            }
        });
    }

    /**
     * Deletes a directory recursively.
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    /**
     * Custom chunk generator that generates random biomes.
     */
    private static class RandomBiomeChunkGenerator extends ChunkGenerator {
        private static final Biome[] BIOMES = Biome.values();
        private final Random random = new Random();

        @Override
        public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
            // Generate a random biome for this chunk
            Biome biome = BIOMES[this.random.nextInt(BIOMES.length)];
            
            // Set biome for entire chunk
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = worldInfo.getMinHeight(); y < worldInfo.getMaxHeight(); y++) {
                        chunkData.setBiome(x, y, z, biome);
                    }
                }
            }

            // Generate basic terrain
            int seaLevel = 64;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int height = seaLevel + this.random.nextInt(10) - 5;
                    for (int y = worldInfo.getMinHeight(); y <= height; y++) {
                        if (y == height) {
                            chunkData.setBlock(x, y, z, Material.GRASS_BLOCK);
                        } else if (y > height - 4) {
                            chunkData.setBlock(x, y, z, Material.DIRT);
                        } else {
                            chunkData.setBlock(x, y, z, Material.STONE);
                        }
                    }
                }
            }
        }

        @Override
        public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
            return new BiomeProvider() {
                @Override
                public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                    return BIOMES[random.nextInt(BIOMES.length)];
                }

                @Override
                public List<Biome> getBiomes(WorldInfo worldInfo) {
                    return List.of(BIOMES);
                }
            };
        }
    }
}

