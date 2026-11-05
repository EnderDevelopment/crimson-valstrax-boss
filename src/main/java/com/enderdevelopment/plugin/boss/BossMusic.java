package com.enderdevelopment.plugin.boss;

import com.enderdevelopment.plugin.config.BossConfig;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Plays a looping boss theme built from Minecraft's note-block instruments and
 * a background music box. No audio files are bundled; the theme is "composed"
 * as a sequence of note-block notes scheduled on the main thread, which keeps
 * the plugin self-contained and works with any resource pack.
 *
 * The theme is a dark, driving dragon motif loosely inspired by the Crimson
 * Glow Valstrax battle music from Monster Hunter Rise: Sunbreak.
 */
public class BossMusic {

    private static final class Note {
        final Sound instrument;
        final int semitone;      // 0..24 relative to the base pitch
        final long delayTicks;   // offset from the start of the phrase
        final float volume;

        Note(Sound instrument, int semitone, long delayTicks, float volume) {
            this.instrument = instrument;
            this.semitone = semitone;
            this.delayTicks = delayTicks;
            this.volume = volume;
        }
    }

    private final Plugin plugin;
    private final BossConfig config;
    private final List<Note> theme;
    private final long phraseTicks;
    private BukkitTask playingTask;
    private Location origin;
    private double radius = 64.0;

    public BossMusic(Plugin plugin, BossConfig config) {
        this.plugin = plugin;
        this.config = config;
        Phrase phrase = composeTheme();
        this.theme = phrase.notes;
        this.phraseTicks = phrase.lengthTicks;
    }

    /**
     * Starts the looping theme. Every phrase the melody restarts. Each note is
     * scheduled individually so the loop stays in sync even if individual notes
     * get delayed by server load.
     */
    public void start(Location origin, double radius) {
        stop();
        this.origin = origin.clone();
        this.radius = radius;
        if (!config.isPlayTheme()) {
            return;
        }
        playingTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (origin == null || !origin.isWorldLoaded()) {
                    return;
                }
                for (Note note : theme) {
                    long when = note.delayTicks;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            playForNearby(note);
                        }
                    }.runTaskLater(plugin, when);
                }
            }
        }.runTaskTimer(plugin, 2L, phraseTicks);
    }

    public void stop() {
        if (playingTask != null) {
            playingTask.cancel();
            playingTask = null;
        }
    }

    public boolean isPlaying() {
        return playingTask != null && !playingTask.isCancelled();
    }

    private void playForNearby(Note note) {
        if (origin == null || !origin.isWorldLoaded()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(origin.getWorld())) {
                continue;
            }
            if (player.getLocation().distanceSquared(origin) > radius * radius) {
                continue;
            }
            // Note-block note pitch is a semitone offset; 0 = one octave below
            // normal pitch bar.
            float pitch = (float) Math.pow(2.0, (note.semitone - 12) / 24.0);
            player.playSound(player.getLocation(), note.instrument, note.volume, pitch);
        }
    }

    /** A short, looping piece of boss music. */
    private Phrase composeTheme() {
        List<Note> notes = new ArrayList<>();
        long bar = 4; // 4 ticks per beat -> 16 ticks per 4/4 bar

        Sound bass = Sound.BLOCK_NOTE_BLOCK_BASS;
        Sound kick = Sound.BLOCK_NOTE_BLOCK_BASEDRUM;
        Sound snare = Sound.BLOCK_NOTE_BLOCK_SNARE;
        Sound hat = Sound.BLOCK_NOTE_BLOCK_HAT;
        Sound flute = Sound.BLOCK_NOTE_BLOCK_FLUTE;
        Sound pling = Sound.BLOCK_NOTE_BLOCK_PLING;
        Sound bell = Sound.BLOCK_NOTE_BLOCK_BELL;
        Sound bg = config.getThemeInstrument();

        // ---- Intro / theme statement (legato, dark) ----
        int[] roots = {0, 3, 5, 3, 0, -2, -3, -5};
        // Lowercase dark bass line over two bars (16 ticks)
        for (int i = 0; i < 8; i++) {
            notes.add(new Note(bass, -12 + roots[i], i * 2, 1.0f));
            notes.add(new Note(kick, 0, i * 2, 0.9f));
            notes.add(new Note(hat, 0, i * 2 + 1, 0.5f));
        }
        // A short ascending "dragon call" motif on flute/pling.
        int[] motif = {12, 15, 17, 15, 12, 10, 12, 15};
        for (int i = 0; i < motif.length; i++) {
            notes.add(new Note(flute, motif[i], 34 + i * 2, 0.9f));
            notes.add(new Note(pling, motif[i] + 12, 35 + i * 2, 0.4f));
        }
        notes.add(new Note(bass, -5, 48, 1.0f));
        notes.add(new Note(kick, 0, 48, 1.0f));
        notes.add(new Note(hat, 0, 50, 0.6f));
        notes.add(new Note(snare, 0, 52, 0.7f));

        // ---- Driving percussion groove (bar 3-4) ----
        for (int i = 0; i < 16; i++) {
            notes.add(new Note(kick, 0, 64 + i * 1, 0.9f));
            if (i % 2 == 1) {
                notes.add(new Note(hat, 0, 64 + i * 1, 0.5f));
            }
            if (i % 4 == 2) {
                notes.add(new Note(snare, 0, 64 + i * 1, 0.7f));
            }
        }
        // Rising tension figure
        int[] tension = {12, 14, 16, 17, 19, 21, 23, 24};
        for (int i = 0; i < tension.length; i++) {
            notes.add(new Note(pling, tension[i], 64 + i * 2, 0.5f));
            notes.add(new Note(bell, tension[i] + 12, 64 + i * 2, 0.35f));
        }
        notes.add(new Note(bass, 0, 78, 1.0f));
        notes.add(new Note(kick, 0, 78, 1.0f));
        notes.add(new Note(snare, 0, 80, 0.8f));
        notes.add(new Note(hat, 0, 81, 0.6f));

        // background wash
        notes.add(new Note(bg, 0, 0, 0.25f));
        notes.add(new Note(bg, 0, 32, 0.25f));
        notes.add(new Note(bg, 0, 64, 0.25f));

        return new Phrase(notes, 96L);
    }

    private static final class Phrase {
        final List<Note> notes;
        final long lengthTicks;

        Phrase(List<Note> notes, long lengthTicks) {
            this.notes = notes;
            this.lengthTicks = lengthTicks;
        }
    }
}
