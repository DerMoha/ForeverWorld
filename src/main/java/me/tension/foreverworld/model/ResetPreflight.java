package me.tension.foreverworld.model;

import java.util.List;
import org.bukkit.Location;

public record ResetPreflight(
        String seasonName,
        String managedWorldName,
        Location currentSpawn,
        Location archiveAnchor,
        Location nextSpawn,
        List<String> affectedOnlinePlayers,
        int pendingResetCount,
        boolean updatesWorldSpawn,
        String placementPolicy,
        List<String> detectedIntegrations,
        List<String> issues,
        boolean blocked
) {
}
