package com.enderdevelopment.plugin.boss;

import com.enderdevelopment.plugin.Main;
import com.enderdevelopment.plugin.config.BossConfig;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Sound;

/**
 * Listens for events that involve the Valstrax entity: damage, death (with
 * loot drops and a victory message), and invulnerability of the boss to
 * fall/void damage while airborne.
 */
public class BossListener implements Listener {

    private final Main plugin;
    private final ValstraxBoss boss;
    private final Random random = new Random();

    public BossListener(Main plugin, ValstraxBoss boss) {
        this.plugin = plugin;
        this.boss = boss;
    }

    @EventHandler
    public void onBossDamaged(EntityDamageByEntityEvent event) {
        if (!isBoss(event.getEntity())) {
            return;
        }
        // The boss doesn't hurt itself or die from environment damage in the air.
        if (event.getEntity().equals(event.getDamager())) {
            event.setCancelled(true);
        }
        // Let the boss take damage from players (and bosses) but not from projectiles
        // that are friendly; we simply allow all weapon damage.
        // A small crimson flash whenever the boss is struck.
        event.getEntity().getWorld().playSound(event.getEntity().getLocation(),
                Sound.ENTITY_ENDER_DRAGON_HURT, 1.2f, 1.3f);
        boss.spawnEnergyTrail(event.getEntity().getLocation(), 3);
    }

    @EventHandler
    public void onBossEnvironmentDamage(EntityDamageEvent event) {
        if (!isBoss(event.getEntity())) {
            return;
        }
        // Prevent pointlessly dying to fall damage while holding altitude, and
        // avoid the dragon suffocating while it flies through blocks.
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION
                || event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if (!isBoss(event.getEntity())) {
            return;
        }
        boss.stopMusic();
        boss.getSubTasks().forEach(t -> t.cancel());

        // Clear the auto drops (we control the loot table).
        event.getDrops().clear();
        Location loc = event.getEntity().getLocation();

        // Victory message.
        plugin.getServer().broadcastMessage(ChatColor.DARK_RED
                + "The Crimson Glow Valstrax falls! Its crimson light fades at last.");
        plugin.getServer().broadcastMessage(ChatColor.GOLD
                + "Quest complete: The Gossamer Blade that Descends upon the World.");
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_DEATH, 3.0f, 0.8f);
        boss.spawnBurst(loc, 20);

        // Drops from config.
        for (BossConfig.Drop drop : plugin.getBossConfig().getDrops()) {
            int amount = drop.amount;
            // drop in clusters
            while (amount > 0) {
                int give = Math.min(amount, 8);
                ItemStack item = new ItemStack(drop.material, give);
                loc.getWorld().dropItemNaturally(loc, item);
                amount -= give;
            }
        }
        // Bonus chance for a cook prize.
        if (random.nextInt(100) < 15) {
            loc.getWorld().dropItemNaturally(loc, new ItemStack(
                    org.bukkit.Material.NETHERITE_SCRAP, 1));
            plugin.getServer().broadcastMessage(ChatColor.LIGHT_PURPLE
                    + "A rare crimson material was carved from the fallen dragon!");
        }

        boss.despawn();
    }

    private boolean isBoss(Entity entity) {
        return boss.isSpawned() && boss.getEntity() != null && boss.getEntity().equals(entity);
    }
}
