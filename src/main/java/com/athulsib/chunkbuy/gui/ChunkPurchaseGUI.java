package com.athulsib.chunkbuy.gui;

import com.athulsib.chunkbuy.ChunkBuy;
import com.athulsib.chunkbuy.manager.ChunkManager;
import com.athulsib.chunkbuy.manager.WorldManager;
import com.athulsib.chunkbuy.model.ChunkCoordinate;
import com.athulsib.chunkbuy.util.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * GUI for purchasing chunks.
 * Layout: 9x6 (54 slots)
 * Center slot (22) = Player's current chunk
 * Surrounding slots = Adjacent chunks
 * Bottom row = Navigation buttons + Confirm
 */
public class ChunkPurchaseGUI implements Listener {
    private final ChunkBuy plugin;
    private final ChunkManager chunkManager;
    private final WorldManager worldManager;
    private final ConfigManager configManager;
    private final Map<UUID, ChunkCoordinate> playerViewCenter; // Player's current view center chunk
    private final Map<UUID, Set<ChunkCoordinate>> selectedChunks; // Selected chunks per player

    // GUI slot constants
    private static final int GUI_SIZE = 54;
    private static final int CENTER_SLOT = 22; // Row 3, Column 5 (0-indexed: row 2, col 4)
    private static final int NAV_NORTH = 45; // Row 6, Column 1
    private static final int NAV_SOUTH = 46; // Row 6, Column 2
    private static final int NAV_EAST = 47; // Row 6, Column 3
    private static final int NAV_WEST = 48; // Row 6, Column 4
    private static final int CONFIRM_BUTTON = 49; // Row 6, Column 5

    public ChunkPurchaseGUI(ChunkBuy plugin, ChunkManager chunkManager, WorldManager worldManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.chunkManager = chunkManager;
        this.worldManager = worldManager;
        this.configManager = configManager;
        this.playerViewCenter = new HashMap<>();
        this.selectedChunks = new HashMap<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens the chunk purchase GUI for a player.
     *
     * @param player The player
     */
    public void openGUI(Player player) {
        // Get player's current chunk
        ChunkCoordinate currentChunk = getPlayerChunk(player);
        playerViewCenter.put(player.getUniqueId(), currentChunk);
        selectedChunks.put(player.getUniqueId(), new HashSet<>());

        Inventory gui = createGUI(player, currentChunk);
        player.openInventory(gui);
    }

    /**
     * Creates the GUI inventory.
     */
    private Inventory createGUI(Player player, ChunkCoordinate centerChunk) {
        String title = configManager.getConfig().getString("gui.title", "Purchase Chunks");
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);

        // Fill with chunk slots
        int viewDistance = configManager.getGuiViewDistance();
        int startRow = 1; // Row 2 (0-indexed: 1)
        int startCol = 2; // Column 3 (0-indexed: 2)
        int gridWidth = 5;
        int gridHeight = 3;

        for (int row = 0; row < gridHeight; row++) {
            for (int col = 0; col < gridWidth; col++) {
                int slot = (startRow + row) * 9 + (startCol + col);
                int chunkOffsetX = col - 2; // -2 to +2
                int chunkOffsetZ = row - 1; // -1 to +1

                ChunkCoordinate chunk = new ChunkCoordinate(
                        centerChunk.getX() + chunkOffsetX,
                        centerChunk.getZ() + chunkOffsetZ
                );

                ItemStack item = createChunkItem(player, chunk, centerChunk.equals(chunk));
                gui.setItem(slot, item);
            }
        }

        // Add navigation buttons
        gui.setItem(NAV_NORTH, createNavigationButton("North", Material.ARROW));
        gui.setItem(NAV_SOUTH, createNavigationButton("South", Material.ARROW));
        gui.setItem(NAV_EAST, createNavigationButton("East", Material.ARROW));
        gui.setItem(NAV_WEST, createNavigationButton("West", Material.ARROW));

        // Add confirm button
        Set<ChunkCoordinate> selected = selectedChunks.get(player.getUniqueId());
        gui.setItem(CONFIRM_BUTTON, createConfirmButton(selected != null && !selected.isEmpty()));

        return gui;
    }

