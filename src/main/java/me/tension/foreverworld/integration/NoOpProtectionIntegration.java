package me.tension.foreverworld.integration;

import java.util.List;
import me.tension.foreverworld.model.PlacementArea;

public final class NoOpProtectionIntegration implements ProtectionIntegration {
    @Override
    public String getName() {
        return "none";
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public List<String> findConflicts(PlacementArea area) {
        return List.of();
    }
}
