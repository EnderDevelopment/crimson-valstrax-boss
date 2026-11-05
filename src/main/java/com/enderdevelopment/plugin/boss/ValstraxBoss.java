package com.enderdevelopment.plugin.boss;

import com.enderdevelopment.plugin.config.BossConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * The Crimson Glow Valstrax boss controller. Spawns a hostile entity, drives it
 * with a state machine and a boss bar, and delegates its signature Sunbreak
 * attacks to {@link BossAttacks}.
 */
public class ValstraxBoss {

    public enum State {
        IDLE, ROAM, ROAR, CHARGE, HOVER, DIVE, WING_SLAM, BLAST, POUND, TAIL_SPIN, AMBUSH, ENRAGED
    }

    enum AttackKind {
        DIVE, AIR_CHARGE, BLAST, POUND, TAIL_SPIN, AMBUSH
    }

    private final Plugin plugin;
    private final BossConfig config;
    private final BossMusic music;
    private final BossAttacks attacks;

    private LivingEntity entity;
    private Player target;
    private State state = State.IDLE;
    private boolean fighting = false;
    private boolean enraged = false;

    private final BossBar bar;
    private BukkitTask loopTask;
    private BukkitTask movementTask;
    private final double maxHealth;
    private int manoeuvreTicks;
    private final List<BukkitTask> subTasks = new ArrayList<>();

    public ValstraxBoss(Plugin plugin, BossConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.maxHealth = config.getMaxHealth();
        this.bar = Bukkit.createBossBar(config.getName(), BarColor.RED, BarStyle.SEGMENTED_12);
        this.bar.setVisible(false);
        this.music = new BossMusic(plugin, config);
        this.attacks = new BossAttacks(this);
    }

    public void spawn(Location location) {
        despawn();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        entity = (LivingEntity) world.spawnEntity(location, config.getBaseEntity());
        entity.setCustomName(config.getName());
        entity.setCustomNameVisible(true);
        entity.setMaxHealth(maxHealth);
        entity.setHealth(maxHealth);
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);

        bar.setVisible(true);
        bar.setProgress(1.0);
        bar.setColor(BarColor.RED);
        bar.setStyle(BarStyle.SEGMENTED_12);

