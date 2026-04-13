package me.tension.foreverworld.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.tension.foreverworld.ForeverWorldPlugin;
import me.tension.foreverworld.model.ResetPreflight;
import me.tension.foreverworld.model.SeasonRecord;
import me.tension.foreverworld.service.ResetService;
import me.tension.foreverworld.service.SeasonManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
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

        if (args[0].equalsIgnoreCase("dryrun")) {
            sendDryRun(sender, args.length > 1 ? args[1] : resolvePreviewSeasonName());
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
            ResetService.ResetResult result;
            try {
                result = resetService.runReset(pendingConfirmation.newSeasonName());
            } catch (IllegalStateException exception) {
                sender.sendMessage(Component.text(exception.getMessage()));
                return true;
            }
            pendingConfirmation = null;

            sender.sendMessage(Component.text(
                    "Season reset complete. Archived " + result.archivedSeason().name()
                            + ", started " + result.newSeason().name()
                            + ", processed " + result.onlineProcessed()
                            + " of " + result.affectedOnlinePlayers()
                            + " managed-world online players, and now have " + result.pendingPlayers()
                            + " pending resets."
            ));
            return true;
        }

        sender.sendMessage(Component.text("Usage: /season <status|reset|confirm|dryrun>"));
        return true;
    }

    private void sendStatus(CommandSender sender) {
        SeasonRecord currentSeason = seasonManager.getCurrentSeason();
        sender.sendMessage(Component.text("Current season: " + currentSeason.name() + " (#" + currentSeason.index() + ")"));
        sender.sendMessage(Component.text("Managed world: " + seasonManager.getManagedWorldName()));
        sender.sendMessage(Component.text("Active spawn: " + format(currentSeason.spawnLocation())));
        sender.sendMessage(Component.text("Archived seasons: " + seasonManager.getArchivedSeasons().size()));
        sender.sendMessage(Component.text("Pending offline resets: " + seasonManager.getPendingResetCount()));
    }

    private void sendDryRun(CommandSender sender, String seasonName) {
        ResetPreflight preflight = resetService.buildPreflight(seasonName);
        sender.sendMessage(Component.text("Dry run for season '" + seasonName + "'"));
        sender.sendMessage(Component.text("Managed world: " + preflight.managedWorldName()));
        sender.sendMessage(Component.text("Current spawn: " + format(preflight.currentSpawn())));
        sender.sendMessage(Component.text("Next spawn: " + format(preflight.nextSpawn())));
        sender.sendMessage(Component.text("Archive anchor: " + format(preflight.archiveAnchor())));
        sender.sendMessage(Component.text("Managed-world players affected: " + joinOrNone(preflight.affectedOnlinePlayers())));
        sender.sendMessage(Component.text("Pending resets carried forward: " + preflight.pendingResetCount()));
        sender.sendMessage(Component.text("World spawn update: " + preflight.updatesWorldSpawn()));
        sender.sendMessage(Component.text("Placement policy: " + preflight.placementPolicy()));
        sender.sendMessage(Component.text("Detected integrations: " + joinOrNone(preflight.detectedIntegrations())));
        if (preflight.issues().isEmpty()) {
            sender.sendMessage(Component.text("Preflight: clear"));
            return;
        }

        sender.sendMessage(Component.text((preflight.blocked() ? "Preflight blocked: " : "Preflight warnings: ") + String.join(" | ", preflight.issues())));
    }

    private String resolvePreviewSeasonName() {
        if (pendingConfirmation != null && pendingConfirmation.expiresAt() >= System.currentTimeMillis()) {
            return pendingConfirmation.newSeasonName();
        }
        return "preview";
    }

    private String joinOrNone(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    private String format(Location location) {
        return location.getWorld().getName() + " " + format(location.getX()) + ", " + format(location.getY()) + ", " + format(location.getZ());
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("status", "reset", "confirm", "dryrun");
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("dryrun"))) {
            return new ArrayList<>();
        }

        return List.of();
    }

    private record PendingConfirmation(String newSeasonName, long expiresAt, String requestedBy) {
    }
}
