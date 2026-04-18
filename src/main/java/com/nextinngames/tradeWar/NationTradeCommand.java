package com.nextinngames.tradeWar;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
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
        if (resident == null || !resident.hasNation()) {
            player.sendMessage("§c[TW] You must be in a nation to use trade policies.");
            return true;
        }

        Nation nation = resident.getNationOrNull();
        if (!player.hasPermission("tradewar.admin") && !resident.isKing()) {
            player.sendMessage("§c[TW] Only the King can manage trade policies.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§6--- TradeWar Help ---");
            player.sendMessage("§e/tw tariff <add/remove/list> [value]");
            player.sendMessage("§e/tw sanction <add/remove/list> [target]");
            player.sendMessage("§e/tw embargo <add/remove/list> [nation]");
            return true;
        }

        String category = args[0].toLowerCase();
        String action = args[1].toLowerCase();

        switch (category) {
            case "tariff" -> handleTariff(player, nation, action, args);
            case "sanction" -> handleSanction(player, nation, action, args);
            case "embargo" -> handleEmbargo(player, nation, action, args);
            default -> player.sendMessage("§cUnknown category. Use tariff, sanction, or embargo.");
        }

        return true;
    }

    private void handleTariff(Player player, Nation nation, String action, String[] args) {
        if (action.equals("add")) {
            // Check for required arguments (args[0] is 'tariff', args[1] is 'add')
            if (args.length < 7) {
                player.sendMessage("§cUsage: /tw tariff add <import/export> <town/nation> <name> <item> <%> [minutes]");
                return;
            }

            try {
                String type = args[2].toLowerCase(); // import/export
                String targetType = args[3].toLowerCase(); // town/nation
                String targetName = args[4];
                Material item = args[5].equalsIgnoreCase("all") ? null : Material.valueOf(args[5].toUpperCase());
                double percentage = Double.parseDouble(args[6]);

                long expiry = 0;
                if (args.length > 7) {
                    expiry = System.currentTimeMillis() + (Long.parseLong(args[7]) * 60000L);
                }

                TradeDataManager.TariffRule rule = new TradeDataManager.TariffRule(
                        type, targetType, targetName, item, percentage, expiry
                );

                plugin.getData().tariffRules.computeIfAbsent(nation.getName(), k -> new ArrayList<>()).add(rule);
                player.sendMessage("§a[TW] Tariff added successfully!");
                plugin.getData().saveData();

            } catch (IllegalArgumentException e) {
                player.sendMessage("§cInvalid item or number format!");
            }
        } else if (action.equals("remove")) {
            plugin.getData().tariffRules.remove(nation.getName());
            player.sendMessage("§a[TW] All tariffs cleared.");
            plugin.getData().saveData();
        }
    }

    private void handleEmbargo(Player player, Nation nation, String action, String[] args) {
        String host = nation.getName();
        if (action.equals("add") && args.length >= 3) {
            String target = args[2];
            plugin.getData().embargoes.computeIfAbsent(host, k -> new HashSet<>()).add(target);
            player.sendMessage("§a[TW] Embargoed nation: " + target);
            plugin.getData().saveData();
        } else if (action.equals("remove") && args.length >= 3) {
            String target = args[2];
            if (plugin.getData().embargoes.containsKey(host)) {
                plugin.getData().embargoes.get(host).remove(target);
                player.sendMessage("§a[TW] Lifted embargo on " + target);
                plugin.getData().saveData();
            }
        } else {
            // Improved List Logic
            Set<String> activeEmbargoes = plugin.getData().embargoes.getOrDefault(host, new HashSet<>());
            if (activeEmbargoes.isEmpty()) {
                player.sendMessage("§e[TW] Your nation has no active embargoes.");
            } else {
                player.sendMessage("§e[TW] Active Embargoes: §f" + String.join(", ", activeEmbargoes));
            }
        }
    }

    private void handleSanction(Player player, Nation nation, String action, String[] args) {
        String host = nation.getName();
        if (action.equals("add") && args.length >= 3) {
            String target = args[2];
            plugin.getData().sanctions.computeIfAbsent(host, k -> new HashSet<>()).add(target);
            player.sendMessage("§a[TW] Sanctioned: " + target);
            plugin.getData().saveData();
        } else if (action.equals("remove") && args.length >= 3) {
            String target = args[2];
            if (plugin.getData().sanctions.containsKey(host)) {
                plugin.getData().sanctions.get(host).remove(target);
                player.sendMessage("§a[TW] Removed sanction on " + target);
                plugin.getData().saveData();
            }
        } else {
            // Improved List Logic
            Set<String> activeSanctions = plugin.getData().sanctions.getOrDefault(host, new HashSet<>());
            if (activeSanctions.isEmpty()) {
                player.sendMessage("§e[TW] Your nation has no active sanctions.");
            } else {
                player.sendMessage("§e[TW] Active Sanctions: §f" + String.join(", ", activeSanctions));
            }
        }
    }
}