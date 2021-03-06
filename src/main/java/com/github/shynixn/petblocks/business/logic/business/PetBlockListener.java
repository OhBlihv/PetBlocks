package com.github.shynixn.petblocks.business.logic.business;

import com.github.shynixn.petblocks.business.Config;
import com.github.shynixn.petblocks.business.logic.configuration.ConfigPet;
import com.github.shynixn.petblocks.lib.*;
import com.github.shynixn.petblocks.api.entities.PetBlock;
import com.github.shynixn.petblocks.business.bukkit.PetBlocksPlugin;
import com.github.shynixn.petblocks.business.bukkit.nms.NMSRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

class PetBlockListener extends BukkitEvents {
    private final PetBlockManager manager;
    private final List<PetBlock> jumped = new ArrayList<>();

    private boolean running;

    PetBlockListener(PetBlockManager manager, JavaPlugin plugin) {
        super(plugin);
        this.manager = manager;
        NMSRegistry.registerListener19(manager.carryingPet, plugin);
        this.run();
    }

    private void run() {
        if (!this.running) {
            this.running = true;
            this.getPlugin().getServer().getScheduler().scheduleSyncRepeatingTask(this.getPlugin(), new ParticleRunnable(), 0L, 60L);
            this.getPlugin().getServer().getScheduler().runTaskTimer(this.getPlugin(), new PetHunterRunnable(), 0L, 20);
        }
    }

    @EventHandler
    public void onEntityToggleSneakEvent(final PlayerToggleSneakEvent event) {
        final PetBlock petBlock;
        if (event.getPlayer().getPassenger() != null && this.isPet(event.getPlayer().getPassenger()) && (petBlock = this.manager.getPetBlock(event.getPlayer())) != null) {
            petBlock.eject(event.getPlayer());
        }
    }

