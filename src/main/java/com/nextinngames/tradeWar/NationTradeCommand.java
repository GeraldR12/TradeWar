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
import java.util.Set;

public class NationTradeCommand implements CommandExecutor {
    private final TradeWar plugin;

    public NationTradeCommand(TradeWar plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        Resident resident = TownyAPI.getInstance().getResident(player);

        if (resident == null || !resident.hasTown()) {
            player.sendMessage("§c[TW] You must be in a town to use trade policies.");
            return true;
        }

        if (args.length < 2) {
            sendHelpMenu(player);
            return true;
        }

        String category = args[0].toLowerCase();
        String action = args[1].toLowerCase();

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
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }
    }

    private void handleTariff(Player player, Resident resident, String action, String[] args) {
        Town hostTown = resident.getTownOrNull();
        if (!resident.isMayor() && !player.hasPermission("tradewar.admin")) {
            player.sendMessage("§c[TW] Only the Mayor can manage town tariffs.");
            return;
        }

        if (action.equals("add")) {
            if (args.length < 6) {
                player.sendMessage("§cUsage: /tw tariff add <import/export> <target_town> <item/all> <%> [minutes]");
                return;
            }
            try {
                String type = args[2].toLowerCase();
                if (!type.equals("import") && !type.equals("export")) {
                    player.sendMessage("§cType must be 'import' or 'export'.");
                    return;
                }

                String targetTownName = args[3];
                Material item = args[4].equalsIgnoreCase("all") ? null : Material.valueOf(args[4].toUpperCase());
                double percentage = Double.parseDouble(args[5]);

                long expiry = 0;
                String durationStr = "Permanent";
                if (args.length > 6) {
                    long minutes = Long.parseLong(args[6]);
                    expiry = System.currentTimeMillis() + (minutes * 60000L);
                    durationStr = minutes + " Minutes";
                }

                TradeDataManager.TariffRule rule = new TradeDataManager.TariffRule(
                        type, "town", targetTownName, item, percentage, expiry
                );

                plugin.getData().tariffRules.computeIfAbsent(hostTown.getName(), k -> new ArrayList<>()).add(rule);
                player.sendMessage("§a[TW] Tariff added successfully!");
                plugin.getData().saveData();

                String itemStr = item == null ? "ALL_GOODS" : item.name();
                String announceMsg = "§l[TradeWar] §eThe Town of §f" + hostTown.getName() + " §ehas imposed a §6" + percentage + "% " + type.toUpperCase() + " tariff §eon town §f" + targetTownName + " §efor §b" + itemStr + "§e!";
                Bukkit.broadcastMessage(announceMsg);
                playGlobalAlert();

                String[][] fields = {
                        {"Issued By", player.getName()},
                        {"Issuing Town", hostTown.getName()},
                        {"Target Type", "Town"},
                        {"Target", targetTownName},
                        {"Item", itemStr},
                        {"Percentage", String.format("%.2f%%", percentage)},
                        {"Duration", durationStr}
                };
                DiscordWebhook.sendEmbed(plugin, "New Tariff Issued", 16753920, fields);

            } catch (IllegalArgumentException e) {
                player.sendMessage("§cInvalid item name or number format!");
            }
        } else if (action.equals("remove")) {
            plugin.getData().tariffRules.remove(hostTown.getName());
            player.sendMessage("§a[TW] All tariffs cleared.");
            plugin.getData().saveData();

            String announceMsg = "§l[TradeWar] §eThe Town of §f" + hostTown.getName() + " §ehas lifted all trade tariffs!";
            Bukkit.broadcastMessage(announceMsg);

            String[][] fields = {{"Action By", player.getName()}, {"Town", hostTown.getName()}};
            DiscordWebhook.sendEmbed(plugin, "Tariffs Lifted", 65280, fields);
        } else if (action.equals("list")) {
            List<TradeDataManager.TariffRule> rules = plugin.getData().tariffRules.getOrDefault(hostTown.getName(), new ArrayList<>());
            if (rules.isEmpty()) {
                player.sendMessage("§e[TW] Your town has no active tariffs.");
                return;
            }
            player.sendMessage("§6--- Active Tariffs ---");
            for (TradeDataManager.TariffRule rule : rules) {
                String itemDisplay = rule.item() == null ? "ALL" : rule.item().name();
                String durationDisplay = "Permanent";
                if (rule.expiryTime() > 0) {
                    long remainingMins = (rule.expiryTime() - System.currentTimeMillis()) / 60000L;
                    durationDisplay = remainingMins <= 0 ? "Expired" : remainingMins + " mins left";
                }
                player.sendMessage(String.format("§e- [%s] On Town %s for item %s: §f%.1f%% §e(%s)",
                        rule.type().toUpperCase(), rule.targetName(), itemDisplay, rule.percentage(), durationDisplay));
            }
        }
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