package com.nextinngames.tradeWar.policy;

import com.nextinngames.tradeWar.TradeWar;
import com.palmergames.bukkit.towny.TownyFormatter;
import com.palmergames.bukkit.towny.event.statusscreen.NationStatusScreenEvent;
import com.palmergames.bukkit.towny.event.statusscreen.TownStatusScreenEvent;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.statusscreens.StatusScreen;
import com.palmergames.bukkit.towny.utils.TownyComponents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds [Trade Tariffs] / [Trade Sanctions] / [Trade Embargoes] hover entries to the /town and /nation screens,
 * styled like Towny's own [Residents] entry. Only non-empty entries are shown.
 * <p>
 * A town in a nation shows its nation's policies, since those are the ones that apply to its shops.
 */
public class StatusScreenListener implements Listener {
    private static final int MAX_HOVER_ENTRIES = 20;

    private final TradeWar plugin;

    public StatusScreenListener(TradeWar plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onTownScreen(TownStatusScreenEvent event) {
        Town town = event.getTown();
        Nation nation = town.getNationOrNull();
        PolicyHolder holder = PolicyHolder.governing(town);
        String setBy = nation == null ? null : "Set by the Nation of " + nation.getName();
        addPolicies(event.getStatusScreen(), holder, setBy);
    }

    @EventHandler
    public void onNationScreen(NationStatusScreenEvent event) {
        addPolicies(event.getStatusScreen(), new PolicyHolder(PolicyScope.NATION, event.getNation().getName()), null);
    }

    private void addPolicies(StatusScreen screen, PolicyHolder holder, String setBy) {
        PolicySet policies = plugin.getData().get(holder);
        if (policies == null) {
            return;
        }

        long now = System.currentTimeMillis();
        List<String> tariffs = new ArrayList<>();
        for (Tariff tariff : policies.tariffs) {
            if (!tariff.isExpired(now)) {
                tariffs.add("§e" + TariffCommand.describe(tariff, now));
            }
        }
        List<String> sanctions = new ArrayList<>();
        for (String key : policies.sanctions) {
            sanctions.add(SanctionCommand.prefixedDisplay(key));
        }
        List<String> embargoes = new ArrayList<>(policies.embargoes);

        if (tariffs.isEmpty() && sanctions.isEmpty() && embargoes.isEmpty()) {
            return;
        }

        screen.addComponentOf("tradewar_newline", Component.newline());
        addEntry(screen, "tradewar_tariffs", "Trade Tariffs", tariffs, "\n", setBy);
        addEntry(screen, "tradewar_sanctions", "Trade Sanctions", sanctions, "§f, ", setBy);
        addEntry(screen, "tradewar_embargoes", "Trade Embargoes", embargoes, "§f, ", setBy);
    }

    private static void addEntry(StatusScreen screen, String key, String label, List<String> entries, String separator, String setBy) {
        if (entries.isEmpty()) {
            return;
        }
        int total = entries.size();
        List<String> shown = new ArrayList<>(entries.subList(0, Math.min(total, MAX_HOVER_ENTRIES)));
        if (total > MAX_HOVER_ENTRIES) {
            shown.add("§7and " + (total - MAX_HOVER_ENTRIES) + " more");
        }

        // Header like Towny's "Residents [12]:", entries on the following line(s).
        StringBuilder hover = new StringBuilder(TownyFormatter.getFormattedStrings(label, List.of(), total).stripTrailing());
        if (setBy != null) {
            hover.append("\n§7").append(setBy);
        }
        hover.append("\n§f").append(String.join(separator.equals("\n") ? "\n" : "§f" + separator, shown));

        screen.addComponentOf(key, TownyFormatter.colourHoverKey(label),
                HoverEvent.showText(TownyComponents.miniMessage(hover.toString())));
    }
}
