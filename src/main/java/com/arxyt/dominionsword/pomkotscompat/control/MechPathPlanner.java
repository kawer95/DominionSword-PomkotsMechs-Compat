package com.arxyt.dominionsword.pomkotscompat.control;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import com.arxyt.dominionsword.api.DominionFrontierPlanner;
import com.arxyt.dominionsword.api.DominionGroundTransitionPolicy;

/** Small footprint-aware A* used only by the Pomkots vehicle adapter. */
public final class MechPathPlanner {
    private static final int STEP = 1;
    private static final int MAX_NODES = 4096;
    private static final int MAX_RADIUS = 256;
    private static final int[][] DIRECTIONS = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};

    private MechPathPlanner() {}

    private static final Map<UUID, Search> SEARCHES = new HashMap<>();
    public static void clear() { SEARCHES.values().forEach(s -> s.planner.cancel()); SEARCHES.clear(); }

    /** Each admitted probe checks one movement edge; no terrain array is prepared in advance. */
    public static Route plan(Entity vehicle, Vec3 requestedTarget) {
        if (!(vehicle.level() instanceof ServerLevel level) || requestedTarget == null)
            return new Route(List.of(), Status.NO_PATH);
        long tick = level.getServer().getTickCount();
        SEARCHES.entrySet().removeIf(e -> {
            boolean expired=e.getValue().level.getServer()!=level.getServer() || tick-e.getValue().used>100;
            if(expired)e.getValue().planner.cancel();return expired;
        });
        Search search=SEARCHES.get(vehicle.getUUID());
        if(search!=null && (search.level!=level || search.requested.distanceToSqr(requestedTarget)>.01
                || Math.abs(search.requested.y-requestedTarget.y)>.25
                || search.origin.distanceToSqr(vehicle.position())>16)) {
            search.planner.cancel();SEARCHES.remove(vehicle.getUUID());search=null;
        }
        if(search==null) {
            if(SEARCHES.size()>=16)return new Route(List.of(),Status.PENDING);
            search=new Search(level,vehicle,requestedTarget,tick);SEARCHES.put(vehicle.getUUID(),search);
        }
        search.used=tick;
        if(search.failedUntil>tick)return new Route(List.of(),Status.NO_PATH);
        if(search.failedUntil!=0) {
            search=new Search(level,vehicle,requestedTarget,tick);SEARCHES.put(vehicle.getUUID(),search);
        }
        try(var budget=com.arxyt.dominionsword.api.DominionPathBudget.acquire(level.getServer(),tick,vehicle.getUUID()+":mech")) {
            if(budget==null)return new Route(List.of(),Status.PENDING);
            if(search.candidate==null) {
                var result=search.planner.advance(budget::step);
                if(result.status()==DominionFrontierPlanner.Status.PENDING)return new Route(List.of(),Status.PENDING);
                if(result.status()!=DominionFrontierPlanner.Status.COMPLETE && result.status()!=DominionFrontierPlanner.Status.PARTIAL) {
                    search.failedUntil=tick+40;return new Route(List.of(),Status.NO_PATH);
                }
                search.candidate=result.path().stream().map(n -> new RoutePoint(n.position,false)).toList();
                search.status=result.status()==DominionFrontierPlanner.Status.COMPLETE?Status.COMPLETE:Status.PARTIAL;
            }
            while(search.validated<search.candidate.size()) {
                if(!budget.step())return new Route(List.of(),Status.PENDING);
                if(!walkSweep(level,vehicle,search.candidate.get(search.validated-1).position(),search.candidate.get(search.validated).position(),search.maxRise)) {
                    search.failedUntil=tick+40;search.candidate=null;return new Route(List.of(),Status.NO_PATH);
                }
                search.validated++;
            }
            Route result=new Route(search.candidate,search.status);SEARCHES.remove(vehicle.getUUID());return result;
        }
    }

    private record WalkNode(int x,int z,Vec3 position) {}
    private record WalkKey(int x,int z,int y16) {}
    private static final class Search {
        final ServerLevel level;
        final Vec3 origin,requested;
        final double maxRise;
        final DominionFrontierPlanner<WalkNode,WalkKey> planner;
        List<RoutePoint> candidate;
        Status status;
        int validated=1;
        long used,failedUntil;
        Search(ServerLevel level,Entity vehicle,Vec3 requested,long tick) {
            this.level=level;this.origin=vehicle.position();this.requested=requested;this.used=tick;
            this.maxRise=Math.max(0,Math.min(2,vehicle.maxUpStep()));
            planner=new DominionFrontierPlanner<>(new DominionFrontierPlanner.Domain<WalkNode,WalkKey>() {
                public WalkKey key(WalkNode n) { return new WalkKey(n.x,n.z,(int)Math.round(n.position.y*16)); }
                public double heuristic(WalkNode n) { return Math.max(0,Math.hypot(n.position.x-requested.x,n.position.z-requested.z)-.75)+Math.abs(n.position.y-requested.y); }
                public boolean isGoal(WalkNode n) { return Math.hypot(n.position.x-requested.x,n.position.z-requested.z)<=.75 && DominionGroundTransitionPolicy.sameLevel(n.position.y,requested.y,.75); }
                public boolean isPartialBoundary(WalkNode n) {
                    return Math.hypot(requested.x-origin.x,requested.z-origin.z)>MAX_RADIUS
                            && Math.hypot(n.position.x-origin.x,n.position.z-origin.z)>=MAX_RADIUS-1.5
                            && heuristic(n)<heuristic(new WalkNode(0,0,origin));
                }
                public int successorCount(WalkNode n) { return 9; }
                public DominionFrontierPlanner.Edge<WalkNode> successor(WalkNode from,int action) {
                    int x,z;Vec3 horizontal;
                    if(action==8) {
                        if(Math.hypot(from.position.x-requested.x,from.position.z-requested.z)>2)return null;
                        x=(int)Math.round((requested.x-origin.x)/STEP);z=(int)Math.round((requested.z-origin.z)/STEP);
                        horizontal=new Vec3(requested.x,from.position.y,requested.z);
                    } else {
                        x=from.x+DIRECTIONS[action][0];z=from.z+DIRECTIONS[action][1];
                        horizontal=new Vec3(origin.x+x*STEP,from.position.y,origin.z+z*STEP);
                    }
                    if(Math.hypot(horizontal.x-origin.x,horizontal.z-origin.z)>MAX_RADIUS)return null;
                    Vec3 stand=walkStand(level,vehicle,horizontal,from.position.y,maxRise);
                    if(stand==null || !walkSweep(level,vehicle,from.position,stand,maxRise))return null;
                    double cost=Math.hypot(stand.x-from.position.x,stand.z-from.position.z)
                            +DominionGroundTransitionPolicy.heightPenalty(from.position.y,stand.y);
                    return new DominionFrontierPlanner.Edge<>(new WalkNode(x,z,stand),Math.max(.01,cost));
                }
            },new WalkNode(0,0,origin),MAX_NODES,32768);
        }
    }

    /** Samples only the next footprint; higher floors are never selected by a global heightmap. */
    private static Vec3 walkStand(ServerLevel level,Entity vehicle,Vec3 around,double referenceY,double maxRise) {
        if(!loadedBox(level,placementBox(vehicle,around.x,referenceY,around.z).inflate(0,3,0)))return null;
        Set<Double> heights=new HashSet<>();heights.add(referenceY);
        AABB box=placementBox(vehicle,around.x,referenceY,around.z);
        double hx=box.getXsize()*.4,hz=box.getZsize()*.4;
        for(double[] offset:new double[][]{{0,0},{hx,hz},{hx,-hz},{-hx,hz},{-hx,-hz}}) {
            BlockPos base=BlockPos.containing(around.x+offset[0],referenceY,around.z+offset[1]);
            for(int dy=-3;dy<=2;dy++) {
                BlockPos floor=base.offset(0,dy,0);
                var shape=level.getBlockState(floor).getCollisionShape(level,floor);
                if(!shape.isEmpty())heights.add(floor.getY()+shape.max(net.minecraft.core.Direction.Axis.Y));
            }
        }
        List<Double> ordered=new ArrayList<>(heights);
        ordered.sort(Comparator.comparingDouble((Double y)->Math.abs(y-referenceY)).thenComparingDouble(Double::doubleValue));
        for(double y:ordered) {
            if(!DominionGroundTransitionPolicy.allowed(referenceY,y,maxRise,2))continue;
            AABB pose=placementBox(vehicle,around.x,y,around.z).move(0,.001,0);
            if(!level.noCollision(vehicle,pose))continue;
            int contact=0;
            for(double[] offset:new double[][]{{0,0},{hx,hz},{hx,-hz},{-hx,hz},{-hx,-hz}}) {
                double x=around.x+offset[0],z=around.z+offset[1];
                if(level.getBlockCollisions(vehicle,new AABB(x-.04,y-.15,z-.04,x+.04,y+.01,z+.04)).iterator().hasNext())contact++;
            }
            if(contact>=4)return new Vec3(around.x,y,around.z);
        }
        return null;
    }

    public static boolean canWalkEdge(Entity vehicle,Vec3 to) {
        if (!(vehicle.level() instanceof ServerLevel level) || vehicle.position().distanceToSqr(to)>16)return false;
        double rise=Math.max(0,Math.min(2,vehicle.maxUpStep()));
        Vec3 stand=walkStand(level,vehicle,to,to.y,rise);
        return stand!=null && Math.abs(stand.y-to.y)<.15 && walkSweep(level,vehicle,vehicle.position(),to,rise);
    }

    private static boolean walkSweep(ServerLevel level,Entity vehicle,Vec3 from,Vec3 to,double maxRise) {
        if(!DominionGroundTransitionPolicy.allowed(from.y,to.y,maxRise,2))return false;
        // A step is lift, traverse, then settle. Every part checks the actual physical bounding box.
        double high=Math.max(from.y,to.y);
        List<Vec3> corners=List.of(from,new Vec3(from.x,high,from.z),new Vec3(to.x,high,to.z),to);
        for(int segment=1;segment<corners.size();segment++) {
            Vec3 a=corners.get(segment-1),b=corners.get(segment);int samples=Math.max(1,(int)Math.ceil(a.distanceTo(b)/.25));
            for(int i=1;i<=samples;i++) {
                Vec3 point=a.lerp(b,i/(double)samples);AABB pose=placementBox(vehicle,point.x,point.y,point.z).move(0,.001,0);
                if(!loadedBox(level,pose) || !level.noCollision(vehicle,pose))return false;
                if(segment==2) {
                    double floorY=from.y+(to.y-from.y)*i/(double)samples;
                    if(walkStand(level,vehicle,point,floorY,maxRise)==null)return false;
                }
            }
        }
        return true;
    }
    private static boolean loadedBox(ServerLevel level,AABB box) {
        for(int x=Math.floorDiv((int)Math.floor(box.minX),16);x<=Math.floorDiv((int)Math.floor(box.maxX),16);x++)
            for(int z=Math.floorDiv((int)Math.floor(box.minZ),16);z<=Math.floorDiv((int)Math.floor(box.maxZ),16);z++)
                if(!level.hasChunkAt(new BlockPos(x*16,(int)Math.floor(box.minY),z*16)))return false;
        return true;
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
