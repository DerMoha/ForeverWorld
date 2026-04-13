package me.tension.foreverworld.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.tension.foreverworld.ForeverWorldPlugin;
import me.tension.foreverworld.model.PendingReset;
import me.tension.foreverworld.model.SeasonRecord;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

public final class ResetService {
    private final ForeverWorldPlugin plugin;
    private final SeasonManager seasonManager;
    private final ArchiveService archiveService;

    public ResetService(ForeverWorldPlugin plugin, SeasonManager seasonManager, ArchiveService archiveService) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
        this.archiveService = archiveService;
    }

    public ResetResult runReset(String newSeasonName) {
        SeasonRecord currentSeason = seasonManager.getCurrentSeason();
        Location oldSpawn = currentSeason.spawnLocation();
        Location archiveAnchor = seasonManager.computeArchiveAnchor(oldSpawn);
        Location newSpawn = seasonManager.computeNextSpawn();

        ensureSpawnPlatform(newSpawn);

        long now = System.currentTimeMillis();
        SeasonRecord archivedSeason = currentSeason.archived(archiveAnchor, now);
        SeasonRecord nextSeason = SeasonRecord.current(currentSeason.index() + 1, newSeasonName, newSpawn, now);

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        OfflinePlayer[] knownPlayers = Bukkit.getOfflinePlayers();
        Map<UUID, Integer> slots = seasonManager.assignArchiveSlots(java.util.List.of(knownPlayers));
        int spacing = seasonManager.getArchivePlayerSpacing();
        int rowWidth = seasonManager.getArchiveRowWidth();

        for (Player player : onlinePlayers) {
            int slot = slots.getOrDefault(player.getUniqueId(), 0);
            archiveService.archivePlayer(player, archivedSeason, slot, spacing, rowWidth);
            resetPlayerState(player);
        }

        worldSpawn(newSpawn);

        for (Player player : onlinePlayers) {
            player.teleport(newSpawn);
            trySetRespawn(player, newSpawn);
            player.sendMessage(Component.text("Season reset complete. Welcome to " + newSeasonName + "."));
        }

        Map<UUID, PendingReset> pendingResets = new HashMap<>();
        Set<UUID> onlineIds = new HashSet<>();
        for (Player player : onlinePlayers) {
            onlineIds.add(player.getUniqueId());
        }

        for (OfflinePlayer player : knownPlayers) {
            UUID playerId = player.getUniqueId();
            if (onlineIds.contains(playerId)) {
                continue;
            }

            PendingReset existing = seasonManager.getPendingReset(playerId).orElse(null);
            if (existing != null) {
                pendingResets.put(playerId, existing.withDestination(newSpawn));
                continue;
            }

            int slot = slots.getOrDefault(playerId, pendingResets.size());
            PendingReset pendingReset = new PendingReset(
                    playerId,
                    player.getName() == null ? playerId.toString() : player.getName(),
                    archivedSeason.index(),
                    archivedSeason.name(),
                    archivedSeason.worldName(),
                    slot,
                    archivedSeason.archiveX(),
                    archivedSeason.archiveY(),
                    archivedSeason.archiveZ(),
                    newSpawn.getX(),
                    newSpawn.getY(),
                    newSpawn.getZ(),
                    newSpawn.getYaw(),
                    newSpawn.getPitch()
            );
            pendingResets.put(playerId, pendingReset);
        }

        seasonManager.completeReset(archivedSeason, nextSeason, pendingResets);
        return new ResetResult(archivedSeason, nextSeason, onlinePlayers.size(), pendingResets.size());
    }

    public void processPendingReset(Player player, PendingReset pendingReset) {
        SeasonRecord archivedSeason = new SeasonRecord(
                pendingReset.archivedSeasonIndex(),
                pendingReset.archivedSeasonName(),
                pendingReset.worldName(),
                0,
                0,
                0,
                0,
                0,
                pendingReset.archiveX(),
                pendingReset.archiveY(),
                pendingReset.archiveZ(),
                0,
                0
        );

        archiveService.archivePlayer(
                player,
                archivedSeason,
                pendingReset.archiveSlot(),
                seasonManager.getArchivePlayerSpacing(),
                seasonManager.getArchiveRowWidth()
        );
        resetPlayerState(player);
        player.teleport(pendingReset.destination());
        trySetRespawn(player, pendingReset.destination());
        seasonManager.clearPendingReset(player.getUniqueId());
        player.sendMessage(Component.text("Your items from season " + pendingReset.archivedSeasonName() + " were archived and you were moved to the current season."));
    }

    private void ensureSpawnPlatform(Location spawn) {
        World world = spawn.getWorld();
        int radius = seasonManager.getSpawnPlatformRadius();
        int y = spawn.getBlockY();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getBlockAt(spawn.getBlockX() + x, y - 1, spawn.getBlockZ() + z).setType(org.bukkit.Material.STONE_BRICKS, false);
                world.getBlockAt(spawn.getBlockX() + x, y, spawn.getBlockZ() + z).setType(org.bukkit.Material.AIR, false);
                world.getBlockAt(spawn.getBlockX() + x, y + 1, spawn.getBlockZ() + z).setType(org.bukkit.Material.AIR, false);
            }
        }
    }

    private void worldSpawn(Location newSpawn) {
        World world = newSpawn.getWorld();
        world.setSpawnLocation(newSpawn.getBlockX(), newSpawn.getBlockY(), newSpawn.getBlockZ(), newSpawn.getYaw());
    }

    private void resetPlayerState(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(null);
        player.getEnderChest().clear();

        player.setLevel(0);
        player.setExp(0);
        player.setTotalExperience(0);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        player.setFireTicks(0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
        player.setRemainingAir(player.getMaximumAir());

        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) == null
                ? player.getMaxHealth()
                : player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(maxHealth, player.getMaxHealth()));

        resetAdvancements(player);
    }

    private void resetAdvancements(Player player) {
        Bukkit.advancementIterator().forEachRemaining(advancement -> {
            Set<String> criteria = new HashSet<>(player.getAdvancementProgress(advancement).getAwardedCriteria());
            for (String criterion : criteria) {
                player.getAdvancementProgress(advancement).revokeCriteria(criterion);
            }
        });
    }

    private void trySetRespawn(Player player, Location respawnLocation) {
        try {
            player.setRespawnLocation(respawnLocation, true);
        } catch (NoSuchMethodError ignored) {
            // Older builds can still rely on the world spawn.
        }
    }

    public record ResetResult(SeasonRecord archivedSeason, SeasonRecord newSeason, int onlineProcessed, int pendingPlayers) {
    }
}
