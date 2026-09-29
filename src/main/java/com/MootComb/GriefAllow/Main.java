package com.MootComb.GriefAllow;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
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
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;

public class Main extends JavaPlugin implements Listener {

    // ==================== CONSTANTS ====================
    private static final class Constants {
        // Primitive fuse: always 80 ticks (4 seconds), like an item
        // ignited by flint and steel or redstone.
        static final int TNT_FUSE_PRIMITIVE = 80;

        // Smart fuse: vanilla chain reaction formula.
        // fuse = random(0 .. baseFuse / 4) + baseFuse / 8
        // with baseFuse = 80 => random(0..20) + 10 => 10..30 ticks.
        static final int TNT_FUSE_BASE = 80;
        static final int TNT_FUSE_SMART_DIVISOR_RANDOM = 4;
        static final int TNT_FUSE_SMART_DIVISOR_OFFSET = 8;

        static final float TNT_YIELD = 4.0F;
        static final double BLOCK_CENTER_OFFSET = 0.5;

        // Fallback explosion yield when the source reports 0.
        static final float EXPLOSION_YIELD = 1.0F;
        static final double DEFAULT_EXPLOSION_YIELD_MULTIPLIER = 1.0;

        // Default values
        static final boolean DEFAULT_DEBUG = false;
        static final boolean DEFAULT_ENABLE_TNT = false;
        static final boolean DEFAULT_ENABLE_PISTONS = false;
        static final boolean DEFAULT_ENABLE_WITHER = false;
        static final boolean DEFAULT_ENABLE_SAND = false;
        static final boolean DEFAULT_ENABLE_MINECART = false;
        static final boolean DEFAULT_ENABLE_EGG_SPAWN = false;
        static final boolean DEFAULT_ENABLE_VEHICLE_DESTROY = false;
        static final boolean DEFAULT_ENABLE_FLUID_FLOW = false;
        static final boolean DEFAULT_ENABLE_FISHING_MINECART = false;

        // Folia retry
        static final int FOLIA_MAX_RETRIES = 3;
        static final long FOLIA_RETRY_DELAY_MS = 50;
    }

    // ==================== ENUMS ====================
    public enum ListMode {
        DISABLE,
        WHITELIST,
        BLACKLIST;

        public static ListMode fromString(String value) {
            if (value == null) {
                return DISABLE;
            }

            try {
                return ListMode.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return DISABLE;
            }
        }
    }

    /**
     * What to do with TNT blocks that are caught in an explosion's
     * block list.
     *
     * ACTIVATE - ignite the TNT block so it becomes a primed TNT
     *            entity, exactly like vanilla chain reactions.
     * DESTROY  - break the block naturally (drops an item).
     * NOTHING  - leave the TNT block untouched.
     */
    public enum TntAction {
        ACTIVATE,
        DESTROY,
        NOTHING;

        public static TntAction fromString(String value) {
            if (value == null) {
                return DESTROY;
            }

            try {
                return TntAction.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return DESTROY;
            }
        }
    }

    /**
     * How the fuse of a newly activated TNT is computed.
     *
     * PRIMITIVE - always 80 ticks (4 seconds), like an item ignited
     *             by flint and steel or redstone.
     * SMART     - vanilla chain reaction formula:
     *             random(0 .. 80/4) + 80/8 => 10..30 ticks.
     */
    public enum TntMode {
        PRIMITIVE,
        SMART;

        public static TntMode fromString(String value) {
            if (value == null) {
                return SMART;
            }

            try {
                return TntMode.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return SMART;
            }
        }
    }

    // ==================== CONFIGURATION ====================
    private volatile boolean debug;
    private volatile boolean enableTnt;
    private volatile TntAction tntAction = TntAction.DESTROY;
    private volatile TntMode tntMode = TntMode.SMART;
    private volatile double explosionYieldMultiplier = Constants.DEFAULT_EXPLOSION_YIELD_MULTIPLIER;
    private volatile boolean enablePistons;
    private volatile boolean enableWither;
    private volatile boolean enableSand;
    private volatile boolean enableMinecart;
    private volatile boolean enableEggSpawn;
    private volatile boolean enableVehicleDestroy;
    private volatile boolean enableFluidFlow;
    private volatile boolean enableFishingMinecart;

