# 1.9.0-beta.4 — On-demand ground frontier

Requires Dominion Sword 1.37.0-beta.4. Ground search now expands one-block footprint candidates on demand, follows local standing surfaces within native step limits, and keeps the actual distant goal. Partial routes continue without clamping the goal height onto a nearer point. Movement checks waypoint height and revalidates the next edge. Automatic jump edges remain disabled; explicit jump skills are retained.

Search admission is bounded; the shared time budget remains soft. No game-world physics or load benchmark has been performed.

# 1.9.0-beta.3 — Remaining march route snapshots

Requires Dominion Sword 1.37.0-beta.3. `marchRoute` returns the vehicle's current position and remaining, unconsumed real path nodes. Pending/failed paths remain empty; reaching the end of a partial path allows the existing incremental planner to continue. Debug straight-line previews are not used as movement routes. Native driving/physics is unchanged in this increment.

# 1.9.0-beta.2 — Unified march ownership

Requires Dominion Sword 1.37.0-beta.2. Ground driving yields to the core persistent column/free task's waiting and conservative cruise-cap decisions. Native movement, collision and special flight/jump controllers remain addon-owned. No live physics or load benchmark has been performed.

# 1.9.0-beta.1 — Incremental ground pathfinding

Requires Dominion Sword 1.37.0-beta.1. Ground searches now resume across server ticks under the core planning budget and distinguish pending, complete, partial and failed results. Unreachable routes no longer fall back to an unchecked straight line. Partial routes replan at their endpoint, and valid routes are not discarded every 80 ticks. Final connections are checked.

Automatic jump edges are disabled because ordinary route following did not consume their jump flag; explicit jump skills are unchanged. Biped ground mode opts into experimental shared core corridors; flying mode does not. No in-game physics/load benchmark has been run.
