package com.enderdevelopment.plugin.boss;

import com.enderdevelopment.plugin.boss.ValstraxBoss.State;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * All signature Crimson Glow Valstrax attacks, implemented as scheduled
 * manoeuvres driven from the main thread. Each attack manages its own
 * sub-task timers through the parent boss (so {@code /valstrax kill} cancels
 * them cleanly).
 */
public class BossAttacks {

    private final ValstraxBoss boss;

    public BossAttacks(ValstraxBoss boss) {
        this.boss = boss;
    }

    void roar() {
        LivingEntity e = boss.getEntity();
        if (e != null) {
            e.getWorld().playSound(e.getLocation(), boss.getConfig().getRoarSound(), 3.0f, 0.6f);
        }
    }

    /** Opening roar with knockback and a crimson reveal burst. */
    void performRoar() {
        LivingEntity e = boss.getEntity();
        if (e == null) return;
        boss.setState(State.CHARGE);
        roar();
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 0.8f);
        boss.spawnBurst(e.getLocation(), 4);
        boss.runLater(() -> boss.setState(State.HOVER), 30L);
    }

    /** Chest glows and charges crimson energy, then finishes with a dive. */
    void doAirCharge() {
        LivingEntity e = boss.getEntity();
        if (e == null) return;
        boss.setState(State.CHARGE);
        roar();
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2.0f, 0.5f);
        e.getWorld().playSound(e.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 2.0f, 0.3f);

        final int[] step = {0};
        BukkitTask t = new BukkitRunnable() {
            double radius = 1.5;
            @Override
            public void run() {
                step[0]++;
                if (!boss.isSpawned()) { cancel(); return; }
                radius += 0.35;
                Location loc = boss.getEntity().getLocation();
                int arms = 14;
                for (int i = 0; i < arms; i++) {
                    double angle = 2 * Math.PI * i / arms + step[0] * 0.2;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location p = loc.clone().add(x, 0.5, z);
                    loc.getWorld().spawnParticle(boss.getConfig().getEnergyParticle(), p, 1, 0.1, 0.4, 0.1, 0.0);
                }
                if (step[0] >= 50) {
                    cancel();
                    performDive();
                }
            }
        }.runTaskTimer(boss.getPlugin(), 0L, 1L);
        boss.getSubTasks().add(t);
    }

    /** The iconic supersonic dive: fast forward thrust with damage along the line. */
    void doDive() {
        LivingEntity e = boss.getEntity();
        if (e == null) return;
        boss.setState(State.DIVE);
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 3.0f, 0.4f);
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 3.0f, 0.9f);
        performDive();
    }

    void performDive() {
        LivingEntity e = boss.getEntity();
        if (e == null || boss.getTarget() == null) return;
        boss.setState(State.DIVE);
        Player target = boss.getTarget();
        Vector aim = target.getLocation().toVector().subtract(e.getLocation().toVector());
        if (aim.lengthSquared() < 0.001) aim = new Vector(1, 0, 0);
        aim.normalize();
        final Vector dashDir = aim.clone();

        boss.runLater(() -> {
            if (!boss.isSpawned()) return;
            LivingEntity ent = boss.getEntity();
            ent.setVelocity(dashDir.clone().multiply(3.0));
            ent.getWorld().playSound(ent.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 1.4f);
            BukkitTask check = new BukkitRunnable() {
                int iters = 0;
                final Vector dir = dashDir.clone().multiply(2.2);
                @Override
                public void run() {
                    iters++;
                    if (!boss.isSpawned() || iters > 40) { cancel(); return; }
                    LivingEntity ent2 = boss.getEntity();
                    ent2.setVelocity(dir);
                    boss.spawnEnergyTrail(ent2.getLocation(), 6);
                    // Damage anyone near the dive line.
                    for (Player p : boss.nearbyPlayers()) {
                        if (p.getLocation().distanceSquared(ent2.getLocation()) < 3.5 * 3.5 && !noContact(p)) {
                            p.damage(boss.getConfig().getDiveDamage() * damageMult(p));
                            p.setVelocity(dir.clone().multiply(0.6));
                        }
                    }
                }
            }.runTaskTimer(boss.getPlugin(), 0L, 1L);
            boss.getSubTasks().add(check);

            // Impact at the end of the dive.
            boss.runLater(() -> {
                if (!boss.isSpawned()) return;
                LivingEntity ent3 = boss.getEntity();
                boss.spawnBurst(ent3.getLocation(), 6);
                ent3.getWorld().playSound(ent3.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
                for (Player p : boss.nearbyPlayers()) {
                    if (p.getLocation().distanceSquared(ent3.getLocation()) < 8.0 * 8.0 && !noContact(p)) {
                        p.damage(boss.getConfig().getDiveDamage() * 0.7 * damageMult(p));
                    }
                }
                boss.setState(State.ROAM);
            }, 40L);
        }, 20L);
    }

    /** Volley of dragon energy projectiles. */
    void doBlast() {
        LivingEntity e = boss.getEntity();
        if (e == null || boss.getTarget() == null) return;
        boss.setState(State.BLAST);
        roar();
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 2.0f, 0.8f);
        Player target = boss.getTarget();

        final int[] shot = {0};
        BukkitTask t = new BukkitRunnable() {
            @Override
            public void run() {
                if (!boss.isSpawned() || shot[0] >= 5) { cancel(); return; }
                shot[0]++;
                LivingEntity ent = boss.getEntity();
                if (ent == null) return;
                Location origin = ent.getLocation().clone().add(0, 1.2, 0);
                Vector dir = boss.getTarget().getLocation().add(0, 1, 0).toVector()
                        .subtract(origin.toVector()).normalize().multiply(2.0 + shot[0] * 0.15);
                spawnFireball(origin, dir);
            }
        }.runTaskTimer(boss.getPlugin(), 0L, 12L);
        boss.getSubTasks().add(t);
    }

    private void spawnFireball(Location origin, Vector dir) {
        // A quick travel: burst of particles moving along dir landing where it will.
        final int[] step = {0};
        final Vector stepDir = dir.clone().multiply(0.8);
        BukkitTask t = new BukkitRunnable() {
            @Override
            public void run() {
                step[0]++;
                if (step[0] > 30) { cancel(); impactFireball(origin.clone().add(stepDir.clone().multiply(30))); return; }
                Location p = origin.clone().add(stepDir.clone().multiply(step[0]));
                p.getWorld().spawnParticle(Particle.FLAME, p, 3, 0.2, 0.2, 0.2, 0.0);
                p.getWorld().spawnParticle(boss.getConfig().getEnergyParticle(), p, 1, 0.15, 0.15, 0.15, 0.0);
                origin.getWorld().playSound(p, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.4f);
                boss.getEntity().getWorld().spawnParticle(Particle.SMOKE, p, 1, 0.1, 0.1, 0.1, 0.0);
            }
        }.runTaskTimer(boss.getPlugin(), 0L, 1L);
        boss.getSubTasks().add(t);
    }

    private void impactFireball(Location loc) {
        boss.spawnBurst(loc, 3);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.7f);
        for (Player p : boss.nearbyPlayers()) {
            if (p.getLocation().distanceSquared(loc) < 6.0 * 6.0 && !noContact(p)) {
                p.damage(boss.getConfig().getBlastDamage() * damageMult(p));
                p.setFireTicks(80);
            }
        }
    }

    /** Ground pound: lands and throws a shockwave ring. */
    void doPound() {
        LivingEntity e = boss.getEntity();
        if (e == null) return;
        boss.setState(State.POUND);
        roar();
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.8f);

        boss.getSubTasks().add(new BukkitRunnable() {
            @Override
            public void run() {
                if (!boss.isSpawned()) return;
                LivingEntity ent = boss.getEntity();
                ent.setVelocity(new Vector(0, -1.4, 0));
                if (ent.isOnGround()) {
                    boss.spawnBurst(ent.getLocation(), 8);
                    ent.getWorld().playSound(ent.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.6f);
                    shockwave(ent.getLocation(), 1.0);
                    cancel();
                    boss.setState(State.ROAM);
                }
            }
        }.runTaskTimer(boss.getPlugin(), 0L, 2L));
    }

    private void shockwave(Location origin, double mult) {
        // Expanding ring of energy dealing pound damage outwards.
        double r = boss.getConfig().getPoundDamage() * mult;
        for (Player p : boss.nearbyPlayers()) {
            double dist = p.getLocation().distance(origin);
            if (dist < 9.0 && !noContact(p)) {
                p.damage(r * (1.0 - dist / 18.0));
                Vector away = p.getLocation().toVector().subtract(origin.toVector());
                away.setY(0.4);
                if (away.lengthSquared() > 0.001) p.setVelocity(away.normalize().multiply(1.6));
            }
        }
        // Visual ring.
        for (int ring = 0; ring < 4; ring++) {
            double radius = 2.0 + ring * 2.0;
            for (int i = 0; i < 24; i++) {
                double a = 2 * Math.PI * i / 24;
                Location p = origin.clone().add(Math.cos(a) * radius, 0.2, Math.sin(a) * radius);
                p.getWorld().spawnParticle(boss.getConfig().getEnergyParticle(), p, 1, 0.1, 0.1, 0.1, 0.0);
            }
        }
    }

    /** Tails spin, dealing wide arc damage. */
    void doTailSpin() {
        LivingEntity e = boss.getEntity();
        if (e == null) return;
        boss.setState(State.TAIL_SPIN);
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 1.1f);
        boss.getSubTasks().add(new BukkitRunnable() {
            int spin = 0;
            @Override
            public void run() {
                spin++;
                if (!boss.isSpawned() || spin > 24) { cancel(); boss.setState(State.ROAM); return; }
                LivingEntity ent = boss.getEntity();
                Location loc = ent.getLocation();
                // Swing arc particles
                double a = spin * 0.5;
                for (int i = 0; i < 6; i++) {
                    double ang = a + i * 0.35;
                    double radius = 3.0;
                    Location p = loc.clone().add(Math.cos(ang) * radius, 1.0, Math.sin(ang) * radius);
                    p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p, 1, 0.2, 0.1, 0.2, 0.0);
                }
                // Damage in a radius around the boss during spin.
                if (spin % 2 == 0) {
                    for (Player p : boss.nearbyPlayers()) {
                        if (p.getLocation().distanceSquared(loc) < 4.5 * 4.5 && !noContact(p)) {
                            p.damage(boss.getConfig().getTailDamage() * damageMult(p));
                            Vector away = p.getLocation().toVector().subtract(loc.toVector());
                            away.setY(0.5);
                            if (away.lengthSquared() > 0.001) p.setVelocity(away.normalize().multiply(1.2));
                        }
                    }
                }
            }
        }.runTaskTimer(boss.getPlugin(), 0L, 1L));
    }

    /** Delayed ambush dive: dives, then a delayed explosion follows. */
    void doAmbush() {
        LivingEntity e = boss.getEntity();
        if (e == null) return;
        boss.setState(State.AMBUSH);
        roar();
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 1.2f);
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2.5f, 0.3f);

        // Telegraph with rising particles above the target.
        boss.getSubTasks().add(new BukkitRunnable() {
            int step = 0;
            final Player t = boss.getTarget();
            @Override
            public void run() {
                step++;
                if (!boss.isSpawned() || step > 30 || t == null || !t.isOnline()) { cancel(); return; }
                Location base = t.getLocation().clone().add(0, 2.0, 0);
                base.getWorld().spawnParticle(boss.getConfig().getEnergyParticle(), base,
                        step % 3 == 0 ? 4 : 1, 0.6, 0.3, 0.6, 0.0);
            }
        }.runTaskTimer(boss.getPlugin(), 0L, 1L));

        // Then the boss dives up and crashes down on the target.
        boss.runLater(() -> {
            if (!boss.isSpawned() || boss.getTarget() == null) return;
            LivingEntity ent = boss.getEntity();
            Player tgt = boss.getTarget();
            // Rocket upward first.
            ent.setVelocity(new Vector(0, 2.4, 0));
            ent.getWorld().playSound(ent.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 3.0f, 0.3f);
            boss.runLater(() -> {
                if (!boss.isSpawned()) return;
                LivingEntity ent2 = boss.getEntity();
                Vector drop = tgt.getLocation().toVector().subtract(ent2.getLocation().toVector());
                drop.setY(0);
                if (drop.lengthSquared() > 0.001) ent2.setVelocity(drop.normalize().multiply(3.0).add(new Vector(0, -2.0, 0)));
                boss.spawnEnergyTrail(ent2.getLocation(), 8);
            }, 20L);
        }, 34L);

        // Delayed explosion at the target's marked spot.
        boss.runLater(() -> {
            if (!boss.isSpawned() || boss.getTarget() == null) return;
            Player tgt = boss.getTarget();
            Location spot = tgt.getLocation().clone();
            boss.spawnBurst(spot, 8);
            spot.getWorld().playSound(spot, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.4f);
            for (Player p : boss.nearbyPlayers()) {
                if (p.getLocation().distanceSquared(spot) < 7.0 * 7.0 && !noContact(p)) {
                    p.damage(boss.getConfig().getDiveDamage() * 0.8 * damageMult(p));
                    p.setFireTicks(100);
                    Vector away = p.getLocation().toVector().subtract(spot.toVector());
                    away.setY(0.8);
                    if (away.lengthSquared() > 0.001) p.setVelocity(away.normalize().multiply(1.5));
                }
            }
            boss.setState(State.ROAM);
        }, 60L);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean noContact(Player p) {
        return p.isDead() || !p.isOnline() || p.hasMetadata("valstrax_invuln");
    }

    private double damageMult(Player p) {
        double mult = 1.0;
        if (p.hasPotionEffect(PotionEffectType.RESISTANCE)) {
            mult *= 0.6;
        }
        return mult;
    }
}
