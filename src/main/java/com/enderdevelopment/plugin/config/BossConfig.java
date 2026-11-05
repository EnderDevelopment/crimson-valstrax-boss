package com.enderdevelopment.plugin.config;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

/**
 * Typed wrapper over the plugin config.yml so the rest of the plugin never
 * touches raw YAML strings.
 */
public class BossConfig {

    public static final class Drop {
        public final Material material;
        public final int amount;

        public Drop(Material material, int amount) {
            this.material = material;
            this.amount = amount;
        }
    }

    private final double maxHealth;
    private final EntityType baseEntity;
    private final String name;
    private final double aggroRadius;
    private final double leashRadius;
    private final int attackCycleSeconds;
    private final double wingDamage;
    private final double diveDamage;
    private final double blastDamage;
    private final double poundDamage;
    private final double tailDamage;
    private final double touchDamage;
    private final boolean playTheme;
    private final Sound roarSound;
    private final Particle energyParticle;
    private final List<Drop> drops;
    private final Sound themeInstrument;
    private final double enrageThreshold;
    private final double enrageSpeed;
    private final int enrageCycleSeconds;

    public BossConfig(FileConfiguration c) {
        ConfigurationSection boss = c.getConfigurationSection("boss");
        ConfigurationSection enrage = c.getConfigurationSection("enrage");

        this.maxHealth = boss.getDouble("max-health", 2000.0);
        this.name = boss.getString("name", "Crimson Glow Valstrax");
        this.aggroRadius = boss.getDouble("aggro-radius", 48.0);
        this.leashRadius = boss.getDouble("leash-radius", 64.0);
        this.attackCycleSeconds = boss.getInt("attack-cycle-seconds", 12);
        this.wingDamage = boss.getDouble("wing-damage", 8.0);
        this.diveDamage = boss.getDouble("dive-damage", 16.0);
        this.blastDamage = boss.getDouble("blast-damage", 10.0);
        this.poundDamage = boss.getDouble("pound-damage", 12.0);
        this.tailDamage = boss.getDouble("tail-damage", 7.0);
        this.touchDamage = boss.getDouble("touch-damage", 4.0);
        this.playTheme = boss.getBoolean("play-theme", true);

        this.baseEntity = parseEntityType(boss.getString("base-entity", "ELDER_GUARDIAN"));
        this.roarSound = parseSound(boss.getString("roar-sound", "ENTITY_ENDER_DRAGON_GROWL"));
        this.energyParticle = parseParticle(boss.getString("energy-particle", "DRAGON_BREATH"));
        this.themeInstrument = parseSound(boss.getString("theme-instrument", "MUSIC_DISC_PIGSTEP"));
        this.drops = parseDrops(boss.getStringList("drops"));

        this.enrageThreshold = enrage.getDouble("threshold", 0.30);
        this.enrageSpeed = enrage.getDouble("speed-multiplier", 1.6);
        this.enrageCycleSeconds = enrage.getInt("attack-cycle-seconds", 7);
    }

    private static EntityType parseEntityType(String s) {
        try {
            return EntityType.valueOf(s.toUpperCase());
        } catch (Exception e) {
            return EntityType.ELDER_GUARDIAN;
        }
    }

    private static Sound parseSound(String s) {
        try {
            return Sound.valueOf(s.toUpperCase());
        } catch (Exception e) {
            Bukkit.getLogger().warning("Unknown sound in config: " + s);
            return Sound.ENTITY_ENDER_DRAGON_GROWL;
        }
    }

    private static Particle parseParticle(String s) {
        try {
            return Particle.valueOf(s.toUpperCase());
        } catch (Exception e) {
            Bukkit.getLogger().warning("Unknown particle in config: " + s);
            return Particle.DRAGON_BREATH;
        }
    }

    private static List<Drop> parseDrops(List<String> raw) {
        List<Drop> result = new ArrayList<>();
        for (String entry : raw) {
            String[] parts = entry.split(":");
            if (parts.length < 2) {
                continue;
            }
            try {
                result.add(new Drop(Material.valueOf(parts[0].trim().toUpperCase()),
                        Integer.parseInt(parts[1].trim())));
            } catch (Exception e) {
                Bukkit.getLogger().warning("Invalid drop entry in config: " + entry);
            }
        }
        return result;
    }

    public double getMaxHealth() { return maxHealth; }
    public EntityType getBaseEntity() { return baseEntity; }
    public String getName() { return name; }
    public double getAggroRadius() { return aggroRadius; }
    public double getLeashRadius() { return leashRadius; }
    public int getAttackCycleSeconds() { return attackCycleSeconds; }
    public double getWingDamage() { return wingDamage; }
    public double getDiveDamage() { return diveDamage; }
    public double getBlastDamage() { return blastDamage; }
    public double getPoundDamage() { return poundDamage; }
    public double getTailDamage() { return tailDamage; }
    public double getTouchDamage() { return touchDamage; }
    public boolean isPlayTheme() { return playTheme; }
    public Sound getRoarSound() { return roarSound; }
    public Particle getEnergyParticle() { return energyParticle; }
    public List<Drop> getDrops() { return drops; }
    public Sound getThemeInstrument() { return themeInstrument; }
    public double getEnrageThreshold() { return enrageThreshold; }
    public double getEnrageSpeed() { return enrageSpeed; }
    public int getEnrageCycleSeconds() { return enrageCycleSeconds; }
}