    private volatile ListMode worldsMode = ListMode.DISABLE;
    private final Set<String> worlds = ConcurrentHashMap.newKeySet();

    private volatile ListMode regionsMode = ListMode.DISABLE;
    private final Map<String, Region> regions = new ConcurrentHashMap<>();

    /**
     * Entity types whose explosions the plugin is allowed to process.
     * If an entity type is not listed here, the plugin does not touch
     * the explosion at all - vanilla behaviour is preserved.
     */
    private final Set<String> explosionTypes = ConcurrentHashMap.newKeySet();

    private boolean isFolia;

    // ==================== PLUGIN LIFECYCLE ====================
    @Override
    public void onEnable() {
        isFolia = detectFolia();

        saveDefaultConfig();
        reloadConfig();
        loadConfigValues();
        validateConfig();

        Bukkit.getPluginManager().registerEvents(this, this);

        if (debug) {
            getLogger().info("GriefAllow enabled in DEBUG mode! (Running on " + (isFolia ? "Folia" : "Paper/Spigot") + ")");
            logConfiguration();
        } else {
            getLogger().info("GriefAllow enabled! (Running on " + (isFolia ? "Folia" : "Paper/Spigot") + ")");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("GriefAllow disabled!");
    }

    // ==================== FOLIA DETECTION & SCHEDULING ====================
    private boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }

    private void runTask(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) {
            getLogger().warning("Cannot run task: location or world is null");
            return;
        }

