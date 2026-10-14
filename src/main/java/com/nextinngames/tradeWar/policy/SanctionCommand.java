package com.nextinngames.tradeWar.policy;

import com.nextinngames.tradeWar.DiscordWebhook;
import com.nextinngames.tradeWar.TradeWar;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * /t|/n sanction add <town:Name|player:Name>
 * /t|/n sanction remove <town:Name|player:Name>
 * /t|/n sanction list
 */
public class SanctionCommand extends PolicySubCommand {

    public SanctionCommand(TradeWar plugin, PolicyScope scope) {
        super(plugin, scope, "sanction");
    }

    @Override
    protected void sendHelp(Player player) {
        String cmd = scope.command() + " sanction";
        player.sendMessage("§6--- " + scope.label() + " Sanctions ---");
        player.sendMessage("§e" + cmd + " add <town:Name|player:Name>");
        player.sendMessage("§e" + cmd + " remove <town:Name|player:Name>");
        player.sendMessage("§e" + cmd + " list");
        player.sendMessage("§7Sanctioned towns/players cannot trade with your shops, and your people cannot buy from theirs.");
    }

    @Override
    protected void add(Player player, PolicyHolder holder, String[] args) {
        if (args.length != 2) {
            player.sendMessage(usage("add <town:Name|player:Name>"));
            return;
        }

        Target target = resolveTarget(args[1]);
        if (target == null) {
            player.sendMessage("§c[TW] No town or player named '" + args[1] + "'.");
            return;
        }
        if (target.isTown() && isOwnTown(holder, findTown(target.displayName()))) {
            player.sendMessage("§c[TW] You cannot sanction your own town.");
            return;
        }

        if (!plugin.getData().getOrCreate(holder).sanctions.add(target.key())) {
            player.sendMessage("§e[TW] " + target.displayName() + " is already sanctioned.");
            return;
        }
        plugin.getData().saveData();
        if (target.isTown()) {
            refreshMapTarget(Tariff.TOWN, target.displayName());
        }

        player.sendMessage("§a[TW] Sanctioned " + target.kind() + ": " + target.displayName());
        Bukkit.broadcastMessage("§l[TradeWar] §lSANCTION! " + announceName(holder)
                + " §ehas officially sanctioned " + target.kind() + " §f" + target.displayName() + "§e from doing business!");
        playGlobalAlert();

        String[][] fields = {
                {"Issued By", player.getName()},
                {"Issuer", holder.label()},
                {"Target Type", target.isTown() ? "Town" : "Player"},
                {"Target", target.displayName()}
        };
        DiscordWebhook.sendEmbed(plugin, "New Sanction Issued", 16711680, fields);
    }

    @Override
    protected void remove(Player player, PolicyHolder holder, String[] args) {
        if (args.length != 2) {
            player.sendMessage(usage("remove <town:Name|player:Name>"));
            return;
        }

        PolicySet policies = plugin.getData().get(holder);
        String key = policies == null ? null : findStoredKey(policies.sanctions, args[1]);
        if (key == null) {
            player.sendMessage("§c[TW] You have no sanction on '" + args[1] + "'.");
            return;
        }
        policies.sanctions.remove(key);
        plugin.getData().pruneIfEmpty(holder);
        plugin.getData().saveData();

        String display = displayName(key);
        if (isTownKey(key)) {
            refreshMapTarget(Tariff.TOWN, display);
        }

        player.sendMessage("§a[TW] Removed sanction on " + display);
        Bukkit.broadcastMessage("§l[TradeWar] " + announceName(holder) + " §ehas lifted sanctions on §f" + display + "§e!");
        DiscordWebhook.sendEmbed(plugin, "Sanction Lifted", 65280,
                new String[][]{{"Lifted By", player.getName()}, {"Issuer", holder.label()}, {"Target", display}});
    }

