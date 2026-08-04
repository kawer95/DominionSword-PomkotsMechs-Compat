# 1.6.4

- Replaces Vector Boost's horizontal correction plus free-fall landing with a sampled parabolic flight curve.
- Raises the curve only as much as needed to clear the mech's full collision box and rejects blocked trajectories.
- Converges horizontal and vertical motion together at the safe landing point without a final uncontrolled drop.

# 1.6.3

- Requires Dominion Sword 1.23.3 so a mech driver cannot independently acquire targets or attack.
- Applies the pilot-only combat gate to maids, CustomNPCs and generic ranged or melee mobs.

# 1.6.2

- Raises automatic continuous shoulder-equipment intervals from 12 to 60 seconds.
- Raises automatic missile and ordnance intervals from 20 to 100 seconds.
- Keeps the auxiliary cooldown on the mech itself so changing or losing targets cannot reset it.

# 1.6.1

- Fixes alpha.8 production-name matching for the bullet and missile tick Mixins.
- Prevents the compatibility addon from aborting Pomkots Mechs during Forge startup.

# 1.6.0

- Repairs alpha.8 gatling bullets skipping half of every swept collision path and clears hurt immunity before damage.
- Gives legacy missiles a real owner and the commanded hard-lock target, including PMV03P missiles that otherwise only seek players.
- Stops stale continuous fire when an attack target dies or the attack order stops refreshing.
- Raises automatic auxiliary weapon intervals to 12 seconds for continuous equipment and 20 seconds for missiles/ordnance.
- Moves Boost Evasion from the action menu to an instant skill with a 10-second cooldown.
- Requires Dominion Sword 1.23.1.

# 1.5.0

- Fixes every uncontrolled enemy being rejected as a friendly target because two absent controller UUIDs compared equal.
- Keeps Vector Boost's displayed command target at the clicked jump point while airborne, then settles it at the mech's real landing position so it cannot walk back.
- Extends Vector Boost to 32 blocks with a 30-second cooldown and authoritative client/server range validation.
- Requires Dominion Sword 1.23.0 for point-skill range visualization and seven configurable skill shortcuts.

# 1.4.4

- Applies Dominion attack and movement inputs after Pomkots alpha.8's built-in Mob controller, preventing PMVC01 from overwriting weapon keys before they are consumed; adds throttled combat-state diagnostics to the server log.
- Redirects the persistent vehicle move order to Vector Boost's validated landing point, preventing the mech from walking back to its previous destination after landing.
- Requires Dominion Sword 1.22.3 for the shared point-skill ground marker and safe single-vehicle target redirection.

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