        if (isFolia) {
            runFoliaTask(location, task);
        } else {
            runBukkitTask(task);
        }
    }

    private void runFoliaTask(Location location, Runnable task) {
        try {
            Object regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            regionScheduler.getClass().getMethod("execute", JavaPlugin.class, Location.class, Runnable.class)
                    .invoke(regionScheduler, this, location, task);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to execute Folia region task, falling back to sync", e);
            runBukkitTask(task);
        }
    }

    private void runBukkitTask(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    // ==================== CONFIGURATION LOADING ====================
    private void loadConfigValues() {
        debug = getConfig().getBoolean("debug", Constants.DEFAULT_DEBUG);
        enableTnt = getConfig().getBoolean("enable-tnt", Constants.DEFAULT_ENABLE_TNT);

        tntAction = loadTntAction();
        tntMode = loadTntMode();

        explosionYieldMultiplier = getConfig().getDouble(
                "explosion-yield-multiplier",
                Constants.DEFAULT_EXPLOSION_YIELD_MULTIPLIER);
        if (explosionYieldMultiplier < 0) {
            getLogger().warning("explosion-yield-multiplier is negative, clamping to 0");
            explosionYieldMultiplier = 0;
        }

        enablePistons = getConfig().getBoolean("enable-pistons", Constants.DEFAULT_ENABLE_PISTONS);
        enableWither = getConfig().getBoolean("enable-wither", Constants.DEFAULT_ENABLE_WITHER);
        enableSand = getConfig().getBoolean("enable-sand", Constants.DEFAULT_ENABLE_SAND);
        enableMinecart = getConfig().getBoolean("enable-minecart", Constants.DEFAULT_ENABLE_MINECART);
        enableEggSpawn = getConfig().getBoolean("enable-egg-spawn", Constants.DEFAULT_ENABLE_EGG_SPAWN);
        enableVehicleDestroy = getConfig().getBoolean("enable-vehicle-destroy", Constants.DEFAULT_ENABLE_VEHICLE_DESTROY);
        enableFluidFlow = getConfig().getBoolean("enable-fluid-flow", Constants.DEFAULT_ENABLE_FLUID_FLOW);
        enableFishingMinecart = getConfig().getBoolean("enable-fishing-minecart", Constants.DEFAULT_ENABLE_FISHING_MINECART);

        loadWorldSettings();
        loadRegionSettings();
        loadExplosionTypes();
    }

    /**
     * Loads the TNT action from the config.
     *
     * Priority:
     *   1. "tnt-action" string option, if present and valid.
     *   2. "tnt-chain-reaction" boolean (deprecated), if "tnt-action"
     *      is not set. true -> ACTIVATE, false -> DESTROY.
     *   3. Default DESTROY.
     */
    private TntAction loadTntAction() {
        if (getConfig().isString("tnt-action")) {
            String raw = getConfig().getString("tnt-action");
            TntAction action = TntAction.fromString(raw);
            if (action != null) {
                return action;
            }
            getLogger().warning("Unknown tnt-action value: '" + raw + "'. Falling back to tnt-chain-reaction.");
        }

        if (getConfig().isBoolean("tnt-chain-reaction")) {
            boolean chain = getConfig().getBoolean("tnt-chain-reaction");
            return chain ? TntAction.ACTIVATE : TntAction.DESTROY;
        }

        return TntAction.DESTROY;
    }

    /**
     * Loads the TNT mode from the config. Default is SMART.
     */
    private TntMode loadTntMode() {
        String raw = getConfig().getString("tnt-mode", "SMART");
        TntMode mode = TntMode.fromString(raw);
        if (mode == null) {
            getLogger().warning("Unknown tnt-mode value: '" + raw + "'. Falling back to SMART.");
            return TntMode.SMART;
        }
        return mode;
    }

    private void loadWorldSettings() {
        String modeStr = getConfig().getString("worlds-type", "disable");
        worldsMode = ListMode.fromString(modeStr);

        worlds.clear();
        List<String> worldList = getConfig().getStringList("worlds");
        if (worldList != null) {
            worlds.addAll(worldList);
        }
    }

    private void loadRegionSettings() {
        String modeStr = getConfig().getString("regions-type", "disable");
        regionsMode = ListMode.fromString(modeStr);

        regions.clear();
        ConfigurationSection regionsSection = getConfig().getConfigurationSection("regions");
        if (regionsSection != null) {
            for (String key : regionsSection.getKeys(false)) {
                ConfigurationSection regionSection = regionsSection.getConfigurationSection(key);
                if (regionSection != null) {
                    try {
                        String worldName = regionSection.getString("world");
                        if (worldName == null || worldName.isEmpty()) {
                            getLogger().warning("Region '" + key + "' has no world specified, skipping");
                            continue;
                        }

                        int x1 = regionSection.getInt("x1");
                        int y1 = regionSection.getInt("y1");
                        int z1 = regionSection.getInt("z1");
                        int x2 = regionSection.getInt("x2");
                        int y2 = regionSection.getInt("y2");
                        int z2 = regionSection.getInt("z2");

                        Region region = new Region(worldName, x1, y1, z1, x2, y2, z2);
                        regions.put(key, region);

                        if (debug) {
                            getLogger().info("Loaded region " + key + ": " + region);
                        }
                    } catch (Exception e) {
                        getLogger().warning("Failed to load region '" + key + "': " + e.getMessage());
                    }
                }
            }
        }
    }

    private void loadExplosionTypes() {
        explosionTypes.clear();
        List<String> list = getConfig().getStringList("explosion-types");
        if (list != null) {
            for (String s : list) {
                if (s != null && !s.trim().isEmpty()) {
                    explosionTypes.add(s.trim().toUpperCase());
                }
            }
        }
        if (debug) {
            getLogger().info("Loaded explosion types: " + explosionTypes);
        }
    }

    private void validateConfig() {
        if (worldsMode == ListMode.WHITELIST && worlds.isEmpty()) {
            getLogger().warning("World whitelist mode enabled but no worlds specified! Plugin will not act anywhere.");
        }

        if (regionsMode == ListMode.WHITELIST && regions.isEmpty()) {
            getLogger().warning("Region whitelist mode enabled but no regions specified! Plugin will not act anywhere.");
        }

        if (explosionTypes.isEmpty()) {
            getLogger().warning("No explosion-types specified! No explosions will be processed.");
        }
    }

    private void logConfiguration() {
        getLogger().info("Config values: enableTnt=" + enableTnt +
                ", tntAction=" + tntAction +
                ", tntMode=" + tntMode +
                ", explosionYieldMultiplier=" + explosionYieldMultiplier +
                ", enablePistons=" + enablePistons +
                ", enableWither=" + enableWither +
                ", enableSand=" + enableSand +
                ", enableMinecart=" + enableMinecart +
                ", enableEggSpawn=" + enableEggSpawn +
                ", enableVehicleDestroy=" + enableVehicleDestroy +
                ", enableFluidFlow=" + enableFluidFlow +
                ", enableFishingMinecart=" + enableFishingMinecart);
        getLogger().info("Worlds mode: " + worldsMode + ", Worlds: " + worlds);
        getLogger().info("Regions mode: " + regionsMode + ", Regions count: " + regions.size());
        getLogger().info("Explosion types: " + explosionTypes);
    }

    // ==================== LOCATION CHECKS ====================

    /**
     * Returns true if the plugin SHOULD act at the given location,
     * according to the worlds/regions configuration.
     *
     * IMPORTANT: If this returns false, the plugin must NOT touch the
     * event at all. It must not cancel it, and it must not force-allow
     * it either. This leaves room for other plugins (WorldGuard, etc.)
     * and vanilla behaviour to do their job.
     */
    private boolean shouldPluginAct(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return isWorldInScope(location) && isRegionInScope(location);
    }

    private boolean isWorldInScope(Location location) {
        if (worldsMode == ListMode.DISABLE) {
            return true;
        }

        String worldName = location.getWorld().getName();
        if (worldName == null) {
            return false;
        }

        boolean inWorld = worlds.contains(worldName);
        return worldsMode == ListMode.WHITELIST ? inWorld : !inWorld;
    }

    private boolean isRegionInScope(Location location) {
        if (regionsMode == ListMode.DISABLE || regions.isEmpty()) {
            return true;
        }

        boolean inAnyRegion = false;
        for (Region region : regions.values()) {
            if (region.contains(location)) {
                inAnyRegion = true;
                break;
            }
        }

        return regionsMode == ListMode.WHITELIST ? inAnyRegion : !inAnyRegion;
    }

    // ==================== UTILITY METHODS ====================
    private void debugLog(String message) {
        if (debug && message != null) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    private String getBlockCoords(Block block) {
        if (block == null) {
            return "null";
        }
        return block.getX() + ", " + block.getY() + ", " + block.getZ();
    }

    private String getLocationString(Location location) {
        if (location == null || location.getWorld() == null) {
            return "null";
        }
        return String.format("%s (%d, %d, %d)",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    private Location getBlockCenter(Block block) {
        if (block == null) {
            return null;
        }
        return block.getLocation().clone().add(
                Constants.BLOCK_CENTER_OFFSET,
                Constants.BLOCK_CENTER_OFFSET,
                Constants.BLOCK_CENTER_OFFSET
        );
    }

    private boolean isExplosionTypeAllowed(Entity entity) {
        if (entity == null) {
            return false;
        }
        return explosionTypes.contains(entity.getType().name().toUpperCase());
    }

    /**
     * Computes the fuse for a freshly activated TNT according to the
     * configured tnt-mode.
     *
     * PRIMITIVE - always Constants.TNT_FUSE_PRIMITIVE (80 ticks).
     * SMART     - vanilla chain reaction formula:
     *             random(0 .. 80/4) + 80/8 => 10..30 ticks.
     */
    private int computeFuse() {
        if (tntMode == TntMode.PRIMITIVE) {
            return Constants.TNT_FUSE_PRIMITIVE;
        }

        int base = Constants.TNT_FUSE_BASE;
        int maxRandom = (base / Constants.TNT_FUSE_SMART_DIVISOR_RANDOM) + 1;
        int offset = base / Constants.TNT_FUSE_SMART_DIVISOR_OFFSET;
        return ThreadLocalRandom.current().nextInt(0, maxRandom) + offset;
    }

    // ==================== TNT HANDLING ====================
    private void spawnTNTPrimed(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            debugLog("Cannot spawn TNT: location or world is null");
            return;
        }

        runTask(loc, () -> {
            try {
                TNTPrimed tnt = loc.getWorld().spawn(loc, TNTPrimed.class);
                if (tnt != null) {
                    int fuse = computeFuse();
                    tnt.setFuseTicks(fuse);
                    tnt.setYield(Constants.TNT_YIELD);
                    tnt.setVelocity(new Vector(0, 0, 0));
                    debugLog("Spawned primed TNT at " + getLocationString(loc) +
                            " with fuse=" + fuse + " (mode=" + tntMode + ")");
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to spawn TNT at " + getLocationString(loc), e);
            }
        });
    }

    private void applyTntAction(Block block) {
        if (block == null || block.getType() != Material.TNT) {
            return;
        }

        if (!shouldPluginAct(block.getLocation())) {
            return;
        }

        switch (tntAction) {
            case ACTIVATE:
                Location centerLoc = getBlockCenter(block);
                if (centerLoc != null) {
                    block.setType(Material.AIR);
                    spawnTNTPrimed(centerLoc);
                    debugLog("TNT activated (chain reaction) at " + getBlockCoords(block));
                }
                break;

            case DESTROY:
                block.breakNaturally();
                debugLog("TNT destroyed at " + getBlockCoords(block));
                break;

            case NOTHING:
                debugLog("TNT left untouched at " + getBlockCoords(block));
                break;
        }
    }

    private void handleTNTIgnition(Block block, String source) {
        if (block == null || source == null) {
            return;
        }

        if (!enableTnt || block.getType() != Material.TNT) {
            return;
        }

        if (!shouldPluginAct(block.getLocation())) {
            debugLog("TNT ignition outside plugin scope: " + getBlockCoords(block));
            return;
        }

        applyTntAction(block);
        debugLog("TNT handled (" + tntAction + ") by " + source + " at " + getBlockCoords(block));
    }

    // ==================== EVENT HANDLERS ====================
    //
    // IMPORTANT: When shouldPluginAct(...) returns false, the plugin
    // must NOT touch the event at all. It does not cancel it, it does
    // not force-allow it. This leaves the event to vanilla behaviour
    // and to other plugins such as WorldGuard.
    //

    @EventHandler(priority = EventPriority.LOWEST)
    public void onExplode(EntityExplodeEvent event) {
        if (event == null) {
            return;
        }

        debugLog("onExplode called");

        Location loc = event.getLocation();
        if (!shouldPluginAct(loc)) {
            debugLog("Explosion outside plugin scope, ignoring");
            return;
        }

        Entity source = event.getEntity();
        if (!isExplosionTypeAllowed(source)) {
            debugLog("Explosion type not in config, ignoring: " +
                    (source != null ? source.getType() : "null"));
            return;
        }

        if (!enableTnt) {
            debugLog("Explosions disabled, ignoring");
            return;
        }

        event.setCancelled(false);

        // Scale the vanilla yield of this explosion by the configured
        // multiplier. Base yield depends on the source entity type
        // (e.g. Creeper = 3, TNT = 4, Ghast fireball = 1, etc.).
        float baseYield = event.getYield();
        if (baseYield <= 0.0F) {
            baseYield = Constants.EXPLOSION_YIELD;
        }
        float finalYield = (float) (baseYield * explosionYieldMultiplier);
        event.setYield(finalYield);

        debugLog("Explosion yield: base=" + baseYield +
                ", multiplier=" + explosionYieldMultiplier +
                ", final=" + finalYield);

        List<Block> blocks = event.blockList();
        if (blocks == null) {
            return;
        }

        List<Block> blocksToProcess = new ArrayList<>(blocks);
        for (Block block : blocksToProcess) {
            if (block == null || !shouldPluginAct(block.getLocation())) {
                continue;
            }

            if (block.getType() == Material.TNT) {
                applyTntAction(block);
            } else {
                block.breakNaturally();
            }
        }

        debugLog("Explosion processed: " + (source != null ? source.getType() : "unknown") +
                " (TNT action: " + tntAction + ", mode: " + tntMode + ")");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTntIgnite(BlockIgniteEvent event) {
        if (event == null) {
            return;
        }

        debugLog("onTntIgnite called - Cause: " + event.getCause());

        Block block = event.getBlock();
        if (block == null || !shouldPluginAct(block.getLocation())) {
            debugLog("Block ignition outside plugin scope, ignoring");
            return;
        }

        if (enableTnt && block.getType() == Material.TNT) {
            event.setCancelled(false);
            debugLog("TNT ignition allowed");

            if (event.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
                debugLog("TNT ignited with Flint & Steel!");
            } else if (event.getCause() == BlockIgniteEvent.IgniteCause.FIREBALL) {
                debugLog("TNT ignited with Fireball!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFlintClick(PlayerInteractEvent event) {
        if (event == null) {
            return;
        }

        debugLog("onFlintClick called - Action: " + event.getAction());

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || !shouldPluginAct(clickedBlock.getLocation())) {
            debugLog("Clicked block outside plugin scope, ignoring");
            return;
        }

        if (enableTnt && event.getAction().name().contains("RIGHT_CLICK_BLOCK")) {
            if (event.getItem() != null && event.getItem().getType() == Material.FLINT_AND_STEEL) {
                if (clickedBlock.getType() == Material.TNT) {
                    event.setCancelled(false);
                    debugLog("Flint and steel used on TNT block");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFireArrowHit(ProjectileHitEvent event) {
        if (event == null) {
            return;
        }

        debugLog("onFireArrowHit called");

        if (!enableTnt) {
            debugLog("TNT disabled, ignoring");
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof Arrow)) {
            debugLog("Not an arrow, ignoring");
            return;
        }

        Arrow arrow = (Arrow) entity;
        if (arrow.getFireTicks() <= 0) {
            debugLog("Arrow not on fire, ignoring");
            return;
        }

        Block hitBlock = event.getHitBlock();
        if (hitBlock == null) {
            debugLog("No block hit, ignoring");
            return;
        }

        if (!shouldPluginAct(hitBlock.getLocation())) {
            debugLog("Hit block outside plugin scope, ignoring");
            return;
        }

        if (hitBlock.getType() == Material.TNT) {
            handleTNTIgnition(hitBlock, "fire arrow");
            arrow.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event == null) {
            return;
        }

        debugLog("onPistonExtend called");

        Block block = event.getBlock();
        if (block == null || !shouldPluginAct(block.getLocation())) {
            debugLog("Piston outside plugin scope, ignoring");
            return;
        }

        if (enablePistons) {
            event.setCancelled(false);
            debugLog("Piston extension allowed");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event == null) {
            return;
        }

        debugLog("onPistonRetract called");

        Block block = event.getBlock();
        if (block == null || !shouldPluginAct(block.getLocation())) {
            debugLog("Piston outside plugin scope, ignoring");
            return;
        }

        if (enablePistons) {
            event.setCancelled(false);
            debugLog("Piston retraction allowed");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWitherBlockBreak(EntityChangeBlockEvent event) {
        if (event == null) {
            return;
        }

        debugLog("onWitherBlockBreak called - Entity: " + event.getEntityType());

        Block block = event.getBlock();
        if (block == null || !shouldPluginAct(block.getLocation())) {
            debugLog("Wither block break outside plugin scope, ignoring");
            return;
        }

        if (enableWither && event.getEntity() instanceof Wither) {
            event.setCancelled(false);
            block.breakNaturally();
            debugLog("Wither breaking block at " + getBlockCoords(block));
            debugLog("Wither Break!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWitherDamage(EntityDamageByBlockEvent event) {
        if (event == null) {
            return;
        }

        debugLog("onWitherDamage called");

        Entity entity = event.getEntity();
        if (entity == null || !shouldPluginAct(entity.getLocation())) {
            debugLog("Wither damage outside plugin scope, ignoring");
            return;
        }

        if (enableWither) {
            event.setCancelled(false);
            debugLog("Wither damage allowed");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGravityFall(EntityChangeBlockEvent event) {
        if (event == null) {
            return;
        }

        debugLog("onGravityFall called - Block type: " + event.getBlock().getType());

        Block block = event.getBlock();
        if (block == null || !shouldPluginAct(block.getLocation())) {
            debugLog("Gravity block outside plugin scope, ignoring");
            return;
        }

        if (enableSand) {
            Material type = block.getType();
            if (type == Material.SAND || type == Material.GRAVEL || type == Material.ANVIL) {
                event.setCancelled(false);
                debugLog("Gravity block falling allowed: " + type);
            } else {
                event.setCancelled(false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (event == null) {
            return;
        }

        debugLog("onInventoryMove called");

        Location checkLocation = null;
        if (event.getDestination() != null) {
            checkLocation = event.getDestination().getLocation();
        }
        if (checkLocation == null && event.getSource() != null) {
            checkLocation = event.getSource().getLocation();
        }

        if (checkLocation == null || !shouldPluginAct(checkLocation)) {
            debugLog("Inventory move outside plugin scope, ignoring");
            return;
        }

        if (enableMinecart) {
            event.setCancelled(false);
            debugLog("Minecart hopper item movement allowed");
            debugLog("Minecart hopper working!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEggSpawn(PlayerInteractEvent event) {
        if (event == null) {
            return;
        }

        debugLog("onEggSpawn (PlayerInteractEvent) called - Action: " + event.getAction());

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || !shouldPluginAct(clickedBlock.getLocation())) {
            debugLog("Egg spawn outside plugin scope, ignoring");
            return;
        }

        if (enableEggSpawn && event.getAction().name().contains("RIGHT_CLICK_BLOCK")) {
            if (event.getItem() != null) {
                Material itemType = event.getItem().getType();
                if (itemType != null && itemType.name().endsWith("_SPAWN_EGG")) {
                    event.setCancelled(false);
                    debugLog("Spawn egg used on block: " + itemType);
                    debugLog("Mob spawned from spawn egg on block!");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (event == null) {
            return;
        }

        debugLog("onVehicleDestroy called - Vehicle: " + event.getVehicle().getType());

        Vehicle vehicle = event.getVehicle();
        if (vehicle == null || !shouldPluginAct(vehicle.getLocation())) {
            debugLog("Vehicle outside plugin scope, ignoring");
            return;
        }

        if (enableVehicleDestroy) {
            event.setCancelled(false);
            debugLog("Vehicle destruction forced allowed");
            if (event.getAttacker() instanceof Player) {
                debugLog("Player destroyed a vehicle!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFluidFlow(BlockFromToEvent event) {
        if (event == null) {
            return;
        }

        debugLog("onFluidFlow called - Block: " + event.getBlock().getType());

        Block block = event.getBlock();
        Block toBlock = event.getToBlock();

        if (block == null || toBlock == null) {
            return;
        }

        if (!shouldPluginAct(block.getLocation()) || !shouldPluginAct(toBlock.getLocation())) {
            debugLog("Fluid flow outside plugin scope, ignoring");
            return;
        }

        if (enableFluidFlow) {
            event.setCancelled(false);
            debugLog("Fluid flow forced: " + block.getType());
            debugLog("Water/Lava flowing freely!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFishMinecart(PlayerFishEvent event) {
        if (event == null) {
            return;
        }

        debugLog("onFishMinecart called - State: " + event.getState());

        if (!enableFishingMinecart || event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) {
            return;
        }

        Entity caught = event.getCaught();
        if (!(caught instanceof Minecart)) {
            return;
        }

        if (!shouldPluginAct(caught.getLocation())) {
            debugLog("Fishing minecart outside plugin scope, ignoring");
            return;
        }

        event.setCancelled(false);
        debugLog("Minecart caught by fishing rod - allowing pull");
        debugLog("Minecart pulled by fishing rod!");
    }

    // ==================== REGION CLASS ====================
    private static final class Region {
        private final String worldName;
        private final int minX, minY, minZ;
        private final int maxX, maxY, maxZ;

        public Region(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
            this.worldName = Objects.requireNonNull(worldName, "World name cannot be null");
            this.minX = Math.min(x1, x2);
            this.minY = Math.min(y1, y2);
            this.minZ = Math.min(z1, z2);
            this.maxX = Math.max(x1, x2);
            this.maxY = Math.max(y1, y2);
            this.maxZ = Math.max(z1, z2);
        }

        public boolean contains(Location location) {
            if (location == null || location.getWorld() == null) {
                return false;
            }

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

        @Override
        public String toString() {
            return String.format("world=%s [%d,%d,%d] to [%d,%d,%d]",
                    worldName, minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
