package com.mootcomb.griefallow;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {

    private boolean debug;
    private boolean enableTnt;
    private boolean tntChainReaction;
    private boolean enablePistons;
    private boolean enableWither;
    private boolean enableSand;
    private boolean enableMinecart;

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
                    ", enableMinecart=" + enableMinecart);
        } else {
            getLogger().info("GriefAllow enabled!");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("GriefAllow disabled!");
    }

    private void loadConfigValues() {
        // Debug mode - default false if not present
        debug = getConfig().getBoolean("debug", false);

        // TNT settings - default false if not present
        enableTnt = getConfig().getBoolean("enable-tnt", false);
        tntChainReaction = getConfig().getBoolean("tnt-chain-reaction", false);

        // Piston settings - default false if not present
        enablePistons = getConfig().getBoolean("enable-pistons", false);

        // Wither settings - default false if not present
        enableWither = getConfig().getBoolean("enable-wither", false);

        // Sand/Gravel settings - default false if not present
        enableSand = getConfig().getBoolean("enable-sand", false);

        // Minecart Hopper settings - default false if not present
        enableMinecart = getConfig().getBoolean("enable-minecart", false);
    }

    private void debugLog(String message) {
        if (debug) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    private void infoLog(String message) {
        if (debug) {
            getLogger().info(message);
        }
    }

    // ==================== TNT Explosion ====================
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
                        // Chain reaction ON: ignite TNT
                        block.setType(Material.AIR);
                        TNTPrimed tnt = block.getWorld().spawn(block.getLocation(), TNTPrimed.class);
                        tnt.setFuseTicks(80); // 4 seconds
                        debugLog("TNT chain reaction ignited at " +
                                block.getX() + ", " + block.getY() + ", " + block.getZ());
                    } else {
                        // Chain reaction OFF: drop as item
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

    // ==================== TNT Ignite ====================
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

    // ==================== Flint Interaction ====================
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

    // ==================== Fire Arrow ====================
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

                // УДАЛЯЕМ блок
                tntBlock.setType(Material.AIR);

                // ИСПРАВЛЕНИЕ: Спавним TNT точно в центре блока + небольшое смещение
                org.bukkit.Location loc = tntBlock.getLocation().add(0.5, 0.5, 0.5);
                TNTPrimed tnt = tntBlock.getWorld().spawn(loc, TNTPrimed.class);
                tnt.setFuseTicks(80);

                // ДОПОЛНИТЕЛЬНО: Устанавливаем velocity чтобы TNT не двигался
                tnt.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

                debugLog("Fire arrow hit TNT at " + coords);
                infoLog("TNT ignited by fire arrow at " + coords);

                arrow.remove();
            }
        }
    }

    // ==================== Pistons ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        debugLog("onPistonExtend called");

        if (enablePistons) {
            event.setCancelled(false);
            debugLog("Piston extension allowed");
        } else {
            debugLog("Pistons disabled, cancelling");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        debugLog("onPistonRetract called");

        if (enablePistons) {
            event.setCancelled(false);
            debugLog("Piston retraction allowed");
        } else {
            debugLog("Pistons disabled, cancelling");
            event.setCancelled(true);
        }
    }

    // ==================== Wither Break ====================
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

    // ==================== Wither Damage ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWitherDamage(EntityDamageByBlockEvent event) {
        debugLog("onWitherDamage called");

        if (enableWither) {
            event.setCancelled(false);
            debugLog("Wither damage allowed");
        } else {
            debugLog("Wither damage disabled");
            event.setCancelled(true);
        }
    }

    // ==================== Sand/Gravel Fall ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGravityFall(EntityChangeBlockEvent event) {
        debugLog("onGravityFall called - Block type: " + event.getBlock().getType());

        if (enableSand) {
            Material type = event.getBlock().getType();
            if (type == Material.SAND || type == Material.GRAVEL || type == Material.ANVIL) {
                event.setCancelled(false);
                debugLog("Gravity block falling allowed: " + type);
            } else {
                // Для других блоков - разрешаем
                event.setCancelled(false);
            }
        } else {
            debugLog("Gravity blocks falling disabled");
            event.setCancelled(true);
        }
    }

    // ==================== Minecart Hopper ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        debugLog("onInventoryMove called");

        if (enableMinecart) {
            event.setCancelled(false);
            debugLog("Minecart hopper item movement allowed");
            infoLog("Minecart hopper working!");
        } else {
            debugLog("Minecart hopper disabled");
            event.setCancelled(true);
        }
    }
}
