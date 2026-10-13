package com.nextinngames.tradeWar.policy;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;

/**
 * A nation, or a town without a nation: the only things that can hold trade policies.
 */
public record PolicyHolder(PolicyScope scope, String name) {

    /**
     * The holder whose policies govern {@code town}: its nation if it has one, otherwise the town itself.
     */
    public static PolicyHolder governing(Town town) {
        if (town == null) {
            return null;
        }
        Nation nation = town.getNationOrNull();
        return nation != null ? new PolicyHolder(PolicyScope.NATION, nation.getName()) : new PolicyHolder(PolicyScope.TOWN, town.getName());
    }

    /** e.g. {@code Nation of Cuba}. */
    public String label() {
        return scope.label() + " of " + name;
    }

    /** Name shown on map popups: nations are suffixed so they can't be mistaken for a town. */
    public String mapLabel() {
        return scope == PolicyScope.NATION ? name + " (nation)" : name;
    }
}