    @EventHandler
    public void onEntityLeashEvent(PlayerLeashEntityEvent event) {
        if (this.isPet(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntityEvent(EntityDamageByEntityEvent event) {
        if (this.isPet(event.getDamager())) {
            final PetBlock petBlock = this.getPet(event.getDamager());
            if (petBlock != null && petBlock.getOwner() != null && petBlock.getOwner().equals(event.getEntity())) {
                event.setCancelled(true);
            }
        }
        if (ConfigPet.getInstance().isFleesInCombat()) {
            if (event.getDamager() instanceof Player && this.manager.hasPetBlock((Player) event.getDamager())) {
                this.manager.timeBlocked.put((Player) event.getDamager(), ConfigPet.getInstance().getReappearsInSeconds());
                this.manager.removePetBlock((Player) event.getDamager());
            } else if (event.getEntity() instanceof Player && this.manager.hasPetBlock((Player) event.getEntity())) {
                this.manager.timeBlocked.put((Player) event.getEntity(), ConfigPet.getInstance().getReappearsInSeconds());
                this.manager.removePetBlock((Player) event.getEntity());
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntityEvent(EntityInteractEvent event) {
        if (this.isPet(event.getEntity()) && event.getBlock().getType() == Material.SOIL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerTeleportEvent(final PlayerTeleportEvent event) {
        if (this.manager.hasPetBlock(event.getPlayer())) {
            if (!event.getTo().getWorld().getName().equals(event.getFrom().getWorld().getName())) {
                this.manager.removePetBlock(event.getPlayer());
                if (Config.getInstance().allowPetSpawning(event.getTo())) {
                    PetBlockListener.this.manager.setPetBlock(event.getPlayer(), PetBlockListener.this.manager.dataManager.getPetMeta(event.getPlayer()), ConfigPet.getInstance().getWarpDelay());
                }
            } else if (event.getPlayer().getPassenger() != null && this.isPet(event.getPlayer().getPassenger())) {
                if (!ConfigPet.getInstance().isFollow_fallOffHead()) {
                    final PetBlock petBlock = this.manager.getPetBlock(event.getPlayer());
                    if (petBlock != null)
                        petBlock.teleportWithOwner(event.getTo());
                    event.setCancelled(true);
                } else {
                    final PetBlock petBlock = this.manager.getPetBlock(event.getPlayer());
                    if (petBlock != null)
                        petBlock.eject(event.getPlayer());
                }
            }
        }
    }

    @EventHandler
    public void onPlayerRespawnEvent(final PlayerRespawnEvent event) {
        if (this.manager.hasPetBlock(event.getPlayer())) {
            this.manager.removePetBlock(event.getPlayer());
            this.getPlugin().getServer().getScheduler().runTaskLater(this.getPlugin(), new Runnable() {

                @Override
                public void run() {
                    PetBlockListener.this.manager.setPetBlock(event.getPlayer(), PetBlockListener.this.manager.dataManager.getPetMeta(event.getPlayer()), ConfigPet.getInstance().getWarpDelay());
                }
            }, 60L);
        }
    }

    @EventHandler
    public void entityUnknownRightClickEvent(final PlayerInteractAtEntityEvent event) {
        if (this.isDeadPet(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    private boolean isDeadPet(Entity entity) {
        if (entity instanceof ArmorStand && !this.isPet(entity)) {
            final ArmorStand stand = (ArmorStand) entity;
            final int xidentifier = (int) stand.getBodyPose().getZ();
            final int identifier = (int) stand.getRightArmPose().getX();
            final int lidentifier = (int) stand.getLeftArmPose().getX();
            if (xidentifier == 2877 && (identifier == 2877 || lidentifier == 2877)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityRightClickEvent(final PlayerInteractAtEntityEvent event) {
        if (this.manager.carryingPet.contains(event.getPlayer())) {
            if (!this.manager.hasPetBlock(event.getPlayer()))
                this.manager.setPetBlock(event.getPlayer(), this.manager.dataManager.getPetMeta(event.getPlayer()));
            Interpreter19.setItemInHand19(event.getPlayer(), null, true);
            this.manager.petblocks.remove(event.getPlayer());
            event.setCancelled(true);
        } else if (this.isPet(event.getRightClicked())) {
            final PetBlock petBlock = this.getPet(event.getRightClicked());
            if (petBlock != null && petBlock.getOwner().equals(event.getPlayer())) {
                if (Interpreter19.getItemInHand19(event.getPlayer(), false) != null && Interpreter19.getItemInHand19(event.getPlayer(), false).getType() == Material.CARROT_ITEM) {
                    ParticleEffect.HEART.display(1F, 1F, 1F, 0.1F, 20, event.getRightClicked().getLocation(), event.getRightClicked().getWorld().getPlayers());
                    new SoundData("EAT").play(event.getRightClicked().getLocation());
                    if (Interpreter19.getItemInHand19(event.getPlayer(), false).getAmount() == 1)
                        event.getPlayer().getInventory().setItem(event.getPlayer().getInventory().getHeldItemSlot(), new ItemStack(Material.AIR));
                    else
                        Interpreter19.getItemInHand19(event.getPlayer(), false).setAmount(Interpreter19.getItemInHand19(event.getPlayer(), false).getAmount() - 1);
                    if (!this.jumped.contains(petBlock)) {
                        this.getPlugin().getServer().getScheduler().runTaskLater(this.getPlugin(), new Runnable() {
                            @Override
                            public void run() {
                                PetBlockListener.this.jumped.remove(PetBlockListener.this.getPet(event.getRightClicked()));
                            }
                        }, 20L);
                        this.jumped.add(this.getPet(event.getRightClicked()));
                        petBlock.jump();
                    }
                }
                if (ConfigPet.getInstance().isFollow_carry() && (event.getPlayer().getInventory() == null || Interpreter19.getItemInHand19(event.getPlayer(), true).getType() == Material.AIR)) {
                    Interpreter19.setItemInHand19(event.getPlayer(), petBlock.getArmorStand().getHelmet().clone(), true);
                    this.manager.removePetBlock(event.getPlayer());
                    this.manager.carryingPet.add(event.getPlayer());
                }
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractEvent(PlayerInteractEvent event) {
        if (this.manager.carryingPet.contains(event.getPlayer())) {
            this.handleClickOnEntity(event);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerCommandEvent(PlayerCommandPreprocessEvent event) {
        if (this.manager.carryingPet.contains(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpenEvent(InventoryOpenEvent event) {
        final Player player = (Player) event.getPlayer();
        if (this.manager.carryingPet.contains(player)) {
            event.setCancelled(true);
            event.getPlayer().closeInventory();
        }
    }

    @EventHandler
    public void onPlayerEntityEvent(PlayerInteractEntityEvent event) {
        if (this.manager.carryingPet.contains(event.getPlayer())) {
            this.handleClickOnEntity(event);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeathEvent(PlayerDeathEvent event) {
        if (this.manager.carryingPet.contains(event.getEntity())) {
            if (!this.manager.hasPetBlock(event.getEntity()))
                this.manager.setPetBlock(event.getEntity(), this.manager.dataManager.getPetMeta(event.getEntity()));
            Interpreter19.setItemInHand19(event.getEntity(), null, true);
            this.manager.carryingPet.remove(event.getEntity());
        }
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        if (this.manager.carryingPet.contains(event.getPlayer())) {
            Interpreter19.setItemInHand19(event.getPlayer(), null, true);
            this.manager.carryingPet.remove(event.getPlayer());
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryClickEvent event) {
        final Player player = (Player) event.getWhoClicked();
        if (this.manager.carryingPet.contains(player)) {
            if (!this.manager.hasPetBlock((Player) event.getWhoClicked()))
                this.manager.setPetBlock((Player) event.getWhoClicked(), this.manager.dataManager.getPetMeta((Player) event.getWhoClicked()));
            Interpreter19.setItemInHand19((Player) event.getWhoClicked(), null, true);
            this.manager.carryingPet.remove(player);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (this.manager.carryingPet.contains(event.getPlayer())) {
            if (!this.manager.hasPetBlock(event.getPlayer()))
                this.manager.setPetBlock(event.getPlayer(), this.manager.dataManager.getPetMeta(event.getPlayer()));
            this.manager.carryingPet.remove(event.getPlayer());
            event.getItemDrop().remove();
            Interpreter19.setItemInHand19(event.getPlayer(), null, true);
        }
    }

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent event) {
        if (this.manager.carryingPet.contains(event.getPlayer())) {
            if (!this.manager.hasPetBlock(event.getPlayer()))
                this.manager.setPetBlock(event.getPlayer(), this.manager.dataManager.getPetMeta(event.getPlayer()));
            event.getPlayer().getInventory().setItem(event.getPreviousSlot(), null);
            this.manager.carryingPet.remove(event.getPlayer());
        }
    }

    @EventHandler
    public void entityDamageEvent(final EntityDamageEvent event) {
        if (this.isPet(event.getEntity())) {
            final PetBlock petBlock = this.getPet(event.getEntity());
            if (petBlock == null)
                return;
            if (event.getCause() != DamageCause.FALL)
                petBlock.damage(event.getFinalDamage());
            else if (event.getCause() == DamageCause.FALL)
                petBlock.damage(-2.0);
            event.setCancelled(true);
        }
    }

    private void handleClickOnEntity(PlayerEvent event) {
        if (!this.manager.hasPetBlock(event.getPlayer()))
            this.manager.setPetBlock(event.getPlayer(), this.manager.dataManager.getPetMeta(event.getPlayer()));
        Interpreter19.setItemInHand19(event.getPlayer(), null, true);
        this.manager.carryingPet.remove(event.getPlayer());
    }

    private PetBlock getPet(Entity entity) {
        try {
            for (final PetBlock block : this.manager.petblocks.values()) {
                if (block != null && entity != null && block.getArmorStand() != null && block.getMovementEntity() != null && (block.getArmorStand().equals(entity) || block.getMovementEntity().equals(entity)))
                    return block;
            }
        } catch (final Exception ignored) {
        }
        return null;
    }

    private boolean isPet(Entity entity) {
        return this.getPet(entity) != null;
    }

    private class ParticleRunnable implements Runnable {
        @Override
        public void run() {
            for (final Player player : PetBlockListener.this.manager.carryingPet.toArray(new Player[PetBlockListener.this.manager.carryingPet.size()])) {
                ParticleEffect.HEART.display(0.5F, 0.5F, 0.5F, 0.1F, 1, player.getLocation().add(0, 1, 0), player.getWorld().getPlayers());
            }
            for (final Player player : PetBlockListener.this.manager.petblocks.keySet().toArray(new Player[PetBlockListener.this.manager.petblocks.size()])) {
                if (PetBlockListener.this.manager.petblocks.get(player).isDead() || !Config.getInstance().allowPetSpawning(player.getLocation())) {
                    PetBlockListener.this.manager.removePetBlock(player);
                    if (player.isOnline() && Config.getInstance().allowPetSpawning(player.getLocation()))
                        PetBlockListener.this.manager.setPetBlock(player, PetBlockListener.this.manager.dataManager.getPetMeta(player));
                }
            }
        }
    }

    private class PetHunterRunnable implements Runnable {
        @Override
        public void run() {
            for (final Player player : PetBlockListener.this.manager.timeBlocked.keySet().toArray(new Player[PetBlockListener.this.manager.timeBlocked.size()])) {
                PetBlockListener.this.manager.timeBlocked.put(player, PetBlockListener.this.manager.timeBlocked.get(player) - 1);
                if (PetBlockListener.this.manager.timeBlocked.get(player) <= 0) {
                    PetBlockListener.this.manager.timeBlocked.remove(player);
                    PetBlockListener.this.manager.setPetBlock(player, PetBlockListener.this.manager.dataManager.getPetMeta(player));
                }
            }
            int counter = 0;
            for (final World world : Bukkit.getWorlds()) {
                for (final Entity entity : world.getEntities()) {
                    if (entity instanceof ArmorStand && PetBlockListener.this.isDeadPet(entity)) {
                        entity.remove();
                        counter++;
                    } else if (!PetBlockListener.this.isPet(entity) && entity.getCustomName() != null && entity.getCustomName().equals("PetBlockIdentifier")) {
                        entity.remove();
                        counter++;
                    }
                }
            }
            if (counter == 1)
                BukkitUtilities.sendColorMessage("PetHunter " + ChatColor.GREEN + ">" + ChatColor.YELLOW + " Removed " + counter + " pet.", ChatColor.YELLOW, PetBlocksPlugin.PREFIX_CONSOLE);
            else if (counter > 0)
                BukkitUtilities.sendColorMessage("PetHunter " + ChatColor.GREEN + ">" + ChatColor.YELLOW + " Removed " + counter + " pet.", ChatColor.YELLOW, PetBlocksPlugin.PREFIX_CONSOLE);
        }
    }
}
