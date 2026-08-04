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
import grcmcs.minecraft.mods.pomkotsmechs.entity.vehicle.custom.Pmvc01Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PomkotsMechVehicleAdapter implements DominionVehicleAdapter, DominionSkillProvider {
    private static final Set<String> SUPPORTED = Set.of("pmv01", "pmv01b", "pmv02", "pmv03p", "pmv03", "pmvc01");

    private static final short FORWARD = 1, BACK = 2, LEFT = 4, RIGHT = 8, EVASION = 16, JUMP = 32;
    private static final short WEAPON_ARM_R = 64, WEAPON_ARM_L = 128, WEAPON_SHOULDER_R = 256,
            WEAPON_SHOULDER_L = 512, LOCK = 1024, MODE = 2048;

    private static final String SKILL_VECTOR_BOOST = "pomkots_vector_boost",
            SKILL_FLIGHT_MODE = "pomkots_flight_mode", SKILL_EVASION = "pomkots_evade",
            ACTION_MODE = "pomkots_weapon_mode";
    private static final String SKILL_DODO = "pomkots_ground_dodo",
            SKILL_NOSURI = "pomkots_ground_nosuri", SKILL_MUKUDORI = "pomkots_ground_mukudori";
    private static final double MELEE_SWITCH_RANGE = 10.0D, RANGED_MIN_RANGE = 10.0D,
            RANGED_PREFERRED_RANGE = 24.0D, RANGED_MAX_RANGE = 32.0D;
    private static final double VECTOR_BOOST_MAX_RANGE = 32.0D;
    private static final int VECTOR_BOOST_COOLDOWN_TICKS = 600, EVASION_COOLDOWN_TICKS = 200;
    private static final long AUTO_CONTINUOUS_EQUIPMENT_INTERVAL = 1_200L;
    private static final long AUTO_ORDNANCE_INTERVAL = 2_000L;

    private static final Set<String> MELEE_WEAPONS = Set.of(
            "tsurugi", "kagenobu", "takao", "jinba", "gassan", "mitake", "tenpou");
    private static final Set<String> ENGINEERING_WEAPONS = Set.of(
            "amagi", "daigomaru", "shoutou", "wada");
    private static final Set<String> GROUND_SKILL_WEAPONS = Set.of("dodo", "nosuri", "mukudori");
    private static final Map<UUID, ActiveRoute> ROUTES = new ConcurrentHashMap<>();
    private static final Map<UUID, JumpState> JUMPS = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingPulse> PULSES = new ConcurrentHashMap<>();
    private static final Map<UUID, CombatState> COMBAT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> AUTO_AUXILIARY_READY_TICKS = new ConcurrentHashMap<>();
    private static final Map<SkillCooldownKey, Long> SKILL_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, GroundMarker> GROUND_MARKERS = new ConcurrentHashMap<>();
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
        ensureGroundMode(vehicle);
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
        MechPathPlanner.Route route = planPilotRoute(vehicle, target);
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
        UUID pilotController = PlayerControl.controller(pilot);
        UUID targetController = PlayerControl.controller(target);
        if (pilot == target || (player != null && FactionAccess.sameFaction(player, pilot, target))
                || pilotController != null && pilotController.equals(targetController)) {
            stop(vehicle, false);
            return false;
        }
        PomkotsVehicleBase mech = (PomkotsVehicleBase) vehicle;
        if (requiresCombatMode(mech) && !mech.isMainMode()) {
            submit(mech, MODE);
            return true;
        }
        // The custom mech interprets weapon keys as block-placement controls while build mode is active.
        // Combat AI must never operate engineering equipment or place/remove blocks.
        if (mech instanceof Pmvc01Entity custom && custom.isBuildMode()) custom.setBuildMode(false);
        mech.getLockTargets().lockTargetHard(target);
        CombatState state = COMBAT.computeIfAbsent(mech.getUUID(), ignored -> new CombatState());
        state.lastAttackTick = mech.level().getGameTime();
        if (!target.getUUID().equals(state.target)) {
            state.target = target.getUUID();
            state.nextPrimaryTick = 0L;
        }
        double distance = vehicle.distanceTo(target);
        List<WeaponSlot> melee = meleeWeapons(mech);
        List<WeaponSlot> ranged = rangedWeapons(mech);
        long traceNow = mech.level().getGameTime();
        if (traceNow >= state.nextTraceTick) {
            MechControlBridge bridge = (MechControlBridge) mech;
            DominionSwordPomkotsCompatMod.LOGGER.info(
                    "[DS-POMKOTS-COMBAT] mech={} target={} distance={} lineOfSight={} mainMode={} melee={} ranged={} appliedInput={}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(mech.getType()), target.getType().toString(),
                    String.format(Locale.ROOT, "%.2f", distance), mech.hasLineOfSight(target), mech.isMainMode(),
                    melee.stream().map(WeaponSlot::itemId).toList(), ranged.stream().map(WeaponSlot::itemId).toList(),
                    bridge.dominion$getLastAppliedDriverInput());
            state.nextTraceTick = traceNow + 40L;
        }

        if (mech instanceof Pmv03pEntity flying && flying.isMainMode()) {
            return attackInFlight(flying, target, ranged, state);
        }

        if (!melee.isEmpty() && (ranged.isEmpty() || distance <= MELEE_SWITCH_RANGE)) {
            double reach = Math.max(5.0D, mech.getBbWidth() * 0.75D + target.getBbWidth() * 0.5D + 2.5D);
            if (distance > reach) return driveTo(vehicle, target.position(), true);
            aimAt(pilot, target.getBoundingBox().getCenter());
            setFrame(mech, 0.0F, 0.0F, pilot.getYRot(), pilot.getXRot());
            if (PULSES.containsKey(mech.getUUID())) return true;
            if (scheduleAutomaticEquipment(mech, target, state, mech.level().getGameTime(),
                    ranged.isEmpty() ? null : ranged.get(0))) return true;
            WeaponSlot weapon = melee.get(Math.floorMod(state.meleeCursor++, melee.size()));
            int hold = "takao".equals(weapon.itemId()) ? 14 : weapon.continuous() ? 8 : 1;
            PULSES.put(mech.getUUID(), new PendingPulse(weapon.bit(), hold));
            ACTIVE.add(mech.getUUID());
            return true;
        }

        if (ranged.isEmpty()) {
            stopMovement(mech);
            return true;
        }
        Vec3 away = flatAway(target.position(), vehicle.position());
        if (distance > RANGED_MAX_RANGE || !mech.hasLineOfSight(target)) {
            return driveTo(vehicle, target.position().add(away.scale(RANGED_PREFERRED_RANGE)), true);
        }
        if (distance < RANGED_MIN_RANGE) {
            return driveTo(vehicle, target.position().add(away.scale(RANGED_MIN_RANGE + 4.0D)), true);
        }
        aimAt(pilot, target.getBoundingBox().getCenter());
        setFrame(mech, 0.0F, 0.0F, pilot.getYRot(), pilot.getXRot());
        if (PULSES.containsKey(mech.getUUID())) return true;
        long now = mech.level().getGameTime();
        if (scheduleAutomaticEquipment(mech, target, state, now, ranged.get(0))) return true;
        WeaponSlot weapon = ranged.get(0);
        if (weapon.multiLock() && mech instanceof Pmvc01Entity custom && now >= state.nextPrimaryTick) {
            prepareCustomMultiLock(custom, weapon.inventorySlot(), target);
            PULSES.put(mech.getUUID(), new PendingPulse(weapon.bit(), 1));
            state.nextPrimaryTick = now + 80L;
        } else if (now >= state.nextPrimaryTick) {
            submit(mech, (short)(weapon.bit() | LOCK));
        }
        ACTIVE.add(mech.getUUID());
        return true;
    }

    @Override
    public List<ActionView> actions(Entity vehicle) {
        if (!hasDriver(vehicle)) return List.of();
        List<ActionView> result = new ArrayList<>();
        if (!(vehicle instanceof Pmv03pEntity)) result.add(new ActionView(ACTION_MODE, "@menu.dominionsword_pomkotsmechs_compat.weapon_mode"));
        return result;
    }

    @Override
    public boolean performAction(ServerPlayer player, Entity vehicle, String actionId) {
        if (!canControl(player, vehicle) || !(vehicle instanceof PomkotsVehicleBase mech)) return false;
        short bits = ACTION_MODE.equals(actionId) ? MODE : 0;
        if (bits == 0 || bits == MODE && vehicle instanceof Pmv03pEntity) return false;
        PULSES.put(vehicle.getUUID(), new PendingPulse(bits, 1));
        return true;
    }

    @Override
    public List<SkillView> skills(ServerPlayer commander, Entity actor) {
        if (!canControl(commander, actor)) return List.of();
        List<SkillView> result = new ArrayList<>();
        if (actor instanceof Pmv03pEntity) {
            result.add(new SkillView(SKILL_FLIGHT_MODE,
                    "skill.dominionsword_pomkotsmechs_compat.flight_mode",
                    "minecraft:textures/item/elytra.png", SkillType.INSTANT, true, 0, 0));
        }
        long vectorNow = actor.level().getGameTime();
        int evasionRemaining = (int)Math.max(0L, SKILL_COOLDOWNS.getOrDefault(
                new SkillCooldownKey(actor.getUUID(), SKILL_EVASION), 0L) - vectorNow);
        result.add(new SkillView(SKILL_EVASION,
                "skill.dominionsword_pomkotsmechs_compat.evade",
                "minecraft:textures/item/feather.png", SkillType.INSTANT,
                evasionRemaining == 0 && !PULSES.containsKey(actor.getUUID()), EVASION_COOLDOWN_TICKS,
                evasionRemaining));
        int vectorRemaining = (int)Math.max(0L, SKILL_COOLDOWNS.getOrDefault(
                new SkillCooldownKey(actor.getUUID(), SKILL_VECTOR_BOOST), 0L) - vectorNow);
        result.add(new SkillView(SKILL_VECTOR_BOOST,
                "skill.dominionsword_pomkotsmechs_compat.vector_boost",
                "minecraft:textures/item/firework_rocket.png", SkillType.POINT,
                vectorRemaining == 0 && !JUMPS.containsKey(actor.getUUID()), VECTOR_BOOST_COOLDOWN_TICKS,
                vectorRemaining, 2.5D, VECTOR_BOOST_MAX_RANGE));
        if (actor instanceof Pmvc01Entity custom) {
            addGroundWeaponSkill(result, custom, SKILL_DODO, "dodo",
                    "skill.dominionsword_pomkotsmechs_compat.ground_dodo", "minecraft:textures/item/tnt_minecart.png");
            addGroundWeaponSkill(result, custom, SKILL_NOSURI, "nosuri",
                    "skill.dominionsword_pomkotsmechs_compat.ground_nosuri", "minecraft:textures/item/firework_rocket.png");
            addGroundWeaponSkill(result, custom, SKILL_MUKUDORI, "mukudori",
                    "skill.dominionsword_pomkotsmechs_compat.ground_mukudori", "minecraft:textures/item/fire_charge.png");
        }
        return result;
    }

    @Override
    public boolean activate(SkillContext context, String skillId) {
        if (context == null || !canControl(context.commander(), context.actor())) return false;
        if (SKILL_FLIGHT_MODE.equals(skillId) && context.actor() instanceof Pmv03pEntity mech) {
            boolean enteringFlight = !mech.isMainMode();
            submit(mech, MODE);
            mech.setNoGravity(enteringFlight);
            if (!enteringFlight) {
                Vec3 movement = mech.getDeltaMovement();
                mech.setDeltaMovement(movement.x, 0.0D, movement.z);
            }
            return true;
        }
        if (SKILL_EVASION.equals(skillId) && context.actor() instanceof PomkotsVehicleBase mech) {
            long now = mech.level().getGameTime();
            SkillCooldownKey cooldown = new SkillCooldownKey(mech.getUUID(), SKILL_EVASION);
            if (SKILL_COOLDOWNS.getOrDefault(cooldown, 0L) > now || PULSES.containsKey(mech.getUUID())) return false;
            PULSES.put(mech.getUUID(), new PendingPulse(EVASION, 1));
            SKILL_COOLDOWNS.put(cooldown, now + EVASION_COOLDOWN_TICKS);
            ACTIVE.add(mech.getUUID());
            return true;
        }
        if (isGroundWeaponSkill(skillId) && context.actor() instanceof Pmvc01Entity custom
                && context.target() != null && context.target().position() != null) {
            return fireGroundWeaponSkill(context.commander(), custom, skillId, context.target().position());
        }
        if (!SKILL_VECTOR_BOOST.equals(skillId) || context.target() == null
                || context.target().position() == null
                || !(context.actor() instanceof PomkotsVehicleBase mech) || !mech.onGround()
                || JUMPS.containsKey(mech.getUUID())) return false;
        Vec3 requestedJump = context.target().position();
        double jumpDistance = flatDistance(mech.position(), requestedJump);
        if (jumpDistance < 2.5D || jumpDistance > VECTOR_BOOST_MAX_RANGE) return false;
        long now = mech.level().getGameTime();
        SkillCooldownKey cooldown = new SkillCooldownKey(mech.getUUID(), SKILL_VECTOR_BOOST);
        if (SKILL_COOLDOWNS.getOrDefault(cooldown, 0L) > now) return false;
        Optional<Vec3> landing = MechPathPlanner.safeJumpLanding(mech, requestedJump);
        if (landing.isEmpty()) {
            context.commander().displayClientMessage(Component.translatable(
                    "message.dominionsword_pomkotsmechs_compat.jump_unsafe"), true);
            return false;
        }
        if (!PlayerControl.redirectVehicleMove(context.commander(), mech, requestedJump)) return false;
        ROUTES.remove(mech.getUUID());
        startJump(mech, landing.get(), true);
        SKILL_COOLDOWNS.put(cooldown, now + VECTOR_BOOST_COOLDOWN_TICKS);
        return true;
    }

    public void tick(MinecraftServer server) {
        if (server == null) return;
        cleanupGroundMarkers(server);
        long serverTick = server.overworld().getGameTime();
        SKILL_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= serverTick);
        Set<UUID> known = new HashSet<>();
        known.addAll(ACTIVE); known.addAll(JUMPS.keySet()); known.addAll(PULSES.keySet()); known.addAll(COMBAT.keySet());
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
            CombatState combat = COMBAT.get(id);
            if (combat != null) {
                Entity combatTarget = find(server, combat.target);
                if (!(combatTarget instanceof LivingEntity livingTarget) || !livingTarget.isAlive()
                        || serverTick > combat.lastAttackTick + 1L) {
                    submit(mech, (short)0);
                    mech.getLockTargets().clearLockTargets();
                    PULSES.remove(id);
                    COMBAT.remove(id, combat);
                }
            }
            JumpState jump = JUMPS.get(id);
            if (jump != null && jump.manual) advanceJump(mech, jump);
            PendingPulse pulse = PULSES.get(id);
            if (pulse != null) {
                if (pulse.remainingTicks > 1) submit(mech, pulse.bits);
                else submit(mech, (short)0);
                if (--pulse.remainingTicks <= 0) PULSES.remove(id);
            }
        }
    }

    private boolean driveTo(Entity vehicle, Vec3 finalTarget, boolean combatApproach) {
        PomkotsVehicleBase mech = (PomkotsVehicleBase) vehicle;
        JumpState jump = JUMPS.get(vehicle.getUUID());
        if (jump != null) return true;

        ActiveRoute active = ensureRoute(vehicle, finalTarget);
        List<MechPathPlanner.RoutePoint> points = active.route.points();
        if (points.size() < 2) { stopMovement(mech); return false; }
        while (active.index < points.size() - 1 && flatDistance(vehicle.position(), points.get(active.index).position()) < 1.35D) active.index++;
        MechPathPlanner.RoutePoint point = points.get(Math.min(active.index, points.size() - 1));
        Vec3 target = point.position();
        double finalDistance = flatDistance(vehicle.position(), finalTarget);
        if (finalDistance <= 0.75D || active.index >= points.size() - 1
                && flatDistance(vehicle.position(), target) < 1.35D) {
            stopMovement(mech);
            return true;
        }

        float desiredYaw = yawTo(vehicle.position(), target);
        float yawDelta = Mth.wrapDegrees(desiredYaw - vehicle.getYRot());
        // A walking living vehicle must turn in place like a Mob.  Driving forward through a
        // large yaw error creates the endless circles produced by wheeled-vehicle steering.
        float commandedYaw = vehicle.getYRot() + Mth.clamp(yawDelta, -18.0F, 18.0F);
        float forward = Math.abs(yawDelta) <= 10.0F ? 1.0F : 0.0F;
        if (combatApproach && finalDistance < 1.0D) forward = 0.0F;
        setFrame(mech, forward, 0.0F, commandedYaw, 0.0F);
        submit(mech, forward > 0 ? FORWARD : (short)0);
        ACTIVE.add(mech.getUUID());
        return true;
    }

    private boolean advanceJump(PomkotsVehicleBase mech, JumpState jump) {
        if (jump == null) return false;
        Vec3 delta = jump.landing.subtract(mech.position());
        float desiredYaw = yawTo(mech.position(), jump.landing);
        float yawDelta = Mth.wrapDegrees(desiredYaw - mech.getYRot());
        if (!jump.wasAirborne && mech.onGround() && Math.abs(yawDelta) > 6.0F) {
            float turningYaw = mech.getYRot() + Mth.clamp(yawDelta, -18.0F, 18.0F);
            setFrame(mech, 0.0F, 0.0F, turningYaw, 0.0F);
            submit(mech, (short)0);
            ACTIVE.add(mech.getUUID());
            return true;
        }
        if (Float.isNaN(jump.heading)) jump.heading = desiredYaw;
        jump.ticks++;
        float commandedYaw = jump.heading;
        if (!mech.onGround()) jump.wasAirborne = true;
        if (jump.wasAirborne && mech.onGround()) {
            JUMPS.remove(mech.getUUID());
            settleJumpCommandAtActualLanding(mech);
            stopMovement(mech);
            return true;
        }
        if (jump.wasAirborne && delta.horizontalDistanceSqr() > 1.2D) {
            Vec3 horizontal = delta.multiply(1.0D, 0.0D, 1.0D);
            double speed = Mth.clamp(horizontal.length() * 0.16D, 0.45D, 1.15D);
            Vec3 velocity = horizontal.normalize().scale(speed);
            mech.setDeltaMovement(velocity.x, mech.getDeltaMovement().y, velocity.z);
        }
        if (jump.ticks > 65 || (jump.wasAirborne && delta.horizontalDistanceSqr() < 1.2D && mech.getDeltaMovement().y <= 0.0D)) {
            if (delta.horizontalDistanceSqr() < 1.2D) {
                mech.setDeltaMovement(0.0D, mech.getDeltaMovement().y, 0.0D);
            }
            submit(mech, (short)0);
            setFrame(mech, 0.0F, 0.0F, commandedYaw, 0.0F);
            ACTIVE.add(mech.getUUID());
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

    private static void settleJumpCommandAtActualLanding(PomkotsVehicleBase mech) {
        if (mech.getServer() == null) return;
        UUID controller = PlayerControl.controller(mech);
        ServerPlayer commander = controller == null ? null : mech.getServer().getPlayerList().getPlayer(controller);
        if (commander != null && commander.serverLevel() == mech.level()) {
            PlayerControl.redirectVehicleMove(commander, mech, mech.position());
        }
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
        ActiveRoute route = new ActiveRoute(target, planPilotRoute(vehicle, target), 1, vehicle.level().getGameTime());
        ROUTES.put(vehicle.getUUID(), route);
        return route;
    }

    /**
     * Uses the mounted unit's vanilla Mob navigation to choose walkable nodes.  Pomkots mechs
     * are LivingEntity vehicles rather than Mob instances, so the pilot supplies biological
     * pathfinding while the adapter translates each node into mech driver input.
     */
    private static MechPathPlanner.Route planPilotRoute(Entity vehicle, Vec3 target) {
        LivingEntity passenger = driver(vehicle);
        if (!(passenger instanceof Mob pilot) || target == null) {
            return new MechPathPlanner.Route(List.of());
        }
        Path path = pilot.getNavigation().createPath(BlockPos.containing(target), 0);
        if (path == null || path.getNodeCount() == 0) {
            return new MechPathPlanner.Route(List.of());
        }
        List<MechPathPlanner.RoutePoint> points = new ArrayList<>(path.getNodeCount() + 1);
        points.add(new MechPathPlanner.RoutePoint(vehicle.position(), false));
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            Vec3 position = new Vec3(node.x + 0.5D, node.y, node.z + 0.5D);
            if (flatDistance(points.get(points.size() - 1).position(), position) > 0.2D) {
                points.add(new MechPathPlanner.RoutePoint(position, false));
            }
        }
        return new MechPathPlanner.Route(points);
    }

    private static List<WeaponSlot> meleeWeapons(PomkotsVehicleBase mech) {
        String vehicle = vehicleId(mech);
        if ("pmv01".equals(vehicle) || "pmv01b".equals(vehicle)) {
            return List.of(new WeaponSlot(WEAPON_ARM_L, -1, "pmv01_pile", false, false, false));
        }
        if ("pmv02".equals(vehicle)) {
            return List.of(
                    new WeaponSlot(WEAPON_ARM_L, -1, "pmv02_drill", true, false, false),
                    new WeaponSlot(WEAPON_SHOULDER_R, -1, "pmv02_hammer", false, true, false));
        }
        if (!(mech instanceof Pmvc01Entity custom)) return List.of();
        List<WeaponSlot> result = new ArrayList<>();
        for (int slot : weaponSlots()) {
            ItemStack stack = weapon(custom, slot);
            String id = itemId(stack);
            if (MELEE_WEAPONS.contains(id)) {
                result.add(new WeaponSlot(bitForSlot(slot), slot, id, "jinba".equals(id), "takao".equals(id), false));
            }
        }
        return result;
    }

    private static List<WeaponSlot> rangedWeapons(PomkotsVehicleBase mech) {
        String vehicle = vehicleId(mech);
        if ("pmv01".equals(vehicle) || "pmv01b".equals(vehicle)) {
            return List.of(new WeaponSlot(WEAPON_ARM_R, -1, "pmv01_gatling", true, false, false));
        }
        if ("pmv02".equals(vehicle)) {
            return List.of(new WeaponSlot(WEAPON_ARM_R, -1, "pmv02_needle", true, false, false));
        }
        if ("pmv03p".equals(vehicle)) {
            return List.of(new WeaponSlot(WEAPON_ARM_R, -1, "pmv03p_gatling", true, false, false));
        }
        if ("pmv03".equals(vehicle)) {
            return List.of(new WeaponSlot(WEAPON_ARM_R, -1, "pmv03_rifle", false, false, false));
        }
        if (!(mech instanceof Pmvc01Entity custom)) return List.of();
        List<WeaponSlot> result = new ArrayList<>();
        for (int slot : new int[]{Pmvc01Entity.INV_WEAPON_RIGHT_HAND, Pmvc01Entity.INV_WEAPON_LEFT_HAND}) {
            ItemStack stack = weapon(custom, slot);
            String id = itemId(stack);
            if (!id.isBlank() && !MELEE_WEAPONS.contains(id) && !ENGINEERING_WEAPONS.contains(id)
                    && !GROUND_SKILL_WEAPONS.contains(id)) {
                boolean continuous = "shinobazu".equals(id) || "kasumi".equals(id);
                result.add(new WeaponSlot(bitForSlot(slot), slot, id, continuous, false, "uguisu".equals(id)));
            }
        }
        return result;
    }

    private static boolean scheduleAutomaticEquipment(PomkotsVehicleBase mech, LivingEntity target,
                                                       CombatState state, long now, WeaponSlot primary) {
        UUID mechId = mech.getUUID();
        if (now < AUTO_AUXILIARY_READY_TICKS.getOrDefault(mechId, 0L)) return false;
        String vehicle = vehicleId(mech);
        if ("pmv03p".equals(vehicle)) {
            PULSES.put(mech.getUUID(), new PendingPulse(WEAPON_ARM_L, 1));
            AUTO_AUXILIARY_READY_TICKS.put(mechId, now + AUTO_ORDNANCE_INTERVAL);
            return true;
        }
        if ("pmv01".equals(vehicle) || "pmv01b".equals(vehicle) || "pmv03".equals(vehicle)) {
            mech.getLockTargets().clearLockTargetsMulti();
            for (int i = 0; i < 6; i++) mech.getLockTargets().lockTargetMulti(target);
            mech.getLockTargets().unlockTargetMulti();
            PULSES.put(mech.getUUID(), new PendingPulse(WEAPON_SHOULDER_R, 1));
            AUTO_AUXILIARY_READY_TICKS.put(mechId, now + AUTO_ORDNANCE_INTERVAL);
            return true;
        }
        if (!(mech instanceof Pmvc01Entity custom)) return false;
        List<WeaponSlot> equipment = new ArrayList<>();
        for (int slot : weaponSlots()) {
            String id = itemId(weapon(custom, slot));
            if (id.isBlank() || ENGINEERING_WEAPONS.contains(id) || GROUND_SKILL_WEAPONS.contains(id)
                    || MELEE_WEAPONS.contains(id) || primary != null && primary.inventorySlot() == slot) continue;
            boolean continuous = "suwa".equals(id) || "shinobazu".equals(id) || "kasumi".equals(id);
            boolean multiLock = Pmvc01Entity.getMultiLockTargetNum(weapon(custom, slot)) > 0;
            equipment.add(new WeaponSlot(bitForSlot(slot), slot, id, continuous, false, multiLock));
        }
        if (equipment.isEmpty()) return false;
        WeaponSlot auxiliary = equipment.get(Math.floorMod(state.shoulderCursor++, equipment.size()));
        if (auxiliary.multiLock()) prepareCustomMultiLock(custom, auxiliary.inventorySlot(), target);
        else mech.getLockTargets().lockTargetHard(target);
        PULSES.put(mech.getUUID(), new PendingPulse(auxiliary.bit(), auxiliary.continuous() ? 10 : 1));
        AUTO_AUXILIARY_READY_TICKS.put(mechId, now + (auxiliary.continuous()
                ? AUTO_CONTINUOUS_EQUIPMENT_INTERVAL : AUTO_ORDNANCE_INTERVAL));
        return true;
    }

    private static void prepareCustomMultiLock(Pmvc01Entity mech, int slot, Entity target) {
        mech.getLockTargets().clearLockTargetsMulti(slot);
        int count = Math.max(1, Pmvc01Entity.getMultiLockTargetNum(weapon(mech, slot)));
        for (int i = 0; i < count; i++) mech.getLockTargets().lockTargetMulti(target, slot, mech);
    }

    private static void addGroundWeaponSkill(List<SkillView> result, Pmvc01Entity mech, String skillId,
                                              String weaponId, String label, String icon) {
        if (findWeaponSlot(mech, weaponId) < 0) return;
        long now = mech.level().getGameTime();
        long expiry = SKILL_COOLDOWNS.getOrDefault(new SkillCooldownKey(mech.getUUID(), skillId), 0L);
        int remaining = (int)Math.max(0L, expiry - now);
        result.add(new SkillView(skillId, label, icon, SkillType.POINT,
                remaining == 0 && !PULSES.containsKey(mech.getUUID()), 120, remaining));
    }

    private static boolean fireGroundWeaponSkill(ServerPlayer commander, Pmvc01Entity mech,
                                                  String skillId, Vec3 requested) {
        String weaponId = groundSkillWeaponId(skillId);
        int slot = findWeaponSlot(mech, weaponId);
        if (slot < 0 || PULSES.containsKey(mech.getUUID()) || !(mech.level() instanceof ServerLevel level)
                || requested.distanceToSqr(mech.position()) > 128.0D * 128.0D) return false;
        long now = level.getGameTime();
        SkillCooldownKey key = new SkillCooldownKey(mech.getUUID(), skillId);
        if (SKILL_COOLDOWNS.getOrDefault(key, 0L) > now) return false;
        Optional<Vec3> target = snapToGround(level, requested);
        if (target.isEmpty()) return false;

        Vec3 point = target.get();
        ArmorStand marker = new ArmorStand(level, point.x, point.y, point.z);
        marker.setInvisible(true);
        marker.setInvulnerable(true);
        marker.setNoGravity(true);
        marker.setSilent(true);
        marker.getPersistentData().putBoolean("DominionPomkotsGroundTarget", true);
        if (!level.addFreshEntity(marker)) return false;

        prepareCustomMultiLock(mech, slot, marker);
        LivingEntity pilot = mech.getDrivingPassenger();
        if (pilot != null) aimAt(pilot, point);
        PULSES.put(mech.getUUID(), new PendingPulse(bitForSlot(slot), 1));
        SKILL_COOLDOWNS.put(key, now + 120L);
        GROUND_MARKERS.put(marker.getUUID(), new GroundMarker(now + 320L));
        ACTIVE.add(mech.getUUID());
        return true;
    }

    private static boolean attackInFlight(Pmv03pEntity mech, LivingEntity target,
                                          List<WeaponSlot> ranged, CombatState state) {
        ((MechControlBridge)mech).dominion$setControlFrame(MechControlFrame.INACTIVE);
        Vec3 delta = target.getBoundingBox().getCenter().subtract(mech.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float desiredYaw = yawTo(mech.position(), target.position());
        float desiredPitch = (float)(-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG));
        float yawError = Mth.wrapDegrees(desiredYaw - mech.getYRot());
        float pitchError = Mth.wrapDegrees(desiredPitch - mech.getXRot());
        if (PULSES.containsKey(mech.getUUID())) return true;
        boolean aligned = Math.abs(yawError) <= 12.0F && Math.abs(pitchError) <= 12.0F
                && mech.hasLineOfSight(target);
        if (aligned && scheduleAutomaticEquipment(mech, target, state, mech.level().getGameTime(),
                ranged.isEmpty() ? null : ranged.get(0))) return true;
        short bits = 0;
        if (yawError < -2.0F) bits |= LEFT;
        else if (yawError > 2.0F) bits |= RIGHT;
        if (pitchError < -2.0F) bits |= FORWARD;
        else if (pitchError > 2.0F) bits |= BACK;
        if (!ranged.isEmpty() && aligned) {
            bits |= ranged.get(0).bit();
        }
        submit(mech, bits);
        ACTIVE.add(mech.getUUID());
        return true;
    }

    private static Optional<Vec3> snapToGround(ServerLevel level, Vec3 requested) {
        BlockPos pos = BlockPos.containing(requested);
        for (int i = 0; i <= 32 && pos.getY() >= level.getMinBuildHeight(); i++, pos = pos.below()) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return Optional.of(Vec3.atBottomCenterOf(pos.above()));
            }
        }
        return Optional.empty();
    }

    private static void cleanupGroundMarkers(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Iterator<Map.Entry<UUID, GroundMarker>> iterator = GROUND_MARKERS.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, GroundMarker> entry = iterator.next();
            Entity marker = find(server, entry.getKey());
            if (marker == null || entry.getValue().expiresAt() <= now) {
                if (marker != null) marker.discard();
                GROUND_MARKERS.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static boolean isGroundWeaponSkill(String skillId) {
        return SKILL_DODO.equals(skillId) || SKILL_NOSURI.equals(skillId) || SKILL_MUKUDORI.equals(skillId);
    }

    private static String groundSkillWeaponId(String skillId) {
        if (SKILL_DODO.equals(skillId)) return "dodo";
        if (SKILL_NOSURI.equals(skillId)) return "nosuri";
        if (SKILL_MUKUDORI.equals(skillId)) return "mukudori";
        return "";
    }

    private static int findWeaponSlot(Pmvc01Entity mech, String weaponId) {
        for (int slot : weaponSlots()) if (weaponId.equals(itemId(weapon(mech, slot)))) return slot;
        return -1;
    }

    private static int[] weaponSlots() {
        return new int[]{Pmvc01Entity.INV_WEAPON_RIGHT_HAND, Pmvc01Entity.INV_WEAPON_LEFT_HAND,
                Pmvc01Entity.INV_WEAPON_RIGHT_SHOULDER, Pmvc01Entity.INV_WEAPON_LEFT_SHOULDER};
    }

    private static ItemStack weapon(Pmvc01Entity mech, int slot) {
        if (slot == Pmvc01Entity.INV_WEAPON_RIGHT_HAND) return mech.getRightArmWeapon();
        if (slot == Pmvc01Entity.INV_WEAPON_LEFT_HAND) return mech.getLeftArmWeapon();
        if (slot == Pmvc01Entity.INV_WEAPON_RIGHT_SHOULDER) return mech.getRightShoulderWeapon();
        if (slot == Pmvc01Entity.INV_WEAPON_LEFT_SHOULDER) return mech.getLeftShoulderWeapon();
        return ItemStack.EMPTY;
    }

    private static short bitForSlot(int slot) {
        if (slot == Pmvc01Entity.INV_WEAPON_RIGHT_HAND) return WEAPON_ARM_R;
        if (slot == Pmvc01Entity.INV_WEAPON_LEFT_HAND) return WEAPON_ARM_L;
        if (slot == Pmvc01Entity.INV_WEAPON_RIGHT_SHOULDER) return WEAPON_SHOULDER_R;
        if (slot == Pmvc01Entity.INV_WEAPON_LEFT_SHOULDER) return WEAPON_SHOULDER_L;
        return 0;
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && "pomkotsmechs".equals(key.getNamespace()) ? key.getPath() : "";
    }

    private static String vehicleId(Entity entity) {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null && "pomkotsmechs".equals(key.getNamespace()) ? key.getPath() : "";
    }

    private static boolean requiresCombatMode(PomkotsVehicleBase mech) {
        String id = vehicleId(mech);
        return "pmv01".equals(id) || "pmv01b".equals(id) || "pmv02".equals(id) || "pmv03".equals(id);
    }

    private static Vec3 flatAway(Vec3 from, Vec3 to) {
        Vec3 away = from.vectorTo(to).multiply(1.0D, 0.0D, 1.0D);
        return away.lengthSqr() < 0.01D ? new Vec3(0.0D, 0.0D, 1.0D) : away.normalize();
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

    private static void submit(PomkotsVehicleBase mech, short bits) {
        ((MechControlBridge)mech).dominion$queueDriverInput(bits);
    }

    private static void ensureGroundMode(Entity vehicle) {
        if (vehicle instanceof Pmv03pEntity pmv03p && pmv03p.isMainMode()) {
            pmv03p.setDriverInput(new DriverInput(MODE));
        }
    }

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
            if (pilot != null) {
                pilot.zza = 0;
                pilot.xxa = 0;
                if (pilot instanceof Mob mob) mob.getNavigation().stop();
            }
        }
        UUID id = vehicle.getUUID();
        ACTIVE.remove(id); JUMPS.remove(id); PULSES.remove(id); COMBAT.remove(id);
        if (clearTasks) ROUTES.remove(id);
    }

    private static Entity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private static void cleanup(UUID id) {
        ACTIVE.remove(id); ROUTES.remove(id); JUMPS.remove(id); PULSES.remove(id); COMBAT.remove(id);
        AUTO_AUXILIARY_READY_TICKS.remove(id);
        SKILL_COOLDOWNS.keySet().removeIf(key -> key.vehicleId().equals(id));
    }

    private static final class ActiveRoute {
        final Vec3 target; final MechPathPlanner.Route route; final long builtAt; int index;
        ActiveRoute(Vec3 target, MechPathPlanner.Route route, int index, long builtAt) {
            this.target = target; this.route = route; this.index = index; this.builtAt = builtAt;
        }
    }
    private static final class JumpState {
        final Vec3 landing; final boolean manual; int ticks; boolean wasAirborne; float heading = Float.NaN;
        JumpState(Vec3 landing, boolean manual) { this.landing = landing; this.manual = manual; }
    }
    private static final class PendingPulse {
        final short bits; int remainingTicks;
        PendingPulse(short bits, int pressTicks) {
            this.bits = bits;
            this.remainingTicks = Math.max(1, pressTicks) + 1;
        }
    }
    private static final class CombatState {
        UUID target;
        long nextPrimaryTick;
        long nextTraceTick;
        long lastAttackTick;
        int meleeCursor;
        int shoulderCursor;
    }
    private record WeaponSlot(short bit, int inventorySlot, String itemId,
                              boolean continuous, boolean charge, boolean multiLock) {}
    private record SkillCooldownKey(UUID vehicleId, String skillId) {}
    private record GroundMarker(long expiresAt) {}
}
