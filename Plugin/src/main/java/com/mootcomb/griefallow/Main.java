package com.mootcomb.griefallow;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class Main extends JavaPlugin implements Listener {

    // Configuration flags - using volatile for visibility across threads
    private volatile boolean debug;
    private volatile boolean enableTnt;
    private volatile boolean tntChainReaction;
    private volatile boolean enablePistons;
    private volatile boolean enableWither;
    private volatile boolean enableSand;
    private volatile boolean enableMinecart;
    private volatile boolean enableEggSpawn;
    private volatile boolean enableVehicleDestroy;
    private volatile boolean enableFluidFlow;
    private volatile boolean enableFishingMinecart;

    // World and Region settings
    private volatile String worldsType;
    private Set<String> worlds;
    private volatile String regionsType;
    private Map<String, Region> regions;

    // Flag to detect if we're running on Folia
    private boolean isFolia;

    @Override
    public void onEnable() {
        // Detect platform
        isFolia = isFoliaPresent();

        saveDefaultConfig();
        reloadConfig();
        loadConfigValues();
        loadWorldSettings();
        loadRegionSettings();

        Bukkit.getPluginManager().registerEvents(this, this);

        if (debug) {
            getLogger().info("GriefAllow enabled in DEBUG mode! (Running on " + (isFolia ? "Folia" : "Paper/Spigot") + ")");
            getLogger().info("Config values: enableTnt=" + enableTnt +
                    ", tntChainReaction=" + tntChainReaction +
                    ", enablePistons=" + enablePistons +
                    ", enableWither=" + enableWither +
                    ", enableSand=" + enableSand +
                    ", enableMinecart=" + enableMinecart +
                    ", enableEggSpawn=" + enableEggSpawn +
                    ", enableVehicleDestroy=" + enableVehicleDestroy +
                    ", enableFluidFlow=" + enableFluidFlow +
                    ", enableFishingMinecart=" + enableFishingMinecart);
            getLogger().info("Worlds type: " + worldsType + ", Worlds: " + worlds);
            getLogger().info("Regions type: " + regionsType + ", Regions count: " + regions.size());
        } else {
            getLogger().info("GriefAllow enabled! (Running on " + (isFolia ? "Folia" : "Paper/Spigot") + ")");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("GriefAllow disabled!");
    }

    // Detect if Folia API is available
    private boolean isFoliaPresent() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // Platform-independent scheduler
    private void runTask(Location location, Runnable task) {
        if (isFolia) {
            // Folia: execute in the region where the location belongs
            try {
                // Use reflection to avoid compile-time dependency on Folia API
                Class<?> bukkitClass = Bukkit.class;
                Object regionScheduler = bukkitClass.getMethod("getRegionScheduler").invoke(null);
                regionScheduler.getClass().getMethod("execute",
                                JavaPlugin.class, Location.class, Runnable.class)
                        .invoke(regionScheduler, this, location, task);
            } catch (Exception e) {
                getLogger().warning("Failed to execute Folia region task, falling back to sync: " + e.getMessage());
                Bukkit.getScheduler().runTask(this, task);
            }
        } else {
            // Paper/Spigot: run synchronously
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(this, task);
            }
        }
    }

    // Load all configuration values from config.yml
    private void loadConfigValues() {
        debug = getConfig().getBoolean("debug", false);
        enableTnt = getConfig().getBoolean("enable-tnt", false);
        tntChainReaction = getConfig().getBoolean("tnt-chain-reaction", false);
        enablePistons = getConfig().getBoolean("enable-pistons", false);
        enableWither = getConfig().getBoolean("enable-wither", false);
        enableSand = getConfig().getBoolean("enable-sand", false);
        enableMinecart = getConfig().getBoolean("enable-minecart", false);
        enableEggSpawn = getConfig().getBoolean("enable-egg-spawn", false);
        enableVehicleDestroy = getConfig().getBoolean("enable-vehicle-destroy", false);
        enableFluidFlow = getConfig().getBoolean("enable-fluid-flow", false);
        enableFishingMinecart = getConfig().getBoolean("enable-fishing-minecart", false);
    }

    // Load world settings
    private void loadWorldSettings() {
        worldsType = getConfig().getString("worlds-type", "disable").toLowerCase();
        worlds = ConcurrentHashMap.newKeySet();
        worlds.addAll(getConfig().getStringList("worlds"));

        if (!worldsType.equals("whitelist") && !worldsType.equals("blacklist") && !worldsType.equals("disable")) {
            getLogger().warning("Invalid worlds-type: " + worldsType + ". Using 'disable'.");
            worldsType = "disable";
        }
    }

    // Load region settings
    private void loadRegionSettings() {
        regionsType = getConfig().getString("regions-type", "disable").toLowerCase();
        regions = new ConcurrentHashMap<>();

        if (!regionsType.equals("whitelist") && !regionsType.equals("blacklist") && !regionsType.equals("disable")) {
            getLogger().warning("Invalid regions-type: " + regionsType + ". Using 'disable'.");
            regionsType = "disable";
        }

        ConfigurationSection regionsSection = getConfig().getConfigurationSection("regions");
        if (regionsSection != null) {
            for (String key : regionsSection.getKeys(false)) {
                ConfigurationSection regionSection = regionsSection.getConfigurationSection(key);
                if (regionSection != null) {
                    String worldName = regionSection.getString("world");
                    int x1 = regionSection.getInt("x1");
                    int y1 = regionSection.getInt("y1");
                    int z1 = regionSection.getInt("z1");
                    int x2 = regionSection.getInt("x2");
                    int y2 = regionSection.getInt("y2");
                    int z2 = regionSection.getInt("z2");

                    regions.put(key, new Region(worldName, x1, y1, z1, x2, y2, z2));

                    if (debug) {
                        getLogger().info("Loaded region " + key + ": world=" + worldName +
                                " [" + x1 + "," + y1 + "," + z1 + "] to [" + x2 + "," + y2 + "," + z2 + "]");
                    }
                }
            }
        }
    }

    // Check if a location is in an allowed world
    private boolean isAllowedWorld(Location location) {
        if (worldsType.equals("disable")) {
            return true;
        }

        String worldName = location.getWorld().getName();

        if (worldsType.equals("whitelist")) {
            return worlds.contains(worldName);
        } else { // blacklist
            return !worlds.contains(worldName);
        }
    }

    // Check if a location is in an allowed region
    private boolean isAllowedRegion(Location location) {
        if (regionsType.equals("disable") || regions.isEmpty()) {
            return true;
        }

        boolean inAnyRegion = false;

        for (Region region : regions.values()) {
            if (region.contains(location)) {
                inAnyRegion = true;
                break;
            }
        }

        if (regionsType.equals("whitelist")) {
            return inAnyRegion;
        } else { // blacklist
            return !inAnyRegion;
        }
    }

    // Combined check for world and region
    private boolean isLocationAllowed(Location location) {
        return isAllowedWorld(location) && isAllowedRegion(location);
    }

    // Log debug messages if debug mode is enabled
    private void debugLog(String message) {
        if (debug) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    // ==================== TNT EXPLOSION HANDLER ====================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onExplode(EntityExplodeEvent event) {
        debugLog("onExplode called");

        if (!isLocationAllowed(event.getLocation())) {
            debugLog("Explosion location not allowed, cancelling event");
            event.setCancelled(true);
            return;
        }

        if (enableTnt) {
            debugLog("TNT explosions enabled");
            event.setCancelled(false);
            event.setYield(1.0F);

            debugLog("Chain reaction mode: " + tntChainReaction);

            // Create a copy of the block list to avoid concurrent modification
            List<Block> blocksToProcess = new ArrayList<>(event.blockList());

            for (Block block : blocksToProcess) {
                if (!isLocationAllowed(block.getLocation())) {
                    continue;
                }

                if (block.getType() == Material.TNT) {
                    if (tntChainReaction) {
                        final Location loc = block.getLocation().clone();
                        block.setType(Material.AIR);

                        // Platform-independent scheduling
                        runTask(loc, () -> {
                            TNTPrimed tnt = loc.getWorld().spawn(loc, TNTPrimed.class);
                            tnt.setFuseTicks(80);
                            tnt.setYield(4.0F);
                            tnt.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                        });

                        debugLog("TNT forced vanilla ignition at " +
                                block.getX() + ", " + block.getY() + ", " + block.getZ());
                    } else {
                        block.breakNaturally();
                        debugLog("TNT dropped as item at " +
                                block.getX() + ", " + block.getY() + ", " + block.getZ());
                    }
                } else {
                    block.breakNaturally();
                }
            }

            debugLog("TNT Explosion with " + (tntChainReaction ? "chain reaction!" : "items drop!"));
        } else {
            debugLog("TNT explosions disabled, skipping");
        }
    }

    // ==================== TNT IGNITE HANDLER ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTntIgnite(BlockIgniteEvent event) {
        debugLog("onTntIgnite called - Cause: " + event.getCause());

        if (!isLocationAllowed(event.getBlock().getLocation())) {
            debugLog("Block ignition location not allowed, skipping");
            return;
        }

        if (enableTnt) {
            if (event.getBlock().getType() == Material.TNT) {
                event.setCancelled(false);
                debugLog("TNT ignition allowed");

                if (event.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
                    debugLog("TNT ignited with Flint & Steel!");
                } else if (event.getCause() == BlockIgniteEvent.IgniteCause.FIREBALL) {
                    debugLog("TNT ignited with Fireball!");
                }
            }
        } else {
            debugLog("TNT ignition disabled, skipping");
        }
    }

    // ==================== FLINT AND STEEL HANDLER ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFlintClick(PlayerInteractEvent event) {
        debugLog("onFlintClick called - Action: " + event.getAction());

        if (event.getClickedBlock() != null && !isLocationAllowed(event.getClickedBlock().getLocation())) {
            debugLog("Clicked block location not allowed, skipping");
            return;
        }

        if (enableTnt) {
            if (event.getAction().name().contains("RIGHT_CLICK_BLOCK")) {
                if (event.getItem() != null && event.getItem().getType() == Material.FLINT_AND_STEEL) {
                    if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.TNT) {
                        event.setCancelled(false);
                        debugLog("Flint and steel used on TNT block");
                        debugLog("Flint and steel on TNT!");
                    }
                }
            }
        } else {
            debugLog("TNT interactions disabled, skipping");
        }
    }

    // ==================== FIRE ARROW HANDLER ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFireArrowHit(ProjectileHitEvent event) {
        debugLog("onFireArrowHit called");

        if (!enableTnt) {
            debugLog("TNT disabled, skipping");
            return;
        }

        if (!(event.getEntity() instanceof Arrow)) {
            debugLog("Not an arrow, skipping");
            return;
        }

        Arrow arrow = (Arrow) event.getEntity();

        if (arrow.getFireTicks() <= 0) {
            debugLog("Arrow not on fire, skipping");
            return;
        }

        if (event.getHitBlock() == null) {
            debugLog("No block hit, skipping");
            return;
        }

        if (!isLocationAllowed(event.getHitBlock().getLocation())) {
            debugLog("Hit block location not allowed, skipping");
            return;
        }

        if (event.getHitBlock().getType() == Material.TNT) {
            Block tntBlock = event.getHitBlock();
            final Location loc = tntBlock.getLocation().clone().add(0.5, 0.5, 0.5);

            int x = tntBlock.getX();
            int y = tntBlock.getY();
            int z = tntBlock.getZ();
            String coords = x + ", " + y + ", " + z;

            // Remove the TNT block
            tntBlock.setType(Material.AIR);

            // Platform-independent scheduling for TNT spawn
            runTask(loc, () -> {
                TNTPrimed tnt = loc.getWorld().spawn(loc, TNTPrimed.class);
                tnt.setFuseTicks(80);
                tnt.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            });

            debugLog("Fire arrow hit TNT at " + coords);
            debugLog("TNT ignited by fire arrow at " + coords);

            arrow.remove();
        }
    }

    // ==================== PISTON EXTEND HANDLER ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        debugLog("onPistonExtend called");

        if (!isLocationAllowed(event.getBlock().getLocation())) {
            debugLog("Piston location not allowed, skipping");
            return;
        }

        if (enablePistons) {
            event.setCancelled(false);
            debugLog("Piston extension allowed");
        } else {
            debugLog("Pistons disabled, skipping");
        }
    }

    // ==================== PISTON RETRACT HANDLER ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        debugLog("onPistonRetract called");

        if (!isLocationAllowed(event.getBlock().getLocation())) {
            debugLog("Piston location not allowed, skipping");
            return;
        }

        if (enablePistons) {
            event.setCancelled(false);
            debugLog("Piston retraction allowed");
        } else {
            debugLog("Pistons disabled, skipping");
        }
    }

    // ==================== WITHER BLOCK BREAK HANDLER ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWitherBlockBreak(EntityChangeBlockEvent event) {
        debugLog("onWitherBlockBreak called - Entity: " + event.getEntityType());

        if (!isLocationAllowed(event.getBlock().getLocation())) {
            debugLog("Wither block break location not allowed, skipping");
            return;
        }

        if (enableWither) {
            if (event.getEntityType().name().contains("WITHER")) {
                event.setCancelled(false);
                event.getBlock().breakNaturally();
                debugLog("Wither breaking block at " +
                        event.getBlock().getX() + ", " +
                        event.getBlock().getY() + ", " +
                        event.getBlock().getZ());
                debugLog("Wither Break!");
            }
        } else {
            debugLog("Wither disabled, skipping");
        }
    }

    // ==================== WITHER DAMAGE HANDLER ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWitherDamage(EntityDamageByBlockEvent event) {
        debugLog("onWitherDamage called");

        if (!isLocationAllowed(event.getEntity().getLocation())) {
            debugLog("Wither damage location not allowed, skipping");
            return;
        }

        if (enableWither) {
            event.setCancelled(false);
            debugLog("Wither damage allowed");
        } else {
            debugLog("Wither damage disabled, skipping");
        }
    }

    // ==================== GRAVITY BLOCK HANDLER ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGravityFall(EntityChangeBlockEvent event) {
        debugLog("onGravityFall called - Block type: " + event.getBlock().getType());

        if (!isLocationAllowed(event.getBlock().getLocation())) {
            debugLog("Gravity block location not allowed, skipping");
            return;
        }

        if (enableSand) {
            Material type = event.getBlock().getType();
            if (type == Material.SAND || type == Material.GRAVEL || type == Material.ANVIL) {
                event.setCancelled(false);
                debugLog("Gravity block falling allowed: " + type);
            } else {
                event.setCancelled(false);
            }
        } else {
            debugLog("Gravity blocks falling disabled, skipping");
        }
    }

    // ==================== MINECART HOPPER HANDLER ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        debugLog("onInventoryMove called");

        Location checkLocation = event.getDestination() != null ?
                event.getDestination().getLocation() : event.getSource().getLocation();

        if (checkLocation != null && !isLocationAllowed(checkLocation)) {
            debugLog("Inventory location not allowed, skipping");
            return;
        }

        if (enableMinecart) {
            event.setCancelled(false);
            debugLog("Minecart hopper item movement allowed");
            debugLog("Minecart hopper working!");
        } else {
            debugLog("Minecart hopper disabled, skipping");
        }
    }

    // ==================== EGG SPAWN HANDLER ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEggSpawn(PlayerInteractEvent event) {
        debugLog("onEggSpawn (PlayerInteractEvent) called - Action: " + event.getAction());

        if (event.getClickedBlock() != null && !isLocationAllowed(event.getClickedBlock().getLocation())) {
            debugLog("Egg spawn location not allowed, skipping");
            return;
        }

        if (enableEggSpawn) {
            if (event.getAction().name().contains("RIGHT_CLICK_BLOCK")) {
                if (event.getItem() != null && event.getItem().getType().name().endsWith("_SPAWN_EGG")) {
                    event.setCancelled(false);
                    debugLog("Spawn egg used on block: " + event.getItem().getType());
                    debugLog("Mob spawned from spawn egg on block!");
                }
            }
        } else {
            debugLog("Egg spawn disabled, skipping");
        }
    }

    // ==================== VEHICLE DESTROY HANDLER ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        debugLog("onVehicleDestroy called - Vehicle: " + event.getVehicle().getType());

        if (!isLocationAllowed(event.getVehicle().getLocation())) {
            debugLog("Vehicle location not allowed, skipping");
            return;
        }

        if (enableVehicleDestroy) {
            event.setCancelled(false);
            debugLog("Vehicle destruction forced allowed");
            if (event.getAttacker() instanceof Player) {
                debugLog("Player destroyed a vehicle!");
            }
        } else {
            debugLog("Vehicle destruction disabled, using default behavior");
        }
    }

    // ==================== FLUID FLOW HANDLER ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFluidFlow(BlockFromToEvent event) {
        debugLog("onFluidFlow called - Block: " + event.getBlock().getType());

        if (!isLocationAllowed(event.getBlock().getLocation()) ||
                !isLocationAllowed(event.getToBlock().getLocation())) {
            debugLog("Fluid flow location not allowed, skipping");
            return;
        }

        if (enableFluidFlow) {
            event.setCancelled(false);
            debugLog("Fluid flow forced: " + event.getBlock().getType());
            debugLog("Water/Lava flowing freely!");
        } else {
            debugLog("Fluid flow disabled, using default behavior");
        }
    }

    // ==================== FISHING ROD MINECART HANDLER ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFishMinecart(PlayerFishEvent event) {
        debugLog("onFishMinecart called - State: " + event.getState());

        if (enableFishingMinecart) {
            if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
                if (event.getCaught() instanceof Minecart) {
                    if (!isLocationAllowed(event.getCaught().getLocation())) {
                        debugLog("Caught minecart location not allowed, skipping");
                        return;
                    }

                    event.setCancelled(false);
                    debugLog("Minecart caught by fishing rod - allowing pull");
                    debugLog("Minecart pulled by fishing rod!");
                }
            }
        } else {
            debugLog("Minecart fishing disabled, using default behavior");
        }
    }

    // Inner class to represent a protected region (immutable - thread-safe)
    private static class Region {
        private final String worldName;
        private final int minX, minY, minZ;
        private final int maxX, maxY, maxZ;

        public Region(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
            this.worldName = worldName;

            this.minX = Math.min(x1, x2);
            this.minY = Math.min(y1, y2);
            this.minZ = Math.min(z1, z2);
            this.maxX = Math.max(x1, x2);
            this.maxY = Math.max(y1, y2);
            this.maxZ = Math.max(z1, z2);
        }

        public boolean contains(Location location) {
            if (!location.getWorld().getName().equals(worldName)) {
                return false;
            }

            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();

            return x >= minX && x <= maxX &&
                    y >= minY && y <= maxY &&
                    z >= minZ && z <= maxZ;
        }
    }
}
