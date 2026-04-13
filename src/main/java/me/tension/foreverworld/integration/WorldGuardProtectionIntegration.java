package me.tension.foreverworld.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import me.tension.foreverworld.model.PlacementArea;

public final class WorldGuardProtectionIntegration implements ProtectionIntegration {
    @Override
    public String getName() {
        return "WorldGuard";
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public List<String> findConflicts(PlacementArea area) {
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(area.world()));
        if (regionManager == null) {
            return List.of("WorldGuard has no region manager loaded for " + area.describeBounds() + ".");
        }

        Set<String> regionIds = new TreeSet<>();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int y = area.minY(); y <= area.maxY(); y++) {
                for (int z = area.minZ(); z <= area.maxZ(); z++) {
                    ApplicableRegionSet regionSet = regionManager.getApplicableRegions(BlockVector3.at(x, y, z));
                    regionSet.getRegions().stream()
                            .map(ProtectedRegion::getId)
                            .filter(regionId -> !ProtectedRegion.GLOBAL_REGION.equals(regionId))
                            .forEach(regionIds::add);
                }
            }
        }

        if (regionIds.isEmpty()) {
            return List.of();
        }

        return List.of("WorldGuard blocks " + area.label() + " in regions: " + String.join(", ", regionIds) + ".");
    }
}
