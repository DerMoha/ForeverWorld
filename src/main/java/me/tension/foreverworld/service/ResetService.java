package me.tension.foreverworld.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.tension.foreverworld.ForeverWorldPlugin;
import me.tension.foreverworld.integration.IntegrationRegistry;
import me.tension.foreverworld.model.PendingReset;
import me.tension.foreverworld.model.PlacementArea;
import me.tension.foreverworld.model.ResetPreflight;
import me.tension.foreverworld.model.SeasonRecord;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
    private final IntegrationRegistry integrationRegistry;

    public ResetService(ForeverWorldPlugin plugin, SeasonManager seasonManager, ArchiveService archiveService,
                        IntegrationRegistry integrationRegistry) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
        this.archiveService = archiveService;
        this.integrationRegistry = integrationRegistry;
    }

    public ResetResult runReset(String newSeasonName) {
        ResetPlan resetPlan = buildResetPlan(newSeasonName);
        ResetPreflight preflight = buildPreflight(newSeasonName);
        if (preflight.blocked()) {
            throw new IllegalStateException("Reset preflight failed: " + String.join(" ", preflight.issues()));
        }

        ensureSpawnPlatform(resetPlan.newSpawn());

        long now = System.currentTimeMillis();
        SeasonRecord archivedSeason = resetPlan.currentSeason().archived(resetPlan.archiveAnchor(), now);
        SeasonRecord nextSeason = SeasonRecord.current(resetPlan.currentSeason().index() + 1, newSeasonName, resetPlan.newSpawn(), now);

        Map<UUID, PendingReset> pendingResets = new HashMap<>();
        int onlineProcessed = 0;

        for (Player player : resetPlan.affectedOnlinePlayers()) {
            int slot = resetPlan.archiveSlots().getOrDefault(player.getUniqueId(), 0);
            ItemStack[] carriedContents = cloneContents(player.getInventory().getContents());
            ItemStack[] enderContents = cloneContents(player.getEnderChest().getContents());
            if (!player.teleport(resetPlan.newSpawn())) {
                pendingResets.put(player.getUniqueId(), createPendingReset(player, archivedSeason, resetPlan.newSpawn(), slot));
                plugin.getLogger().warning("Teleport failed for " + player.getName() + " during season reset. Pending reset retained.");
                player.sendMessage(Component.text("Your season reset was prepared, but teleport failed. It will be retried when you rejoin."));
                continue;
            }

            archiveService.archivePlayerContents(
                    player.getName(),
                    carriedContents,
                    enderContents,
                    archivedSeason,
                    slot,
                    resetPlan.spacing(),
                    resetPlan.rowWidth()
            );
            resetPlayerState(player);
            onlineProcessed++;
            trySetRespawn(player, resetPlan.newSpawn());
            player.sendMessage(Component.text("Season reset complete. Welcome to " + newSeasonName + "."));
            integrationRegistry.handlePlayerReset(player, archivedSeason, nextSeason);
        }

        if (seasonManager.shouldUpdateWorldSpawn()) {
            worldSpawn(resetPlan.newSpawn());
        }

        seasonManager.getPendingResetIds().forEach(playerId -> seasonManager.getPendingReset(playerId)
                .map(existing -> existing.withDestination(resetPlan.newSpawn()))
                .ifPresent(updated -> pendingResets.put(playerId, updated)));

        seasonManager.completeReset(archivedSeason, nextSeason, pendingResets);
        integrationRegistry.handleSeasonReset(archivedSeason, nextSeason);
        return new ResetResult(archivedSeason, nextSeason, onlineProcessed, pendingResets.size(), resetPlan.affectedOnlinePlayers().size());
    }

    public ResetPreflight buildPreflight(String newSeasonName) {
        ResetPlan resetPlan = buildResetPlan(newSeasonName);
        SeasonRecord archivedSeason = resetPlan.currentSeason().archived(resetPlan.archiveAnchor(), System.currentTimeMillis());
        List<String> issues = new ArrayList<>();
        issues.addAll(findPlacementIssues(describeSpawnPlatformArea(resetPlan.newSpawn())));
        for (int slot : resetPlan.archiveSlots().values()) {
            issues.addAll(findPlacementIssues(archiveService.describePodArea(archivedSeason, slot, resetPlan.spacing(), resetPlan.rowWidth())));
        }
        issues.addAll(integrationRegistry.buildWarnings(seasonManager));

        return new ResetPreflight(
                newSeasonName,
                seasonManager.getManagedWorldName(),
                resetPlan.currentSeason().spawnLocation(),
                resetPlan.archiveAnchor(),
                resetPlan.newSpawn(),
                resetPlan.affectedOnlinePlayers().stream().map(Player::getName).sorted().toList(),
                seasonManager.getPendingResetCount(),
                seasonManager.shouldUpdateWorldSpawn(),
                seasonManager.getPlacementPolicy(),
                integrationRegistry.getDetectedPlugins(),
                List.copyOf(issues),
                seasonManager.shouldAbortOnPlacementIssues() && !issues.isEmpty()
        );
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

        List<String> issues = findPlacementIssues(archiveService.describePodArea(
                archivedSeason,
                pendingReset.archiveSlot(),
                seasonManager.getArchivePlayerSpacing(),
                seasonManager.getArchiveRowWidth()
        ));
        if (seasonManager.shouldAbortOnPlacementIssues() && !issues.isEmpty()) {
            plugin.getLogger().warning("Pending reset for " + player.getName() + " is blocked: " + String.join(" ", issues));
            player.sendMessage(Component.text("Your season reset is still pending because the archive area is blocked. Please contact staff."));
            return;
        }

        ItemStack[] carriedContents = cloneContents(player.getInventory().getContents());
        ItemStack[] enderContents = cloneContents(player.getEnderChest().getContents());
        if (!player.teleport(pendingReset.destination())) {
            plugin.getLogger().warning("Teleport failed for pending reset player " + player.getName() + ". Leaving reset queued.");
            player.sendMessage(Component.text("Your season reset is still pending because teleport failed. Please contact staff."));
            return;
        }

        archiveService.archivePlayerContents(
                player.getName(),
                carriedContents,
                enderContents,
                archivedSeason,
                pendingReset.archiveSlot(),
                seasonManager.getArchivePlayerSpacing(),
                seasonManager.getArchiveRowWidth()
        );
        resetPlayerState(player);
        trySetRespawn(player, pendingReset.destination());
        seasonManager.clearPendingReset(player.getUniqueId());
        player.sendMessage(Component.text("Your items from season " + pendingReset.archivedSeasonName() + " were archived and you were moved to the current season."));
        integrationRegistry.handlePlayerReset(player, archivedSeason, seasonManager.getCurrentSeason());
    }

    private boolean shouldResetPlayer(Player player) {
        if (!seasonManager.shouldOnlyResetManagedWorldPlayers()) {
            return true;
        }
        return seasonManager.isManagedWorld(player.getWorld());
    }

    private PendingReset createPendingReset(OfflinePlayer player, SeasonRecord archivedSeason, Location newSpawn, int slot) {
        UUID playerId = player.getUniqueId();
        return new PendingReset(
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
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] cloned = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            ItemStack stack = contents[index];
            cloned[index] = stack == null ? null : stack.clone();
        }
        return cloned;
    }

    private ResetPlan buildResetPlan(String newSeasonName) {
        SeasonRecord currentSeason = seasonManager.getCurrentSeason();
        Location oldSpawn = currentSeason.spawnLocation();
        Location archiveAnchor = seasonManager.computeArchiveAnchor(oldSpawn);
        Location newSpawn = seasonManager.computeNextSpawn();
        List<Player> affectedOnlinePlayers = Bukkit.getOnlinePlayers().stream()
                .map(Player.class::cast)
                .filter(this::shouldResetPlayer)
                .toList();
        Map<UUID, Integer> archiveSlots = seasonManager.assignArchiveSlots(affectedOnlinePlayers.stream()
                .map(player -> (OfflinePlayer) player)
                .toList());
        return new ResetPlan(currentSeason, archiveAnchor, newSpawn, affectedOnlinePlayers, archiveSlots,
                seasonManager.getArchivePlayerSpacing(), seasonManager.getArchiveRowWidth(), newSeasonName);
    }

    private PlacementArea describeSpawnPlatformArea(Location spawn) {
        int radius = seasonManager.getSpawnPlatformRadius();
        return new PlacementArea(
                "spawn platform",
                spawn.getWorld(),
                spawn.getBlockX() - radius,
                spawn.getBlockY(),
                spawn.getBlockZ() - radius,
                spawn.getBlockX() + radius,
                spawn.getBlockY() + 1,
                spawn.getBlockZ() + radius
        );
    }

    private List<String> findPlacementIssues(PlacementArea area) {
        List<String> issues = new ArrayList<>();
        if (hasOccupiedBuildSpace(area)) {
            issues.add(area.label() + " has occupied build space at " + area.describeBounds() + ".");
        }
        if (seasonManager.shouldCheckWorldGuardProtection()) {
            issues.addAll(integrationRegistry.getProtectionIntegration().findConflicts(area));
        }
        return issues;
    }

    private boolean hasOccupiedBuildSpace(PlacementArea area) {
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int y = area.minY(); y <= area.maxY(); y++) {
                for (int z = area.minZ(); z <= area.maxZ(); z++) {
                    if (area.world().getBlockAt(x, y, z).getType() != Material.AIR) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void ensureSpawnPlatform(Location spawn) {
        World world = spawn.getWorld();
        int radius = seasonManager.getSpawnPlatformRadius();
        int y = spawn.getBlockY();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getBlockAt(spawn.getBlockX() + x, y - 1, spawn.getBlockZ() + z).setType(Material.STONE_BRICKS, false);
                world.getBlockAt(spawn.getBlockX() + x, y, spawn.getBlockZ() + z).setType(Material.AIR, false);
                world.getBlockAt(spawn.getBlockX() + x, y + 1, spawn.getBlockZ() + z).setType(Material.AIR, false);
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

        if (seasonManager.shouldResetVanillaAdvancements()) {
            resetAdvancements(player);
        }
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

    public record ResetResult(SeasonRecord archivedSeason, SeasonRecord newSeason, int onlineProcessed, int pendingPlayers,
                              int affectedOnlinePlayers) {
    }

    private record ResetPlan(SeasonRecord currentSeason, Location archiveAnchor, Location newSpawn,
                             List<Player> affectedOnlinePlayers, Map<UUID, Integer> archiveSlots,
                             int spacing, int rowWidth, String seasonName) {
    }
}
