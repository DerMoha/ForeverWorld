package me.tension.foreverworld.model;

import org.bukkit.World;

public record PlacementArea(String label, World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public String describeBounds() {
        return world.getName() + " [" + minX + ", " + minY + ", " + minZ + "] -> ["
                + maxX + ", " + maxY + ", " + maxZ + "]";
    }
}
