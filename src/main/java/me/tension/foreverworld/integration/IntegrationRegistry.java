package me.tension.foreverworld.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import me.tension.foreverworld.ForeverWorldPlugin;
import me.tension.foreverworld.placeholder.ForeverWorldPlaceholderExpansion;
import me.tension.foreverworld.service.SeasonManager;
import me.tension.foreverworld.model.SeasonRecord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

public final class IntegrationRegistry {
    private static final String ESSENTIALS = "Essentials";
    private static final String ESSENTIALS_SPAWN = "EssentialsSpawn";
    private static final String AURA_SKILLS = "AuraSkills";
    private static final String MCMMO = "mcMMO";
    private static final String PLACEHOLDER_API = "PlaceholderAPI";
    private static final String DYNMAP = "dynmap";
    private static final String BLUE_MAP = "BlueMap";

    private final ForeverWorldPlugin plugin;
    private final List<String> detectedPlugins = new ArrayList<>();
    private ProtectionIntegration protectionIntegration = new NoOpProtectionIntegration();

    public IntegrationRegistry(ForeverWorldPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        detectPlugin(pluginManager, "Multiverse-Core");
        detectPlugin(pluginManager, "WorldGuard");
        detectPlugin(pluginManager, ESSENTIALS);
        detectPlugin(pluginManager, ESSENTIALS_SPAWN);
        detectPlugin(pluginManager, AURA_SKILLS);
        detectPlugin(pluginManager, MCMMO);
        detectPlugin(pluginManager, PLACEHOLDER_API);
        detectPlugin(pluginManager, DYNMAP);
        detectPlugin(pluginManager, BLUE_MAP);

        if (pluginManager.isPluginEnabled("WorldGuard")) {
            protectionIntegration = new WorldGuardProtectionIntegration();
        }
    }

    public void registerPlaceholderExpansion(SeasonManager seasonManager) {
        if (!isPluginDetected(PLACEHOLDER_API) || !plugin.getConfig().getBoolean("integrations.placeholderapi.register-expansion", true)) {
            return;
        }

        new ForeverWorldPlaceholderExpansion(plugin, seasonManager).register();
        plugin.getLogger().info("Registered PlaceholderAPI expansion 'foreverworld'.");
    }

    public List<String> buildWarnings(SeasonManager seasonManager) {
        List<String> warnings = new ArrayList<>();
        if ((isPluginDetected(ESSENTIALS) || isPluginDetected(ESSENTIALS_SPAWN))
                && seasonManager.shouldUpdateWorldSpawn()
                && plugin.getConfig().getBoolean("integrations.essentialsx.warn-on-spawn-conflict", true)) {
            warnings.add("EssentialsX is installed while update-world-spawn is enabled. Leave world spawn ownership off unless you want ForeverWorld to override it.");
        }

        if (isPluginDetected(AURA_SKILLS) && getConfiguredCommands("integrations.auraskills.player-reset-commands").isEmpty()) {
            warnings.add("AuraSkills is installed but no reset commands are configured, so AuraSkills progression will persist between seasons.");
        }

        if (isPluginDetected(MCMMO) && getConfiguredCommands("integrations.mcmmo.player-reset-commands").isEmpty()) {
            warnings.add("mcMMO is installed but no reset commands are configured, so mcMMO progression will persist between seasons.");
        }

        if (isPluginDetected(DYNMAP) && !plugin.getConfig().getBoolean("integrations.dynmap.update-markers", true)) {
            warnings.add("dynmap is installed but season markers are disabled in the config.");
        }

        if (isPluginDetected(BLUE_MAP) && getConfiguredCommands("integrations.bluemap.season-reset-commands").isEmpty()) {
            warnings.add("BlueMap is installed but no season reset commands are configured, so map markers will not update automatically.");
        }

        return warnings;
    }

    public void handlePlayerReset(Player player, SeasonRecord archivedSeason, SeasonRecord newSeason) {
        Map<String, String> replacements = buildReplacements(player, archivedSeason, newSeason);
        if (isPluginDetected(ESSENTIALS)) {
            executeConfiguredCommands("integrations.essentialsx.player-reset-commands", replacements);
        }
        if (isPluginDetected(AURA_SKILLS)) {
            executeConfiguredCommands("integrations.auraskills.player-reset-commands", replacements);
        }
        if (isPluginDetected(MCMMO)) {
            executeConfiguredCommands("integrations.mcmmo.player-reset-commands", replacements);
        }
    }

    public void handleSeasonReset(SeasonRecord archivedSeason, SeasonRecord newSeason) {
        Map<String, String> replacements = buildReplacements(null, archivedSeason, newSeason);
        if (isPluginDetected(DYNMAP) && plugin.getConfig().getBoolean("integrations.dynmap.update-markers", true)) {
            updateDynmapMarkers(archivedSeason, newSeason);
        }
        if (isPluginDetected(BLUE_MAP)) {
            executeConfiguredCommands("integrations.bluemap.season-reset-commands", replacements);
        }
    }

