# 1.4.3

- Fixes Vector Boost spiralling in the air: mechs now turn in place before launch and keep the launch heading until landing.
- Removes direct left-arm and shoulder equipment triggers from the vehicle action menu.
- Automatically rotates every eligible auxiliary weapon against the locked enemy while excluding the primary gun, melee weapons, engineering equipment and point-targeted skill weapons.

# 1.4.2

- Fixes ground mechs circling their destination by turning in place before applying forward input.
- Replaces the vehicle-specific ground route planner with the mounted unit's vanilla Mob navigation; the mech now follows the biological pilot's walkable path nodes.
- Keeps Vector Boost as an explicitly activated skill instead of automatically jumping during ordinary movement.

# 1.4.1

- Fixes the production Forge client crash caused by unmapped `travel` and `tick` Mixin targets in the obfuscated Pomkots alpha.8 JAR.
- Supports both development names and production SRG names without relying on an empty generated refmap.

# 1.4.0

- Separates melee and ranged combat: melee-equipped mechs close to weapon reach, while ranged mechs seek line of sight, hold a useful firing distance and retreat inside ten blocks.
- Automatically cycles supported melee weapons and periodically fires fixed multi-lock missiles, PMV03P's horizontal missile, plus PMVC01 Suwa, Kawasemi and Tsubame shoulder weapons.
- Adds point-targeted ground strike skills for PMVC01 Dodo, Nosuri and Mukudori launchers.
- Excludes block placement, block breaking and entity-lifting engineering equipment from automatic combat and skills.
- Automatically restores PMV01, PMV01B, PMV02 and PMV03 to their combat mode when an attack order starts.
- Uses Pomkots Mechs alpha.8's own `enablePlayerVehicleBlockDestruction` option for weapon terrain protection; no duplicate Dominion Sword option is added.

The compatibility add-on is MIT-licensed. Pomkots Mechs remains a separate dependency and is not redistributed by this project.
