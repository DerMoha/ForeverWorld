package me.tension.foreverworld.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public record SeasonRecord(
        int index,
        String name,
        String worldName,
        double spawnX,
        double spawnY,
        double spawnZ,
        float spawnYaw,
        float spawnPitch,
        double archiveX,
        double archiveY,
        double archiveZ,
        long createdAt,
        long resetAt
) {
    public Location spawnLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("World '" + worldName + "' is not loaded");
        }
        return new Location(world, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
    }

    public Location archiveAnchor() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("World '" + worldName + "' is not loaded");
        }
        return new Location(world, archiveX, archiveY, archiveZ);
    }

    public void writeTo(ConfigurationSection section) {
        section.set("index", index);
        section.set("name", name);
        section.set("world", worldName);
        section.set("spawn.x", spawnX);
        section.set("spawn.y", spawnY);
        section.set("spawn.z", spawnZ);
        section.set("spawn.yaw", spawnYaw);
        section.set("spawn.pitch", spawnPitch);
        section.set("archive.x", archiveX);
        section.set("archive.y", archiveY);
        section.set("archive.z", archiveZ);
        section.set("created-at", createdAt);
        section.set("reset-at", resetAt);
    }

    public static SeasonRecord fromSection(ConfigurationSection section) {
        return new SeasonRecord(
                section.getInt("index"),
                section.getString("name", "unknown"),
                section.getString("world", "world"),
                section.getDouble("spawn.x"),
                section.getDouble("spawn.y"),
                section.getDouble("spawn.z"),
                (float) section.getDouble("spawn.yaw"),
                (float) section.getDouble("spawn.pitch"),
                section.getDouble("archive.x"),
                section.getDouble("archive.y"),
                section.getDouble("archive.z"),
                section.getLong("created-at"),
                section.getLong("reset-at")
        );
    }

    public static SeasonRecord current(int index, String name, Location spawn, long createdAt) {
        return new SeasonRecord(
                index,
                name,
                spawn.getWorld().getName(),
                spawn.getX(),
                spawn.getY(),
                spawn.getZ(),
                spawn.getYaw(),
                spawn.getPitch(),
                spawn.getX(),
                spawn.getY(),
                spawn.getZ(),
                createdAt,
                0L
        );
    }

    public SeasonRecord archived(Location archiveAnchor, long resetAt) {
        return new SeasonRecord(
                index,
                name,
                worldName,
                spawnX,
                spawnY,
                spawnZ,
                spawnYaw,
                spawnPitch,
                archiveAnchor.getX(),
                archiveAnchor.getY(),
                archiveAnchor.getZ(),
                createdAt,
                resetAt
        );
    }
}
