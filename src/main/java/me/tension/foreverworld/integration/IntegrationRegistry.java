package me.tension.foreverworld.integration;

import java.util.ArrayList;
import java.util.List;
import me.tension.foreverworld.ForeverWorldPlugin;
import org.bukkit.plugin.PluginManager;

public final class IntegrationRegistry {
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
        detectPlugin(pluginManager, "Essentials");
        detectPlugin(pluginManager, "EssentialsSpawn");
        detectPlugin(pluginManager, "AuraSkills");
        detectPlugin(pluginManager, "mcMMO");
        detectPlugin(pluginManager, "PlaceholderAPI");
        detectPlugin(pluginManager, "dynmap");
        detectPlugin(pluginManager, "BlueMap");

        if (pluginManager.isPluginEnabled("WorldGuard")) {
            protectionIntegration = new WorldGuardProtectionIntegration();
        }
    }

    private void detectPlugin(PluginManager pluginManager, String name) {
        if (pluginManager.isPluginEnabled(name)) {
            detectedPlugins.add(name);
        }
    }

    public List<String> getDetectedPlugins() {
        return List.copyOf(detectedPlugins);
    }

    public ProtectionIntegration getProtectionIntegration() {
        return protectionIntegration;
    }
}
