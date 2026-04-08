package com.mootcomb.griefallow;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {

    // Configuration flags
    private boolean debug;
    private boolean enableTnt;
    private boolean tntChainReaction;
    private boolean enablePistons;
    private boolean enableWither;
    private boolean enableSand;
    private boolean enableMinecart;
    private boolean enableEggSpawn;
    private boolean enableVehicleDestroy;
    private boolean enableFluidFlow;
    private boolean enableFishingMinecart;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        loadConfigValues();

        Bukkit.getPluginManager().registerEvents(this, this);

        if (debug) {
            getLogger().info("GriefAllow enabled in DEBUG mode!");
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
        } else {
            getLogger().info("GriefAllow enabled!");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("GriefAllow disabled!");
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

    // Log debug messages if debug mode is enabled
    private void debugLog(String message) {
        if (debug) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    // Log info messages if debug mode is enabled
    private void infoLog(String message) {
        if (debug) {
            getLogger().info(message);
        }
    }

    // ==================== TNT EXPLOSION HANDLER ====================
    // Controls TNT explosions, block drops, and chain reactions
    @EventHandler(priority = EventPriority.LOWEST)
    public void onExplode(EntityExplodeEvent event) {
        debugLog("onExplode called");

        if (enableTnt) {
            debugLog("TNT explosions enabled");
            event.setCancelled(false);
            event.setYield(1.0F);

            debugLog("Chain reaction mode: " + tntChainReaction);

            for (Block block : event.blockList()) {
                if (block.getType() == Material.TNT) {
                    if (tntChainReaction) {
                        // Chain reaction ON: ignite nearby TNT
                        block.setType(Material.AIR);
                        TNTPrimed tnt = block.getWorld().spawn(block.getLocation(), TNTPrimed.class);
                        tnt.setFuseTicks(80);
                        debugLog("TNT chain reaction ignited at " +
                                block.getX() + ", " + block.getY() + ", " + block.getZ());
                    } else {
                        // Chain reaction OFF: drop TNT as item
                        block.breakNaturally();
                        debugLog("TNT dropped as item at " +
                                block.getX() + ", " + block.getY() + ", " + block.getZ());
                    }
                } else {
                    // Break other blocks naturally
                    block.breakNaturally();
                }
            }

            infoLog("TNT Explosion with " + (tntChainReaction ? "chain reaction!" : "items drop!"));
        } else {
            debugLog("TNT explosions disabled, skipping");
        }
    }

    // ==================== TNT IGNITE HANDLER ====================
    // Allows TNT to be ignited by flint and steel, fireballs, etc.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTntIgnite(BlockIgniteEvent event) {
        debugLog("onTntIgnite called - Cause: " + event.getCause());

        if (enableTnt) {
            if (event.getBlock().getType() == Material.TNT) {
                event.setCancelled(false);
                debugLog("TNT ignition allowed");

                if (event.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
                    infoLog("TNT ignited with Flint & Steel!");
                } else if (event.getCause() == BlockIgniteEvent.IgniteCause.FIREBALL) {
                    infoLog("TNT ignited with Fireball!");
                }
            }
        } else {
            debugLog("TNT ignition disabled, skipping");
        }
    }

    // ==================== FLINT AND STEEL HANDLER ====================
    // Allows players to right-click TNT with flint and steel
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFlintClick(PlayerInteractEvent event) {
        debugLog("onFlintClick called - Action: " + event.getAction());

        if (enableTnt) {
            if (event.getAction().name().contains("RIGHT_CLICK_BLOCK")) {
                if (event.getItem() != null && event.getItem().getType() == Material.FLINT_AND_STEEL) {
                    if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.TNT) {
                        event.setCancelled(false);
                        debugLog("Flint and steel used on TNT block");
                        infoLog("Flint and steel on TNT!");
                    }
                }
            }
        } else {
            debugLog("TNT interactions disabled, skipping");
        }
    }

    // ==================== FIRE ARROW HANDLER ====================
    // Allows flaming arrows to ignite TNT blocks
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFireArrowHit(ProjectileHitEvent event) {
        debugLog("onFireArrowHit called");

        if (enableTnt) {
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

            if (event.getHitBlock().getType() == Material.TNT) {
                Block tntBlock = event.getHitBlock();

                int x = tntBlock.getX();
                int y = tntBlock.getY();
                int z = tntBlock.getZ();
                String coords = x + ", " + y + ", " + z;

                // Remove the TNT block
                tntBlock.setType(Material.AIR);

                // Spawn primed TNT at the location
                org.bukkit.Location loc = tntBlock.getLocation().add(0.5, 0.5, 0.5);
                TNTPrimed tnt = tntBlock.getWorld().spawn(loc, TNTPrimed.class);
                tnt.setFuseTicks(80);

                // Set zero velocity so it doesn't fly away
                tnt.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

                debugLog("Fire arrow hit TNT at " + coords);
                infoLog("TNT ignited by fire arrow at " + coords);

                arrow.remove();
            }
        }
    }

    // ==================== PISTON EXTEND HANDLER ====================
    // Controls whether pistons can extend and push blocks
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        debugLog("onPistonExtend called");

        if (enablePistons) {
            event.setCancelled(false);
            debugLog("Piston extension allowed");
        } else {
            debugLog("Pistons disabled, skipping");
        }
    }

    // ==================== PISTON RETRACT HANDLER ====================
    // Controls whether pistons can retract and pull blocks
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        debugLog("onPistonRetract called");

        if (enablePistons) {
            event.setCancelled(false);
            debugLog("Piston retraction allowed");
        } else {
            debugLog("Pistons disabled, skipping");
        }
    }

    // ==================== WITHER BLOCK BREAK HANDLER ====================
    // Allows Wither to break blocks
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWitherBlockBreak(EntityChangeBlockEvent event) {
        debugLog("onWitherBlockBreak called - Entity: " + event.getEntityType());

        if (enableWither) {
            if (event.getEntityType().name().contains("WITHER")) {
                event.setCancelled(false);
                event.getBlock().breakNaturally();
                debugLog("Wither breaking block at " +
                        event.getBlock().getX() + ", " +
                        event.getBlock().getY() + ", " +
                        event.getBlock().getZ());
                infoLog("Wither Break!");
            }
        } else {
            debugLog("Wither disabled, skipping");
        }
    }

    // ==================== WITHER DAMAGE HANDLER ====================
    // Allows Wither to deal damage to entities
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWitherDamage(EntityDamageByBlockEvent event) {
        debugLog("onWitherDamage called");

        if (enableWither) {
            event.setCancelled(false);
            debugLog("Wither damage allowed");
        } else {
            debugLog("Wither damage disabled, skipping");
        }
    }

    // ==================== GRAVITY BLOCK HANDLER ====================
    // Allows sand, gravel, and anvils to fall
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGravityFall(EntityChangeBlockEvent event) {
        debugLog("onGravityFall called - Block type: " + event.getBlock().getType());

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
    // Allows minecart hoppers to transfer items between inventories
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        debugLog("onInventoryMove called");

        if (enableMinecart) {
            event.setCancelled(false);
            debugLog("Minecart hopper item movement allowed");
            infoLog("Minecart hopper working!");
        } else {
            debugLog("Minecart hopper disabled, skipping");
        }
    }

    // ==================== EGG SPAWN HANDLER ====================
    // Allows mobs to spawn from spawn eggs
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEggSpawn(CreatureSpawnEvent event) {
        debugLog("onEggSpawn called - Reason: " + event.getSpawnReason());

        if (enableEggSpawn) {
            if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
                event.setCancelled(false);
                debugLog("Mob spawn from egg allowed: " + event.getEntityType());
                infoLog("Mob spawned from spawn egg!");
            }
        } else {
            debugLog("Egg spawn disabled, skipping");
        }
    }

    // ==================== VEHICLE DESTROY HANDLER ====================
    // Forces players to be able to destroy minecarts and boats when enabled
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        debugLog("onVehicleDestroy called - Vehicle: " + event.getVehicle().getType());

        if (enableVehicleDestroy) {
            event.setCancelled(false);
            debugLog("Vehicle destruction forced allowed");
            if (event.getAttacker() instanceof Player) {
                infoLog("Player destroyed a vehicle!");
            }
        } else {
            debugLog("Vehicle destruction disabled, using default behavior");
        }
    }

    // ==================== FLUID FLOW HANDLER ====================
    // Forces water and lava to flow and destroy blocks when enabled
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFluidFlow(BlockFromToEvent event) {
        debugLog("onFluidFlow called - Block: " + event.getBlock().getType());

        if (enableFluidFlow) {
            event.setCancelled(false);
            debugLog("Fluid flow forced: " + event.getBlock().getType());
            infoLog("Water/Lava flowing freely!");
        } else {
            debugLog("Fluid flow disabled, using default behavior");
        }
    }

    // ==================== FISHING ROD MINECART HANDLER ====================
    // Allows players to pull minecarts with a fishing rod when enabled
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFishMinecart(PlayerFishEvent event) {
        debugLog("onFishMinecart called - State: " + event.getState());

        if (enableFishingMinecart) {
            if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
                if (event.getCaught() instanceof Minecart) {
                    event.setCancelled(false);
                    debugLog("Minecart caught by fishing rod - allowing pull");
                    infoLog("Minecart pulled by fishing rod!");
                }
            }
        } else {
            debugLog("Minecart fishing disabled, using default behavior");
        }
    }
}