    /**
     * Creates an item representing a chunk slot.
     */
    private ItemStack createChunkItem(Player player, ChunkCoordinate chunk, boolean isCenter) {
        UUID playerUuid = player.getUniqueId();
        boolean isOwned = chunkManager.isChunkOwned(playerUuid, chunk);
        boolean isSelected = selectedChunks.getOrDefault(playerUuid, Collections.emptySet()).contains(chunk);

        Material material;
        String name;
        List<String> lore = new ArrayList<>();

        if (isCenter) {
            // Player's head in center
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(player);
                meta.setDisplayName("§6Your Current Chunk");
                lore.add("§7Chunk: §f" + chunk.getX() + ", " + chunk.getZ());
                if (isOwned) {
                    lore.add("§a✓ You own this chunk");
                } else {
                    lore.add("§c✗ Not owned");
                }
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            return head;
        } else if (isSelected) {
            // Green head for selected
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§aSelected Chunk");
                lore.add("§7Chunk: §f" + chunk.getX() + ", " + chunk.getZ());
                if (isOwned) {
                    lore.add("§a✓ You own this chunk");
                } else {
                    lore.add("§eClick to purchase");
                }
                lore.add("§7Right-click to deselect");
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            return head;
        } else if (isOwned) {
            material = Material.GREEN_CONCRETE;
            name = "§aOwned Chunk";
            lore.add("§7Chunk: §f" + chunk.getX() + ", " + chunk.getZ());
            lore.add("§a✓ You own this chunk");
        } else {
            material = Material.GRAY_CONCRETE;
            name = "§7Available Chunk";
            lore.add("§7Chunk: §f" + chunk.getX() + ", " + chunk.getZ());
            lore.add("§eLeft-click to select");
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates a navigation button.
     */
    private ItemStack createNavigationButton(String direction, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Navigate " + direction);
            meta.setLore(Arrays.asList("§7Click to view chunks", "§7in the " + direction.toLowerCase() + " direction"));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates the confirm button.
     */
    private ItemStack createConfirmButton(boolean hasSelection) {
        Material material = hasSelection ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (hasSelection) {
                meta.setDisplayName("§a§lConfirm Purchase");
                meta.setLore(Arrays.asList("§7Click to confirm", "§7and purchase selected chunks"));
            } else {
                meta.setDisplayName("§7No Chunks Selected");
                meta.setLore(Arrays.asList("§7Select chunks to purchase first"));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Gets the chunk coordinate the player is currently in.
     */
    private ChunkCoordinate getPlayerChunk(Player player) {
        org.bukkit.Chunk chunk = player.getLocation().getChunk();
        return new ChunkCoordinate(chunk.getX(), chunk.getZ());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory inv = event.getInventory();

        // Check if this is our GUI
        String title = configManager.getConfig().getString("gui.title", "Purchase Chunks");
        if (!inv.getView().getTitle().equals(title)) return;

        event.setCancelled(true);

        int slot = event.getSlot();
        if (slot < 0 || slot >= GUI_SIZE) return;

        // Handle navigation buttons
        if (slot == NAV_NORTH) {
            navigate(player, 0, -1);
            return;
        } else if (slot == NAV_SOUTH) {
            navigate(player, 0, 1);
            return;
        } else if (slot == NAV_EAST) {
            navigate(player, 1, 0);
            return;
        } else if (slot == NAV_WEST) {
            navigate(player, -1, 0);
            return;
        }

        // Handle confirm button
        if (slot == CONFIRM_BUTTON) {
            handleConfirm(player);
            return;
        }

        // Handle chunk selection (only in chunk grid area)
        if (isChunkSlot(slot)) {
            ChunkCoordinate clickedChunk = getChunkAtSlot(player, slot);
            if (clickedChunk != null && !clickedChunk.equals(getPlayerChunk(player))) {
                if (event.isLeftClick()) {
                    selectChunk(player, clickedChunk);
                } else if (event.isRightClick()) {
                    deselectChunk(player, clickedChunk);
                }
            }
        }
    }

    /**
     * Checks if a slot is in the chunk grid area.
     */
    private boolean isChunkSlot(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        return row >= 1 && row <= 3 && col >= 2 && col <= 6;
    }

    /**
     * Gets the chunk coordinate at a specific slot.
     */
    private ChunkCoordinate getChunkAtSlot(Player player, int slot) {
        ChunkCoordinate center = playerViewCenter.get(player.getUniqueId());
        if (center == null) return null;

        int row = slot / 9;
        int col = slot % 9;
        int startRow = 1;
        int startCol = 2;
        int chunkOffsetX = (col - startCol) - 2;
        int chunkOffsetZ = (row - startRow) - 1;

        return new ChunkCoordinate(center.getX() + chunkOffsetX, center.getZ() + chunkOffsetZ);
    }

    /**
     * Selects a chunk for purchase.
     */
    private void selectChunk(Player player, ChunkCoordinate chunk) {
        UUID playerUuid = player.getUniqueId();
        Set<ChunkCoordinate> selected = selectedChunks.computeIfAbsent(playerUuid, k -> new HashSet<>());

        // Validate purchase
        String validation = chunkManager.validatePurchase(playerUuid, chunk);
        if (validation != null) {
            player.sendMessage("§c" + validation);
            return;
        }

        selected.add(chunk);
        refreshGUI(player);
        player.sendMessage("§aChunk selected: " + chunk.getX() + ", " + chunk.getZ());
    }

    /**
     * Deselects a chunk.
     */
    private void deselectChunk(Player player, ChunkCoordinate chunk) {
        UUID playerUuid = player.getUniqueId();
        Set<ChunkCoordinate> selected = selectedChunks.get(playerUuid);
        if (selected != null) {
            selected.remove(chunk);
            refreshGUI(player);
            player.sendMessage("§7Chunk deselected: " + chunk.getX() + ", " + chunk.getZ());
        }
    }

    /**
     * Navigates the GUI view.
     */
    private void navigate(Player player, int dx, int dz) {
        ChunkCoordinate current = playerViewCenter.get(player.getUniqueId());
        if (current != null) {
            ChunkCoordinate newCenter = new ChunkCoordinate(current.getX() + dx, current.getZ() + dz);
            playerViewCenter.put(player.getUniqueId(), newCenter);
            refreshGUI(player);
        }
    }

    /**
     * Handles the confirm button click.
     */
    private void handleConfirm(Player player) {
        UUID playerUuid = player.getUniqueId();
        Set<ChunkCoordinate> selected = selectedChunks.get(playerUuid);
        if (selected == null || selected.isEmpty()) {
            player.sendMessage("§cNo chunks selected!");
            return;
        }

        // Open confirmation GUI
        // Get confirmation GUI from plugin (we'll need to add it to main class)
        // For now, create it here - this should be refactored to use a singleton
        ConfirmationGUI confirmationGUI = new ConfirmationGUI(plugin, chunkManager, worldManager, configManager, this);
        confirmationGUI.openConfirmationGUI(player, new ArrayList<>(selected));
    }

    /**
     * Refreshes the GUI for a player.
     */
    private void refreshGUI(Player player) {
        ChunkCoordinate center = playerViewCenter.get(player.getUniqueId());
        if (center == null) return;

        Inventory gui = createGUI(player, center);
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        // Clean up player data after a delay (in case they reopen quickly)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getViewers().isEmpty()) {
                playerViewCenter.remove(player.getUniqueId());
                selectedChunks.remove(player.getUniqueId());
            }
        }, 20L);
    }
}

