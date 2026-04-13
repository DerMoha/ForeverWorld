package me.tension.foreverworld.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.tension.foreverworld.ForeverWorldPlugin;
import me.tension.foreverworld.model.SeasonRecord;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class ArchiveService {
    private final ForeverWorldPlugin plugin;

    public ArchiveService(ForeverWorldPlugin plugin) {
        this.plugin = plugin;
    }

    public void archivePlayer(Player player, SeasonRecord archivedSeason, int archiveSlot, int spacing, int rowWidth) {
        Location podBase = podBase(archivedSeason.archiveAnchor(), archiveSlot, spacing, rowWidth);
        Location floorBase = preparePad(podBase);

        List<ItemStack> carriedItems = new ArrayList<>();
        carriedItems.addAll(Arrays.asList(player.getInventory().getContents()));
        List<ItemStack> enderItems = Arrays.asList(player.getEnderChest().getContents());

        Chest chestOne = createChest(floorBase.clone().add(0, 0, 0), Component.text(player.getName() + " - carried 1"));
        Chest chestTwo = createChest(floorBase.clone().add(1, 0, 0), Component.text(player.getName() + " - carried 2"));
        Chest chestThree = createChest(floorBase.clone().add(2, 0, 0), Component.text(player.getName() + " - ender"));

        fillSequentially(List.of(chestOne, chestTwo), carriedItems);
        fillSequentially(List.of(chestThree), enderItems);
        placeMarkerSign(floorBase.clone().add(0, 0, 1), player.getName(), archivedSeason.name(), archiveSlot);
    }

    private Location podBase(Location anchor, int slot, int spacing, int rowWidth) {
        int column = slot % rowWidth;
        int row = slot / rowWidth;
        int baseX = anchor.getBlockX() + (column * spacing);
        int baseZ = anchor.getBlockZ() + (row * spacing);
        return new Location(anchor.getWorld(), baseX, anchor.getY(), baseZ);
    }

    private Location preparePad(Location base) {
        World world = base.getWorld();
        int highestY = 0;
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 1; z++) {
                highestY = Math.max(highestY, world.getHighestBlockYAt(base.getBlockX() + x, base.getBlockZ() + z));
            }
        }

        int y = highestY + 1;
        for (int x = -1; x <= 3; x++) {
            for (int z = -1; z <= 2; z++) {
                Block floor = world.getBlockAt(base.getBlockX() + x, y - 1, base.getBlockZ() + z);
                floor.setType(Material.STONE_BRICKS, false);

                world.getBlockAt(base.getBlockX() + x, y, base.getBlockZ() + z).setType(Material.AIR, false);
                world.getBlockAt(base.getBlockX() + x, y + 1, base.getBlockZ() + z).setType(Material.AIR, false);
            }
        }

        return new Location(world, base.getBlockX(), y, base.getBlockZ());
    }

    private Chest createChest(Location location, Component name) {
        Block block = location.getBlock();
        block.setType(Material.CHEST, false);
        Chest chest = (Chest) block.getState();
        chest.customName(name);
        chest.update(true, false);
        return (Chest) block.getState();
    }

    private void fillSequentially(List<Chest> chests, List<ItemStack> items) {
        int itemIndex = 0;
        for (Chest chest : chests) {
            ItemStack[] contents = new ItemStack[chest.getBlockInventory().getSize()];
            for (int slot = 0; slot < contents.length && itemIndex < items.size(); slot++) {
                ItemStack stack = items.get(itemIndex++);
                if (stack != null && stack.getType() != Material.AIR) {
                    contents[slot] = stack.clone();
                }
            }
            chest.getBlockInventory().setContents(contents);
        }
    }

    private void placeMarkerSign(Location location, String playerName, String seasonName, int slot) {
        Block block = location.getBlock();
        block.setType(Material.OAK_SIGN, false);
        if (!(block.getState() instanceof Sign sign)) {
            plugin.getLogger().warning("Could not create sign marker for archive slot " + slot + ".");
            return;
        }

        sign.setLine(0, playerName);
        sign.setLine(1, "Season: " + seasonName);
        sign.setLine(2, "Slot " + slot);
        sign.setLine(3, "ForeverWorld");
        sign.update(true, false);
    }
}
