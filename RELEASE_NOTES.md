# 1.9.0-beta.1 — Incremental ground pathfinding

Requires Dominion Sword 1.37.0-beta.1. Ground searches now resume across server ticks under the core planning budget and distinguish pending, complete, partial and failed results. Unreachable routes no longer fall back to an unchecked straight line. Partial routes replan at their endpoint, and valid routes are not discarded every 80 ticks. Final connections are checked.

Automatic jump edges are disabled because ordinary route following did not consume their jump flag; explicit jump skills are unchanged. Biped ground mode opts into experimental shared core corridors; flying mode does not. No in-game physics/load benchmark has been run.
