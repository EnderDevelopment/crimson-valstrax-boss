package com.enderdevelopment.plugin.commands;

import com.enderdevelopment.plugin.Main;
import com.enderdevelopment.plugin.boss.ValstraxBoss;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /valstrax handler: spawn, kill, stop, music, reload, hp.
 */
public class ValstraxCommand implements CommandExecutor {

    private final Main plugin;
    private final ValstraxBoss boss;

    public ValstraxCommand(Main plugin, ValstraxBoss boss) {
        this.plugin = plugin;
        this.boss = boss;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.GRAY + "Usage: /valstrax <spawn|kill|stop|music|reload|hp>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn" -> spawn(player);
            case "kill" -> kill(player);
            case "stop" -> stop(player);
            case "music" -> music(player, args.length > 1 && args[1].equalsIgnoreCase("stop"));
            case "reload" -> reload(player);
            case "hp" -> hp(player);
            default -> player.sendMessage(ChatColor.GRAY + "Unknown subcommand. Use spawn, kill, stop, music, reload, hp.");
        }
        return true;
    }

    private void spawn(Player player) {
        if (boss.isSpawned()) {
            player.sendMessage(ChatColor.RED + "The Valstrax is already flying. Kill it first (/valstrax kill).");
            return;
        }
        Location loc = player.getLocation();
        loc.setY(player.getWorld().getHighestBlockYAt(loc) + 8.0);
        boss.spawn(loc);
        player.sendMessage(ChatColor.RED + "Crimson Glow Valstrax has spawned. Good hunting, " + player.getName() + ".");
    }

    private void kill(Player player) {
        if (!boss.isSpawned()) {
            player.sendMessage(ChatColor.RED + "No Valstrax is present.");
            return;
        }
        player.sendMessage(ChatColor.RED + "You dismiss the Valstrax from this realm.");
        boss.despawn();
    }

    private void stop(Player player) {
        if (!boss.isSpawned()) {
            player.sendMessage(ChatColor.RED + "No Valstrax is present.");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "You stop the Valstrax's attack cycle (it remains in the world).");
        boss.stopFight();
        boss.stopMusic();
    }

    private void music(Player player, boolean stop) {
        if (stop) {
            boss.stopMusic();
            player.sendMessage(ChatColor.GOLD + "Boss theme stopped.");
        } else {
            if (!boss.isSpawned()) {
                player.sendMessage(ChatColor.RED + "Spawn the boss first.");
                return;
            }
            boss.startMusic();
            player.sendMessage(ChatColor.GOLD + "Streaming the Crimson Glow battle theme.");
        }
    }

    private void reload(Player player) {
        plugin.reloadBossConfig();
        player.sendMessage(ChatColor.GREEN + "Valstrax config reloaded.");
    }

    private void hp(Player player) {
        if (!boss.isSpawned()) {
            player.sendMessage(ChatColor.RED + "No Valstrax is present.");
            return;
        }
        double hp = boss.getEntity().getHealth();
        double max = boss.getMaxHealth();
        player.sendMessage(ChatColor.RED + "Crimson Glow Valstrax HP: "
                + String.format("%.0f", hp) + " / " + String.format("%.0f", max));
    }
}
