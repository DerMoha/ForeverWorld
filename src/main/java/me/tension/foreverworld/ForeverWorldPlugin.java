package me.tension.foreverworld;

import me.tension.foreverworld.command.SeasonCommand;
import me.tension.foreverworld.integration.IntegrationRegistry;
import me.tension.foreverworld.listener.PlayerJoinListener;
import me.tension.foreverworld.service.ArchiveService;
import me.tension.foreverworld.service.ResetService;
import me.tension.foreverworld.service.SeasonManager;
import me.tension.foreverworld.storage.SeasonStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ForeverWorldPlugin extends JavaPlugin {
    private SeasonManager seasonManager;
    private ResetService resetService;
    private IntegrationRegistry integrationRegistry;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        SeasonStorage seasonStorage = new SeasonStorage(this);
        this.seasonManager = new SeasonManager(this, seasonStorage);
        this.seasonManager.loadOrInitialize();

        this.integrationRegistry = new IntegrationRegistry(this);
        this.integrationRegistry.initialize();

        ArchiveService archiveService = new ArchiveService(this);
        this.resetService = new ResetService(this, seasonManager, archiveService, integrationRegistry);

        SeasonCommand seasonCommand = new SeasonCommand(this, seasonManager, resetService);
        PluginCommand command = getCommand("season");
        if (command == null) {
            throw new IllegalStateException("Command 'season' is missing from plugin.yml");
        }

        command.setExecutor(seasonCommand);
        command.setTabCompleter(seasonCommand);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, seasonManager, resetService), this);

        getLogger().info("ForeverWorld enabled for season " + seasonManager.getCurrentSeason().name() + ".");
        getLogger().info("Managed world: " + seasonManager.getManagedWorldName() + ".");
        if (integrationRegistry.getDetectedPlugins().isEmpty()) {
            getLogger().info("No compatible companion plugins detected.");
        } else {
            getLogger().info("Detected companion plugins: " + String.join(", ", integrationRegistry.getDetectedPlugins()) + ".");
        }
        getLogger().info("Protection integration: " + integrationRegistry.getProtectionIntegration().getName() + ".");
    }

    @Override
    public void onDisable() {
        if (seasonManager != null) {
            seasonManager.save();
        }
    }
}
