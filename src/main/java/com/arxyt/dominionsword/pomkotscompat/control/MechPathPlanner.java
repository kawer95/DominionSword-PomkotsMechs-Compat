package com.arxyt.dominionsword.pomkotscompat.control;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Small footprint-aware A* used only by the Pomkots vehicle adapter. */
public final class MechPathPlanner {
    private static final int STEP = 2;
    private static final int MAX_NODES = 3500;
    private static final int MAX_RADIUS = 72;
    private static final int[][] DIRECTIONS = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};

    private MechPathPlanner() {}

    public static Route plan(Entity vehicle, Vec3 requestedTarget) {
        if (!(vehicle.level() instanceof ServerLevel level) || requestedTarget == null) return Route.direct(vehicle.position(), requestedTarget);
        Vec3 startPos = vehicle.position();
        Vec3 target = clampRange(startPos, requestedTarget, MAX_RADIUS);
        Node start = new Node((int)Math.floor(startPos.x), (int)Math.floor(startPos.y), (int)Math.floor(startPos.z));
        int goalX = (int)Math.floor(target.x), goalZ = (int)Math.floor(target.z);

        PriorityQueue<OpenNode> open = new PriorityQueue<>(Comparator.comparingDouble(OpenNode::score));
        Map<Node, Double> cost = new HashMap<>();
        Map<Node, StepFrom> cameFrom = new HashMap<>();
        cost.put(start, 0.0D);
        open.add(new OpenNode(start, heuristic(start, goalX, goalZ)));
        Node best = start;
        double bestDistance = heuristic(start, goalX, goalZ);
        int expanded = 0;

        while (!open.isEmpty() && expanded++ < MAX_NODES) {
            Node current = open.poll().node();
            double distance = heuristic(current, goalX, goalZ);
            if (distance < bestDistance) { best = current; bestDistance = distance; }
            if (distance <= 2.5D) { best = current; break; }

            for (int[] direction : DIRECTIONS) {
                int nx = current.x + direction[0] * STEP;
                int nz = current.z + direction[1] * STEP;
                Integer ny = findStandY(level, vehicle, nx + 0.5D, nz + 0.5D, current.y, 2);
                if (ny == null || !sweepClear(level, vehicle, current.center(), new Vec3(nx + 0.5D, ny, nz + 0.5D))) continue;
                Node next = new Node(nx, ny, nz);
                double stepCost = Math.hypot(direction[0], direction[1]) * STEP + Math.abs(ny - current.y) * 0.8D;
                relax(current, next, false, stepCost, goalX, goalZ, open, cost, cameFrom);
            }

            // Jump links are considered only when the first walking step in that direction is blocked.
            for (int i = 0; i < 4; i++) {
                int[] direction = DIRECTIONS[i];
                int walkX = current.x + direction[0] * STEP, walkZ = current.z + direction[1] * STEP;
                if (findStandY(level, vehicle, walkX + 0.5D, walkZ + 0.5D, current.y, 2) != null) continue;
                for (int distanceBlocks : new int[]{6, 8, 10}) {
                    int nx = current.x + direction[0] * distanceBlocks;
                    int nz = current.z + direction[1] * distanceBlocks;
                    Integer ny = findStandY(level, vehicle, nx + 0.5D, nz + 0.5D, current.y, 3);
                    if (ny == null) continue;
                    Vec3 landing = new Vec3(nx + 0.5D, ny, nz + 0.5D);
                    if (!safeJumpArc(level, vehicle, current.center(), landing)) continue;
                    Node next = new Node(nx, ny, nz);
                    relax(current, next, true, distanceBlocks * 1.15D + 3.0D, goalX, goalZ, open, cost, cameFrom);
                    break;
                }
            }
        }

        if (best.equals(start)) return Route.direct(startPos, requestedTarget);
        LinkedList<RoutePoint> points = new LinkedList<>();
        Node cursor = best;
        while (!cursor.equals(start)) {
            StepFrom from = cameFrom.get(cursor);
            if (from == null) break;
            points.addFirst(new RoutePoint(cursor.center(), from.jump()));
            cursor = from.previous();
        }
        points.addFirst(new RoutePoint(startPos, false));
        if (bestDistance <= 2.5D && requestedTarget.distanceToSqr(points.getLast().position()) > 1.0D) {
            Integer finalY = findStandY(level, vehicle, requestedTarget.x, requestedTarget.z, best.y, 2);
            if (finalY != null) points.add(new RoutePoint(new Vec3(requestedTarget.x, finalY, requestedTarget.z), false));
        }
        return new Route(List.copyOf(points));
    }

    public static Optional<Vec3> safeJumpLanding(Entity vehicle, float yawDegrees) {
        if (!(vehicle.level() instanceof ServerLevel level)) return Optional.empty();
        double radians = Math.toRadians(yawDegrees);
        Vec3 direction = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians)).normalize();
        Vec3 start = vehicle.position();
        for (int distance : new int[]{6, 8, 10}) {
            Vec3 horizontal = start.add(direction.scale(distance));
            Integer y = findStandY(level, vehicle, horizontal.x, horizontal.z, (int)Math.floor(start.y), 3);
            if (y == null) continue;
            Vec3 landing = new Vec3(horizontal.x, y, horizontal.z);
            if (safeJumpArc(level, vehicle, start, landing)) return Optional.of(landing);
        }
        return Optional.empty();
    }

    /** Resolves a clicked point to the farthest safe landing on the same vector, capped to boost range. */
    public static Optional<Vec3> safeJumpLanding(Entity vehicle, Vec3 requestedTarget) {
        if (!(vehicle.level() instanceof ServerLevel level) || requestedTarget == null) return Optional.empty();
        Vec3 start = vehicle.position();
        Vec3 horizontal = requestedTarget.subtract(start).multiply(1.0D, 0.0D, 1.0D);
        double requestedDistance = horizontal.length();
        if (requestedDistance < 2.5D) return Optional.empty();
        Vec3 direction = horizontal.scale(1.0D / requestedDistance);
        double maximum = Math.min(32.0D, requestedDistance);
        for (double distance = maximum; distance >= 3.5D; distance -= 1.0D) {
            Vec3 point = start.add(direction.scale(distance));
            Integer y = findStandY(level, vehicle, point.x, point.z, (int)Math.floor(start.y), 5);
            if (y == null) continue;
            Vec3 landing = new Vec3(point.x, y, point.z);
            if (safeJumpArc(level, vehicle, start, landing)) return Optional.of(landing);
        }
        return Optional.empty();
    }

    public static boolean blockedAhead(Entity vehicle, float yawDegrees, double distance) {
        if (!(vehicle.level() instanceof ServerLevel level)) return false;
        double radians = Math.toRadians(yawDegrees);
        Vec3 direction = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        AABB box = vehicle.getBoundingBox().deflate(0.05D);
        for (double d = 0.75D; d <= distance; d += 0.75D) {
            if (!level.noCollision(vehicle, box.move(direction.scale(d)))) return true;
        }
        return false;
    }

    private static void relax(Node current, Node next, boolean jump, double edgeCost, int goalX, int goalZ,
                              PriorityQueue<OpenNode> open, Map<Node, Double> cost, Map<Node, StepFrom> cameFrom) {
        double candidate = cost.getOrDefault(current, Double.POSITIVE_INFINITY) + edgeCost;
        if (candidate >= cost.getOrDefault(next, Double.POSITIVE_INFINITY)) return;
        cost.put(next, candidate);
        cameFrom.put(next, new StepFrom(current, jump));
        open.add(new OpenNode(next, candidate + heuristic(next, goalX, goalZ)));
    }

    private static boolean sweepClear(ServerLevel level, Entity vehicle, Vec3 from, Vec3 to) {
        for (int i = 1; i <= 4; i++) {
            double t = i / 4.0D;
            Vec3 p = from.lerp(to, t);
            if (!level.noCollision(vehicle, placementBox(vehicle, p.x, p.y, p.z).deflate(0.04D))) return false;
        }
        return true;
    }

    private static boolean safeJumpArc(ServerLevel level, Entity vehicle, Vec3 start, Vec3 landing) {
        double horizontal = Math.hypot(landing.x - start.x, landing.z - start.z);
        double apex = Math.max(4.0D, Math.min(7.0D, horizontal * 0.6D));
        for (int i = 2; i <= 16; i++) {
            double t = i / 16.0D;
            double y = start.y + (landing.y - start.y) * t + 4.0D * apex * t * (1.0D - t);
            Vec3 p = new Vec3(start.x + (landing.x - start.x) * t, y, start.z + (landing.z - start.z) * t);
            if (!level.noCollision(vehicle, placementBox(vehicle, p.x, p.y, p.z).deflate(0.08D))) return false;
        }
        return hasSupport(level, vehicle, landing.x, landing.y, landing.z);
    }

    private static Integer findStandY(ServerLevel level, Entity vehicle, double x, double z, int baseY, int range) {
        for (int delta : offsetOrder(range)) {
            int y = baseY + delta;
            AABB box = placementBox(vehicle, x, y, z).deflate(0.04D);
            if (level.noCollision(vehicle, box) && hasSupport(level, vehicle, x, y, z)) return y;
        }
        return null;
    }

    private static int[] offsetOrder(int range) {
        int[] out = new int[range * 2 + 1];
        out[0] = 0;
        for (int i = 1; i <= range; i++) { out[i * 2 - 1] = i; out[i * 2] = -i; }
        return out;
    }

    private static boolean hasSupport(ServerLevel level, Entity vehicle, double x, double y, double z) {
        AABB box = placementBox(vehicle, x, y, z);
        double hx = Math.max(0.25D, box.getXsize() * 0.42D), hz = Math.max(0.25D, box.getZsize() * 0.42D);
        int supported = 0;
        for (double[] offset : new double[][]{{0,0},{hx,hz},{hx,-hz},{-hx,hz},{-hx,-hz}}) {
            BlockPos below = BlockPos.containing(x + offset[0], y - 0.12D, z + offset[1]);
            BlockState state = level.getBlockState(below);
            if (!state.getFluidState().isEmpty()) return false;
            if (!state.getCollisionShape(level, below).isEmpty()) supported++;
        }
        return supported >= 4;
    }

    private static AABB placementBox(Entity vehicle, double x, double y, double z) {
        return vehicle.getBoundingBox().move(x - vehicle.getX(), y - vehicle.getY(), z - vehicle.getZ());
    }

    private static Vec3 clampRange(Vec3 start, Vec3 target, double max) {
        Vec3 flat = new Vec3(target.x - start.x, 0.0D, target.z - start.z);
        if (flat.lengthSqr() <= max * max) return target;
        Vec3 limited = flat.normalize().scale(max);
        return new Vec3(start.x + limited.x, target.y, start.z + limited.z);
    }

    private static double heuristic(Node node, int goalX, int goalZ) {
        return Math.hypot(goalX - node.x, goalZ - node.z);
    }

    private record Node(int x, int y, int z) { Vec3 center() { return new Vec3(x + 0.5D, y, z + 0.5D); } }
    private record OpenNode(Node node, double score) {}
    private record StepFrom(Node previous, boolean jump) {}
    public record RoutePoint(Vec3 position, boolean jumpFromPrevious) {}
    public record Route(List<RoutePoint> points) {
        public Route { points = points == null ? List.of() : List.copyOf(points); }
        public static Route direct(Vec3 start, Vec3 target) {
            if (target == null) return new Route(List.of(new RoutePoint(start, false)));
            return new Route(List.of(new RoutePoint(start, false), new RoutePoint(target, false)));
        }
        public List<Vec3> positions() { return points.stream().map(RoutePoint::position).toList(); }
    }
}
