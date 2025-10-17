package com.jojodmo.physics;

import java.util.*;
import java.util.logging.Level;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class Main extends JavaPlugin implements Listener {

	public static Main that;

	public static String prefix;
	public static boolean toggleGlobal;
	public static Map<String, PhysicsState> worlds = new HashMap<>();
	public static List<Material> doMaterials = new ArrayList<>();
	public static List<Material> dontDoMaterials = new ArrayList<>();
	public static boolean ignoreDoMaterials = false;
	public static boolean hasWG = false;
	public static boolean hookedWG = false;

	private static PlatformScheduler scheduler;

	@Override
	public void onLoad() {
		if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
			hasWG = true;
			hookedWG = WGHandler.setup();
		}
	}

	@Override
	public void onEnable() {
		that = this;
		Main.scheduler = new FoliaLib(this).getScheduler();

		prefix = ChatColor.DARK_AQUA + "[NoPhysics] " + ChatColor.AQUA;

		this.saveDefaultConfig();

		toggleGlobal = this.getConfig().getBoolean("is-toggle-global", this.getConfig().getBoolean("isToggleGlobal", true));
		ConfigurationSection section = this.getConfig().getConfigurationSection("worlds");

		if (section != null) {
			for (String world : section.getKeys(false)) {
				worlds.put(world.toLowerCase(), PhysicsState.fromBoolean(this.getConfig().getBoolean("worlds." + world)));
			}
		}

		List<String> mats = this.getConfig().getStringList("blockTypes");
		for (String mat : mats) {
			if (mat == null || mat.isEmpty()) {
				continue;
			}

			Material m = matchMaterial(mat);
			if (m == null) {
				this.getLogger().log(Level.INFO, "Invalid material in config.yml under blockTypes: '" + mat + "'. Ignoring this material.");
			} else {
				doMaterials.add(m);
			}
		}

		if (!doMaterials.isEmpty()) {
			this.getLogger().log(Level.INFO, "NoPhysics will only act on the following materials: " + Arrays.toString(doMaterials.toArray()));
		}

		mats = this.getConfig().getStringList("enablePhysicsBlockTypes");
		for (String mat : mats) {
			if (mat == null || mat.isEmpty()) {
				continue;
			}

			Material m = matchMaterial(mat);
			if (m == null) {
				this.getLogger().log(Level.INFO, "Invalid material in config.yml under enablePhysicsBlockTypes: '" + mat + "'. Ignoring this material.");
			} else {
				dontDoMaterials.add(m);
			}
		}

		if (!dontDoMaterials.isEmpty()) {
			this.getLogger().log(Level.INFO, "NoPhysics will NOT act on the following materials: " + Arrays.toString(dontDoMaterials.toArray()));
		}

		this.getServer().getPluginManager().registerEvents(this, this);

		this.getLogger().log(Level.INFO, "NoPhysics V" + this.getDescription().getVersion() + " by jojodmo enabled!");
		if (hookedWG) {
			this.getLogger().log(Level.INFO, "Successfully hooked into WorldGuard");
		}

		try {
			new Metrics(this);
		} catch(Exception ignore) {
		}
	}

	private static Material matchMaterial(String mat) {
		if (mat == null || mat.isEmpty()) {
			return null;
		}

		try {
			Material m = Material.matchMaterial(mat);
			if (m != null) {
				return m;
			}

			return Material.matchMaterial(mat, true);
		} catch (Exception ignore) {
		}

		return null;
	}

	private static boolean shouldDoPhysics(Material m, boolean def) {
		if (doMaterials.isEmpty() && dontDoMaterials.isEmpty() || ignoreDoMaterials) {
			return def;
		}

		if (m.name().matches("^BARRIER(_BLOCK)?$")) {
			return false;
		} else if (dontDoMaterials.contains(m)) {
			return true;
		}

		return !doMaterials.contains(m);
	}

	private static boolean shouldDoPhysics(Block b) {
		boolean matP = shouldDoPhysics(b.getType(), false);
		PhysicsState world = shouldDoPhysics(b.getWorld());

		if (!hookedWG) {
			return (world != PhysicsState.DISABLE) || matP;
		}

		boolean wg = WGHandler.getWorldguardPhysicsValue(b.getLocation());
		// TODO: make world.parse(wg) || matP
		return world == PhysicsState.DISABLE ? matP : wg || matP;
	}

	private static PhysicsState shouldDoPhysics(World w) {
		return worlds.getOrDefault(w.getName().toLowerCase(), PhysicsState.OBEY_WORLDGUARD);
	}

	private void setDoPhysics(World world, PhysicsState value) {
		if (world == null) {
			return;
		}

		worlds.put(world.getName().toLowerCase(), value);
	}

	private void setDoPhysicsGlobal(PhysicsState value) {
		for (World w : this.getServer().getWorlds()) {
			setDoPhysics(w, value);
		}
	}

	@Override
	public void onDisable() {
		this.getLogger().log(Level.INFO, "NoPhysics by jojodmo disabled!");

		that = null;
	}

	public boolean hasPhysics(Material m) {
		return m.hasGravity() || !m.isSolid() || m.name().toUpperCase().matches("^((.+)_DOOR|(.+)_TRAPDOOR|(.+)_BED|(.+)_FENCE_GATE|DRAGON_EGG|(.+)_PLATE|DAYLIGHT_DETECTOR|BANNER)$");
	}

	private static final List<Location> ignorePhysics = new ArrayList<>();

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void blockPlace(@NotNull BlockPlaceEvent e) {
		Material newType = e.getBlock().getType();
		if (!shouldDoPhysics(e.getBlock()) && hasPhysics(newType)) {
			final Location below = e.getBlock().getLocation().subtract(0, 1, 0).clone();
			final Location currentLoc = e.getBlock().getLocation().clone();
			final Material belowType = below.getBlock().getType();

			ignorePhysics.add(below);
			ignorePhysics.add(currentLoc);
			if (!belowType.isSolid()) {
				below.getBlock().setType(Material.BARRIER);
				scheduler.runAtLocationLater(currentLoc, task -> {
					below.getBlock().setType(belowType);
					scheduler.runAtLocationLater(currentLoc, nextTask -> {
						ignorePhysics.remove(below);
						ignorePhysics.remove(currentLoc);
					}, 5L);
				}, 5L);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void blockBreak(@NotNull BlockBreakEvent e) {
		if (!shouldDoPhysics(e.getBlock())) {
			e.setCancelled(true);

			e.getBlock().setType(Material.AIR, false);
		}
	}

	@EventHandler
	public void blockPhysics(@NotNull BlockPhysicsEvent e) {
		if (shouldDoPhysics(e.getBlock().getWorld()) == PhysicsState.DISABLE) {
			e.setCancelled(true);
		}

		if (shouldCancelPhysicsEvent(e.getBlock()) || !shouldDoPhysics(e.getChangedType(), true)) {
			e.setCancelled(true);
		}
	}

	public static boolean shouldCancelPhysicsEvent(Block b) {
		return !shouldDoPhysics(b) || ignorePhysics.contains(b.getLocation());
	}

	public boolean onCommand(@NotNull CommandSender sender, Command cmd, @NotNull String label, String @NotNull [] args) {
		if (cmd.getName().equalsIgnoreCase("physics")) {
			if (args.length == 0 || args[0].toLowerCase().matches("info|plugin|pl|ver|version")) {
				sender.sendMessage(prefix + "Running NoPhysics " + this.getDescription().getVersion() + " By jojodmo");
				return true;
			} else {
				if (!sender.hasPermission("physics.toggle") && !sender.isOp()) {
					sender.sendMessage(prefix + "You do not have permission to do this.");
					return true;
				}

				boolean global = toggleGlobal;
				final String worldName = args.length > 1 ? args[1] : (sender instanceof Player player ? player.getWorld().getName() : "null");
				World world = null;

				if (args.length < 2) {
					if (sender instanceof Player player) {
						world = player.getWorld();
					} else if (!global) {
						sender.sendMessage(prefix + "Console Usage: /physics <enable/disable/status/checkBlockTypes> <world>");
						return true;
					}
				} else {
					global = args[1].equalsIgnoreCase("global");
					world = Bukkit.getWorld(args[1]);
				}

				if (!global && world == null) {
					sender.sendMessage(prefix + "The world " + worldName + " doesn't exist!");
					return true;
				}

				String inWorld = global ? "globally" : "in " + worldName;

				if (args[0].toLowerCase().matches("enable|true|yes|on|1")) {
					if (global) {
						setDoPhysicsGlobal(PhysicsState.OBEY_WORLDGUARD);
					} else {
						setDoPhysics(world, PhysicsState.OBEY_WORLDGUARD);
					}

					if (hasWG) {
						sender.sendMessage(prefix + "Block physics now obeys WorldGuard rules " + inWorld);
					} else {
						sender.sendMessage(prefix + "Block physics enabled " + inWorld);
					}
				} else if (args[0].toLowerCase().matches("disable|false|no|off|0")) {
					if (global) {
						setDoPhysicsGlobal(PhysicsState.DISABLE);
					} else {setDoPhysics(world, PhysicsState.DISABLE);
					}

					if (hasWG) {
						sender.sendMessage(prefix + "Block physics disabled everywhere " + inWorld + ", regardless of WorldGuard rules");
					} else {
						sender.sendMessage(prefix + "Block physics disabled " + inWorld);
					}
				} else if (args[0].toLowerCase().matches("check|ison|isoff|current|state|status")) {
					if (world == null) {
						sender.sendMessage(prefix + "Usage: /physics status <world>");
						return true;
					}

					String onoff = ChatColor.DARK_AQUA + (shouldDoPhysics(world) != PhysicsState.DISABLE ? "enabled" : "disabled") + ChatColor.AQUA;
					sender.sendMessage(prefix + "Block physics is currently " + onoff + " in " + worldName);
				} else if (args[0].toLowerCase().matches("checkblocktypes|blocks|block|blocktypes|materials|materiallist")) {
					if (doMaterials.isEmpty() && dontDoMaterials.isEmpty()) {
						sender.sendMessage(prefix + "You don't have any material checks in your materials list!");
					} else {
						String onoff = ChatColor.DARK_AQUA + (ignoreDoMaterials ? "IGNORED" : "used") + ChatColor.AQUA;
						sender.sendMessage(prefix + "The materials list is now " + onoff + " globally");
					}
				} else {
					sender.sendMessage(prefix + "Usage: /physics <enable/disable/status/checkBlockTypes>");
				}
			}

			return true;
		}

		return false;
	}

	public static PlatformScheduler getScheduler() {
		return scheduler;
	}
}
