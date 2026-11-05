# Crimson Glow Valstrax Boss Plugin

A Minecraft Paper plugin that recreates the Crimson Glow Valstrax boss fight from Monster Hunter Rise: Sunbreak. This plugin adds a challenging dragon boss with signature attacks, particle effects, sounds, looping battle music, and a boss bar to your server.

## Install

1. Download the `CrimsonGlowValstrax.jar` file from the release.
2. Place the JAR file into your server's `plugins` folder.
3. Restart your server, or run `/reload` if your server supports it.
4. The plugin will create its default configuration file automatically.

## Use

1. To spawn the boss, stand where you want it to appear and run:
   ```
   /valstrax spawn
   ```
   The boss will spawn 8 blocks above your current location and immediately begin its attack cycle.

2. The boss will start its fight sequence with a roar and begin attacking nearby players within its aggro radius (48 blocks by default).

3. Control commands:
   - `/valstrax kill` – Removes the boss instantly
   - `/valstrax stop` – Stops the boss's attack cycle but keeps it in the world
   - `/valstrax music` – Starts playing the boss theme
   - `/valstrax music stop` – Stops the music
   - `/valstrax reload` – Reloads configuration values
   - `/valstrax hp` – Shows the boss's current health

4. When the boss's health drops below 30%, it will become enraged: it moves faster, attacks more frequently, and its boss bar turns purple.

5. Defeat the boss to receive its loot drops, which are configurable in the `config.yml` file.

---

More Minecraft plugin projects by EnderDevelopment: https://github.com/EnderDevelopment

Project page: https://dash.enderdevelopment.com/community/c56628b5-6d98-459f-b6b7-775d0c9a714c?utm_source=github&utm_medium=readme&utm_campaign=auto_publish&utm_content=c56628b5-6d98-459f-b6b7-775d0c9a714c
