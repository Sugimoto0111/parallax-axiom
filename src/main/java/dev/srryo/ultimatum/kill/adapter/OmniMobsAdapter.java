package dev.srryo.ultimatum.kill.adapter;

import dev.srryo.ultimatum.UltimatumMod;
import dev.srryo.ultimatum.kill.ContainerBypass;
import dev.srryo.ultimatum.kill.KillAdapter;
import dev.srryo.ultimatum.kill.KillService;
import dev.srryo.ultimatum.kill.ReflectionAccess;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class OmniMobsAdapter implements KillAdapter {
    private static final String PACKAGE_PREFIX = "flashfur.omnimobs.entities.";
    private static final String METAPOTENT_LEVEL =
            "flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfurLevel";
    private static final String RESPAWNING =
            "flashfur.omnimobs.entities.flashfur.powers.Respawning";
    private static final String HEALTH_MANAGER =
            "flashfur.omnimobs.entities.anticheat.HealthManager";

    @Override
    public boolean supports(Entity target) {
        return target.getClass().getName().startsWith(PACKAGE_PREFIX);
    }

    @Override
    public void execute(ServerLevel level, Entity target, ServerPlayer attacker, KillService service)
            throws Exception {
        if (target.getClass().getName().endsWith("MetapotentFlashfurEntity")) {
            removeMetapotentController(level, target, service);
        } else {
            removeRespawningBoss(level, target, attacker, service);
        }
    }

    private static void removeRespawningBoss(ServerLevel level, Entity selected,
                                              ServerPlayer attacker, KillService service)
            throws Exception {
        ClassLoader loader = selected.getClass().getClassLoader();
        Set<Entity> family = new LinkedHashSet<>();
        family.add(selected);

        Class<?> respawningClass = Class.forName(RESPAWNING, false, loader);
        Object rawRespawning = ReflectionAccess.getStatic(respawningClass, "RESPAWNING_ENTITIES");
        if (rawRespawning instanceof Collection<?> respawning) {
            for (Object entry : new ArrayList<>(respawning)) {
                if (entry instanceof Entity candidate
                        && (candidate == selected || candidate.getUUID().equals(selected.getUUID()))) {
                    ContainerBypass.removeCollection(respawning, entry);
                    family.add(candidate);
                }
            }
        }

        int deaths = 0;
        int erasures = 0;
        for (Entity entity : family) {
            // Flashfur snapshots this every tick and uses it to force-add itself after removal.
            ReflectionAccess.put(entity, null, "oldPos");
            boolean healthZeroed = forceManagedHealth(entity, loader, 0.0F);
            if (healthZeroed && service.tryForcedDeath(level, entity, attacker)) {
                deaths++;
                hideBossBar(level, entity);
                // Omni's BossEntity performs its own death animation and force-removal at
                // deathTime 20. Only erase if another protection still leaves the corpse alive.
                service.scheduleTask(level.getServer(), 40, () -> {
                    removeManagedHealth(entity, loader);
                    // BossEntity deliberately reports isRemoved() == false forever.
                    // Consult the real server indexes so a completed normal death does
                    // not receive an unnecessary tombstone/deep erasure afterwards.
                    if (service.isEntityIndexed(level, entity.getUUID())) {
                        UltimatumMod.LOGGER.info(
                                "Omni-Mobs normal death timed out; deeply erasing {}", entity);
                        service.markAndErase(level, entity, 200);
                    }
                });
                continue;
            }

            erasures++;
            removeManagedHealth(entity, loader);
            hideBossBar(level, entity);
            service.markAndErase(level, entity, 200);
        }
        UltimatumMod.LOGGER.info(
                "Detached {} Omni-Mobs respawn anchor(s): {} entered normal death, {} required erasure",
                family.size(), deaths, erasures);
    }

    private static boolean forceManagedHealth(Entity target, ClassLoader loader, float health) {
        int updated = 0;
        try {
            Class<?> healthManager = Class.forName(HEALTH_MANAGER, false, loader);
            for (String getter : List.of("getHealthHashMap", "getLastGoodHealthValues")) {
                Method method = ReflectionAccess.findMethod(healthManager, getter);
                if (method == null) {
                    continue;
                }
                Object rawMap = method.invoke(null);
                if (rawMap instanceof Map<?, ?> map
                        && ContainerBypass.putMapEntry(map, target, health)) {
                    updated++;
                }
            }
        } catch (Throwable error) {
            UltimatumMod.LOGGER.debug("Could not force Omni-Mobs managed health for {}", target, error);
        }
        return updated > 0;
    }

    private static void removeManagedHealth(Entity target, ClassLoader loader) {
        try {
            Class<?> healthManager = Class.forName(HEALTH_MANAGER, false, loader);
            for (String getter : List.of("getHealthHashMap", "getLastGoodHealthValues")) {
                Method method = ReflectionAccess.findMethod(healthManager, getter);
                if (method == null) {
                    continue;
                }
                Object rawMap = method.invoke(null);
                if (rawMap instanceof Map<?, ?> map) {
                    ContainerBypass.removeMapKey(map, target);
                }
            }
        } catch (Throwable error) {
            UltimatumMod.LOGGER.debug("Could not detach Omni-Mobs health manager for {}", target, error);
        }
    }

    private static void hideBossBar(ServerLevel level, Object owner) {
        Object bossEvent = ReflectionAccess.get(owner, "bossEvent");
        if (bossEvent == null) {
            return;
        }
        try {
            if (bossEvent instanceof ServerBossEvent serverBossEvent) {
                serverBossEvent.setVisible(false);
                serverBossEvent.removeAllPlayers();

                // Force a client-side removal as well. Omni-Mobs can replace Forge's event bus,
                // leaving a client boss overlay alive even after the server event is detached.
                ClientboundBossEventPacket removePacket =
                        ClientboundBossEventPacket.createRemovePacket(serverBossEvent.getId());
                for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                    player.connection.send(removePacket);
                }
                return;
            }
            Method setVisible = ReflectionAccess.findMethod(bossEvent.getClass(), "setVisible", boolean.class);
            if (setVisible != null) {
                setVisible.invoke(bossEvent, false);
            }
            Method removePlayers = ReflectionAccess.findMethod(bossEvent.getClass(), "removeAllPlayers");
            if (removePlayers != null) {
                removePlayers.invoke(bossEvent);
            }
        } catch (Throwable error) {
            UltimatumMod.LOGGER.warn("Could not hide Omni-Mobs boss bar for {}", owner, error);
        }
    }

    private static void removeMetapotentController(ServerLevel level, Entity selected, KillService service)
            throws Exception {
        Object controller = ReflectionAccess.get(selected, "metapotentFlashfur");
        if (controller == null) {
            service.markAndErase(level, selected, 200);
            return;
        }

        eraseMetapotentController(level, controller, selected, service);
    }

    public boolean eraseLookedAtController(ServerLevel level, ServerPlayer player, double reach,
                                           KillService service) {
        try {
            ClassLoader loader = OmniMobsAdapter.class.getClassLoader();
            Class<?> levelClass = Class.forName(METAPOTENT_LEVEL, false, loader);
            Object rawControllers = ReflectionAccess.getStatic(levelClass, "metapotentFlashfurList");
            if (!(rawControllers instanceof Collection<?> controllers) || controllers.isEmpty()) {
                return false;
            }

            Vec3 start = player.getEyePosition();
            Vec3 end = start.add(player.getViewVector(1.0F).scale(reach));
            Object closest = null;
            double closestDistance = reach * reach;
            Object nearestInLevel = null;
            double nearestDistance = reach * reach;
            for (Object controller : new ArrayList<>(controllers)) {
                Object controllerLevel = ReflectionAccess.get(controller, "level");
                if (controllerLevel != null && controllerLevel != level) {
                    continue;
                }
                Object rawPos = ReflectionAccess.get(controller, "pos");
                if (rawPos instanceof Vec3 position) {
                    double playerDistance = start.distanceToSqr(position);
                    if (playerDistance <= nearestDistance) {
                        nearestDistance = playerDistance;
                        nearestInLevel = controller;
                    }
                }
                Object rawBounds = ReflectionAccess.get(controller, "boundingBox");
                Optional<Vec3> hit = rawBounds instanceof AABB bounds
                        ? bounds.clip(start, end) : Optional.empty();
                if (rawBounds instanceof AABB bounds && bounds.contains(start)) {
                    hit = Optional.of(start);
                }
                if (hit.isEmpty()) {
                    if (rawPos instanceof Vec3 position) {
                        Vec3 direction = end.subtract(start);
                        double along = position.subtract(start).dot(direction) / direction.lengthSqr();
                        if (along >= 0.0D && along <= 1.0D) {
                            Vec3 nearest = start.add(direction.scale(along));
                            if (nearest.distanceToSqr(position) <= 64.0D) {
                                hit = Optional.of(nearest);
                            }
                        }
                    }
                }
                if (hit.isEmpty()) {
                    continue;
                }
                double distance = start.distanceToSqr(hit.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = controller;
                }
            }
            if (closest == null) {
                closest = nearestInLevel;
            }
            if (closest == null) {
                UltimatumMod.LOGGER.info(
                        "Absolute Infinity scan saw {} controller(s), but none were in this level within {} blocks",
                        controllers.size(), reach);
                return false;
            }
            eraseMetapotentController(level, closest, null, service);
            return true;
        } catch (Throwable error) {
            UltimatumMod.LOGGER.error("Could not resolve Absolute Infinity controller from look ray", error);
            return false;
        }
    }

    private static void eraseMetapotentController(ServerLevel level, Object controller, Entity selected,
                                                   KillService service) throws Exception {

        ClassLoader loader = controller.getClass().getClassLoader();
        Class<?> levelClass = Class.forName(METAPOTENT_LEVEL, false, loader);
        Object controllers = ReflectionAccess.getStatic(levelClass, "metapotentFlashfurList");
        if (controllers instanceof Collection<?> collection) {
            ContainerBypass.removeCollection(collection, controller);
        }

        ReflectionAccess.put(controller, true, "removed");

        hideBossBar(level, controller);

        // A controller can own more than one visible proxy. Remove every proxy bound to
        // the same controller, then tombstone the selected spawn point against recreation.
        List<Entity> proxies = new ArrayList<>();
        Object ownedProxy = ReflectionAccess.get(controller, "metapotentFlashfurProxy");
        if (ownedProxy instanceof Entity proxy) {
            proxies.add(proxy);
        }
        for (Entity entity : level.getAllEntities()) {
            if (ReflectionAccess.get(entity, "metapotentFlashfur") == controller
                    && !proxies.contains(entity)) {
                proxies.add(entity);
            }
        }
        for (Entity proxy : proxies) {
            service.markAndErase(level, proxy, 200);
        }
        if (selected != null && !proxies.contains(selected)) {
            service.markAndErase(level, selected, 200);
        }
        UltimatumMod.LOGGER.info("Removed Absolute Infinity controller and {} proxy entity/entities",
                proxies.size());
    }

    @Override
    public String name() {
        return "Omni-Mobs";
    }
}
