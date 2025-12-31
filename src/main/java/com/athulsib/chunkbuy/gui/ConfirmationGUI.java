package com.athulsib.chunkbuy.gui;

import com.athulsib.chunkbuy.ChunkBuy;
import com.athulsib.chunkbuy.generator.ChunkGeneratorService;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Confirmation GUI before finalizing chunk purchase.
 */
public class ConfirmationGUI implements Listener {
    private final ChunkBuy plugin;
    private final ChunkManager chunkManager;
    private final WorldManager worldManager;
    private final ConfigManager configManager;
    private final ChunkGeneratorService chunkGenerator;
    private final ChunkPurchaseGUI purchaseGUI;
    private final java.util.Map<UUID, List<ChunkCoordinate>> pendingPurchases;

    private static final int CONFIRM_SLOT = 11; // Row 2, Column 2
    private static final int CANCEL_SLOT = 15; // Row 2, Column 6

    public ConfirmationGUI(ChunkBuy plugin, ChunkManager chunkManager, WorldManager worldManager, ConfigManager configManager, ChunkPurchaseGUI purchaseGUI) {
        this.plugin = plugin;
        this.chunkManager = chunkManager;
        this.worldManager = worldManager;
        this.configManager = configManager;
        this.chunkGenerator = new ChunkGeneratorService(plugin, configManager);
        this.purchaseGUI = purchaseGUI;
        this.pendingPurchases = new java.util.HashMap<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens the confirmation GUI for a player.
     *
     * @param player           The player
     * @param selectedChunks   The chunks to purchase
     */
    public void openConfirmationGUI(Player player, List<ChunkCoordinate> selectedChunks) {
        pendingPurchases.put(player.getUniqueId(), selectedChunks);

        String title = configManager.getConfig().getString("gui.confirm-title", "Confirm Purchase");
        Inventory gui = Bukkit.createInventory(null, 27, title);

        // Fill with information
        fillGUI(gui, player, selectedChunks);

        player.openInventory(gui);
    }

    /**
     * Fills the GUI with items.
     */
    private void fillGUI(Inventory gui, Player player, List<ChunkCoordinate> chunks) {
        // Calculate total cost
        double totalCost = chunks.size() * configManager.getDefaultChunkPrice();

        // Info item
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§6Purchase Information");
            List<String> lore = new ArrayList<>();
            lore.add("§7Chunks to purchase: §f" + chunks.size());
            lore.add("§7Price per chunk: §f$" + String.format("%.2f", configManager.getDefaultChunkPrice()));
            lore.add("§7Total cost: §a$" + String.format("%.2f", totalCost));
            lore.add("");
            lore.add("§7Selected chunks:");
            for (ChunkCoordinate chunk : chunks) {
                lore.add("§7  • " + chunk.getX() + ", " + chunk.getZ());
            }
            infoMeta.setLore(lore);
            info.setItemMeta(infoMeta);
        }
        gui.setItem(13, info); // Center slot

        // Confirm button
        ItemStack confirm = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName("§a§lConfirm Purchase");
            confirmMeta.setLore(Arrays.asList(
                    "§7Click to confirm and",
                    "§7purchase the selected chunks"
            ));
            confirm.setItemMeta(confirmMeta);
        }
        gui.setItem(CONFIRM_SLOT, confirm);

        // Cancel button
        ItemStack cancel = new ItemStack(Material.RED_CONCRETE);
        ItemMeta cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName("§c§lCancel");
            cancelMeta.setLore(Arrays.asList(
                    "§7Click to cancel and",
                    "§7return to chunk selection"
            ));
            cancel.setItemMeta(cancelMeta);
        }
        gui.setItem(CANCEL_SLOT, cancel);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory inv = event.getInventory();

        // Check if this is our confirmation GUI
        String title = configManager.getConfig().getString("gui.confirm-title", "Confirm Purchase");
        if (!inv.getView().getTitle().equals(title)) return;

        event.setCancelled(true);

        int slot = event.getSlot();

        if (slot == CONFIRM_SLOT) {
            handleConfirm(player);
        } else if (slot == CANCEL_SLOT) {
            handleCancel(player);
        }
    }

    /**
     * Handles the confirm button click.
     */
    private void handleConfirm(Player player) {
        UUID playerUuid = player.getUniqueId();
        List<ChunkCoordinate> chunks = pendingPurchases.get(playerUuid);

        if (chunks == null || chunks.isEmpty()) {
            player.sendMessage("§cNo chunks to purchase!");
            player.closeInventory();
            return;
        }

        // TODO: Check economy (Vault integration - Phase 2)
        // For now, proceed with purchase

        player.closeInventory();
        player.sendMessage("§aProcessing purchase...");

        // Process purchase asynchronously
        processPurchase(player, chunks);
    }

    /**
     * Processes the chunk purchase.
     */
    private void processPurchase(Player player, List<ChunkCoordinate> chunks) {
        UUID playerUuid = player.getUniqueId();

        // Get or create player's world
        worldManager.getOrCreatePlayerWorld(playerUuid).thenAccept(world -> {
            if (world == null) {
                player.sendMessage("§cFailed to load your world!");
                return;
            }

            // Process chunks one by one
            processChunksSequentially(player, world, chunks, 0);
        });
    }

    /**
     * Processes chunks sequentially to avoid overwhelming the server.
     */
    private void processChunksSequentially(Player player, org.bukkit.World world, List<ChunkCoordinate> chunks, int index) {
        if (index >= chunks.size()) {
            player.sendMessage("§a§lPurchase complete! All chunks have been generated.");
            pendingPurchases.remove(player.getUniqueId());
            return;
        }

        ChunkCoordinate chunk = chunks.get(index);
        UUID playerUuid = player.getUniqueId();

        // Validate one more time
        String validation = chunkManager.validatePurchase(playerUuid, chunk);
        if (validation != null) {
            player.sendMessage("§cSkipping chunk " + chunk.getX() + ", " + chunk.getZ() + ": " + validation);
            processChunksSequentially(player, world, chunks, index + 1);
            return;
        }

        // Generate chunk
        chunkGenerator.generateChunk(world, chunk).thenAccept(success -> {
            if (success) {
                // Add to ownership
                chunkManager.addChunk(playerUuid, chunk);
                player.sendMessage("§aGenerated chunk: " + chunk.getX() + ", " + chunk.getZ());
            } else {
                player.sendMessage("§cFailed to generate chunk: " + chunk.getX() + ", " + chunk.getZ());
            }

            // Process next chunk
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                processChunksSequentially(player, world, chunks, index + 1);
            }, configManager.getChunksPerTick());
        });
    }

    /**
     * Handles the cancel button click.
     */
    private void handleCancel(Player player) {
        pendingPurchases.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage("§7Purchase cancelled.");

        // Reopen the purchase GUI
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            purchaseGUI.openGUI(player);
        }, 5L);
    }
}

