package com.nextinngames.tradeWar.policy;

import com.nextinngames.tradeWar.TradeWar;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI.CommandType;
import com.palmergames.bukkit.towny.object.AddonCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers tariff, sanction and embargo as Towny addon subcommands of both /town and /nation,
 * plus /townyadmin reload tradewar.
 */
public final class TownyPolicyCommands {
    private final TradeWar plugin;
    private final List<AddonCommand> registered = new ArrayList<>();

    public TownyPolicyCommands(TradeWar plugin) {
        this.plugin = plugin;
    }

    public void register() {
        for (PolicyScope scope : PolicyScope.values()) {
            for (PolicySubCommand command : List.of(
                    new TariffCommand(plugin, scope),
                    new SanctionCommand(plugin, scope),
                    new EmbargoCommand(plugin, scope))) {
                AddonCommand addon = new AddonCommand(scope.commandType(), command.name(), command);
                if (TownyCommandAddonAPI.addSubCommand(addon)) {
                    registered.add(addon);
                } else {
                    plugin.getLogger().warning(scope.command() + " " + command.name() + " is already registered by another plugin; skipped.");
                }
            }
        }
        AddonCommand reload = new AddonCommand(CommandType.TOWNYADMIN_RELOAD, "tradewar", new ReloadCommand(plugin));
        if (TownyCommandAddonAPI.addSubCommand(reload)) {
            registered.add(reload);
        } else {
            plugin.getLogger().warning("/ta reload tradewar is already registered by another plugin; skipped.");
        }
        plugin.getLogger().info("Registered " + registered.size() + " Towny subcommands (/t and /n: tariff, sanction, embargo; /ta reload tradewar).");
    }

    public void unregister() {
        for (AddonCommand addon : registered) {
            TownyCommandAddonAPI.removeSubCommand(addon);
        }
        registered.clear();
    }
}