        startLoop();
        startMovement();
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "A crimson glint is spotted on the horizon...");
        Bukkit.broadcastMessage(ChatColor.RED + config.getName() + " has taken to the skies! Hunt it down!");
    }

    public void despawn() {
        stopFight();
        stopMusic();
        if (entity != null) {
            entity.remove();
            entity = null;
        }
        bar.setVisible(false);
        bar.removeAll();
        state = State.IDLE;
        fighting = false;
        target = null;
        enraged = false;
    }

    /** Stops all scheduled tasks but keeps the entity alive. */
    public void stopFight() {
        if (loopTask != null) { loopTask.cancel(); loopTask = null; }
        if (movementTask != null) { movementTask.cancel(); movementTask = null; }
        cancelSubTasks();
        fighting = false;
        bar.setVisible(false);
        bar.removeAll();
    }

    public void stopMusic() { music.stop(); }
    public boolean isSpawned() { return entity != null && !entity.isDead(); }
    public boolean isFighting() { return fighting; }
    public boolean isEnraged() { return enraged; }
    public LivingEntity getEntity() { return entity; }
    public double getMaxHealth() { return maxHealth; }
    public Plugin getPlugin() { return plugin; }
    public BossConfig getConfig() { return config; }
    public Player getTarget() { return target; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public void startMusic() { music.start(entity.getLocation(), config.getLeashRadius()); }
    public List<BukkitTask> getSubTasks() { return subTasks; }

    void cancelSubTasks() {
        for (BukkitTask t : subTasks) { t.cancel(); }
        subTasks.clear();
    }

    // ------------------------------------------------------------------
    // Loop
    // ------------------------------------------------------------------

    private void startLoop() {
        loopTask = new BukkitRunnable() {
            @Override public void run() { if (isSpawned()) tickFight(); }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void startMovement() {
        movementTask = new BukkitRunnable() {
            @Override public void run() { if (isSpawned() && fighting) moveEntity(); }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    List<Player> nearbyPlayers() {
        List<Player> list = new ArrayList<>();
        if (entity == null || entity.getWorld() == null) return list;
        double r2 = config.getLeashRadius() * config.getLeashRadius();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(entity.getWorld())
                    && p.getLocation().distanceSquared(entity.getLocation()) <= r2) {
                list.add(p);
            }
        }
        return list;
    }

    private void tickFight() {
        List<Player> players = nearbyPlayers();
        if (players.isEmpty()) { state = State.IDLE; return; }
        target = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        updateBossBar(players);

        if (!fighting) {
            fighting = true;
            state = State.CHARGE;
            attacks.performRoar();
            startMusic();
            return;
        }
        if (state == State.ROAR || state == State.CHARGE || state == State.AMBUSH) {
            return; // mid-manoeuvre
        }
        if (state == State.ROAM || state == State.IDLE || state == State.HOVER) {
            beginNextAttack();
        }
    }

    private void beginNextAttack() {
        int cycle = enraged ? config.getEnrageCycleSeconds() : config.getAttackCycleSeconds();
        if (manoeuvreTicks > 0) { manoeuvreTicks--; state = State.HOVER; return; }
        manoeuvreTicks = cycle * 20;

        AttackKind[] pool;
        if (enraged) {
            pool = new AttackKind[]{AttackKind.AIR_CHARGE, AttackKind.DIVE, AttackKind.BLAST,
                    AttackKind.POUND, AttackKind.TAIL_SPIN, AttackKind.AMBUSH, AttackKind.DIVE};
        } else {
            pool = new AttackKind[]{AttackKind.AIR_CHARGE, AttackKind.DIVE, AttackKind.BLAST,
                    AttackKind.POUND, AttackKind.TAIL_SPIN, AttackKind.AMBUSH};
        }
        AttackKind kind = pool[ThreadLocalRandom.current().nextInt(pool.length)];
        switch (kind) {
            case AIR_CHARGE -> attacks.doAirCharge();
            case DIVE -> attacks.doDive();
            case BLAST -> attacks.doBlast();
            case POUND -> attacks.doPound();
            case TAIL_SPIN -> attacks.doTailSpin();
            case AMBUSH -> attacks.doAmbush();
        }
    }

    private void updateBossBar(List<Player> players) {
        if (!bar.isVisible()) bar.setVisible(true);
        for (Player p : players) { if (!bar.getPlayers().contains(p)) bar.addPlayer(p); }
        List<Player> toRemove = new ArrayList<>(bar.getPlayers());
        toRemove.removeAll(players);
        for (Player p : toRemove) bar.removePlayer(p);
        bar.setProgress(Math.max(0.0, Math.min(1.0, entity.getHealth() / maxHealth)));
        if (!enraged && entity.getHealth() / maxHealth <= config.getEnrageThreshold()) {
            setEnraged(true);
        }
    }

    void setEnraged(boolean value) {
        if (enraged == value) return;
        enraged = value;
        if (value) {
            bar.setColor(BarColor.PURPLE);
            bar.setTitle(config.getName() + " (ENRAGED)");
            Bukkit.broadcastMessage(ChatColor.RED + config.getName() + "'s chest burns with an intense crimson light!");
            attacks.roar();
        } else {
            bar.setColor(BarColor.RED);
            bar.setTitle(config.getName());
        }
    }

    // ------------------------------------------------------------------
    // Movement
    // ------------------------------------------------------------------

    private void moveEntity() {
        if (entity == null || target == null || !target.isOnline()) return;
        Location loc = entity.getLocation();
        Location goal = target.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        if (loc.distanceSquared(goal) > config.getLeashRadius() * config.getLeashRadius()) {
            Vector pull = loc.toVector().subtract(goal.toVector()).normalize().multiply(-1.5);
            entity.setVelocity(pull);
            return;
        }
        double speed = 0.5;
        if (enraged) speed *= config.getEnrageSpeed();

        if (state == State.DIVE) {
            Location ahead = target.getLocation().clone().add(target.getLocation().getDirection().multiply(3));
            Vector dir = ahead.toVector().subtract(loc.toVector()).normalize().multiply(2.4);
            entity.setVelocity(dir);
            spawnEnergyTrail(loc, 6);
            return;
        }

        if (state == State.ROAM || state == State.HOVER || state == State.IDLE) {
            Vector toward = goal.toVector().subtract(loc.toVector());
            double hoverY = Math.max(goal.getY() + 4.0, world.getHighestBlockYAt(goal) + 6.0);
            Vector horiz = new Vector(toward.getX(), 0, toward.getZ());
            Vector dir = new Vector(0, 0, 0);
            if (horiz.lengthSquared() > 0.001) dir = horiz.normalize().multiply(speed * 0.7d);
            Vector toHover = new Vector(0, hoverY - loc.getY(), 0);
            if (toHover.lengthSquared() > 0.001) dir.add(toHover.normalize().multiply(speed * 1.2d));
            double drift = Math.sin(entity.getTicksLived() * 0.05) * 0.3;
            entity.setVelocity(dir.add(new Vector(0, drift, 0)));
            spawnAmbientParticles(loc, 2);
        }
    }

    // ------------------------------------------------------------------
    // Particle helpers (used by attacks too)
    // ------------------------------------------------------------------

    public void spawnEnergyTrail(Location loc, int count) {
        Particle p = config.getEnergyParticle();
        for (int i = 0; i < count; i++) {
            loc.getWorld().spawnParticle(p, loc, 1, 0.2, 0.2, 0.2, 0.0);
        }
    }

    public void spawnAmbientParticles(Location loc, int count) {
        Location base = loc.clone().add(0, 1.2, 0);
        loc.getWorld().spawnParticle(config.getEnergyParticle(), base, count, 0.6, 0.4, 0.6, 0.0);
    }

    public void spawnBurst(Location loc, int count) {
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, count, 0.5, 0.5, 0.5, 0.0);
        loc.getWorld().spawnParticle(config.getEnergyParticle(), loc, count * 2, 0.6, 0.6, 0.6, 0.0);
    }

    public void runLater(Runnable run, long delayTicks) {
        BukkitTask t = new BukkitRunnable() {
            @Override public void run() { run.run(); }
        }.runTaskLater(plugin, delayTicks);
        subTasks.add(t);
    }
}
