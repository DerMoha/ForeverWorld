package me.tension.foreverworld.storage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.tension.foreverworld.ForeverWorldPlugin;
import me.tension.foreverworld.model.PendingReset;
import me.tension.foreverworld.model.SeasonRecord;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class SeasonStorage {
    private final ForeverWorldPlugin plugin;
    private final File file;

    public SeasonStorage(ForeverWorldPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "seasons.yml");
    }

    public StoredState load() {
        if (!file.exists()) {
            return new StoredState(null, new ArrayList<>(), new HashMap<>());
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        SeasonRecord currentSeason = null;
        ConfigurationSection currentSection = config.getConfigurationSection("current-season");
        if (currentSection != null) {
            currentSeason = SeasonRecord.fromSection(currentSection);
        }

        List<SeasonRecord> archivedSeasons = new ArrayList<>();
        ConfigurationSection archivedSection = config.getConfigurationSection("archived-seasons");
        if (archivedSection != null) {
            for (String key : archivedSection.getKeys(false)) {
                ConfigurationSection section = archivedSection.getConfigurationSection(key);
                if (section != null) {
                    archivedSeasons.add(SeasonRecord.fromSection(section));
                }
            }
        }

        Map<UUID, PendingReset> pendingResets = new HashMap<>();
        ConfigurationSection pendingSection = config.getConfigurationSection("pending-resets");
        if (pendingSection != null) {
            for (String key : pendingSection.getKeys(false)) {
                ConfigurationSection section = pendingSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }

                try {
                    UUID playerId = UUID.fromString(key);
                    pendingResets.put(playerId, PendingReset.fromSection(playerId, section));
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Skipping invalid pending reset entry for key " + key + ".");
                }
            }
        }

        return new StoredState(currentSeason, archivedSeasons, pendingResets);
    }

    public void save(SeasonRecord currentSeason, List<SeasonRecord> archivedSeasons, Map<UUID, PendingReset> pendingResets) {
        YamlConfiguration config = new YamlConfiguration();

        if (currentSeason != null) {
            ConfigurationSection currentSection = config.createSection("current-season");
            currentSeason.writeTo(currentSection);
        }

        ConfigurationSection archivedSection = config.createSection("archived-seasons");
        for (SeasonRecord record : archivedSeasons) {
            ConfigurationSection seasonSection = archivedSection.createSection(String.valueOf(record.index()));
            record.writeTo(seasonSection);
        }

        ConfigurationSection pendingSection = config.createSection("pending-resets");
        for (Map.Entry<UUID, PendingReset> entry : pendingResets.entrySet()) {
            ConfigurationSection playerSection = pendingSection.createSection(entry.getKey().toString());
            entry.getValue().writeTo(playerSection);
        }

        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data folder");
            }
            config.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save seasons.yml", exception);
        }
    }

    public record StoredState(
            SeasonRecord currentSeason,
            List<SeasonRecord> archivedSeasons,
            Map<UUID, PendingReset> pendingResets
    ) {
    }
}
