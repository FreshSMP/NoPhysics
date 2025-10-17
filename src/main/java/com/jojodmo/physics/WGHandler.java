package com.jojodmo.physics;

import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.logging.Level;

public class WGHandler {

    public static boolean hasWorldGuard = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;

    public static StateFlag flag;

    public static boolean setup() {
        if (!hasWorldGuard) {
            return false;
        }

        try {
            flag = new StateFlag("physics", true);
            FlagRegistry r = WorldGuard.getInstance().getFlagRegistry();
            r.register(flag);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("\n\n");
            Bukkit.getLogger().log(Level.WARNING, "Could not hook into World Guard. You should be using version 7.0.0 or newer. NoPhysics will still work");
            return false;
        }

        return true;
    }

    public static boolean getWorldguardPhysicsValue(Location location) {
        if (!hasWorldGuard || location == null) {
            return true;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        if (container == null) {
            return false;
        }

        BukkitWorld w = new BukkitWorld(location.getWorld());
        com.sk89q.worldedit.util.Location loc = new com.sk89q.worldedit.util.Location(w, location.getBlockX(), location.getBlockY(), location.getBlockZ());
        ApplicableRegionSet rs = container.createQuery().getApplicableRegions(loc);

        StateFlag.State state = rs.queryState(null, flag);
        return state != StateFlag.State.DENY;
    }
}
