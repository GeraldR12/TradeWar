package com.nextinngames.tradeWar.policy;

import com.nextinngames.tradeWar.DiscordWebhook;
import com.nextinngames.tradeWar.TradeWar;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * /t|/n tariff add <nation:Name|town:Name> <percent> [duration]
 * /t|/n tariff remove <nation:Name|town:Name|all>
 * /t|/n tariff list
 */
public class TariffCommand extends PolicySubCommand {
    private static final double DEFAULT_MAX_TARIFF_PERCENTAGE = 100.0;

    public TariffCommand(TradeWar plugin, PolicyScope scope) {
        super(plugin, scope, "tariff");
    }

    @Override
    protected void sendHelp(Player player) {
        String cmd = scope.command() + " tariff";
        player.sendMessage("§6--- " + scope.label() + " Tariffs ---");
        player.sendMessage("§e" + cmd + " add <nation:Name|town:Name> <%> [30m|2h|1d]");
        player.sendMessage("§e" + cmd + " remove <nation:Name|town:Name>");
        player.sendMessage("§e" + cmd + " remove all");
        player.sendMessage("§e" + cmd + " list");
        player.sendMessage("§7Tariffs are charged on all goods your shops sell to the target.");
    }

    @Override
    protected void add(Player player, PolicyHolder holder, String[] args) {
        if (args.length != 3 && args.length != 4) {
            player.sendMessage(usage("add <nation:Name|town:Name> <%> [30m|2h|1d]"));
            return;
        }

        Target target = resolveTarget(args[1]);
        if (target == null) {
            player.sendMessage("§c[TW] No nation or town named '" + args[1] + "'.");
            return;
        }
        if (target.type().equals(Tariff.NATION) ? isOwnNation(holder, target.name()) : isOwnTown(holder, findTown(target.name()))) {
            player.sendMessage("§c[TW] You cannot impose a tariff on yourself.");
            return;
        }

        double percentage;
        try {
            percentage = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c[TW] Tariff percentage must be a valid number.");
            return;
        }
        double maxPercentage = getMaxTariffPercentage();
        if (!Double.isFinite(percentage) || percentage <= 0.0 || percentage > maxPercentage) {
            player.sendMessage("§c[TW] Tariff percentage must be greater than 0 and no more than " + PolicyRules.formatPercent(maxPercentage) + "%.");
            return;
        }

        long expiry = 0L;
        if (args.length == 4) {
            try {
                expiry = Math.addExact(System.currentTimeMillis(), PolicyRules.parseDurationMillis(args[3]));
            } catch (NumberFormatException | ArithmeticException e) {
                player.sendMessage("§c[TW] Invalid duration '" + args[3] + "'. Use e.g. 90 (minutes), 30m, 2h or 1d.");
                return;
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c[TW] Duration must be greater than 0.");
                return;
            }
        }
        String duration = PolicyRules.formatRemaining(expiry, System.currentTimeMillis());

        plugin.getData().getOrCreate(holder).putTariff(new Tariff(target.type(), target.name(), percentage, expiry));
        plugin.getData().saveData();
        refreshMapTarget(target.type(), target.name());

        String percent = PolicyRules.formatPercent(percentage);
        player.sendMessage("§a[TW] Tariff of " + percent + "% set on " + target.display() + " (" + duration + ").");
        Bukkit.broadcastMessage("§l[TradeWar] " + announceName(holder) + " §ehas imposed a §6" + percent
                + "% tariff §eon " + target.type() + " §f" + target.name() + " §efor §bALL_GOODS§e!");
        playGlobalAlert();

        String[][] fields = {
                {"Issued By", player.getName()},
                {"Issuer", holder.label()},
                {"Target", target.display()},
                {"Item", "ALL_GOODS"},
                {"Percentage", String.format(Locale.ROOT, "%.2f%%", percentage)},
                {"Duration", duration}
        };
        DiscordWebhook.sendEmbed(plugin, "New Tariff Issued", 16753920, fields);
    }

