package com.nextinngames.tradeWar.policy;

import com.nextinngames.tradeWar.TradeWar;
import com.palmergames.bukkit.towny.event.DeleteNationEvent;
import com.palmergames.bukkit.towny.event.DeleteTownEvent;
import com.palmergames.bukkit.towny.event.NationAddTownEvent;
import com.palmergames.bukkit.towny.event.NationRemoveTownEvent;
import com.palmergames.bukkit.towny.event.RenameNationEvent;
import com.palmergames.bukkit.towny.event.RenameTownEvent;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Keeps trade policies in step with Towny: renames carry policies over, deletions drop the holder's own policies,
 * and a town's own policies pause while it is in a nation (the nation's apply instead) and resume when it leaves.
 */
public class TownyChangeListener implements Listener {
    private final TradeWar plugin;

    public TownyChangeListener(TradeWar plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTownJoinNation(NationAddTownEvent event) {
        notifyTown(event.getTown(), "§e[TradeWar] Your town's own tariffs, sanctions and embargoes are paused while it is part of the Nation of "
                + event.getNation().getName() + ". The nation's trade policy now applies.");
        refreshMaps();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTownLeaveNation(NationRemoveTownEvent event) {
        notifyTown(event.getTown(), "§e[TradeWar] Your town has left its nation: its own tariffs, sanctions and embargoes apply again (/t tariff list).");
        refreshMaps();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTownRename(RenameTownEvent event) {
        plugin.getData().rename(PolicyScope.TOWN, event.getOldName(), event.getTown().getName());
        refreshMaps();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNationRename(RenameNationEvent event) {
        plugin.getData().rename(PolicyScope.NATION, event.getOldName(), event.getNation().getName());
        refreshMaps();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTownDelete(DeleteTownEvent event) {
        if (plugin.getData().townPolicies.remove(event.getTownName()) != null) {
            plugin.getData().saveData();
        }
        refreshMaps();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNationDelete(DeleteNationEvent event) {
        if (plugin.getData().nationPolicies.remove(event.getNationName()) != null) {
            plugin.getData().saveData();
        }
        refreshMaps();
    }

    private void notifyTown(Town town, String message) {
        PolicySet own = plugin.getData().townPolicies.get(town.getName());
        if (own == null || own.isEmpty()) {
            return;
        }
        for (Resident resident : town.getResidents()) {
            Player player = resident.getPlayer();
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void refreshMaps() {
        plugin.getBlueMapIntegration().ifPresent(bm -> bm.fullRefresh());
        plugin.getPl3xMapIntegration().ifPresent(pm -> pm.fullRefresh());
    }
}
