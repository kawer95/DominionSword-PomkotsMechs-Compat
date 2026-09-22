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

    private static final Map<UUID, Search> SEARCHES = new HashMap<>();
    public static void clear() { SEARCHES.clear(); }

    /** Incremental main-thread search. Pending/no-path are never disguised as direct routes. */
    public static Route plan(Entity vehicle, Vec3 requestedTarget) {
        if (!(vehicle.level() instanceof ServerLevel level) || requestedTarget == null)
            return new Route(List.of(), Status.NO_PATH);
        long tick = level.getGameTime();
        SEARCHES.entrySet().removeIf(e -> e.getValue().level != level && e.getValue().level.getServer() != level.getServer()
                || e.getValue().level == level && tick - e.getValue().used > 100);
        Search search = SEARCHES.get(vehicle.getUUID());
        if (search == null || search.level != level || search.requested.distanceToSqr(requestedTarget) > 1
                || search.origin.distanceToSqr(vehicle.position()) > 16) {
            if (SEARCHES.size() >= 128 && !SEARCHES.containsKey(vehicle.getUUID())) return new Route(List.of(), Status.PENDING);
            search = new Search(level, vehicle.position(), requestedTarget, tick);
            SEARCHES.put(vehicle.getUUID(), search);
        }
        search.used = tick;
        if (search.failedUntil > tick) return new Route(List.of(), Status.NO_PATH);
        if (search.failedUntil != 0) {
            search = new Search(level, vehicle.position(), requestedTarget, tick);
            SEARCHES.put(vehicle.getUUID(), search);
        }
        try (var budget = com.arxyt.dominionsword.api.DominionPathBudget.acquire(level.getServer(),
                level.getServer().getTickCount(), vehicle.getUUID().toString() + ":mech")) {
            if (budget == null) return new Route(List.of(), Status.PENDING);
            while (!search.open.isEmpty() && search.expanded < MAX_NODES && budget.step()) {
                OpenNode item = search.open.poll();
                Node current = item.node();
                if (!search.closed.add(current)) continue;
                search.expanded++;
                double distance = heuristic(current, search.goalX, search.goalZ);
                if (distance < search.bestDistance) { search.best = current; search.bestDistance = distance; }
                if (distance <= 2.5D && Math.abs(current.y - search.target.y) <= 2) {
                    Route result = finish(vehicle, search, current);
                    SEARCHES.remove(vehicle.getUUID());
                    return result;
                }
                for (int[] d : DIRECTIONS) {
                    int nx = current.x + d[0] * STEP, nz = current.z + d[1] * STEP;
                    if (Math.hypot(nx - search.start.x, nz - search.start.z) > MAX_RADIUS + 8) continue;
                    Integer ny = findStandY(level, vehicle, nx + .5, nz + .5, current.y, 2);
                    if (ny == null || !sweepClear(level, vehicle, current.center(), new Vec3(nx + .5, ny, nz + .5))) continue;
                    Node next = new Node(nx, ny, nz);
                    if (search.closed.contains(next)) continue;
                    relax(current, next, false, Math.hypot(d[0],d[1]) * STEP + Math.abs(ny-current.y)*.8,
                            search.goalX, search.goalZ, search.open, search.cost, search.from);
                }
                // Jump skills remain available separately. Route following cannot yet execute jump edges.
            }
            if (search.open.isEmpty() || search.expanded >= MAX_NODES) {
                if (!search.best.equals(search.start) && search.bestDistance + 2 < heuristic(search.start, search.goalX, search.goalZ)) {
                    Route result = finish(vehicle, search, search.best);
                    SEARCHES.remove(vehicle.getUUID());
                    return result;
                }
                search.failedUntil = tick + 40;
                return new Route(List.of(), Status.NO_PATH);
            }
            return new Route(List.of(), Status.PENDING);
        }
    }

    private static Route finish(Entity vehicle, Search search, Node end) {
        LinkedList<RoutePoint> points = new LinkedList<>();
        for (Node cursor = end; !cursor.equals(search.start); ) {
            StepFrom previous = search.from.get(cursor);
            if (previous == null) break;
            points.addFirst(new RoutePoint(cursor.center(), false));
            cursor = previous.previous();
        }
        points.addFirst(new RoutePoint(search.origin, false));
        Vec3 last = points.getLast().position();
        // The last segment is tested just like every ordinary edge, including height and support.
        boolean full = false;
        if (last.distanceToSqr(search.requested) <= 16) {
            Integer y = findStandY(search.level, vehicle, search.requested.x, search.requested.z, end.y, 2);
            if (y != null && Math.abs(y - search.requested.y) <= 1) {
                Vec3 exact = new Vec3(search.requested.x, y, search.requested.z);
                if (sweepClear(search.level, vehicle, last, exact)) {
                    if (last.distanceToSqr(exact) > .01) points.add(new RoutePoint(exact,false));
                    full = true;
                }
            }
        }
        return new Route(points, full ? Status.COMPLETE : Status.PARTIAL);
    }

    private static final class Search {
        final ServerLevel level;
        final Vec3 origin, requested, target;
        final Node start;
        final int goalX, goalZ;
        final PriorityQueue<OpenNode> open = new PriorityQueue<>(Comparator.comparingDouble(OpenNode::score));
        final Map<Node,Double> cost = new HashMap<>();
        final Map<Node,StepFrom> from = new HashMap<>();
        final Set<Node> closed = new HashSet<>();
        Node best;
        double bestDistance;
        int expanded;
        long used, failedUntil;
        Search(ServerLevel level, Vec3 origin, Vec3 requested, long tick) {
            this.level=level; this.origin=origin; this.requested=requested;
            target=clampRange(origin,requested,MAX_RADIUS);
            start=new Node((int)Math.floor(origin.x),(int)Math.floor(origin.y),(int)Math.floor(origin.z));
            goalX=(int)Math.floor(target.x); goalZ=(int)Math.floor(target.z);
            best=start; bestDistance=heuristic(start,goalX,goalZ); used=tick;
            cost.put(start,0D); open.add(new OpenNode(start,bestDistance));
        }
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
        if (!level.hasChunkAt(BlockPos.containing(x, baseY, z))) return null;
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
            if (!level.hasChunkAt(below)) return false;
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
    public enum Status { COMPLETE, PARTIAL, PENDING, NO_PATH }
    public record Route(List<RoutePoint> points, Status status) {
        public Route(List<RoutePoint> points) { this(points, points == null || points.isEmpty() ? Status.NO_PATH : Status.COMPLETE); }
        public Route { points = points == null ? List.of() : List.copyOf(points); }
        public static Route direct(Vec3 start, Vec3 target) {
            if (target == null) return new Route(List.of(new RoutePoint(start, false)));
            return new Route(List.of(new RoutePoint(start, false), new RoutePoint(target, false)));
        }
        public List<Vec3> positions() { return points.stream().map(RoutePoint::position).toList(); }
    }
}
