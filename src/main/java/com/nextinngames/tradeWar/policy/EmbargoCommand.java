package com.nextinngames.tradeWar.policy;

import com.nextinngames.tradeWar.DiscordWebhook;
import com.nextinngames.tradeWar.TradeWar;
import com.palmergames.bukkit.towny.object.Nation;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * /t|/n embargo add|remove <nation>, /t|/n embargo list. Cuts off all trade with every town of the target nation.
 */
public class EmbargoCommand extends PolicySubCommand {

    public EmbargoCommand(TradeWar plugin, PolicyScope scope) {
        super(plugin, scope, "embargo");
    }

    @Override
    protected void sendHelp(Player player) {
        String cmd = scope.command() + " embargo";
        player.sendMessage("§6--- " + scope.label() + " Embargoes ---");
        player.sendMessage("§e" + cmd + " add <nation>");
        player.sendMessage("§e" + cmd + " remove <nation>");
        player.sendMessage("§e" + cmd + " list");
        player.sendMessage("§7An embargo blocks all trade, both ways, with every town of that nation.");
    }

    @Override
    protected void add(Player player, PolicyHolder holder, String[] args) {
        if (args.length != 2) {
            player.sendMessage(usage("add <nation>"));
            return;
        }
        Nation target = findNation(args[1]);
        if (target == null) {
            player.sendMessage("§c[TW] Nation '" + args[1] + "' does not exist.");
            return;
        }
        if (isOwnNation(holder, target.getName())) {
            player.sendMessage("§c[TW] You cannot embargo your own nation.");
            return;
        }
        if (!plugin.getData().getOrCreate(holder).embargoes.add(target.getName())) {
            player.sendMessage("§e[TW] " + target.getName() + " is already under embargo.");
            return;
        }
        plugin.getData().saveData();
        refreshMapTarget(Tariff.NATION, target.getName());

        player.sendMessage("§a[TW] Embargoed nation: " + target.getName());
        Bukkit.broadcastMessage("§l[TradeWar] §lEMBARGO! " + announceName(holder)
                + " §ehas declared a total trade embargo against the Nation of §f" + target.getName() + "§e!");
        playGlobalAlert();

        String[][] fields = {{"Issued By", player.getName()}, {"Issuer", holder.label()}, {"Target Nation", target.getName()}};
        DiscordWebhook.sendEmbed(plugin, "New Embargo Issued", 12582912, fields);
    }

    @Override
    protected void remove(Player player, PolicyHolder holder, String[] args) {
        if (args.length != 2) {
            player.sendMessage(usage("remove <nation>"));
            return;
        }
        PolicySet policies = plugin.getData().get(holder);
        // Embargo sets are case-insensitive, so this also finds the stored spelling.
        String stored = policies == null ? null : policies.embargoes.stream()
                .filter(name -> name.equalsIgnoreCase(args[1])).findFirst().orElse(null);
        if (stored == null) {
            player.sendMessage("§c[TW] You have no embargo on '" + args[1] + "'.");
            return;
        }
        policies.embargoes.remove(stored);
        plugin.getData().pruneIfEmpty(holder);
        plugin.getData().saveData();
        refreshMapTarget(Tariff.NATION, stored);

        player.sendMessage("§a[TW] Lifted embargo on " + stored);
        Bukkit.broadcastMessage("§l[TradeWar] " + announceName(holder)
                + " §ehas lifted the trade embargo against the Nation of §f" + stored + "§e!");
        DiscordWebhook.sendEmbed(plugin, "Embargo Lifted", 65280,
                new String[][]{{"Lifted By", player.getName()}, {"Issuer", holder.label()}, {"Target Nation", stored}});
    }

    @Override
    protected void list(Player player, PolicyHolder holder) {
        PolicySet policies = plugin.getData().get(holder);
        if (policies == null || policies.embargoes.isEmpty()) {
            player.sendMessage("§e[TW] The " + holder.label() + " has no active embargoes.");
        } else {
            player.sendMessage("§e[TW] Active Embargoes: §f" + String.join(", ", policies.embargoes));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filterByStart(List.of("add", "remove", "list"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return filterByStart(nationNames(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            PolicySet policies = senderPolicies(sender);
            return policies == null ? List.of() : filterByStart(new ArrayList<>(policies.embargoes), args[1]);
        }
        return List.of();
    }
}
