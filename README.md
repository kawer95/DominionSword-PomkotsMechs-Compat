# Dominion Sword: Pomkots Mechs Compatibility

Forge 1.20.1 compatibility add-on that lets Dominion Sword-controlled units board and command supported Pomkots Mechs vehicles.

## Requirements

- Dominion Sword 1.22.0 or newer
- Pomkots Mechs 0.0.1-alpha.3
- Forge 47.x for Minecraft 1.20.1

## Licensing

This compatibility add-on is released under the MIT License. Pomkots Mechs is a separate project by grc_mcs and is not bundled or redistributed here. The add-on does not include Pomkots sound effects, music, models, textures, or compiled mod JARs.

Compatibility add-on development was explicitly approved by the Pomkots Mechs author. Users must obtain Pomkots Mechs from its official distribution channel.

## Building

Supply local development JAR paths to Gradle; the dependencies are intentionally not bundled:

```powershell
.\gradlew.bat build -Pdominionsword_jar=<Dominion Sword jar> -Ppomkots_jar=<Pomkots dev jar> -Pgeckolib_jar=<GeckoLib jar>
```

For local automatic deployment, create an ignored `local.properties` file containing `deploy_directory=<mods path>` and optionally `deploy_prefix=<file prefix>`, then run `deployToGame`.
