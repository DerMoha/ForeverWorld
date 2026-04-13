package me.tension.foreverworld.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import me.tension.foreverworld.ForeverWorldPlugin;
import me.tension.foreverworld.model.PendingReset;
import me.tension.foreverworld.model.SeasonRecord;
import me.tension.foreverworld.storage.SeasonStorage;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.World.Environment;

public final class SeasonManager {
    private final ForeverWorldPlugin plugin;
    private final SeasonStorage storage;
    private SeasonRecord currentSeason;
    private final List<SeasonRecord> archivedSeasons = new ArrayList<>();
    private final Map<UUID, PendingReset> pendingResets = new HashMap<>();

    public SeasonManager(ForeverWorldPlugin plugin, SeasonStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void loadOrInitialize() {
        SeasonStorage.StoredState state = storage.load();
        archivedSeasons.clear();
        archivedSeasons.addAll(state.archivedSeasons());
        pendingResets.clear();
        pendingResets.putAll(state.pendingResets());

        if (state.currentSeason() != null) {
            currentSeason = state.currentSeason();
            return;
        }

        World world = resolveManagedWorld();
        Location spawn = world.getSpawnLocation();
        currentSeason = SeasonRecord.current(0, "origin", spawn, System.currentTimeMillis());
        save();
    }

    public World resolveManagedWorld() {
        String configuredWorld = getManagedWorldName();
        World world = plugin.getServer().getWorld(configuredWorld);
        if (world == null) {
            throw new IllegalStateException("Configured world '" + configuredWorld + "' is not loaded");
        }
        if (world.getEnvironment() != Environment.NORMAL) {
            throw new IllegalStateException("Configured world '" + configuredWorld + "' is not a normal overworld");
        }
        return world;
    }

    public String getManagedWorldName() {
        String configuredWorld = plugin.getConfig().getString("world-name", "").trim();
        if (configuredWorld.isEmpty()) {
            throw new IllegalStateException("Config option 'world-name' must be set to the managed overworld");
        }
        return configuredWorld;
    }

    public boolean isManagedWorld(World world) {
        return world != null && world.getName().equals(getManagedWorldName());
    }

    public SeasonRecord getCurrentSeason() {
        return currentSeason;
    }

    public List<SeasonRecord> getArchivedSeasons() {
        return List.copyOf(archivedSeasons);
    }

    public int getPendingResetCount() {
        return pendingResets.size();
    }

    public Optional<PendingReset> getPendingReset(UUID playerId) {
        return Optional.ofNullable(pendingResets.get(playerId));
    }

    public Set<UUID> getPendingResetIds() {
        return Set.copyOf(pendingResets.keySet());
    }

    public void clearPendingReset(UUID playerId) {
        pendingResets.remove(playerId);
        save();
    }

    public int getSeasonDistance() {
        return Math.max(1000, plugin.getConfig().getInt("season-distance-blocks", 50000));
    }

    public int getArchiveOffsetX() {
        return plugin.getConfig().getInt("archive-anchor-offset-x", 0);
    }

    public int getArchiveOffsetZ() {
        return plugin.getConfig().getInt("archive-anchor-offset-z", 24);
    }

    public int getArchivePlayerSpacing() {
        return Math.max(4, plugin.getConfig().getInt("archive-player-spacing", 6));
    }

    public int getArchiveRowWidth() {
        return Math.max(1, plugin.getConfig().getInt("archive-row-width", 8));
    }

    public int getSpawnPlatformRadius() {
        return Math.max(1, plugin.getConfig().getInt("spawn-platform-radius", 2));
    }

    public int getConfirmationSeconds() {
        return Math.max(10, plugin.getConfig().getInt("confirmation-seconds", 60));
    }

    public boolean shouldUpdateWorldSpawn() {
        return plugin.getConfig().getBoolean("update-world-spawn", false);
    }

    public boolean shouldOnlyResetManagedWorldPlayers() {
        return plugin.getConfig().getBoolean("only-reset-managed-world-players", true);
    }

    public boolean shouldCheckWorldGuardProtection() {
        return plugin.getConfig().getBoolean("check-worldguard-protection", true);
    }

    public String getPlacementPolicy() {
        return plugin.getConfig().getString("placement-policy", "abort").trim().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean shouldAbortOnPlacementIssues() {
        return !"ignore".equals(getPlacementPolicy());
    }

    public boolean shouldResetVanillaAdvancements() {
        return plugin.getConfig().getBoolean("integrations.minecraft.reset-advancements", true);
    }

    public Location computeArchiveAnchor(Location oldSpawn) {
        World world = oldSpawn.getWorld();
        int x = oldSpawn.getBlockX() + getArchiveOffsetX();
        int z = oldSpawn.getBlockZ() + getArchiveOffsetZ();
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    public Location computeNextSpawn() {
        Location previousSpawn = currentSeason.spawnLocation();
        World world = previousSpawn.getWorld();
        int x = previousSpawn.getBlockX() + getSeasonDistance();
        int z = previousSpawn.getBlockZ();
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5, previousSpawn.getYaw(), previousSpawn.getPitch());
    }

    public Map<UUID, Integer> assignArchiveSlots(Collection<OfflinePlayer> players) {
        List<OfflinePlayer> sortedPlayers = players.stream()
                .filter(player -> player.getUniqueId() != null)
                .sorted(Comparator.comparing(player -> player.getUniqueId().toString()))
                .toList();

        Map<UUID, Integer> slots = new HashMap<>();
        for (int index = 0; index < sortedPlayers.size(); index++) {
            slots.put(sortedPlayers.get(index).getUniqueId(), index);
        }
        return slots;
    }

    public void completeReset(SeasonRecord archivedSeason, SeasonRecord newCurrentSeason, Map<UUID, PendingReset> updatedPendingResets) {
        archivedSeasons.add(archivedSeason);
        currentSeason = newCurrentSeason;
        pendingResets.clear();
        pendingResets.putAll(updatedPendingResets);
        save();
    }

    public PendingReset updatePendingDestination(UUID playerId, Location destination) {
        PendingReset current = pendingResets.get(playerId);
        if (current == null) {
            return null;
        }

        PendingReset updated = current.withDestination(destination);
        pendingResets.put(playerId, updated);
        save();
        return updated;
    }

    public void save() {
        storage.save(currentSeason, archivedSeasons, pendingResets);
    }
}
