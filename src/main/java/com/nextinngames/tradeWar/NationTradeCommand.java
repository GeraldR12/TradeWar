package com.nextinngames.tradeWar;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

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
        if (action.equals("add") && args.length >= 3) {
            try {
                double rate = Double.parseDouble(args[2]);
                plugin.getData().tariffs.put(nation.getName(), rate);
                player.sendMessage("§a[TW] Set nation tariff to " + rate + "%");
                plugin.getData().saveData();
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid number.");
            }
        } else if (action.equals("remove")) {
            plugin.getData().tariffs.remove(nation.getName());
            player.sendMessage("§a[TW] Tariff removed.");
            plugin.getData().saveData();
        } else {
            double current = plugin.getData().tariffs.getOrDefault(nation.getName(), 0.0);
            player.sendMessage("§e[TW] Your current tariff: " + current + "%");
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
            player.sendMessage("§e[TW] Active Embargoes: " + plugin.getData().embargoes.getOrDefault(host, new HashSet<>()));
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
            player.sendMessage("§e[TW] Active Sanctions: " + plugin.getData().sanctions.getOrDefault(host, new HashSet<>()));
        }
    }
}