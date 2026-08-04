# Dominion Sword: Pomkots Mechs Compatibility

Forge 1.20.1 compatibility add-on that lets Dominion Sword-controlled units board and command supported Pomkots Mechs vehicles.

## Requirements

- Dominion Sword 1.22.0 or newer
- Pomkots Mechs 0.0.1-alpha.8
- GBF 1.0.1 or newer, plus the other dependencies required by Pomkots Mechs alpha.8
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

## Supported vehicles

- PMV01, PMV01B, PMV02, PMV03P and PMV03
- PMVC01 customizable mech

PMVT01 is a stationary turret rather than a mobile mech and is intentionally not exposed as a Dominion Sword vehicle.

PMV03P exposes an instant command-mode skill for switching between its native alpha.8 flight and ground modes. All supported mobile mechs also retain the point-targeted Vector Boost skill.

Combat orders select close-range or ranged positioning from the installed weapon set. Fixed-mech missile systems and PMVC01 Suwa, Kawasemi and Tsubame shoulder weapons are used periodically; PMV03P also uses its horizontal missile in both flight and ground combat. PMVC01 engineering tools are never selected automatically. Dodo, Nosuri and Mukudori launchers appear as point-targeted ground strike skills when equipped.

Weapon terrain damage is controlled by Pomkots Mechs' own `enablePlayerVehicleBlockDestruction` setting. Set it to `false` in the Pomkots Mechs configuration to prevent weapon explosions from breaking blocks, including when the pilot is a Dominion Sword unit.
