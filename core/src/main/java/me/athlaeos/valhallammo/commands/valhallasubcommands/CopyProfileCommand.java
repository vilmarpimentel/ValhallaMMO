package me.athlaeos.valhallammo.commands.valhallasubcommands;

import me.athlaeos.valhallammo.ValhallaMMO;
import me.athlaeos.valhallammo.commands.Command;
import me.athlaeos.valhallammo.persistence.ProfilePersistence;
import me.athlaeos.valhallammo.playerstats.profiles.Profile;
import me.athlaeos.valhallammo.playerstats.profiles.ProfileRegistry;
import me.athlaeos.valhallammo.playerstats.profiles.implementations.PowerProfile;
import me.athlaeos.valhallammo.skills.skills.SkillRegistry;
import me.athlaeos.valhallammo.utility.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Copies the levels, EXP and unlocked perks of one account onto another. Useful when a player
 * renamed their account (new UUID) and wants their old progress transferred to the new account.
 * Both source and target may be online or offline. The target's existing progress is overwritten.
 */
public class CopyProfileCommand implements Command {

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) return false;
        String sourceName = args[1];
        String targetName = args[2];
        boolean confirmed = args.length >= 4 && args[3].equalsIgnoreCase("confirm");

        if (sourceName.equalsIgnoreCase(targetName)) {
            Utils.sendMessage(sender, Utils.chat("&cThe source and target cannot be the same player."));
            return true;
        }

        ProfilePersistence persistence = ProfileRegistry.getPersistence();
        if (persistence == null) {
            Utils.sendMessage(sender, Utils.chat("&cThe profile database is not ready yet."));
            return true;
        }

        if (!confirmed) {
            Utils.sendMessage(sender, Utils.chat("&eThis will &cOVERWRITE&e all of &f" + targetName + "&e's levels, EXP and perks with those of &f" + sourceName + "&e."));
            Utils.sendMessage(sender, Utils.chat("&eRun &f/valhalla copyprofile " + sourceName + " " + targetName + " confirm&e to proceed."));
            return true;
        }

        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        Utils.sendMessage(sender, Utils.chat("&7Copying profile from &f" + sourceName + "&7 to &f" + targetName + "&7, this may take a moment..."));

        CompletableFuture.runAsync(() -> {
            OfflinePlayer source = Bukkit.getOfflinePlayer(sourceName);
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            UUID sourceId = source.getUniqueId();
            UUID targetId = target.getUniqueId();

            boolean sourceLoaded = persistence.isLoaded(sourceId);
            if (!sourceLoaded && !source.hasPlayedBefore()) {
                runSync(() -> Utils.sendMessage(sender, Utils.chat("&cNo player named &f" + sourceName + "&c could be found. Make sure the name is spelled correctly.")));
                return;
            }

            // Read all of the source's profiles, preferring the live cached copy if they are online.
            Map<Class<? extends Profile>, Profile> copies = new HashMap<>();
            boolean hasData = false;
            for (Class<? extends Profile> type : ProfileRegistry.getRegisteredProfiles().keySet()) {
                Profile sourceProfile;
                if (sourceLoaded) {
                    sourceProfile = persistence.getPersistentProfile(sourceId, type);
                    if (sourceProfile == null) sourceProfile = ProfileRegistry.getBlankProfile(sourceId, type);
                } else {
                    sourceProfile = ProfileRegistry.getBlankProfile(sourceId, type);
                    persistence.fillProfile(sourceId, sourceProfile);
                }

                if (sourceProfile.getLevel() > 0 || sourceProfile.getTotalEXP() > 0) hasData = true;
                if (sourceProfile instanceof PowerProfile power && !power.getUnlockedPerks().isEmpty()) hasData = true;

                Profile targetProfile = ProfileRegistry.getBlankProfile(targetId, type);
                targetProfile.copyStats(sourceProfile);
                targetProfile.setShouldForcePersist(true);
                copies.put(type, targetProfile);
            }

            if (!hasData) {
                runSync(() -> Utils.sendMessage(sender, Utils.chat("&c" + sourceName + " has no ValhallaMMO progress to copy.")));
                return;
            }

            if (onlineTarget != null && persistence.isLoaded(targetId)) {
                // Target is online: update the live cache on the main thread and recalculate their stats.
                runSync(() -> {
                    for (Map.Entry<Class<? extends Profile>, Profile> e : copies.entrySet()) {
                        persistence.trySetPersistentProfile(targetId, e.getValue(), e.getKey());
                    }
                    ProfilePersistence.markProfilesReset(targetId, copies.keySet());
                    SkillRegistry.updateSkillProgression(onlineTarget, false);
                    persistence.saveProfileAsync(targetId);
                    Utils.sendMessage(sender, Utils.chat("&aCopied &f" + sourceName + "&a's progress onto &f" + targetName + "&a (online)."));
                });
            } else {
                // Target is offline: write straight to the database under the target's UUID.
                for (Profile targetProfile : copies.values()) {
                    persistence.insertOrUpdateProfile(targetId, targetProfile);
                }
                runSync(() -> Utils.sendMessage(sender, Utils.chat("&aCopied &f" + sourceName + "&a's progress onto &f" + targetName + "&a. They will have it the next time they log in.")));
            }
        }, persistence.profileThreads).exceptionally(ex -> {
            ValhallaMMO.logWarning("Failed to copy profile from " + sourceName + " to " + targetName + ": ");
            ex.printStackTrace();
            runSync(() -> Utils.sendMessage(sender, Utils.chat("&cAn error occurred while copying the profile, check the console.")));
            return null;
        });

        return true;
    }

    private static void runSync(Runnable runnable) {
        Bukkit.getScheduler().runTask(ValhallaMMO.getInstance(), runnable);
    }

    @Override
    public String getFailureMessage(String[] args) {
        return "&c/valhalla copyprofile [source] [target] <confirm>";
    }

    @Override
    public String getDescription() {
        return "Copies the levels, EXP and perks of one account onto another (e.g. after a rename).";
    }

    @Override
    public String getCommand() {
        return "/valhalla copyprofile [source] [target] <confirm>";
    }

    @Override
    public String[] getRequiredPermissions() {
        return new String[]{"valhalla.copyprofile"};
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("valhalla.copyprofile");
    }

    @Override
    public List<String> getSubcommandArgs(CommandSender sender, String[] args) {
        if (args.length == 2 || args.length == 3) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        if (args.length == 4) return new ArrayList<>(List.of("confirm"));
        return Command.noSubcommandArgs();
    }
}
