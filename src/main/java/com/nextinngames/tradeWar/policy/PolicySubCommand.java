package com.nextinngames.tradeWar.policy;

import com.nextinngames.tradeWar.TradeWar;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared plumbing for the tariff / sanction / embargo subcommands of /town and /nation.
 * <p>
 * A town in a nation has no trade policy of its own: only the nation can set it, through its capital.
 * A town without a nation sets its own.
 */
abstract class PolicySubCommand implements CommandExecutor, TabCompleter {

    protected final TradeWar plugin;
    protected final PolicyScope scope;
    private final String name;

    PolicySubCommand(TradeWar plugin, PolicyScope scope, String name) {
        this.plugin = plugin;
        this.scope = scope;
        this.name = name;
    }

    String name() {
        return name;
    }

    PolicyScope scope() {
        return scope;
    }

    protected abstract void sendHelp(Player player);

    protected abstract void add(Player player, PolicyHolder holder, String[] args);

    protected abstract void remove(Player player, PolicyHolder holder, String[] args);

    protected abstract void list(Player player, PolicyHolder holder);

    @Override
    public final boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c[TW] Only players can manage " + name + "s.");
            return true;
        }
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (!action.equals("add") && !action.equals("remove") && !action.equals("list")) {
            sendHelp(player);
            return true;
        }

        PolicyHolder holder = action.equals("list") ? ownHolder(player) : requireManager(player);
        if (holder == null) {
            return true;
        }
        switch (action) {
            case "add" -> add(player, holder, args);
            case "remove" -> remove(player, holder, args);
            default -> list(player, holder);
        }
        return true;
    }

    /**
     * The holder this player belongs to at this command's scope, or null (with the reason sent) if there is none.
     */
    protected PolicyHolder ownHolder(Player player) {
        Resident resident = TownyAPI.getInstance().getResident(player);
        Town town = resident == null ? null : resident.getTownOrNull();
        if (scope == PolicyScope.NATION) {
            Nation nation = resident == null ? null : resident.getNationOrNull();
            if (nation == null) {
                player.sendMessage("§c[TW] You must be in a nation to use /n " + name + ". Towns without a nation use /t " + name + ".");
                return null;
            }
            return new PolicyHolder(PolicyScope.NATION, nation.getName());
        }
        if (town == null) {
            player.sendMessage("§c[TW] You must be in a town to use /t " + name + ".");
            return null;
        }
        Nation nation = town.getNationOrNull();
        if (nation != null) {
            player.sendMessage("§c[TW] Your town is part of the Nation of " + nation.getName()
                    + ". Its trade policy is set by the capital with /n " + name + ".");
            return null;
        }
        return new PolicyHolder(PolicyScope.TOWN, town.getName());
    }

    /**
     * Like {@link #ownHolder} but also requires the right to change policy:
     * <ul>
     *   <li>Nation: the King (the capital's mayor), or a resident of the capital whose Towny rank has
     *       {@code towny.command.nation.<name>}.</li>
     *   <li>Town (no nation): the Mayor, or a resident whose Towny rank has {@code towny.command.town.<name>}.</li>
     * </ul>
     * {@code tradewar.admin} bypasses the rank check, but never lets a town in a nation act on its own.
     */
    protected PolicyHolder requireManager(Player player) {
        PolicyHolder holder = ownHolder(player);
        if (holder == null || player.hasPermission("tradewar.admin")) {
            return holder;
        }
        Resident resident = TownyAPI.getInstance().getResident(player);
        boolean allowed;
        if (scope == PolicyScope.NATION) {
            allowed = resident.isKing()
                    || (resident.getTownOrNull().isCapital() && resident.hasPermissionNode("towny.command.nation." + name));
        } else {
            allowed = resident.isMayor() || resident.hasPermissionNode("towny.command.town." + name);
        }
        if (!allowed) {
            player.sendMessage(scope == PolicyScope.NATION
                    ? "§c[TW] Only your nation's capital (the King, or a capital rank allowed to) can manage " + name + "s."
                    : "§c[TW] Only the Mayor (or a town rank allowed to) can manage town " + name + "s.");
            return null;
        }
        return holder;
    }

    protected String usage(String rest) {
        return "§cUsage: " + scope.command() + " " + name + " " + rest;
    }

    /** {@code §eThe Nation of §fCuba} */
    protected static String announceName(PolicyHolder holder) {
        return "§eThe " + holder.scope().label() + " of §f" + holder.name();
    }

    /** Whether {@code town} is the holder itself or one of its member towns. */
    protected static boolean isOwnTown(PolicyHolder holder, Town town) {
        if (holder.scope() == PolicyScope.TOWN) {
            return town.getName().equalsIgnoreCase(holder.name());
        }
        Nation nation = town.getNationOrNull();
        return nation != null && nation.getName().equalsIgnoreCase(holder.name());
    }

    protected static boolean isOwnNation(PolicyHolder holder, String nationName) {
        return holder.scope() == PolicyScope.NATION && holder.name().equalsIgnoreCase(nationName);
    }

    protected static Town findTown(String name) {
        return TownyAPI.getInstance().getTown(name);
    }

    protected static Nation findNation(String name) {
        return TownyAPI.getInstance().getNation(name);
    }

    protected static String stripPrefix(String raw, String prefix) {
        return raw.regionMatches(true, 0, prefix, 0, prefix.length()) ? raw.substring(prefix.length()) : null;
    }

    protected static List<String> filterByStart(List<String> candidates, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }

    protected static List<String> townNames() {
        return TownyAPI.getInstance().getTowns().stream().map(Town::getName).collect(Collectors.toList());
    }

    protected static List<String> nationNames() {
        return TownyAPI.getInstance().getNations().stream().map(Nation::getName).collect(Collectors.toList());
    }

    /** The sender's own policies, quietly, for tab completion. */
    protected PolicySet senderPolicies(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return null;
        }
        Resident resident = TownyAPI.getInstance().getResident(player);
        Town town = resident == null ? null : resident.getTownOrNull();
        PolicyHolder holder = PolicyHolder.governing(town);
        return holder != null && holder.scope() == scope ? plugin.getData().get(holder) : null;
    }

    protected static void playGlobalAlert() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }
    }

    /** Refreshes map markers of a tariff/embargo/sanction target. */
    protected void refreshMapTarget(String targetType, String targetName) {
        if (Tariff.NATION.equals(targetType)) {
            Nation nation = findNation(targetName);
            if (nation != null) {
                refreshMapTowns(nation.getTowns().stream().map(Town::getName).collect(Collectors.toSet()));
            }
        } else {
            Town town = findTown(targetName);
            if (town != null) {
                refreshMapTowns(Set.of(town.getName()));
            }
        }
    }

    protected void refreshMapTowns(Set<String> townNames) {
        plugin.getBlueMapIntegration().ifPresent(bm -> bm.onTownsChanged(townNames));
        plugin.getPl3xMapIntegration().ifPresent(pm -> pm.onTownsChanged(townNames));
    }

    protected void refreshMapAll() {
        plugin.getBlueMapIntegration().ifPresent(bm -> bm.fullRefresh());
        plugin.getPl3xMapIntegration().ifPresent(pm -> pm.fullRefresh());
    }
}