    @Override
    protected void list(Player player, PolicyHolder holder) {
        PolicySet policies = plugin.getData().get(holder);
        if (policies == null || policies.sanctions.isEmpty()) {
            player.sendMessage("§e[TW] The " + holder.label() + " has no active sanctions.");
            return;
        }
        List<String> names = new ArrayList<>();
        for (String key : policies.sanctions) {
            names.add(prefixedDisplay(key));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        player.sendMessage("§e[TW] Active Sanctions: §f" + String.join(", ", names));
    }

    /**
     * {@code town:X} / {@code player:X}, or a bare name that is tried as a town first, then a player.
     */
    private static Target resolveTarget(String raw) {
        String name;
        if ((name = stripPrefix(raw, PolicyRules.TOWN_PREFIX)) != null) {
            return townTarget(name);
        }
        if ((name = stripPrefix(raw, PolicyRules.PLAYER_PREFIX)) != null) {
            return playerTarget(name);
        }
        Target town = townTarget(raw);
        return town != null ? town : playerTarget(raw);
    }

    private static Target townTarget(String name) {
        Town town = findTown(name);
        return town == null ? null : new Target(PolicyRules.townKey(town.getName()), "town", town.getName());
    }

    private static Target playerTarget(String name) {
        Resident resident = TownyAPI.getInstance().getResident(name);
        if (resident == null || resident.getUUID() == null) {
            return null;
        }
        return new Target(PolicyRules.playerKey(resident.getUUID()), "player", resident.getName());
    }

    private static String findStoredKey(Set<String> sanctions, String raw) {
        String townName = stripPrefix(raw, PolicyRules.TOWN_PREFIX);
        String playerName = stripPrefix(raw, PolicyRules.PLAYER_PREFIX);
        String wantedPrefix = townName != null ? PolicyRules.TOWN_PREFIX : playerName != null ? PolicyRules.PLAYER_PREFIX : null;
        String wantedName = townName != null ? townName : playerName != null ? playerName : raw;
        for (String key : sanctions) {
            if ((wantedPrefix == null || key.regionMatches(true, 0, wantedPrefix, 0, wantedPrefix.length()))
                    && displayName(key).equalsIgnoreCase(wantedName)) {
                return key;
            }
        }
        return null;
    }

    private static boolean isTownKey(String key) {
        return key.regionMatches(true, 0, PolicyRules.TOWN_PREFIX, 0, PolicyRules.TOWN_PREFIX.length());
    }

    /** e.g. {@code town:Havana} or {@code player:Steve}; shared with the Towny status screen. */
    static String prefixedDisplay(String key) {
        return (isTownKey(key) ? PolicyRules.TOWN_PREFIX : PolicyRules.PLAYER_PREFIX) + displayName(key);
    }

    private static String displayName(String key) {
        if (isTownKey(key)) {
            return key.substring(PolicyRules.TOWN_PREFIX.length());
        }
        String rawId = key.substring(PolicyRules.PLAYER_PREFIX.length());
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(rawId));
            return player.getName() != null ? player.getName() : rawId;
        } catch (IllegalArgumentException e) {
            return rawId;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filterByStart(List.of("add", "remove", "list"), args[0]);
        }
        if (args.length != 2) {
            return List.of();
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        List<String> targets = new ArrayList<>();
        if (action.equals("add")) {
            townNames().forEach(t -> targets.add(PolicyRules.TOWN_PREFIX + t));
            Bukkit.getOnlinePlayers().forEach(p -> targets.add(PolicyRules.PLAYER_PREFIX + p.getName()));
        } else if (action.equals("remove")) {
            PolicySet policies = senderPolicies(sender);
            if (policies != null) {
                policies.sanctions.forEach(key -> targets.add(prefixedDisplay(key)));
            }
        }
        return filterByStart(targets, args[1]);
    }

    private record Target(String key, String kind, String displayName) {
        boolean isTown() {
            return kind.equals("town");
        }
    }
}
