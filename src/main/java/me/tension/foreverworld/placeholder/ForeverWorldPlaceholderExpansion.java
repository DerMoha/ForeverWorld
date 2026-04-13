package me.tension.foreverworld.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.tension.foreverworld.ForeverWorldPlugin;
import me.tension.foreverworld.service.SeasonManager;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public final class ForeverWorldPlaceholderExpansion extends PlaceholderExpansion {
    private final ForeverWorldPlugin plugin;
    private final SeasonManager seasonManager;

    public ForeverWorldPlaceholderExpansion(ForeverWorldPlugin plugin, SeasonManager seasonManager) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getIdentifier() {
        return "foreverworld";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        return switch (params.toLowerCase()) {
            case "current_season" -> seasonManager.getCurrentSeason().name();
            case "current_index" -> String.valueOf(seasonManager.getCurrentSeason().index());
            case "managed_world" -> seasonManager.getManagedWorldName();
            case "pending_resets" -> String.valueOf(seasonManager.getPendingResetCount());
            case "archived_seasons" -> String.valueOf(seasonManager.getArchivedSeasons().size());
            case "next_spawn_x" -> format(seasonManager.computeNextSpawn().getX());
            case "next_spawn_y" -> format(seasonManager.computeNextSpawn().getY());
            case "next_spawn_z" -> format(seasonManager.computeNextSpawn().getZ());
            case "current_spawn_x" -> format(seasonManager.getCurrentSeason().spawnX());
            case "current_spawn_y" -> format(seasonManager.getCurrentSeason().spawnY());
            case "current_spawn_z" -> format(seasonManager.getCurrentSeason().spawnZ());
            case "player_pending_reset" -> player != null && seasonManager.getPendingReset(player.getUniqueId()).isPresent() ? "true" : "false";
            default -> null;
        };
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
