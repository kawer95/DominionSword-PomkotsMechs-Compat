package com.arxyt.dominionsword.pomkotscompat;

import com.arxyt.dominionsword.api.DominionVehicleAdapter;
import com.arxyt.dominionsword.api.DominionSkillProvider;
import com.arxyt.dominionsword.api.VehicleDismounts;
import com.arxyt.dominionsword.control.FactionAccess;
import com.arxyt.dominionsword.control.PlayerControl;
import com.arxyt.dominionsword.pomkotscompat.control.MechControlBridge;
import com.arxyt.dominionsword.pomkotscompat.control.MechControlFrame;
import com.arxyt.dominionsword.pomkotscompat.control.MechPathPlanner;
import com.arxyt.dominionsword.pomkotscompat.control.PomkotsPilotState;
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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PomkotsMechVehicleAdapter implements DominionVehicleAdapter, DominionSkillProvider {
    private static final Set<String> SUPPORTED = Set.of("pmv01", "pmv01b", "pmv02", "pmv03p", "pmv03", "pmvc01");

    private static final short FORWARD = 1, BACK = 2, LEFT = 4, RIGHT = 8, EVASION = 16, JUMP = 32;
    private static final short WEAPON_ARM_R = 64, WEAPON_ARM_L = 128, WEAPON_SHOULDER_R = 256,
            WEAPON_SHOULDER_L = 512, LOCK = 1024, MODE = 2048;

    private static final String SKILL_VECTOR_BOOST = "dominionsword_pomkotsmechs_compat:vector_boost",
            SKILL_FLIGHT_MODE = "dominionsword_pomkotsmechs_compat:flight_mode",
            SKILL_EVASION = "dominionsword_pomkotsmechs_compat:evade",
            ACTION_MODE = "pomkots_weapon_mode";
    private static final String SKILL_DODO = "dominionsword_pomkotsmechs_compat:ground_dodo",
            SKILL_NOSURI = "dominionsword_pomkotsmechs_compat:ground_nosuri",
            SKILL_MUKUDORI = "dominionsword_pomkotsmechs_compat:ground_mukudori";
    private static final double MELEE_SWITCH_RANGE = 10.0D, RANGED_MIN_RANGE = 10.0D,
            RANGED_PREFERRED_RANGE = 24.0D, RANGED_MAX_RANGE = 32.0D;
    private static final double SUWA_MAX_RANGE = 64.0D;
    private static final double VECTOR_BOOST_MAX_RANGE = 32.0D;
    private static final int VECTOR_BOOST_COOLDOWN_TICKS = 600, EVASION_COOLDOWN_TICKS = 200;
    private static final long AUTO_CONTINUOUS_EQUIPMENT_INTERVAL = 1_200L;
    private static final long AUTO_ORDNANCE_INTERVAL = 2_000L;
    private static final long WEAPON_DEBUG_INTERVAL = 10L;
    private static final int OFFHAND_BURST_TICKS = 20;
    private static final long OFFHAND_RANGED_INTERVAL = 100L;
    private static final long MELEE_PRESS_INTERVAL = 40L;
    private static final short MOVEMENT_MASK = (short)(FORWARD | BACK | LEFT | RIGHT | JUMP | EVASION);

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
    private static final Map<UUID, Integer> SUWA_BURST_REMAINING_TICKS = new ConcurrentHashMap<>();
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
            if (!VehicleDismounts.dismount(vehicle, occupant)) return false;
            PomkotsPilotState.restore((Mob) occupant);
        }
        ensureGroundMode(vehicle);
        PomkotsPilotState.begin(unit, vehicle);
        boolean boarded = unit.getVehicle() == vehicle || unit.startRiding(vehicle, true);
        if (!boarded) {
            PomkotsPilotState.restore(unit);
            return false;
        }
        if (vehicle instanceof PomkotsVehicleBase mech) {
            submit(mech, (short) 0);
            ((MechControlBridge) mech).dominion$setControlFrame(MechControlFrame.INACTIVE);
        }
        return true;
    }

    @Override
    public boolean dismount(ServerPlayer player, Entity vehicle, int seat) {
        if (seat != 0) return false;
        LivingEntity passenger = driver(vehicle);
        if (passenger == null) return false;
        stop(vehicle, true);
        boolean dismounted = VehicleDismounts.dismount(vehicle, passenger);
        if (dismounted && passenger instanceof Mob mob) PomkotsPilotState.restore(mob);
        return dismounted;
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
        double maximumRangedDistance = maximumRangedDistance(mech);
        long traceNow = mech.level().getGameTime();
        if (traceNow >= state.nextTraceTick) {
            MechControlBridge bridge = (MechControlBridge) mech;
            DominionSwordPomkotsCompatMod.LOGGER.info(
                    "[DS-POMKOTS-COMBAT] mech={} target={} distance={} maxRange={} lineOfSight={} mainMode={} melee={} ranged={} ammo={} appliedInput={}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(mech.getType()), target.getType().toString(),
                    String.format(Locale.ROOT, "%.2f", distance),
                    String.format(Locale.ROOT, "%.2f", maximumRangedDistance),
                    mech.hasLineOfSight(target), mech.isMainMode(),
                    melee.stream().map(WeaponSlot::itemId).toList(), ranged.stream().map(WeaponSlot::itemId).toList(),
                    mech instanceof Pmvc01Entity custom ? customAmmoStatus(custom) : "native",
                    bridge.dominion$getLastAppliedDriverInput());
            state.nextTraceTick = traceNow + 40L;
        }
        if (traceNow >= state.lastWeaponDebugTick) {
            state.lastWeaponDebugTick = traceNow + WEAPON_DEBUG_INTERVAL;
            DominionSwordPomkotsCompatMod.LOGGER.info(
                    "[DS-POMKOTS-WEAPON] decision mech={} target={} distance={} maxRange={} los={} mainMode={} buildMode={} melee={} ranged={} pulse={} ammo={}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(mech.getType()), target.getType(),
                    String.format(Locale.ROOT, "%.2f", distance),
                    String.format(Locale.ROOT, "%.2f", maximumRangedDistance),
                    mech.hasLineOfSight(target), mech.isMainMode(),
                    mech instanceof Pmvc01Entity custom && custom.isBuildMode(),
                    melee.stream().map(w -> w.itemId() + "@" + w.inventorySlot()).toList(),
                    ranged.stream().map(w -> w.itemId() + "@" + w.inventorySlot()).toList(),
                    PULSES.containsKey(mech.getUUID()),
                    mech instanceof Pmvc01Entity custom ? customAmmoStatus(custom) : "native");
        }

        if (mech instanceof Pmv03pEntity flying && flying.isMainMode()) {
            return attackInFlight(flying, target, ranged, state);
        }

        long now = mech.level().getGameTime();
        if (!melee.isEmpty() && (ranged.isEmpty() || distance <= MELEE_SWITCH_RANGE)) {
            double reach = Math.max(5.0D, mech.getBbWidth() * 0.75D + target.getBbWidth() * 0.5D + 2.5D);
            if (distance > reach) return driveTo(vehicle, target.position(), true);
            aimAt(pilot, target.getBoundingBox().getCenter());
            setFrame(mech, 0.0F, 0.0F, pilot.getYRot(), pilot.getXRot());
            WeaponSlot weapon = melee.get(Math.floorMod(state.meleeCursor++, melee.size()));
            if (now >= state.nextMeleeTick) {
                PendingPulse existing = PULSES.get(mech.getUUID());
                if (existing != null) {
                    existing.concurrentPrimaryBits |= weapon.bit();
                    DominionSwordPomkotsCompatMod.LOGGER.info(
                            "[DS-POMKOTS-WEAPON] melee concurrent mech={} weapon={} slot={} bit={} pulse={}",
                            mech.getUUID(), weapon.itemId(), weapon.inventorySlot(), weapon.bit(), existing.bits);
                } else {
                    int hold = "takao".equals(weapon.itemId()) ? 14 : weapon.continuous() ? 8 : 1;
                    DominionSwordPomkotsCompatMod.LOGGER.info(
                            "[DS-POMKOTS-WEAPON] melee fire mech={} weapon={} slot={} bit={} hold={} distance={} reach={}",
                            mech.getUUID(), weapon.itemId(), weapon.inventorySlot(), weapon.bit(), hold,
                            String.format(Locale.ROOT, "%.2f", distance),
                            String.format(Locale.ROOT, "%.2f", reach));
                    PULSES.put(mech.getUUID(), new PendingPulse(weapon.bit(), hold));
                }
                state.nextMeleeTick = now + MELEE_PRESS_INTERVAL;
            }
            ACTIVE.add(mech.getUUID());
            return true;
        }

        if (ranged.isEmpty()) {
            if (mech instanceof Pmvc01Entity custom && hasAutomaticEquipment(custom)) {
                Vec3 away = flatAway(target.position(), vehicle.position());
                if (distance > maximumRangedDistance || !mech.hasLineOfSight(target)) {
                    return driveTo(vehicle, target.position().add(away.scale(RANGED_PREFERRED_RANGE)), true);
                }
                if (distance < RANGED_MIN_RANGE) {
                    return driveTo(vehicle, target.position().add(away.scale(RANGED_MIN_RANGE + 4.0D)), true);
                }
                aimAt(pilot, target.getBoundingBox().getCenter());
                setFrame(mech, 0.0F, 0.0F, pilot.getYRot(), pilot.getXRot());
                if (!PULSES.containsKey(mech.getUUID())) {
                    scheduleAutomaticEquipment(mech, target, state, mech.level().getGameTime(), null);
                }
                ACTIVE.add(mech.getUUID());
                return true;
            }
            stopMovement(mech);
            return true;
        }
        Vec3 away = flatAway(target.position(), vehicle.position());
        if (distance > maximumRangedDistance || !mech.hasLineOfSight(target)) {
            return driveTo(vehicle, target.position().add(away.scale(RANGED_PREFERRED_RANGE)), true);
        }
        if (distance < RANGED_MIN_RANGE) {
            return driveTo(vehicle, target.position().add(away.scale(RANGED_MIN_RANGE + 4.0D)), true);
        }
        aimAt(pilot, target.getBoundingBox().getCenter());
        setFrame(mech, 0.0F, 0.0F, pilot.getYRot(), pilot.getXRot());

        WeaponSlot main = ranged.get(0);
        short handBits = (short)(main.bit() | LOCK);
        if (ranged.size() > 1) {
            WeaponSlot offhand = ranged.get(1);
            if (mech instanceof Pmvc01Entity custom) {
                ensureHandReload(custom, offhand.inventorySlot());
                ensureHandReload(custom, main.inventorySlot());
            }
            if (now >= state.nextOffhandTick) {
                state.offhandUntilTick = now + (offhand.continuous() ? OFFHAND_BURST_TICKS : 1);
                state.nextOffhandTick = now + OFFHAND_RANGED_INTERVAL;
                DominionSwordPomkotsCompatMod.LOGGER.info(
                        "[DS-POMKOTS-WEAPON] offhand burst mech={} weapon={} slot={} bit={} press={} next={}",
                        mech.getUUID(), offhand.itemId(), offhand.inventorySlot(), offhand.bit(),
                        state.offhandUntilTick - now, OFFHAND_RANGED_INTERVAL);
            }
            if (now < state.offhandUntilTick) handBits |= offhand.bit();
        } else if (mech instanceof Pmvc01Entity custom) {
            ensureHandReload(custom, main.inventorySlot());
        }

        PendingPulse activePulse = PULSES.get(mech.getUUID());
        if (activePulse != null) {
            if (activePulse.allowConcurrentPrimary && !main.multiLock()) {
                activePulse.concurrentPrimaryBits = handBits;
                DominionSwordPomkotsCompatMod.LOGGER.info(
                        "[DS-POMKOTS-WEAPON] ranged concurrent mech={} bits={} pulse={}",
                        mech.getUUID(), handBits, activePulse.bits);
            }
            return true;
        }
        if (scheduleAutomaticEquipment(mech, target, state, now, main)) {
            PendingPulse scheduledPulse = PULSES.get(mech.getUUID());
            if (scheduledPulse != null && scheduledPulse.allowConcurrentPrimary && !main.multiLock()) {
                scheduledPulse.concurrentPrimaryBits = handBits;
                DominionSwordPomkotsCompatMod.LOGGER.info(
                        "[DS-POMKOTS-WEAPON] ranged concurrent scheduled mech={} bits={} pulse={}",
                        mech.getUUID(), handBits, scheduledPulse.bits);
            }
            return true;
        }
        if (main.multiLock() && mech instanceof Pmvc01Entity custom && now >= state.nextPrimaryTick) {
            prepareCustomMultiLock(custom, main.inventorySlot(), target);
            PULSES.put(mech.getUUID(), new PendingPulse(main.bit(), 1));
            state.nextPrimaryTick = now + 80L;
            DominionSwordPomkotsCompatMod.LOGGER.info(
                    "[DS-POMKOTS-WEAPON] ranged multlock mech={} weapon={} slot={} bit={} count={}",
                    mech.getUUID(), main.itemId(), main.inventorySlot(), main.bit(),
                    Pmvc01Entity.getMultiLockTargetNum(weapon(custom, main.inventorySlot())));
        } else if (now >= state.nextPrimaryTick) {
            submit(mech, handBits);
            DominionSwordPomkotsCompatMod.LOGGER.info(
                    "[DS-POMKOTS-WEAPON] ranged fire mech={} main={} offhand={} submit={}",
                    mech.getUUID(), main.itemId() + "@" + main.inventorySlot(),
                    ranged.size() > 1 ? ranged.get(1).itemId() + "@" + ranged.get(1).inventorySlot() : "none",
                    handBits);
        } else if (now % 20L == 0L) {
            DominionSwordPomkotsCompatMod.LOGGER.info(
                    "[DS-POMKOTS-WEAPON] ranged gated mech={} weapon={} slot={} nextPrimaryTick={} now={}",
                    mech.getUUID(), main.itemId(), main.inventorySlot(), state.nextPrimaryTick, now);
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
                    "minecraft:textures/item/elytra.png", SkillType.TOGGLE, true, 0, 0));
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
        Optional<JumpState> jump = buildJumpState(mech, landing.get(), true);
        if (jump.isEmpty()) {
            context.commander().displayClientMessage(Component.translatable(
                    "message.dominionsword_pomkotsmechs_compat.jump_unsafe"), true);
            return false;
        }
        if (!PlayerControl.redirectVehicleMove(context.commander(), mech, requestedJump)) return false;
        ROUTES.remove(mech.getUUID());
        JUMPS.put(mech.getUUID(), jump.get());
        ACTIVE.add(mech.getUUID());
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
                        || serverTick > combat.lastAttackTick + 20L) {
                    submit(mech, (short)0);
                    mech.getLockTargets().clearLockTargets();
                    cancelPulse(id, "combat interrupted");
                    COMBAT.remove(id, combat);
                }
            }
            JumpState jump = JUMPS.get(id);
            if (jump != null && jump.manual) advanceJump(mech, jump);
            PendingPulse pulse = PULSES.get(id);
            if (pulse != null) {
                if (pulse.ammoSlot >= 0 && mech instanceof Pmvc01Entity custom) {
                    Pmvc01Entity.AmmoManager ammo = custom.getAmmoManager(pulse.ammoSlot);
                    if (ammo.getBulletNum() <= 0) {
                        if (!ammo.isReloading() && ammo.getMagazineNum() > 0) {
                            ammo.startReload();
                            DominionSwordPomkotsCompatMod.LOGGER.info(
                                    "[DominionSword Pomkots Compat] PMVC shoulder reload started: mech={}, slot={}, magazines={}",
                                    mech.getUUID(), pulse.ammoSlot, ammo.getMagazineNum());
                        } else if (!ammo.isReloading() && ammo.getMagazineNum() <= 0) {
                            submit(mech, (short) 0);
                            cancelPulse(id, "out of ammunition");
                        }
                        if (pulse.allowConcurrentPrimary && pulse.concurrentPrimaryBits != 0) {
                            submit(mech, pulse.concurrentPrimaryBits);
                        }
                        continue;
                    }
                    if (pulse.waitingForReload) {
                        DominionSwordPomkotsCompatMod.LOGGER.info(
                                "[DominionSword Pomkots Compat] PMVC shoulder reload completed; firing: mech={}, slot={}, bullets={}",
                                mech.getUUID(), pulse.ammoSlot, ammo.getBulletNum());
                        pulse.waitingForReload = false;
                    }
                }
                if (pulse.remainingTicks > 1) {
                    short submitted = (short) (pulse.bits | pulse.concurrentPrimaryBits);
                    short movement = (short) (((MechControlBridge) mech).dominion$getQueuedDriverInput() & MOVEMENT_MASK);
                    submit(mech, (short)(movement | submitted));
                    if (serverTick % 5L == 0L) {
                        DominionSwordPomkotsCompatMod.LOGGER.info(
                                "[DS-POMKOTS-WEAPON] pulse tick mech={} bits={} concurrent={} submitted={} movement={} remaining={} slot={}",
                                mech.getUUID(), pulse.bits, pulse.concurrentPrimaryBits, submitted, movement,
                                pulse.remainingTicks, pulse.ammoSlot);
                    }
                }
                else submit(mech, (short)(((MechControlBridge) mech).dominion$getQueuedDriverInput() & MOVEMENT_MASK));
                if (--pulse.remainingTicks <= 0) {
                    completePulse(id, serverTick);
                } else if (pulse.accumulatesFiringTime) {
                    SUWA_BURST_REMAINING_TICKS.put(id, Math.max(0, pulse.remainingTicks - 1));
                }
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
        float desiredYaw = yawTo(mech.position(), jump.landing);
        float yawDelta = Mth.wrapDegrees(desiredYaw - mech.getYRot());
        if (jump.step == 0 && mech.onGround() && Math.abs(yawDelta) > 6.0F) {
            float turningYaw = mech.getYRot() + Mth.clamp(yawDelta, -18.0F, 18.0F);
            setFrame(mech, 0.0F, 0.0F, turningYaw, 0.0F);
            submit(mech, (short)0);
            ACTIVE.add(mech.getUUID());
            return true;
        }
        if (Float.isNaN(jump.heading)) jump.heading = desiredYaw;
        // Pomkots starts ACT_JUMP only when it consumes a JUMP input while the mech is still
        // on the ground. Prime that native action for one full tick before our sampled curve
        // lifts the mech so its original animation sound keyframes still run.
        if (!jump.launchPrimed) {
            jump.launchPrimed = true;
            setFrame(mech, 0.0F, 0.0F, jump.heading, 0.0F);
            submit(mech, JUMP);
            ACTIVE.add(mech.getUUID());
            return true;
        }
        jump.step++;
        int index = Math.min(jump.step, jump.path.size() - 1);
        Vec3 desired = jump.path.get(index);
        mech.setNoGravity(true);
        mech.setDeltaMovement(Vec3.ZERO);
        setFrame(mech, 0.0F, 0.0F, jump.heading, 0.0F);
        submit(mech, (short)0);
        mech.move(MoverType.SELF, desired.subtract(mech.position()));
        mech.setDeltaMovement(Vec3.ZERO);
        mech.fallDistance = 0.0F;

        if (mech.position().distanceToSqr(desired) > 0.16D) {
            finishJump(mech);
            return true;
        }
        if (index >= jump.path.size() - 1) {
            finishJump(mech);
            return true;
        }
        ACTIVE.add(mech.getUUID());
        return true;
    }

    private static Optional<JumpState> buildJumpState(PomkotsVehicleBase mech, Vec3 landing, boolean manual) {
        Vec3 start = mech.position();
        double distance = flatDistance(start, landing);
        int duration = Mth.clamp((int)Math.ceil(distance / 0.85D), 12, 40);
        double initialLift = Mth.clamp(2.75D + distance * 0.12D, 3.0D, 6.5D);
        for (double lift = initialLift; lift <= 12.0D + 1.0E-6D; lift += 1.25D) {
            List<Vec3> path = new ArrayList<>(duration + 1);
            for (int step = 0; step <= duration; step++) {
                double t = step / (double)duration;
                Vec3 linear = start.lerp(landing, t);
                path.add(linear.add(0.0D, 4.0D * lift * t * (1.0D - t), 0.0D));
            }
            if (jumpCurveClear(mech, start, path)) {
                return Optional.of(new JumpState(landing, manual, List.copyOf(path)));
            }
        }
        return Optional.empty();
    }

    private static boolean jumpCurveClear(PomkotsVehicleBase mech, Vec3 start, List<Vec3> path) {
        AABB bounds = mech.getBoundingBox().deflate(0.04D);
        for (int i = 1; i < path.size(); i++) {
            Vec3 from = path.get(i - 1), to = path.get(i);
            int samples = Math.max(1, (int)Math.ceil(from.distanceTo(to) / 0.3D));
            int checkedSamples = i == path.size() - 1 ? samples - 1 : samples;
            for (int sample = 1; sample <= checkedSamples; sample++) {
                Vec3 point = from.lerp(to, sample / (double)samples);
                if (!mech.level().noCollision(mech, bounds.move(point.subtract(start)))) return false;
            }
        }
        return true;
    }

    private static void finishJump(PomkotsVehicleBase mech) {
        JUMPS.remove(mech.getUUID());
        mech.setNoGravity(false);
        mech.setDeltaMovement(Vec3.ZERO);
        mech.fallDistance = 0.0F;
        settleJumpCommandAtActualLanding(mech);
        stopMovement(mech);
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

    private static MechPathPlanner.Route planPilotRoute(Entity vehicle, Vec3 target) {
        if (target == null) return new MechPathPlanner.Route(List.of());
        return MechPathPlanner.plan(vehicle, target);
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

    private static double maximumRangedDistance(PomkotsVehicleBase mech) {
        if (!(mech instanceof Pmvc01Entity custom)) return RANGED_MAX_RANGE;
        double maximum = RANGED_MAX_RANGE;
        for (int slot : new int[]{Pmvc01Entity.INV_WEAPON_RIGHT_SHOULDER,
                Pmvc01Entity.INV_WEAPON_LEFT_SHOULDER}) {
            ItemStack stack = weapon(custom, slot);
            if ("suwa".equals(itemId(stack)) && hasUsableAmmo(custom, slot)) {
                maximum = Math.max(maximum, SUWA_MAX_RANGE);
            }
        }
        return maximum;
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
        for (int slot : new int[]{Pmvc01Entity.INV_WEAPON_RIGHT_SHOULDER, Pmvc01Entity.INV_WEAPON_LEFT_SHOULDER}) {
            String id = itemId(weapon(custom, slot));
            String skipReason = null;
            if (id.isBlank()) skipReason = "empty";
            else if (ENGINEERING_WEAPONS.contains(id)) skipReason = "engineering";
            else if (GROUND_SKILL_WEAPONS.contains(id)) skipReason = "ground-skill";
            else if (MELEE_WEAPONS.contains(id)) skipReason = "melee";
            else if (primary != null && primary.inventorySlot() == slot) skipReason = "primary-slot";
            else if (!hasUsableAmmo(custom, slot)) skipReason = "no-usable-ammo";
            if (skipReason != null) {
                if (now % 20L == 0L) {
                    Pmvc01Entity.AmmoManager ammo = custom.getAmmoManager(slot);
                    DominionSwordPomkotsCompatMod.LOGGER.info(
                            "[DS-POMKOTS-WEAPON] shoulder skip mech={} slot={} weapon={} reason={} ammo={}/{} +{}",
                            mech.getUUID(), slot, id, skipReason,
                            ammo.getBulletNum(), ammo.getBulletNumPerMagazine(), ammo.getMagazineNum());
                }
                continue;
            }
            boolean continuous = "suwa".equals(id) || "shinobazu".equals(id) || "kasumi".equals(id);
            boolean multiLock = Pmvc01Entity.getMultiLockTargetNum(weapon(custom, slot)) > 0;
            equipment.add(new WeaponSlot(bitForSlot(slot), slot, id, continuous, false, multiLock));
        }
        if (equipment.isEmpty()) {
            if (now % 20L == 0L) {
                DominionSwordPomkotsCompatMod.LOGGER.info(
                        "[DS-POMKOTS-WEAPON] shoulder scan none mech={}", mech.getUUID());
            }
            return false;
        }
        WeaponSlot auxiliary = equipment.get(Math.floorMod(state.shoulderCursor++, equipment.size()));
        if (auxiliary.multiLock()) prepareCustomMultiLock(custom, auxiliary.inventorySlot(), target);
        else mech.getLockTargets().lockTargetHard(target);
        Pmvc01Entity.AmmoManager ammo = custom.getAmmoManager(auxiliary.inventorySlot());
        int waitingAmmoSlot = ammo.getBulletNum() <= 0 && ammo.getMagazineNum() > 0
                ? auxiliary.inventorySlot() : -1;
        if (waitingAmmoSlot >= 0 && !ammo.isReloading()) ammo.startReload();
        boolean shoulderGatling = "suwa".equals(auxiliary.itemId());
        int pressTicks = shoulderGatling
                ? SUWA_BURST_REMAINING_TICKS.getOrDefault(mechId, 20 * 20)
                : auxiliary.continuous() ? 10 : 1;
        int cooldownTicksAfter = shoulderGatling ? 20 * 20 : 0;
        if (shoulderGatling) SUWA_BURST_REMAINING_TICKS.put(mechId, pressTicks);
        PULSES.put(mech.getUUID(), new PendingPulse(auxiliary.bit(), pressTicks,
                auxiliary.inventorySlot(), cooldownTicksAfter, waitingAmmoSlot >= 0, shoulderGatling));
        DominionSwordPomkotsCompatMod.LOGGER.info(
                "[DominionSword Pomkots Compat] PMVC automatic shoulder weapon scheduled: mech={}, weapon={}, slot={}, bit={}, bullets={}, magazines={}, waitingForReload={}",
                mech.getUUID(), auxiliary.itemId(), auxiliary.inventorySlot(), auxiliary.bit(), ammo.getBulletNum(),
                ammo.getMagazineNum(), waitingAmmoSlot >= 0);
        if (!shoulderGatling) {
            AUTO_AUXILIARY_READY_TICKS.put(mechId, now + (auxiliary.continuous()
                    ? AUTO_CONTINUOUS_EQUIPMENT_INTERVAL : AUTO_ORDNANCE_INTERVAL));
        }
        return true;
    }

    private static boolean hasUsableAmmo(Pmvc01Entity mech, int weaponSlot) {
        Pmvc01Entity.AmmoManager ammo = mech.getAmmoManager(weaponSlot);
        return ammo.getBulletNumPerMagazine() > 0
                && (ammo.getBulletNum() > 0 || ammo.getMagazineNum() > 0);
    }

    private static boolean hasAutomaticEquipment(Pmvc01Entity mech) {
        for (int slot : new int[]{Pmvc01Entity.INV_WEAPON_RIGHT_SHOULDER, Pmvc01Entity.INV_WEAPON_LEFT_SHOULDER}) {
            String id = itemId(weapon(mech, slot));
            if (!id.isBlank() && !ENGINEERING_WEAPONS.contains(id) && !GROUND_SKILL_WEAPONS.contains(id)
                    && !MELEE_WEAPONS.contains(id) && hasUsableAmmo(mech, slot)) return true;
        }
        return false;
    }

    private static void ensureHandReload(Pmvc01Entity mech, int slot) {
        if (slot < 0) return;
        Pmvc01Entity.AmmoManager ammo = mech.getAmmoManager(slot);
        if (ammo.getBulletNum() <= 0 && ammo.getMagazineNum() > 0 && !ammo.isReloading()) {
            ammo.startReload();
            DominionSwordPomkotsCompatMod.LOGGER.info(
                    "[DS-POMKOTS-WEAPON] hand reload started mech={} slot={} magazines={}",
                    mech.getUUID(), slot, ammo.getMagazineNum());
        }
    }

    private static String customAmmoStatus(Pmvc01Entity mech) {
        List<String> status = new ArrayList<>();
        for (int slot : weaponSlots()) {
            String weaponId = itemId(weapon(mech, slot));
            if (weaponId.isBlank()) continue;
            Pmvc01Entity.AmmoManager ammo = mech.getAmmoManager(slot);
            String ammoId = itemId(mech.getItem(slot + 6));
            status.add(weaponId + "=" + ammo.getBulletNum() + "/" + ammo.getBulletNumPerMagazine()
                    + "+" + ammo.getMagazineNum() + "x" + (ammoId.isBlank() ? "empty" : ammoId));
        }
        return status.toString();
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

    private static void cancelPulse(UUID vehicleId, String reason) {
        PendingPulse pulse = PULSES.remove(vehicleId);
        if (pulse == null || !pulse.accumulatesFiringTime) return;
        int remaining = SUWA_BURST_REMAINING_TICKS.getOrDefault(vehicleId,
                Math.max(0, pulse.remainingTicks - 1));
        DominionSwordPomkotsCompatMod.LOGGER.info(
                "[DominionSword Pomkots Compat] PMVC shoulder burst paused: mech={}, reason={}, remainingFireTicks={}",
                vehicleId, reason, remaining);
    }

    private static void completePulse(UUID vehicleId, long now) {
        PendingPulse pulse = PULSES.remove(vehicleId);
        if (pulse == null || pulse.cooldownTicksAfter <= 0) return;
        SUWA_BURST_REMAINING_TICKS.remove(vehicleId);
        long readyAt = now + pulse.cooldownTicksAfter;
        AUTO_AUXILIARY_READY_TICKS.merge(vehicleId, readyAt, Math::max);
        DominionSwordPomkotsCompatMod.LOGGER.info(
                "[DominionSword Pomkots Compat] PMVC shoulder burst completed: mech={}, cooldownTicks={}, readyAt={}",
                vehicleId, pulse.cooldownTicksAfter, readyAt);
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
        ACTIVE.remove(id); JUMPS.remove(id); cancelPulse(id, "control stopped"); COMBAT.remove(id);
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
        SUWA_BURST_REMAINING_TICKS.remove(id);
        SKILL_COOLDOWNS.keySet().removeIf(key -> key.vehicleId().equals(id));
    }

    private static final class ActiveRoute {
        final Vec3 target; final MechPathPlanner.Route route; final long builtAt; int index;
        ActiveRoute(Vec3 target, MechPathPlanner.Route route, int index, long builtAt) {
            this.target = target; this.route = route; this.index = index; this.builtAt = builtAt;
        }
    }
    private static final class JumpState {
        final Vec3 landing; final boolean manual; final List<Vec3> path;
        int step; float heading = Float.NaN; boolean launchPrimed;
        JumpState(Vec3 landing, boolean manual, List<Vec3> path) {
            this.landing = landing; this.manual = manual; this.path = path;
        }
    }
    private static final class PendingPulse {
        final short bits; int remainingTicks; final int ammoSlot; final int cooldownTicksAfter;
        final boolean allowConcurrentPrimary, accumulatesFiringTime;
        boolean waitingForReload; short concurrentPrimaryBits;
        PendingPulse(short bits, int pressTicks) {
            this(bits, pressTicks, -1, 0, false, false);
        }
        PendingPulse(short bits, int pressTicks, int ammoSlot, int cooldownTicksAfter,
                     boolean waitingForReload, boolean allowConcurrentPrimary) {
            this.bits = bits;
            this.remainingTicks = Math.max(1, pressTicks) + 1;
            this.ammoSlot = ammoSlot;
            this.cooldownTicksAfter = cooldownTicksAfter;
            this.waitingForReload = waitingForReload;
            this.allowConcurrentPrimary = allowConcurrentPrimary;
            this.accumulatesFiringTime = allowConcurrentPrimary;
        }
    }
    private static final class CombatState {
        UUID target;
        long nextPrimaryTick;
        long nextOffhandTick;
        long offhandUntilTick;
        long nextMeleeTick;
        long nextTraceTick;
        long lastWeaponDebugTick;
        long lastAttackTick;
        int meleeCursor;
        int shoulderCursor;
    }
    private record WeaponSlot(short bit, int inventorySlot, String itemId,
                              boolean continuous, boolean charge, boolean multiLock) {}
    private record SkillCooldownKey(UUID vehicleId, String skillId) {}
    private record GroundMarker(long expiresAt) {}
}
