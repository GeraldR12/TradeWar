package com.nextinngames.tradeWar;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TradeTabCompleter implements TabCompleter {

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            return filterCompletions(Arrays.asList("tariff", "sanction", "embargo"), args[0]);
        }

        String category = args[0].toLowerCase();

        switch (category) {
            case "tariff":
                if (args.length == 2) {
                    return filterCompletions(Arrays.asList("add", "remove", "list"), args[1]);
                }
                if (args[1].equalsIgnoreCase("add")) {
                    if (args.length == 3) return filterCompletions(Arrays.asList("import", "export"), args[2]);
                    if (args.length == 4) return filterCompletions(getTownNames(), args[3]);
                    if (args.length == 5) {
                        List<String> items = Arrays.stream(Material.values()).map(m -> m.name().toLowerCase()).collect(Collectors.toList());
                        items.add("all");
                        return filterCompletions(items, args[4]);
                    }
                    if (args.length == 6) return filterCompletions(Arrays.asList("10", "50", "100"), args[5]);
                    if (args.length == 7) return filterCompletions(Arrays.asList("60", "1440"), args[6]);
                }
                break;

            case "sanction":
                if (args.length == 2) {
                    return filterCompletions(Arrays.asList("add", "remove", "list"), args[1]);
                }
                if (args.length == 3 && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                    return filterCompletions(getTownNames(), args[2]);
                }
                break;

            case "embargo":
                if (args.length == 2) {
                    return filterCompletions(Arrays.asList("add", "remove", "list"), args[1]);
                }
                if (args.length == 3 && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                    return filterCompletions(getNationNames(), args[2]);
                }
                break;
        }

        return completions;
    }

    private List<String> filterCompletions(List<String> list, String currentArg) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(currentArg.toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<String> getTownNames() {
        return TownyAPI.getInstance().getTowns().stream().map(Town::getName).collect(Collectors.toList());
    }

    private List<String> getNationNames() {
        return TownyAPI.getInstance().getNations().stream().map(Nation::getName).collect(Collectors.toList());
    }
}