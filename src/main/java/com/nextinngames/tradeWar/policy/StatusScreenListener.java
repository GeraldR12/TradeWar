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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds [Trade Tariffs] / [Trade Sanctions] / [Trade Embargoes] hover entries to the /town and /nation screens,
 * styled like Towny's own [Residents] entry. Only non-empty entries are shown.
 * <p>
 * A town in a nation shows its nation's policies, since those are the ones that apply to its shops.
 * <p>
 * Entries are inserted inline: on /town after [Plots] (and any entries other plugins such as Quarters put right
 * after it), on /nation after [Towns]. If the anchor is missing they are appended on a new line instead.
 */
public class StatusScreenListener implements Listener {
    private static final int MAX_HOVER_ENTRIES = 20;

    private final TradeWar plugin;

    public StatusScreenListener(TradeWar plugin) {
        this.plugin = plugin;
    }

    // HIGHEST so other plugins' entries next to [Plots] (e.g. Quarters) are already on the screen.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTownScreen(TownStatusScreenEvent event) {
        Town town = event.getTown();
        Nation nation = town.getNationOrNull();
        PolicyHolder holder = PolicyHolder.governing(town);
        String setBy = nation == null ? null : "Set by the Nation of " + nation.getName();
        addPolicies(event.getStatusScreen(), holder, setBy, "plots", true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNationScreen(NationStatusScreenEvent event) {
        addPolicies(event.getStatusScreen(), new PolicyHolder(PolicyScope.NATION, event.getNation().getName()), null, "towns", false);
    }

    private void addPolicies(StatusScreen screen, PolicyHolder holder, String setBy, String anchor, boolean skipForeign) {
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

        Map<String, Component> entries = new LinkedHashMap<>();
        addEntry(entries, "tradewar_tariffs", "Trade Tariffs", tariffs, "\n", setBy);
        addEntry(entries, "tradewar_sanctions", "Trade Sanctions", sanctions, "§f, ", setBy);
        addEntry(entries, "tradewar_embargoes", "Trade Embargoes", embargoes, "§f, ", setBy);
        insertAfter(screen, anchor, skipForeign, entries);
    }

    /**
     * StatusScreen only supports appending, so the components after the insertion point are removed and re-added.
     * With skipForeign, the insertion point moves past the components following the anchor on the same line
     * (other plugins' hover entries) up to the next newline or Towny's extraFields.
     */
    private static void insertAfter(StatusScreen screen, String anchor, boolean skipForeign, Map<String, Component> entries) {
        List<String> keys = new ArrayList<>(screen.getComponentKeys());
        int index = keys.indexOf(anchor);
        if (index < 0) {
            screen.addComponentOf("tradewar_newline", Component.newline());
            entries.forEach(screen::addComponentOf);
            return;
        }
        index++;
        if (skipForeign) {
            while (index < keys.size() && !keys.get(index).equals("extraFields")
                    && !Component.newline().equals(screen.getComponentOrNull(keys.get(index)))) {
                index++;
            }
        }

        Map<String, Component> tail = new LinkedHashMap<>();
        for (String key : keys.subList(index, keys.size())) {
            tail.put(key, screen.getComponentOrNull(key));
            screen.removeStatusComponent(key);
        }
        entries.forEach(screen::addComponentOf);
        tail.forEach(screen::addComponentOf);
    }

    private static void addEntry(Map<String, Component> out, String key, String label, List<String> entries, String separator, String setBy) {
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

        out.put(key, TownyComponents.miniMessage(TownyFormatter.colourHoverKey(label))
                .hoverEvent(HoverEvent.showText(TownyComponents.miniMessage(hover.toString()))));
    }
}
