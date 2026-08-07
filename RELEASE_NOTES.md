# 1.7.23

- Includes the off-hand bit in the ranged fire input mask, so a machine gun or other second hand weapon actually fires alongside the main hand.

# 1.7.22

- Introduces a priority weapon state machine for PMVC01 piloting.
- Within 10 blocks, any melee weapon switches the mech to melee mode: it closes distance and presses all equipped melee weapons (both arms alternate naturally for non-concurrent swords), while shoulder ordnance keeps firing and hand ranged weapons stop.
- Beyond 10 blocks, both hand ranged weapons fire together, with hand grenade launchers and multi-lock weapons keeping their cooldown pulses and shoulder grenade launchers keeping their existing schedule.
- Centralizes per-tick driver input so movement, hand weapons and shoulder bursts share one input mask instead of overwriting each other.

# 1.7.21

- Fires main-hand and off-hand weapons together: the main hand holds fire while the off hand runs short bursts with a cooldown, so a gatling and machine gun no longer starve each other.
- Starts a hand-weapon reload automatically when the chamber is empty and magazines are loaded, fixing off-hand machine guns stuck at 0 rounds.
- Gives melee weapons priority in melee range and presses them on a cooldown cadence, so an off-hand sword can actually swing instead of being starved by shoulder ordnance.
- Preserves movement input while shoulder bursts are running.

# 1.7.20

- Adds throttled weapon diagnostics to the server log for PMVC01 piloting: combat branch decisions, submitted driver-input bits, shoulder-equipment skip reasons and native weapon action/ammo state per slot.

# 1.7.6

- Replaces passenger navigation with the add-on's mech-footprint A* so a mounted NoAI pilot no longer produces an empty movement route.
- Restricts periodic PMVC01 equipment fire to shoulder weapons and skips weapons whose ammunition slot is empty or contains a mismatched magazine.

# 1.7.5

- Applies Dominion movement frames inside PMVC01's overridden travel method so a Mob pilot can actually move and turn the custom mech.
- Preserves PMVC01's native chassis, generator and fuel requirements.

# 1.7.4

- Adds Simplified Chinese localization for PMVC01 weapons, ammunition, chassis parts, generators, boosters, extensions, workbench controls, statistics and status messages.

# 1.7.3

- Allows PMVC01 loadouts with only automatic shoulder equipment to acquire range and fire instead of being treated as unarmed.
- Adds per-slot PMVC01 weapon, chamber, magazine and ammunition-item state to the throttled combat diagnostic log.

# 1.7.2

- Restores a Dominion pilot's exact pre-boarding AI state after leaving PMVC01, including forced dismount and destroyed-mech paths.
- Prevents PMVC01's native mob autopilot from taking over Dominion pilots before their first command.
- Clears stale driver input when boarding so the mech remains stationary until commanded.

# 1.7.0

- Migrates all command skills to collision-safe, namespaced identifiers required by Dominion Sword 1.24.0.
- Declares flight-mode switching as a targetless toggle so it can participate in strict multi-selection skill intersection.
- Retains point targeting for Vector Boost and ground weapons, which remain available only when a single mech is selected.

# 1.6.5

- Fixes Vector Boost bypassing Pomkots Mechs' native jump action by lifting the mech before its queued jump input was consumed.
- Primes the native jump animation for one grounded tick, restoring the original jump and booster sound keyframes without changing the precise landing curve.

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