    @Override
    protected void remove(Player player, PolicyHolder holder, String[] args) {
        if (args.length != 2) {
            player.sendMessage(usage("remove <nation:Name|town:Name|all>"));
            return;
        }
        PolicySet policies = plugin.getData().get(holder);
        List<Tariff> tariffs = policies == null ? List.of() : policies.tariffs;

        if (args[1].equalsIgnoreCase("all")) {
            if (tariffs.isEmpty()) {
                player.sendMessage("§e[TW] You have no tariffs to remove.");
                return;
            }
            policies.tariffs.clear();
            plugin.getData().pruneIfEmpty(holder);
            plugin.getData().saveData();
            refreshMapAll();
            player.sendMessage("§a[TW] All tariffs cleared.");
            Bukkit.broadcastMessage("§l[TradeWar] " + announceName(holder) + " §ehas lifted all trade tariffs!");
            DiscordWebhook.sendEmbed(plugin, "Tariffs Lifted", 65280,
                    new String[][]{{"Action By", player.getName()}, {"Issuer", holder.label()}});
            return;
        }

        // Match against stored entries so tariffs on since-deleted towns/nations can still be removed.
        String raw = args[1];
        String type = null;
        String name = raw;
        String stripped;
        if ((stripped = stripPrefix(raw, PolicyRules.NATION_PREFIX)) != null) {
            type = Tariff.NATION;
            name = stripped;
        } else if ((stripped = stripPrefix(raw, PolicyRules.TOWN_PREFIX)) != null) {
            type = Tariff.TOWN;
            name = stripped;
        }
        String matchType = type;
        String matchName = name;
        List<Tariff> removed = tariffs.stream()
                .filter(t -> (matchType == null || t.targetType().equals(matchType)) && t.targetName().equalsIgnoreCase(matchName))
                .toList();
        if (removed.isEmpty()) {
            player.sendMessage("§c[TW] You have no tariff on '" + raw + "'.");
            return;
        }
        policies.tariffs.removeAll(removed);
        plugin.getData().pruneIfEmpty(holder);
        plugin.getData().saveData();
        removed.forEach(t -> refreshMapTarget(t.targetType(), t.targetName()));

        String targetName = removed.get(0).targetName();
        player.sendMessage("§a[TW] Removed tariff on " + targetName + ".");
        Bukkit.broadcastMessage("§l[TradeWar] " + announceName(holder) + " §ehas lifted its tariff on §f" + targetName + "§e!");
        DiscordWebhook.sendEmbed(plugin, "Tariff Lifted", 65280,
                new String[][]{{"Lifted By", player.getName()}, {"Issuer", holder.label()}, {"Target", targetName}});
    }

    @Override
    protected void list(Player player, PolicyHolder holder) {
        long now = System.currentTimeMillis();
        PolicySet policies = plugin.getData().get(holder);
        List<Tariff> tariffs = new ArrayList<>(policies == null ? List.of() : policies.tariffs);
        tariffs.removeIf(t -> t.isExpired(now));
        if (tariffs.isEmpty()) {
            player.sendMessage("§e[TW] The " + holder.label() + " has no active tariffs.");
            return;
        }
        tariffs.sort(Comparator.comparing(Tariff::targetName, String.CASE_INSENSITIVE_ORDER));
        player.sendMessage("§6--- " + holder.label() + " Tariffs ---");
        for (Tariff tariff : tariffs) {
            player.sendMessage("§e- " + describe(tariff, now));
        }
    }

    /** e.g. {@code nation:Cuba §f10% §e(2h 5m)}; shared with the Towny status screen. */
    static String describe(Tariff tariff, long now) {
        return tariff.targetType() + ":" + tariff.targetName() + " §f" + PolicyRules.formatPercent(tariff.percentage())
                + "% §e(" + PolicyRules.formatRemaining(tariff.expiryTime(), now) + ")";
    }

    /**
     * {@code nation:X} / {@code town:X}, or a bare name that is tried as a nation first, then a town.
     */
    private static Target resolveTarget(String raw) {
        String name;
        if ((name = stripPrefix(raw, PolicyRules.NATION_PREFIX)) != null) {
            Nation nation = findNation(name);
            return nation == null ? null : new Target(Tariff.NATION, nation.getName());
        }
        if ((name = stripPrefix(raw, PolicyRules.TOWN_PREFIX)) != null) {
            Town town = findTown(name);
            return town == null ? null : new Target(Tariff.TOWN, town.getName());
        }
        Nation nation = findNation(raw);
        if (nation != null) {
            return new Target(Tariff.NATION, nation.getName());
        }
        Town town = findTown(raw);
        return town == null ? null : new Target(Tariff.TOWN, town.getName());
    }

    private double getMaxTariffPercentage() {
        double configured = plugin.getConfig().getDouble("tariffs.max-percentage", DEFAULT_MAX_TARIFF_PERCENTAGE);
        return Double.isFinite(configured) && configured > 0.0 ? configured : DEFAULT_MAX_TARIFF_PERCENTAGE;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filterByStart(List.of("add", "remove", "list"), args[0]);
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && action.equals("add")) {
            List<String> targets = new ArrayList<>();
            nationNames().forEach(n -> targets.add(PolicyRules.NATION_PREFIX + n));
            townNames().forEach(t -> targets.add(PolicyRules.TOWN_PREFIX + t));
            return filterByStart(targets, args[1]);
        }
        if (args.length == 2 && action.equals("remove")) {
            List<String> targets = new ArrayList<>();
            targets.add("all");
            PolicySet policies = senderPolicies(sender);
            if (policies != null) {
                policies.tariffs.forEach(t -> targets.add(t.targetType() + ":" + t.targetName()));
            }
            return filterByStart(targets, args[1]);
        }
        if (args.length == 3 && action.equals("add")) {
            return filterByStart(List.of("5", "10", "15", "20", "25", "30"), args[2]);
        }
        if (args.length == 4 && action.equals("add")) {
            return filterByStart(List.of("30m", "1h", "12h", "1d", "7d"), args[3]);
        }
        return List.of();
    }

    private record Target(String type, String name) {
        String display() {
            return type + ":" + name;
        }
    }
}
