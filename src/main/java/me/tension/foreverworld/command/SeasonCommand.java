package me.tension.foreverworld.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.tension.foreverworld.ForeverWorldPlugin;
import me.tension.foreverworld.model.SeasonRecord;
import me.tension.foreverworld.service.ResetService;
import me.tension.foreverworld.service.SeasonManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class SeasonCommand implements CommandExecutor, TabCompleter {
    private final ForeverWorldPlugin plugin;
    private final SeasonManager seasonManager;
    private final ResetService resetService;
    private PendingConfirmation pendingConfirmation;

    public SeasonCommand(ForeverWorldPlugin plugin, SeasonManager seasonManager, ResetService resetService) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
        this.resetService = resetService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foreverworld.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use this command."));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /season reset <season-name>"));
                return true;
            }

            String newSeasonName = args[1];
            long expiresAt = System.currentTimeMillis() + (seasonManager.getConfirmationSeconds() * 1000L);
            pendingConfirmation = new PendingConfirmation(newSeasonName, expiresAt, sender.getName());
            sender.sendMessage(Component.text("Reset for season '" + newSeasonName + "' is armed. Run /season confirm within " + seasonManager.getConfirmationSeconds() + " seconds."));
            return true;
        }

        if (args[0].equalsIgnoreCase("confirm")) {
            if (pendingConfirmation == null || pendingConfirmation.expiresAt() < System.currentTimeMillis()) {
                pendingConfirmation = null;
                sender.sendMessage(Component.text("There is no active reset confirmation."));
                return true;
            }

            sender.sendMessage(Component.text("Running season reset to '" + pendingConfirmation.newSeasonName() + "'."));
            ResetService.ResetResult result = resetService.runReset(pendingConfirmation.newSeasonName());
            pendingConfirmation = null;

            sender.sendMessage(Component.text(
                    "Season reset complete. Archived " + result.archivedSeason().name()
                            + ", started " + result.newSeason().name()
                            + ", processed " + result.onlineProcessed()
                            + " online players, and queued " + result.pendingPlayers()
                            + " offline players."
            ));
            return true;
        }

        sender.sendMessage(Component.text("Usage: /season <status|reset|confirm>"));
        return true;
    }

    private void sendStatus(CommandSender sender) {
        SeasonRecord currentSeason = seasonManager.getCurrentSeason();
        sender.sendMessage(Component.text("Current season: " + currentSeason.name() + " (#" + currentSeason.index() + ")"));
        sender.sendMessage(Component.text("Active spawn: "
                + currentSeason.worldName() + " "
                + format(currentSeason.spawnX()) + ", "
                + format(currentSeason.spawnY()) + ", "
                + format(currentSeason.spawnZ())));
        sender.sendMessage(Component.text("Archived seasons: " + seasonManager.getArchivedSeasons().size()));
        sender.sendMessage(Component.text("Pending offline resets: " + seasonManager.getPendingResetCount()));
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("status", "reset", "confirm");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return new ArrayList<>();
        }

        return List.of();
    }

    private record PendingConfirmation(String newSeasonName, long expiresAt, String requestedBy) {
    }
}
