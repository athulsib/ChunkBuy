package com.athulsib.chunkbuy.command;

import com.athulsib.chunkbuy.ChunkBuy;
import com.athulsib.chunkbuy.gui.ChunkPurchaseGUI;
import com.athulsib.chunkbuy.manager.ChunkManager;
import com.athulsib.chunkbuy.manager.WorldManager;
import com.athulsib.chunkbuy.util.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Command handler for /chunkbuy
 */
public class ChunkBuyCommand implements CommandExecutor, TabCompleter {
    private final ChunkBuy plugin;
    private final ChunkManager chunkManager;
    private final WorldManager worldManager;
    private final ConfigManager configManager;
    private final ChunkPurchaseGUI gui;

    public ChunkBuyCommand(ChunkBuy plugin, ChunkManager chunkManager, WorldManager worldManager, ConfigManager configManager, ChunkPurchaseGUI gui) {
        this.plugin = plugin;
        this.chunkManager = chunkManager;
        this.worldManager = worldManager;
        this.configManager = configManager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("chunkbuy.reload")) {
                sender.sendMessage("§cYou don't have permission to use this command.");
                return true;
            }

            configManager.reload();
            sender.sendMessage("§aChunkBuy configuration reloaded!");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        gui.openGUI(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("chunkbuy.reload")) {
                completions.add("reload");
            }
        }

        return completions;
    }
}

