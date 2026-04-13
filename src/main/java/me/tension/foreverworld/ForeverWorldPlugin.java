package me.tension.foreverworld;

import me.tension.foreverworld.command.SeasonCommand;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();

        SeasonStorage seasonStorage = new SeasonStorage(this);
        this.seasonManager = new SeasonManager(this, seasonStorage);
        this.seasonManager.loadOrInitialize();

        ArchiveService archiveService = new ArchiveService(this);
        this.resetService = new ResetService(this, seasonManager, archiveService);

        SeasonCommand seasonCommand = new SeasonCommand(this, seasonManager, resetService);
        PluginCommand command = getCommand("season");
        if (command == null) {
            throw new IllegalStateException("Command 'season' is missing from plugin.yml");
        }

        command.setExecutor(seasonCommand);
        command.setTabCompleter(seasonCommand);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, seasonManager, resetService), this);

        getLogger().info("ForeverWorld enabled for season " + seasonManager.getCurrentSeason().name() + ".");
    }

    @Override
    public void onDisable() {
        if (seasonManager != null) {
            seasonManager.save();
        }
    }
}
