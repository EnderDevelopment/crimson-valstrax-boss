package com.enderdevelopment.plugin;

import com.enderdevelopment.plugin.boss.BossListener;
import com.enderdevelopment.plugin.boss.ValstraxBoss;
import com.enderdevelopment.plugin.commands.ValstraxCommand;
import com.enderdevelopment.plugin.config.BossConfig;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main entry point of the Crimson Glow Valstrax boss plugin.
 *
 * This is a Java (Paper/Bukkit) plugin, not a Bedrock addon. It re-creates the
 * Sunbreak Crimson Glow Valstrax hunt using the Paper API: a custom boss
 * entity, a state-machine of its signature attacks, particles, sounds, a
 * looping boss theme and a boss bar.
 */
public final class Main extends JavaPlugin {

    private static Main instance;
    private BossConfig config;
    private ValstraxBoss boss;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadBossConfig();

        this.boss = new ValstraxBoss(this, config);
        Bukkit.getPluginManager().registerEvents(new BossListener(this, boss), this);
        getCommand("valstrax").setExecutor(new ValstraxCommand(this, boss));

        getLogger().info("Crimson Glow Valstrax has learned your location. Good luck, Hunter.");
    }

    @Override
    public void onDisable() {
        if (boss != null) {
            boss.stopFight();
            boss.despawn();
        }
    }

    /** Reloads the config into a fresh BossConfig object. */
    public void reloadBossConfig() {
        reloadConfig();
        this.config = new BossConfig(getConfig());
    }

    public static Main getInstance() {
        return instance;
    }

    public BossConfig getBossConfig() {
        return config;
    }

    public ValstraxBoss getBoss() {
        return boss;
    }
}
