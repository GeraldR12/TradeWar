package com.nextinngames.tradeWar;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class NationTradeCommand implements CommandExecutor {
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final double DEFAULT_MAX_TARIFF_PERCENTAGE = 100.0;

    private final TradeWar plugin;

    public NationTradeCommand(TradeWar plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident == null || !resident.hasTown()) {
            player.sendMessage("§c[TW] You must be in a town to use trade policies.");
            return true;
        }

        if (args.length < 2) {
            sendHelpMenu(player);
            return true;
        }

        String category = args[0].toLowerCase(Locale.ROOT);
        String action = args[1].toLowerCase(Locale.ROOT);

        switch (category) {
            case "tariff" -> handleTariff(player, resident, action, args);
            case "sanction" -> handleSanction(player, resident, action, args);
            case "embargo" -> handleEmbargo(player, resident, action, args);
            default -> player.sendMessage("§cUnknown category. Use tariff, sanction, or embargo.");
        }
        return true;
    }

    private void sendHelpMenu(Player player) {
        player.sendMessage("§6--- TradeWar Help ---");
        player.sendMessage("§e/tw tariff add <import/export> <target_town> <item/all> <%> [minutes]");
        player.sendMessage("§e/tw tariff remove");
        player.sendMessage("§e/tw tariff list");
        player.sendMessage("§e/tw sanction add <target_town>");
        player.sendMessage("§e/tw sanction remove <target_town>");
        player.sendMessage("§e/tw sanction list");
        player.sendMessage("§e/tw embargo add <target_nation>");
        player.sendMessage("§e/tw embargo remove <target_nation>");
        player.sendMessage("§e/tw embargo list");
    }

    private void playGlobalAlert() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }
    }

    private void handleTariff(Player player, Resident resident, String action, String[] args) {
        Town hostTown = resident.getTownOrNull();
        if (hostTown == null) {
            player.sendMessage("§c[TW] Your town could not be resolved.");
            return;
        }

        if (!resident.isMayor() && !player.hasPermission("tradewar.admin")) {
            player.sendMessage("§c[TW] Only the Mayor can manage town tariffs.");
            return;
        }

        switch (action) {
            case "add" -> addTariff(player, hostTown, args);
            case "remove" -> clearTariffs(player, hostTown);
            case "list" -> listTariffs(player, hostTown);
            default -> player.sendMessage("§cUnknown action. Use add, remove, or list.");
        }
    }

    private void addTariff(Player player, Town hostTown, String[] args) {
        if (args.length != 6 && args.length != 7) {
            player.sendMessage("§cUsage: /tw tariff add <import/export> <target_town> <item/all> <%> [minutes]");
            return;
        }

        String type = args[2].toLowerCase(Locale.ROOT);
        if (!type.equals("import") && !type.equals("export")) {
            player.sendMessage("§c[TW] Type must be 'import' or 'export'.");
            return;
        }

        Town targetTown = findTown(args[3]);
        if (targetTown == null) {
            player.sendMessage("§c[TW] Town '" + args[3] + "' does not exist.");
            return;
        }

        if (targetTown.getName().equalsIgnoreCase(hostTown.getName())) {
            player.sendMessage("§c[TW] You cannot impose a tariff on your own town.");
            return;
        }

        Material item = null;
        if (!args[4].equalsIgnoreCase("all")) {
            item = Material.matchMaterial(args[4]);
            if (item == null || !item.isItem()) {
                player.sendMessage("§c[TW] Invalid item: " + args[4] + ".");
                return;
            }
        }

        double percentage;
        try {
            percentage = Double.parseDouble(args[5]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c[TW] Tariff percentage must be a valid number.");
            return;
        }

        double maxPercentage = getMaxTariffPercentage();
        if (!Double.isFinite(percentage) || percentage <= 0.0 || percentage > maxPercentage) {
            player.sendMessage("§c[TW] Tariff percentage must be greater than 0 and no more than " + formatNumber(maxPercentage) + "%.");
            return;
        }

        long expiry = 0L;
        String duration = "Permanent";
        if (args.length == 7) {
            long minutes;
            try {
                minutes = Long.parseLong(args[6]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c[TW] Duration must be a valid number of minutes.");
                return;
            }

            if (minutes <= 0L) {
                player.sendMessage("§c[TW] Duration must be greater than 0 minutes.");
                return;
            }

            try {
                expiry = Math.addExact(System.currentTimeMillis(), Math.multiplyExact(minutes, MILLIS_PER_MINUTE));
            } catch (ArithmeticException e) {
                player.sendMessage("§c[TW] Duration is too large.");
                return;
            }
            duration = minutes + (minutes == 1 ? " Minute" : " Minutes");
        }

        String targetTownName = targetTown.getName();
        TradeDataManager.TariffRule rule = new TradeDataManager.TariffRule(
                type,
                "town",
                targetTownName,
                item,
                percentage,
                expiry
        );

        plugin.getData().tariffRules.computeIfAbsent(hostTown.getName(), ignored -> new ArrayList<>()).add(rule);
        plugin.getData().saveData();
        plugin.getBlueMapIntegration().ifPresent(bm -> bm.onTownChanged(targetTownName));
        player.sendMessage("§a[TW] Tariff added successfully!");

        String itemName = item == null ? "ALL_GOODS" : item.name();
        String announceMsg = "§l[TradeWar] §eThe Town of §f" + hostTown.getName()
                + " §ehas imposed a §6" + formatNumber(percentage) + "% " + type.toUpperCase(Locale.ROOT)
                + " tariff §eon town §f" + targetTownName + " §efor §b" + itemName + "§e!";
        Bukkit.broadcastMessage(announceMsg);
        playGlobalAlert();

        String[][] fields = {
                {"Issued By", player.getName()},
                {"Issuing Town", hostTown.getName()},
                {"Target Type", "Town"},
                {"Target", targetTownName},
                {"Item", itemName},
                {"Percentage", String.format(Locale.ROOT, "%.2f%%", percentage)},
                {"Duration", duration}
        };
        DiscordWebhook.sendEmbed(plugin, "New Tariff Issued", 16753920, fields);
    }

    private void clearTariffs(Player player, Town hostTown) {
        plugin.getData().tariffRules.remove(hostTown.getName());
        plugin.getData().saveData();
        plugin.getBlueMapIntegration().ifPresent(bm -> bm.fullRefresh());
        player.sendMessage("§a[TW] All tariffs cleared.");

        Bukkit.broadcastMessage("§l[TradeWar] §eThe Town of §f" + hostTown.getName() + " §ehas lifted all trade tariffs!");
        String[][] fields = {{"Action By", player.getName()}, {"Town", hostTown.getName()}};
        DiscordWebhook.sendEmbed(plugin, "Tariffs Lifted", 65280, fields);
    }

    private void listTariffs(Player player, Town hostTown) {
        List<TradeDataManager.TariffRule> rules = plugin.getData().tariffRules.getOrDefault(hostTown.getName(), List.of());
        if (rules.isEmpty()) {
            player.sendMessage("§e[TW] Your town has no active tariffs.");
            return;
        }

        player.sendMessage("§6--- Active Tariffs ---");
        for (TradeDataManager.TariffRule rule : rules) {
            String itemDisplay = rule.item() == null ? "ALL" : rule.item().name();
            String durationDisplay = "Permanent";
            if (rule.expiryTime() > 0) {
                long remainingMillis = rule.expiryTime() - System.currentTimeMillis();
                if (remainingMillis <= 0) {
                    durationDisplay = "Expired";
                } else {
                    long remainingMinutes = Math.max(1L, (remainingMillis + MILLIS_PER_MINUTE - 1L) / MILLIS_PER_MINUTE);
                    durationDisplay = remainingMinutes + (remainingMinutes == 1 ? " min left" : " mins left");
                }
            }

            player.sendMessage(String.format(
                    Locale.ROOT,
                    "§e- [%s] On Town %s for item %s: §f%.1f%% §e(%s)",
                    rule.type().toUpperCase(Locale.ROOT),
                    rule.targetName(),
                    itemDisplay,
                    rule.percentage(),
                    durationDisplay
            ));
        }
    }

    private Town findTown(String name) {
        return TownyAPI.getInstance().getTowns().stream()
                .filter(town -> town.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private void notifyTownChanged(String rawTownName) {
        Town town = TownyAPI.getInstance().getTown(rawTownName);
        if (town != null) {
            plugin.getBlueMapIntegration().ifPresent(bm -> bm.onTownChanged(town.getName()));
        }
    }

    private void notifyNationChanged(String rawNationName) {
        Nation nation = TownyAPI.getInstance().getNation(rawNationName);
        if (nation == null) {
            return;
        }
        Set<String> townNames = nation.getTowns().stream().map(Town::getName).collect(Collectors.toSet());
        plugin.getBlueMapIntegration().ifPresent(bm -> bm.onTownsChanged(townNames));
    }

    private double getMaxTariffPercentage() {
        double configured = plugin.getConfig().getDouble("tariffs.max-percentage", DEFAULT_MAX_TARIFF_PERCENTAGE);
        return Double.isFinite(configured) && configured > 0.0 ? configured : DEFAULT_MAX_TARIFF_PERCENTAGE;
    }

    private String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private void handleEmbargo(Player player, Resident resident, String action, String[] args) {
        if (!resident.hasNation() || (!resident.isKing() && !player.hasPermission("tradewar.admin"))) {
            player.sendMessage("§c[TW] Only the King of a nation can manage trade embargoes.");
            return;
        }
        Nation hostNation = resident.getNationOrNull();
        String host = hostNation.getName();

        try {
            if (action.equals("add") && args.length >= 3) {
                String target = args[2];
                plugin.getData().embargoes.computeIfAbsent(host, k -> new HashSet<>()).add(target);
                player.sendMessage("§a[TW] Embargoed nation: " + target);
                plugin.getData().saveData();
                notifyNationChanged(target);

                String announceMsg = "§l[TradeWar] §lEMBARGO! §eThe Nation of §f" + host + " §ehas declared a total trade embargo against the Nation of §f" + target + "§e!";
                Bukkit.broadcastMessage(announceMsg);
                playGlobalAlert();

                String[][] fields = {{"Issued By", player.getName()}, {"Issuing Nation", host}, {"Target Nation", target}};
                DiscordWebhook.sendEmbed(plugin, "New Embargo Issued", 12582912, fields);

            } else if (action.equals("remove") && args.length >= 3) {
                String target = args[2];
                if (plugin.getData().embargoes.containsKey(host)) {
                    plugin.getData().embargoes.get(host).remove(target);
                    player.sendMessage("§a[TW] Lifted embargo on " + target);
                    plugin.getData().saveData();
                    notifyNationChanged(target);

                    String announceMsg = "§l[TradeWar] §eThe Nation of §f" + host + " §ehas lifted the trade embargo against the Nation of §f" + target + "§e!";
                    Bukkit.broadcastMessage(announceMsg);

                    String[][] fields = {{"Lifted By", player.getName()}, {"Issuing Nation", host}, {"Target Nation", target}};
                    DiscordWebhook.sendEmbed(plugin, "Embargo Lifted", 65280, fields);
                }
            } else if (action.equals("list")) {
                Set<String> activeEmbargoes = plugin.getData().embargoes.getOrDefault(host, new HashSet<>());
                if (activeEmbargoes.isEmpty()) {
                    player.sendMessage("§e[TW] Your nation has no active embargoes.");
                } else {
                    player.sendMessage("§e[TW] Active Embargoes: §f" + String.join(", ", activeEmbargoes));
                }
            } else {
                player.sendMessage("§cUnknown action. Use add, remove, or list.");
            }
        } catch (Exception e) {
            player.sendMessage("§cOperation failed.");
            e.printStackTrace();
        }
    }

    private void handleSanction(Player player, Resident resident, String action, String[] args) {
        Town hostTown = resident.getTownOrNull();
        if (!resident.isMayor() && !player.hasPermission("tradewar.admin")) {
            player.sendMessage("§c[TW] Only the Mayor can manage town sanctions.");
            return;
        }
        String host = hostTown.getName();

        try {
            if (action.equals("add") && args.length >= 3) {
                String target = args[2];
                plugin.getData().sanctions.computeIfAbsent(host, k -> new HashSet<>()).add(target);
                player.sendMessage("§a[TW] Sanctioned town: " + target);
                plugin.getData().saveData();
                notifyTownChanged(target);

                String announceMsg = "§l[TradeWar] §lSANCTION! §eThe Town of §f" + host + " §ehas officially sanctioned town §f" + target + "§e from doing business!";
                Bukkit.broadcastMessage(announceMsg);
                playGlobalAlert();

                String[][] fields = {{"Issued By", player.getName()}, {"Issuing Town", host}, {"Target Type", "Town"}, {"Target", target}};
                DiscordWebhook.sendEmbed(plugin, "New Sanction Issued", 16711680, fields);

            } else if (action.equals("remove") && args.length >= 3) {
                String target = args[2];
                if (plugin.getData().sanctions.containsKey(host)) {
                    plugin.getData().sanctions.get(host).remove(target);
                    player.sendMessage("§a[TW] Removed sanction on " + target);
                    plugin.getData().saveData();
                    notifyTownChanged(target);

                    String announceMsg = "§l[TradeWar] §eThe Town of §f" + host + " §ehas lifted sanctions on town §f" + target + "§e!";
                    Bukkit.broadcastMessage(announceMsg);

                    String[][] fields = {{"Lifted By", player.getName()}, {"Issuing Town", host}, {"Target Town", target}};
                    DiscordWebhook.sendEmbed(plugin, "Sanction Lifted", 65280, fields);
                }
            } else if (action.equals("list")) {
                Set<String> activeSanctions = plugin.getData().sanctions.getOrDefault(host, new HashSet<>());
                if (activeSanctions.isEmpty()) {
                    player.sendMessage("§e[TW] Your town has no active sanctions.");
                } else {
                    player.sendMessage("§e[TW] Active Sanctions: §f" + String.join(", ", activeSanctions));
                }
            } else {
                player.sendMessage("§cUnknown action. Use add, remove, or list.");
            }
        } catch (Exception e) {
            player.sendMessage("§cOperation failed.");
            e.printStackTrace();
        }
    }
}