    private void updateDynmapMarkers(SeasonRecord archivedSeason, SeasonRecord newSeason) {
        String setId = plugin.getConfig().getString("integrations.dynmap.marker-set-id", "foreverworld").trim();
        String setLabel = plugin.getConfig().getString("integrations.dynmap.marker-set-label", "ForeverWorld").trim();
        String currentMarkerId = plugin.getConfig().getString("integrations.dynmap.current-marker-id", "foreverworld-current").trim();
        String currentMarkerLabel = plugin.getConfig().getString("integrations.dynmap.current-marker-label", "CurrentSeasonSpawn").trim();
        String archiveMarkerPrefix = plugin.getConfig().getString("integrations.dynmap.archive-marker-id-prefix", "foreverworld-archive-").trim();
        String archiveLabelPrefix = plugin.getConfig().getString("integrations.dynmap.archive-marker-label-prefix", "SeasonArchive").trim();
        String icon = plugin.getConfig().getString("integrations.dynmap.marker-icon", "default").trim();

        Location newSpawn = newSeason.spawnLocation();
        Location archiveAnchor = archivedSeason.archiveAnchor();
        String archiveMarkerId = archiveMarkerPrefix + archivedSeason.index();
        String archiveLabel = archiveLabelPrefix + "-" + sanitizeLabel(archivedSeason.name());

        dispatchConsoleCommand("dmarker addset id:" + setId + " " + setLabel);
        dispatchConsoleCommand("dmarker updateset id:" + setId + " newlabel:" + setLabel);

        dispatchConsoleCommand("dmarker delete id:" + currentMarkerId + " set:" + setId);
        dispatchConsoleCommand("dmarker add id:" + currentMarkerId + " " + currentMarkerLabel + " icon:" + icon
                + " set:" + setId + " x:" + block(newSpawn.getX()) + " y:" + block(newSpawn.getY())
                + " z:" + block(newSpawn.getZ()) + " world:" + newSeason.worldName());

        dispatchConsoleCommand("dmarker delete id:" + archiveMarkerId + " set:" + setId);
        dispatchConsoleCommand("dmarker add id:" + archiveMarkerId + " " + archiveLabel + " icon:" + icon
                + " set:" + setId + " x:" + block(archiveAnchor.getX()) + " y:" + block(archiveAnchor.getY())
                + " z:" + block(archiveAnchor.getZ()) + " world:" + archivedSeason.worldName());
    }

    private List<String> getConfiguredCommands(String path) {
        return plugin.getConfig().getStringList(path).stream()
                .map(String::trim)
                .filter(command -> !command.isEmpty())
                .toList();
    }

    private void executeConfiguredCommands(String path, Map<String, String> replacements) {
        for (String template : getConfiguredCommands(path)) {
            dispatchConsoleCommand(applyReplacements(template, replacements));
        }
    }

    private void dispatchConsoleCommand(String command) {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        plugin.getServer().dispatchCommand(console, command);
    }

    private Map<String, String> buildReplacements(Player player, SeasonRecord archivedSeason, SeasonRecord newSeason) {
        Map<String, String> values = new HashMap<>();
        values.put("{managed_world}", newSeason.worldName());
        values.put("{new_season}", newSeason.name());
        values.put("{new_season_index}", String.valueOf(newSeason.index()));
        values.put("{archived_season}", archivedSeason.name());
        values.put("{archived_season_index}", String.valueOf(archivedSeason.index()));
        values.put("{new_spawn_x}", block(newSeason.spawnX()));
        values.put("{new_spawn_y}", block(newSeason.spawnY()));
        values.put("{new_spawn_z}", block(newSeason.spawnZ()));
        values.put("{archive_x}", block(archivedSeason.archiveX()));
        values.put("{archive_y}", block(archivedSeason.archiveY()));
        values.put("{archive_z}", block(archivedSeason.archiveZ()));
        if (player != null) {
            values.put("{player}", player.getName());
            values.put("{uuid}", player.getUniqueId().toString());
        }
        return values;
    }

    private String applyReplacements(String template, Map<String, String> replacements) {
        String command = template;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            command = command.replace(entry.getKey(), entry.getValue());
        }
        return command;
    }

    private String block(double value) {
        return String.valueOf((int) Math.floor(value));
    }

    private String sanitizeLabel(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
    }

    private void detectPlugin(PluginManager pluginManager, String name) {
        if (pluginManager.isPluginEnabled(name)) {
            detectedPlugins.add(name);
        }
    }

    public boolean isPluginDetected(String name) {
        return detectedPlugins.contains(name);
    }

    public List<String> getDetectedPlugins() {
        return List.copyOf(detectedPlugins);
    }

    public ProtectionIntegration getProtectionIntegration() {
        return protectionIntegration;
    }
}
