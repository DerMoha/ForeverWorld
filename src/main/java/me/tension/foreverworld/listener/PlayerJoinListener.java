package me.tension.foreverworld.listener;

import me.tension.foreverworld.ForeverWorldPlugin;
import me.tension.foreverworld.service.ResetService;
import me.tension.foreverworld.service.SeasonManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerJoinListener implements Listener {
    private final ForeverWorldPlugin plugin;
    private final SeasonManager seasonManager;
    private final ResetService resetService;

    public PlayerJoinListener(ForeverWorldPlugin plugin, SeasonManager seasonManager, ResetService resetService) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
        this.resetService = resetService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        seasonManager.getPendingReset(player.getUniqueId()).ifPresent(pendingReset ->
                plugin.getServer().getScheduler().runTask(plugin, () -> resetService.processPendingReset(player, pendingReset))
        );
    }
}
