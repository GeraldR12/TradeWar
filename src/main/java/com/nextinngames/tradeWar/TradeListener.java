package com.nextinngames.tradeWar;

import com.ghostchu.quickshop.api.event.economy.ShopPurchaseEvent;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.Set;

public class TradeListener implements Listener {

    private final TradeWar plugin;

    public TradeListener(TradeWar plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPurchaseBlocker(ShopPurchaseEvent event) {
        Player buyer = event.getPurchaser().getBukkitPlayer().orElse(null);
        if (buyer == null) return;

        Town shopTown = TownyAPI.getInstance().getTown(event.getShop().getLocation());
        if (shopTown == null || !shopTown.hasNation()) return;

        String hostNation = shopTown.getNationOrNull().getName();
        Resident resident = TownyAPI.getInstance().getResident(buyer);

        if (resident != null && resident.hasNation()) {
            String buyerNation = resident.getNationOrNull().getName();
            if (plugin.getData().embargoes.getOrDefault(hostNation, new HashSet<>()).contains(buyerNation)) {
                event.setCancelled(true);
                buyer.sendMessage("§c[TradeWar] " + hostNation + " has an active embargo against your nation!");
                return;
            }
        }

        Set<String> hostSanctions = plugin.getData().sanctions.getOrDefault(hostNation, new HashSet<>());
        String townName = (resident != null && resident.hasTown()) ? resident.getTownOrNull().getName() : "";

        if (hostSanctions.contains(buyer.getName()) || hostSanctions.contains(townName)) {
            event.setCancelled(true);
            buyer.sendMessage("§c[TradeWar] You are sanctioned from trading in " + hostNation + ".");
        }
    }
}