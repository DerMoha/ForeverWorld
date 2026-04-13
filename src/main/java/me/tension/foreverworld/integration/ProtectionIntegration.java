package me.tension.foreverworld.integration;

import java.util.List;
import me.tension.foreverworld.model.PlacementArea;

public interface ProtectionIntegration {
    String getName();

    boolean isActive();

    List<String> findConflicts(PlacementArea area);
}
