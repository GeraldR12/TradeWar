package com.nextinngames.tradeWar.policy;

import com.palmergames.bukkit.towny.TownyCommandAddonAPI.CommandType;

/**
 * Who sets trade policy: a nation (through its capital) or a town that is not in a nation.
 */
public enum PolicyScope {
    TOWN("Town", "/t", CommandType.TOWN),
    NATION("Nation", "/n", CommandType.NATION);

    private final String label;
    private final String command;
    private final CommandType commandType;

    PolicyScope(String label, String command, CommandType commandType) {
        this.label = label;
        this.command = command;
        this.commandType = commandType;
    }

    public String label() {
        return label;
    }

    /** The Towny command players type, e.g. {@code /t}. */
    public String command() {
        return command;
    }

    public CommandType commandType() {
        return commandType;
    }
}
