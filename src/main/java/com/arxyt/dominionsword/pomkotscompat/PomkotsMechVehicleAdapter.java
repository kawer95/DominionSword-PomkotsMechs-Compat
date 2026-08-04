package com.arxyt.dominionsword.pomkotscompat;

import com.arxyt.dominionsword.api.DominionVehicleAdapter;
import com.arxyt.dominionsword.api.DominionSkillProvider;
import com.arxyt.dominionsword.api.VehicleDismounts;
import com.arxyt.dominionsword.control.FactionAccess;
import com.arxyt.dominionsword.control.PlayerControl;
import com.arxyt.dominionsword.pomkotscompat.control.MechControlBridge;
import com.arxyt.dominionsword.pomkotscompat.control.MechControlFrame;
import com.arxyt.dominionsword.pomkotscompat.control.MechPathPlanner;
import grcmcs.minecraft.mods.pomkotsmechs.client.input.DriverInput;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.Pmv03pEntity;
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.PomkotsVehicleBase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PomkotsMechVehicleAdapter implements DominionVehicleAdapter, DominionSkillProvider {
    private static final Set<String> SUPPORTED = Set.of("pmv01", "pmv01b", "pmv02", "pmv03p");

    private static final short FORWARD = 1, BACK = 2, LEFT = 4, RIGHT = 8, EVASION = 16, JUMP = 32;
    private static final short WEAPON_ARM_R = 64, WEAPON_ARM_L = 128, WEAPON_SHOULDER_R = 256,
            WEAPON_SHOULDER_L = 512, LOCK = 1024, MODE = 2048;

    private static final String SKILL_VECTOR_BOOST = "pomkots_vector_boost", ACTION_EVASION = "pomkots_evade",
            ACTION_MODE = "pomkots_weapon_mode", ACTION_LEFT_ARM = "pomkots_left_arm",
            ACTION_RIGHT_SHOULDER = "pomkots_right_shoulder", ACTION_LEFT_SHOULDER = "pomkots_left_shoulder";

    private static final Map<UUID, ActiveRoute> ROUTES = new ConcurrentHashMap<>();
    private static final Map<UUID, JumpState> JUMPS = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingPulse> PULSES = new ConcurrentHashMap<>();
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    @Override public int priority() { return 100; }

    @Override
    public boolean supports(Entity vehicle) {
        if (!(vehicle instanceof PomkotsVehicleBase)) return false;
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType());
        return key != null && "pomkotsmechs".equals(key.getNamespace()) && SUPPORTED.contains(key.getPath());
    }

    @Override
    public boolean selectable(Entity vehicle) {
        LivingEntity driver = driver(vehicle);
        return supports(vehicle) && driver instanceof Mob && !(driver instanceof Player);
    }

    @Override
    public AABB selectionBounds(Entity vehicle) {
        return vehicle.getBoundingBox().inflate(0.35D, 0.2D, 0.35D);
    }

    @Override
    public HealthView health(Entity vehicle) {
        return vehicle instanceof LivingEntity living ? new HealthView(living.getHealth(), living.getMaxHealth()) : null;
    }

    @Override
    public List<SeatView> seats(Entity vehicle) {
        if (!supports(vehicle)) return List.of();
        return List.of(new SeatView(0, "driver", driver(vehicle)));
    }

    @Override
    public boolean hasDriver(Entity vehicle) {
        LivingEntity driver = driver(vehicle);
        return driver instanceof Mob && !(driver instanceof Player);
    }

    @Override
    public boolean select(ServerPlayer player, Entity vehicle) {
        LivingEntity driver = driver(vehicle);
        return player != null && driver instanceof Mob && !(driver instanceof Player)
                && (player.getUUID().equals(PlayerControl.controller(driver)) || FactionAccess.canControl(player, driver));
    }

    @Override
    public boolean release(ServerPlayer player, Entity vehicle) {
        stop(vehicle, true);
        return true;
    }

    @Override
    public Vec3 boardingPosition(Mob unit, Entity vehicle) {
        AABB access = vehicle.getBoundingBox().inflate(1.15D, 0.0D, 1.15D);
        double x = Mth.clamp(unit.getX(), access.minX, access.maxX);
        double z = Mth.clamp(unit.getZ(), access.minZ, access.maxZ);
        boolean insideX = unit.getX() > access.minX && unit.getX() < access.maxX;
        boolean insideZ = unit.getZ() > access.minZ && unit.getZ() < access.maxZ;
        if (insideX && insideZ) {
            double west = unit.getX() - access.minX, east = access.maxX - unit.getX();
            double north = unit.getZ() - access.minZ, south = access.maxZ - unit.getZ();
            double nearest = Math.min(Math.min(west, east), Math.min(north, south));
            if (nearest == west) x = access.minX;
            else if (nearest == east) x = access.maxX;
            else if (nearest == north) z = access.minZ;
            else z = access.maxZ;
        }
        return new Vec3(x, vehicle.getY(), z);
    }

    @Override
    public boolean canBoardFrom(Mob unit, Entity vehicle) {
        Vec3 point = boardingPosition(unit, vehicle);
        return unit.distanceToSqr(point) <= 2.25D * 2.25D;
    }

    @Override
    public boolean board(ServerPlayer player, Mob unit, Entity vehicle, int seat, boolean force) {
        if (!supports(vehicle) || unit == null || seat != 0 || !unit.isAlive()) return false;
        if (player != null && !player.getUUID().equals(PlayerControl.controller(unit))) return false;
        LivingEntity occupant = driver(vehicle);
        if (occupant instanceof Player) {
            if (player != null) player.displayClientMessage(Component.translatable("message.dominionsword_pomkotsmechs_compat.player_driver"), true);
            return false;
        }
        if (occupant != null && occupant != unit) {
            if (!force || !(occupant instanceof Mob) || player == null || !player.getUUID().equals(PlayerControl.controller(occupant))) return false;
            VehicleDismounts.dismount(vehicle, occupant);
        }
        if (vehicle instanceof Pmv03pEntity pmv03p && !pmv03p.isMainMode()) pmv03p.setDriverInput(new DriverInput(MODE));
        return unit.getVehicle() == vehicle || unit.startRiding(vehicle, true);
    }

    @Override
    public boolean dismount(ServerPlayer player, Entity vehicle, int seat) {
        if (seat != 0) return false;
        LivingEntity passenger = driver(vehicle);
        if (passenger == null) return false;
        stop(vehicle, true);
        return VehicleDismounts.dismount(vehicle, passenger);
    }

    @Override
    public boolean dismountAll(ServerPlayer player, Entity vehicle) {
        return dismount(player, vehicle, 0);
    }

    @Override
    public void prepareMoveRoute(ServerPlayer player, Entity vehicle, Vec3 target) {
        if (!canControl(player, vehicle) || target == null) return;
        MechPathPlanner.Route route = MechPathPlanner.plan(vehicle, target);
        ROUTES.put(vehicle.getUUID(), new ActiveRoute(target, route, 1, vehicle.level().getGameTime()));
    }

    @Override
    public List<Vec3> plannedMoveRoute(ServerPlayer player, Entity vehicle, Vec3 target) {
        if (!supports(vehicle) || target == null) return List.of();
        ActiveRoute route = ensureRoute(vehicle, target);
        return route.route.positions();
    }

    @Override
    public boolean move(ServerPlayer player, Entity vehicle, Vec3 target) {
        if (!canControl(player, vehicle) || target == null) { stop(vehicle, false); return false; }
        return driveTo(vehicle, target, false);
    }

    @Override
    public boolean attack(ServerPlayer player, Entity vehicle, LivingEntity target) {
        if (!canControl(player, vehicle) || target == null || !target.isAlive() || target.level() != vehicle.level()) {
            stop(vehicle, false);
            return false;
        }
        LivingEntity pilot = driver(vehicle);
        if (pilot == target || (player != null && FactionAccess.sameFaction(player, pilot, target))
                || Objects.equals(PlayerControl.controller(target), PlayerControl.controller(pilot))) {
            stop(vehicle, false);
            return false;
        }
        PomkotsVehicleBase mech = (PomkotsVehicleBase) vehicle;
        mech.getLockTargets().lockTargetHard(target);
        double distance = vehicle.distanceTo(target);
        if (distance > 32.0D || !mech.hasLineOfSight(target)) {
            Vec3 away = target.position().vectorTo(vehicle.position()).multiply(1.0D, 0.0D, 1.0D);
            if (away.lengthSqr() < 0.01D) away = new Vec3(0, 0, 1);
            Vec3 firingPosition = target.position().add(away.normalize().scale(28.0D));
            return driveTo(vehicle, firingPosition, true);
        }
        aimAt(pilot, target.getBoundingBox().getCenter());
        setFrame(mech, 0.0F, 0.0F, pilot.getYRot(), pilot.getXRot());
        submit(mech, (short)(WEAPON_ARM_R | LOCK));
        return true;
    }

    @Override
    public List<ActionView> actions(Entity vehicle) {
        if (!hasDriver(vehicle)) return List.of();
        List<ActionView> result = new ArrayList<>();
        result.add(new ActionView(ACTION_EVASION, "@menu.dominionsword_pomkotsmechs_compat.evade"));
        if (!(vehicle instanceof Pmv03pEntity)) result.add(new ActionView(ACTION_MODE, "@menu.dominionsword_pomkotsmechs_compat.weapon_mode"));
        result.add(new ActionView(ACTION_LEFT_ARM, "@menu.dominionsword_pomkotsmechs_compat.left_arm"));
        result.add(new ActionView(ACTION_RIGHT_SHOULDER, "@menu.dominionsword_pomkotsmechs_compat.right_shoulder"));
        result.add(new ActionView(ACTION_LEFT_SHOULDER, "@menu.dominionsword_pomkotsmechs_compat.left_shoulder"));
        return result;
    }

    @Override
    public boolean performAction(ServerPlayer player, Entity vehicle, String actionId) {
        if (!canControl(player, vehicle) || !(vehicle instanceof PomkotsVehicleBase mech)) return false;
        short bits = switch (actionId) {
            case ACTION_EVASION -> EVASION;
            case ACTION_MODE -> MODE;
            case ACTION_LEFT_ARM -> WEAPON_ARM_L;
            case ACTION_RIGHT_SHOULDER -> WEAPON_SHOULDER_R;
            case ACTION_LEFT_SHOULDER -> WEAPON_SHOULDER_L;
            default -> 0;
        };
        if (bits == 0 || bits == MODE && vehicle instanceof Pmv03pEntity) return false;
        PULSES.put(vehicle.getUUID(), new PendingPulse(bits, 2));
        return true;
    }

    @Override
    public List<SkillView> skills(ServerPlayer commander, Entity actor) {
        if (!canControl(commander, actor)) return List.of();
        return List.of(new SkillView(SKILL_VECTOR_BOOST,
                "skill.dominionsword_pomkotsmechs_compat.vector_boost",
                "minecraft:textures/item/firework_rocket.png", SkillType.POINT, true, 0, 0));
    }

    @Override
    public boolean activate(SkillContext context, String skillId) {
        if (!SKILL_VECTOR_BOOST.equals(skillId) || context == null || context.target() == null
                || context.target().position() == null || !canControl(context.commander(), context.actor())
                || !(context.actor() instanceof PomkotsVehicleBase mech) || !mech.onGround()
                || JUMPS.containsKey(mech.getUUID())) return false;
        Optional<Vec3> landing = MechPathPlanner.safeJumpLanding(mech, context.target().position());
        if (landing.isEmpty()) {
            context.commander().displayClientMessage(Component.translatable(
                    "message.dominionsword_pomkotsmechs_compat.jump_unsafe"), true);
            return false;
        }
        ROUTES.remove(mech.getUUID());
        startJump(mech, landing.get(), true);
        return true;
    }

    public void tick(MinecraftServer server) {
        if (server == null) return;
        Set<UUID> known = new HashSet<>();
        known.addAll(ACTIVE); known.addAll(JUMPS.keySet()); known.addAll(PULSES.keySet());
        for (UUID id : known) {
            Entity entity = find(server, id);
            if (!(entity instanceof PomkotsVehicleBase mech) || !supports(mech)) {
                cleanup(id);
                continue;
            }
            LivingEntity pilot = mech.getDrivingPassenger();
            if (!(pilot instanceof Mob) || pilot instanceof Player) {
                stop(mech, true);
                continue;
            }
            if (mech instanceof Pmv03pEntity pmv03p && !pmv03p.isMainMode()) {
                submit(mech, MODE);
                setFrame(mech, 0, 0, mech.getYRot(), 0);
            }
            JumpState jump = JUMPS.get(id);
            if (jump != null && jump.manual) advanceJump(mech, jump);
            PendingPulse pulse = PULSES.get(id);
            if (pulse != null) {
                if (pulse.ticks == 2) submit(mech, pulse.bits);
                else submit(mech, (short)0);
                if (--pulse.ticks <= 0) PULSES.remove(id);
            }
        }
    }

    private boolean driveTo(Entity vehicle, Vec3 finalTarget, boolean combatApproach) {
        PomkotsVehicleBase mech = (PomkotsVehicleBase) vehicle;
        if (mech instanceof Pmv03pEntity pmv03p && !pmv03p.isMainMode()) submit(mech, MODE);
        JumpState jump = JUMPS.get(vehicle.getUUID());
        if (jump != null) return advanceJump(mech, jump);

        ActiveRoute active = ensureRoute(vehicle, finalTarget);
        List<MechPathPlanner.RoutePoint> points = active.route.points();
        if (points.isEmpty()) return false;
        while (active.index < points.size() - 1 && flatDistance(vehicle.position(), points.get(active.index).position()) < 1.35D) active.index++;
        if (active.index >= points.size() - 1
                && flatDistance(vehicle.position(), points.get(points.size() - 1).position()) < 1.35D
                && flatDistance(vehicle.position(), finalTarget) > 2.0D) {
            active = rebuildRoute(vehicle, finalTarget);
            points = active.route.points();
        }
        MechPathPlanner.RoutePoint point = points.get(Math.min(active.index, points.size() - 1));
        Vec3 target = point.position();
        double finalDistance = flatDistance(vehicle.position(), finalTarget);
        if (finalDistance <= 0.45D) { stopMovement(mech); return true; }

        float desiredYaw = yawTo(vehicle.position(), target);
        float yawDelta = Mth.wrapDegrees(desiredYaw - vehicle.getYRot());
        if (point.jumpFromPrevious() && mech.onGround()) {
            startJump(mech, target, false);
            return advanceJump(mech, JUMPS.get(vehicle.getUUID()));
        }
        if (mech.onGround() && Math.abs(yawDelta) < 18.0F && MechPathPlanner.blockedAhead(mech, desiredYaw, 3.0D)) {
            Optional<Vec3> landing = MechPathPlanner.safeJumpLanding(mech, desiredYaw);
            if (landing.isPresent() && mech.getEnergy() >= 30) {
                startJump(mech, landing.get(), false);
                return advanceJump(mech, JUMPS.get(vehicle.getUUID()));
            }
        }

        float commandedYaw = vehicle.getYRot() + Mth.clamp(yawDelta, -9.0F, 9.0F);
        float forward = Math.abs(yawDelta) < 78.0F ? 1.0F : 0.0F;
        if (combatApproach && finalDistance < 1.0D) forward = 0.0F;
        setFrame(mech, forward, 0.0F, commandedYaw, 0.0F);
        submit(mech, forward > 0 ? FORWARD : (short)0);
        ACTIVE.add(mech.getUUID());
        return true;
    }

    private boolean advanceJump(PomkotsVehicleBase mech, JumpState jump) {
        if (jump == null) return false;
        jump.ticks++;
        Vec3 delta = jump.landing.subtract(mech.position());
        float desiredYaw = yawTo(mech.position(), jump.landing);
        float yawDelta = Mth.wrapDegrees(desiredYaw - mech.getYRot());
        float commandedYaw = mech.getYRot() + Mth.clamp(yawDelta, -12.0F, 12.0F);
        if (!mech.onGround()) jump.wasAirborne = true;
        if (jump.wasAirborne && mech.onGround()) {
            JUMPS.remove(mech.getUUID());
            stopMovement(mech);
            return true;
        }
        if (jump.ticks > 30 || (jump.wasAirborne && delta.horizontalDistanceSqr() < 1.2D && mech.getDeltaMovement().y <= 0.0D)) {
            JUMPS.remove(mech.getUUID());
            submit(mech, (short)0);
            setFrame(mech, 0.0F, 0.0F, commandedYaw, 0.0F);
            return true;
        }
        boolean holdLift = jump.ticks <= 22 && (!jump.wasAirborne || mech.getDeltaMovement().y > -0.25D);
        short keys = FORWARD;
        if (holdLift) keys |= JUMP;
        setFrame(mech, 1.0F, 0.0F, commandedYaw, 0.0F);
        submit(mech, keys);
        ACTIVE.add(mech.getUUID());
        return true;
    }

    private void startJump(PomkotsVehicleBase mech, Vec3 landing, boolean manual) {
        JUMPS.put(mech.getUUID(), new JumpState(landing, manual));
        ACTIVE.add(mech.getUUID());
    }

    private ActiveRoute ensureRoute(Entity vehicle, Vec3 target) {
        ActiveRoute route = ROUTES.get(vehicle.getUUID());
        long now = vehicle.level().getGameTime();
        if (route == null || route.target.distanceToSqr(target) > 1.0D || now - route.builtAt > 80
                || route.index >= route.route.points().size() && flatDistance(vehicle.position(), target) > 1.0D) {
            route = rebuildRoute(vehicle, target);
        }
        return route;
    }

    private ActiveRoute rebuildRoute(Entity vehicle, Vec3 target) {
        ActiveRoute route = new ActiveRoute(target, MechPathPlanner.plan(vehicle, target), 1, vehicle.level().getGameTime());
        ROUTES.put(vehicle.getUUID(), route);
        return route;
    }

    private boolean canControl(ServerPlayer player, Entity vehicle) {
        if (!supports(vehicle)) return false;
        LivingEntity pilot = driver(vehicle);
        if (!(pilot instanceof Mob) || pilot instanceof Player) return false;
        if (player == null) return true;
        UUID vehicleOwner = PlayerControl.controller(vehicle);
        return player.getUUID().equals(vehicleOwner)
                || vehicleOwner == null && (player.getUUID().equals(PlayerControl.controller(pilot)) || FactionAccess.canControl(player, pilot));
    }

    private static LivingEntity driver(Entity vehicle) {
        return vehicle instanceof PomkotsVehicleBase mech ? mech.getDrivingPassenger() : null;
    }

    private static void aimAt(LivingEntity pilot, Vec3 target) {
        Vec3 delta = target.subtract(pilot.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float)(-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG));
        pilot.setYRot(yaw); pilot.setYHeadRot(yaw); pilot.yBodyRot = yaw; pilot.setXRot(pitch);
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        return (float)Mth.wrapDegrees(-(Mth.atan2(to.x - from.x, to.z - from.z) * Mth.RAD_TO_DEG));
    }

    private static double flatDistance(Vec3 a, Vec3 b) { return Math.hypot(a.x - b.x, a.z - b.z); }

    private static void setFrame(PomkotsVehicleBase mech, float forward, float strafe, float yaw, float pitch) {
        ((MechControlBridge)mech).dominion$setControlFrame(new MechControlFrame(true, forward, strafe, yaw, pitch));
    }

    private static void submit(PomkotsVehicleBase mech, short bits) { mech.setDriverInput(new DriverInput(bits)); }

    private static void stopMovement(PomkotsVehicleBase mech) {
        submit(mech, (short)0);
        ((MechControlBridge)mech).dominion$setControlFrame(new MechControlFrame(true, 0, 0, mech.getYRot(), 0));
        mech.setDeltaMovement(mech.getDeltaMovement().multiply(0.35D, 1.0D, 0.35D));
    }

    private static void stop(Entity vehicle, boolean clearTasks) {
        if (vehicle instanceof PomkotsVehicleBase mech) {
            submit(mech, (short)0);
            mech.getLockTargets().clearLockTargets();
            ((MechControlBridge)mech).dominion$setControlFrame(MechControlFrame.INACTIVE);
            mech.setNoGravity(false);
            LivingEntity pilot = mech.getDrivingPassenger();
            if (pilot != null) { pilot.zza = 0; pilot.xxa = 0; }
        }
        UUID id = vehicle.getUUID();
        ACTIVE.remove(id); JUMPS.remove(id); PULSES.remove(id);
        if (clearTasks) ROUTES.remove(id);
    }

    private static Entity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private static void cleanup(UUID id) { ACTIVE.remove(id); ROUTES.remove(id); JUMPS.remove(id); PULSES.remove(id); }

    private static final class ActiveRoute {
        final Vec3 target; final MechPathPlanner.Route route; final long builtAt; int index;
        ActiveRoute(Vec3 target, MechPathPlanner.Route route, int index, long builtAt) {
            this.target = target; this.route = route; this.index = index; this.builtAt = builtAt;
        }
    }
    private static final class JumpState {
        final Vec3 landing; final boolean manual; int ticks; boolean wasAirborne;
        JumpState(Vec3 landing, boolean manual) { this.landing = landing; this.manual = manual; }
    }
    private static final class PendingPulse {
        final short bits; int ticks;
        PendingPulse(short bits, int ticks) { this.bits = bits; this.ticks = ticks; }
    }
}
