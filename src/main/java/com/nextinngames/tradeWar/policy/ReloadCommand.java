package com.nextinngames.tradeWar.policy;

import com.nextinngames.tradeWar.TradeWar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * /townyadmin reload tradewar — Towny has already checked {@code towny.command.townyadmin.reload}.
 */
public class ReloadCommand implements CommandExecutor {
    private final TradeWar plugin;

    public ReloadCommand(TradeWar plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        try {
            plugin.reload();
            sender.sendMessage("§a[TradeWar] Reloaded config.yml and data.yml ("
                    + plugin.getData().nationPolicies.size() + " nation(s), "
                    + plugin.getData().townPolicies.size() + " town(s) with policies).");
        } catch (Exception e) {
            sender.sendMessage("§c[TradeWar] Reload failed: " + e.getMessage() + " (see console).");
            plugin.getLogger().severe("Reload failed:");
            e.printStackTrace();
        }
        return true;
    }
}
