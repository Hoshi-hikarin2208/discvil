# DiscForge

**Turn your own music into custom Minecraft music discs.**

Drop an `.ogg` file into a folder, run one command, and you have a disc that plays your song in any jukebox. DiscForge builds and hosts the resource pack for you, so there are no datapacks and no manual sound editing.

![Java 21](https://img.shields.io/badge/Java-21-orange)
![Minecraft 1.21+](https://img.shields.io/badge/Minecraft-1.21%2B-brightgreen)
![Platform](https://img.shields.io/badge/Spigot%20%7C%20Paper%20%7C%20Purpur-supported-blue)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

---

## Features

- **Bring your own music.** Any `.ogg` file in `plugins/DiscForge/music/` becomes a disc automatically.
- **Automatic resource pack.** The plugin generates the pack with your sounds and sends it to players when they join.
- **Built-in pack hosting.** A small web server inside the plugin serves the pack. You can also host it yourself.
- **Real jukebox playback.** Insert a disc to play it, click the jukebox to take it out, and the music stops. Breaking or blowing up the jukebox returns the disc.
- **Custom names and artists.** Edit `discs.yml` to set the display name, artist, disc item and custom model data.
- **Automatic track length.** The plugin reads the length of each file so the jukebox note particles stop when the song ends.
- **Nearby "Now playing" message** above the hotbar.
- **Lightweight.** No external dependencies, no database, no other plugins required.

## Requirements

| Requirement | Version |
|---|---|
| Server software | Spigot, Paper or Purpur |
| Minecraft | 1.21 or newer |
| Java | 21 or newer |
| Audio format | Ogg Vorbis (`.ogg`) |

Folia is not supported.

## Installation

1. Download `DiscForge-<version>.jar` and place it in your server's `plugins/` folder.
2. Start the server once so the plugin creates its folders.
3. Open `plugins/DiscForge/config.yml` and set `resource-pack.host.public-address` to your server's public IP address or domain.
4. Open the port from `resource-pack.host.port` (default `8123`, TCP) in your firewall or hosting panel.
5. Restart the server.

## Quick start

1. Convert your song to Ogg Vorbis (see [Preparing your music](#preparing-your-music)).
2. Copy the file to `plugins/DiscForge/music/`, for example `my_song.ogg`.
3. Run `/discforge reload`. Players online are asked to download the updated pack.
4. Run `/discforge give my_song` to receive the disc.
5. Right-click a jukebox with the disc. Your music plays.

## Preparing your music

Minecraft only plays **Ogg Vorbis**. Renaming an `.mp3` to `.ogg` does not work. Convert it with [FFmpeg](https://ffmpeg.org/):

```bash
# Recommended: mono, so the sound fades with distance from the jukebox
ffmpeg -i "song.mp3" -vn -ac 1 -c:a libvorbis -q:a 4 my_song.ogg

# Keep stereo if you prefer (may not fade with distance)
ffmpeg -i "song.mp3" -vn -ac 2 -c:a libvorbis -q:a 4 my_song.ogg
```

Tips:

- Keep files reasonably small. Every player downloads the pack, so a 3 to 4 minute song at `-q:a 3` or `-q:a 4` is usually a few megabytes.
- The file name becomes the disc id. `My Song.ogg` becomes `my_song`. Only lowercase letters, digits, `_` and `-` are kept.
- Only use music you have the right to share with your players.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/discforge` | Show help | `discforge.admin` |
| `/discforge list` | List all loaded discs with their length | `discforge.admin` |
| `/discforge give <disc> [player] [amount]` | Give one or more discs (1 to 64) | `discforge.admin` |
| `/discforge reload` | Reload the config, rescan the music folder and rebuild the pack | `discforge.admin` |
| `/discforge pack` | Send the resource pack to all online players again | `discforge.admin` |
| `/discforge info` | Show the pack link, SHA-1 hash and disc count | `discforge.admin` |

Alias: `/dforge`

## Permissions

| Permission | Description | Default |
|---|---|---|
| `discforge.admin` | Use `/discforge` | OP |
| `discforge.play` | Insert custom discs into jukeboxes | Everyone |

## Configuration

### `config.yml`

```yaml
default-material: MUSIC_DISC_CAT      # item used for new discs
volume: 4.0                           # about 16 blocks of range per 1.0
fallback-duration-seconds: 240        # used if a file's length cannot be read
show-now-playing: true
particles: true

resource-pack:
  send-on-join: true
  pack-format: 46
  host:
    enabled: true
    port: 8123
    public-address: ""                # your server IP or domain
  external-url: ""                    # use this to host the pack yourself
```

### `discs.yml`

Created automatically. Each music file gets an entry you can edit, then apply with `/discforge reload`.

```yaml
discs:
  my_song:
    name: "&6My Song"
    author: "Your Name"
    material: MUSIC_DISC_PIGSTEP
    custom-model-data: 0
```

| Key | Description |
|---|---|
| `name` | Item name. Supports `&` color codes. |
| `author` | Artist shown in the item lore. Leave empty to hide. |
| `material` | Any item. A `MUSIC_DISC_*` item is recommended. |
| `custom-model-data` | Optional number for your own item textures. `0` disables it. |

## Hosting the resource pack

Players need the generated pack to hear your music. Pick one option.

**Option 1: Built-in host (default).** Set `resource-pack.host.public-address` to your server's public IP or domain and open the TCP port (`8123` by default). Players are prompted to download the pack when they join.

**Option 2: Host it yourself.** Upload `plugins/DiscForge/resourcepack/DiscForge-pack.zip` to any site that gives a direct download link. Put the link in `resource-pack.external-url` and set `resource-pack.host.enabled` to `false`. Upload the new zip again whenever you change the music.

**Option 3: `server.properties`.** Set `resource-pack` to a direct link and `resource-pack-sha1` to the value shown by `/discforge info`.

If your hosting provider does not allow extra open ports, use option 2.

## How it works

1. On startup, DiscForge scans `plugins/DiscForge/music/` and registers one disc per `.ogg` file.
2. It builds a resource pack that contains `sounds.json` and your audio files, with one sound event per disc.
3. Each disc item stores its id in the item data. When a player right-clicks a jukebox with it, the plugin plays that sound at the jukebox and remembers the disc in the jukebox block.
4. Clicking the jukebox again, breaking it or exploding it stops the sound and drops the disc.

## Troubleshooting

**The disc goes in but I hear nothing.**
Make sure you accepted the resource pack, and run `/discforge info` to check that a pack link is set. Check that the record volume in the player's sound settings is not at zero. Press `F3 + T` to reload resources.

**Players are not asked to download the pack.**
`public-address` is empty, the port is blocked, or `send-on-join` is `false`. Check the server console for the startup messages.

**The console says a file is not a readable Ogg Vorbis file.**
The file is not Vorbis. Convert it again with the FFmpeg command above.

**The client says the pack is for a different version.**
Change `resource-pack.pack-format` to the number for your Minecraft version and restart the server.

**My new song does not appear.**
The file must end in `.ogg` and be inside `plugins/DiscForge/music/`. Run `/discforge reload`.

## Known limitations

- Players who decline the resource pack cannot hear the music.
- A player who walks into range after a song has started hears it from the next time it is played, not partway through.
- Discs are not visible inside the jukebox and do not give a comparator signal.
- The built-in host serves plain HTTP. Use an external host if you need HTTPS.

## Building from source

You need Java 21 and Maven.

```bash
git clone https://github.com/Hoshi-hikarin2208/DiscForge.git
cd DiscForge
mvn package
```

The jar is created at `target/DiscForge-<version>.jar`. The repository also includes a GitHub Actions workflow that builds the jar on every push and attaches it to the run as an artifact.

### Project layout

```
src/main/java/com/hoshihikarin/discforge/
  DiscForgePlugin.java   plugin entry point and reload logic
  DiscManager.java       scans the music folder and creates disc items
  JukeboxListener.java   insert, eject, break and playback handling
  PackManager.java       resource pack builder and built-in web host
  DiscCommand.java       /discforge command and tab completion
  OggDuration.java       reads the length of an Ogg Vorbis file
  Disc.java              disc data
```

## Roadmap

- Custom disc textures through the resource pack
- Optional Simple Voice Chat streaming, so players do not need a resource pack
- Disc recipes and loot table drops
- Folia support

## License

DiscForge is released under the [MIT License](LICENSE).

## Author

Made by [Renze Daneille P. Rivero](https://github.com/Hoshi-hikarin2208).
