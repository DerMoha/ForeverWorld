package me.tension.foreverworld.model;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public record PendingReset(
        UUID playerId,
        String lastKnownName,
        int archivedSeasonIndex,
        String archivedSeasonName,
        String worldName,
        int archiveSlot,
        double archiveX,
        double archiveY,
        double archiveZ,
        double destinationX,
        double destinationY,
        double destinationZ,
        float destinationYaw,
        float destinationPitch
) {
    public Location archiveAnchor() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("World '" + worldName + "' is not loaded");
        }
        return new Location(world, archiveX, archiveY, archiveZ);
    }

    public Location destination() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("World '" + worldName + "' is not loaded");
        }
        return new Location(world, destinationX, destinationY, destinationZ, destinationYaw, destinationPitch);
    }

    public PendingReset withDestination(Location location) {
        return new PendingReset(
                playerId,
                lastKnownName,
                archivedSeasonIndex,
                archivedSeasonName,
                worldName,
                archiveSlot,
                archiveX,
                archiveY,
                archiveZ,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    public void writeTo(ConfigurationSection section) {
        section.set("player-name", lastKnownName);
        section.set("archived-season-index", archivedSeasonIndex);
        section.set("archived-season-name", archivedSeasonName);
        section.set("world", worldName);
        section.set("archive-slot", archiveSlot);
        section.set("archive.x", archiveX);
        section.set("archive.y", archiveY);
        section.set("archive.z", archiveZ);
        section.set("destination.x", destinationX);
        section.set("destination.y", destinationY);
        section.set("destination.z", destinationZ);
        section.set("destination.yaw", destinationYaw);
        section.set("destination.pitch", destinationPitch);
    }

    public static PendingReset fromSection(UUID playerId, ConfigurationSection section) {
        return new PendingReset(
                playerId,
                section.getString("player-name", playerId.toString()),
                section.getInt("archived-season-index"),
                section.getString("archived-season-name", "unknown"),
                section.getString("world", "world"),
                section.getInt("archive-slot"),
                section.getDouble("archive.x"),
                section.getDouble("archive.y"),
                section.getDouble("archive.z"),
                section.getDouble("destination.x"),
                section.getDouble("destination.y"),
                section.getDouble("destination.z"),
                (float) section.getDouble("destination.yaw"),
                (float) section.getDouble("destination.pitch")
        );
    }
}
