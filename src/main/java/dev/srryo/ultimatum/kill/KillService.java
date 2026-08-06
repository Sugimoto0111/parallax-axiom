package dev.srryo.ultimatum.kill;

import dev.srryo.ultimatum.UltimatumMod;
import dev.srryo.ultimatum.kill.adapter.Pig2Adapter;
import dev.srryo.ultimatum.kill.adapter.TrialMonolithAdapter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jetbrains.annotations.Nullable;

public final class KillService {
    private final Queue<KillRequest> requests = new ConcurrentLinkedQueue<>();
    private final List<KillAdapter> adapters = List.of(
            new Pig2Adapter(),
            new TrialMonolithAdapter()
    );
    private final LogicalControllerEraser logicalControllerEraser = new LogicalControllerEraser();
    private final TombstoneRegistry tombstones = new TombstoneRegistry();
    private final DeepEntityEraser eraser = new DeepEntityEraser();
    private final GenericDeathKernel genericDeathKernel = new GenericDeathKernel();
    private final Map<Long, Queue<Object>> pig2Resets = new ConcurrentHashMap<>();
    private final Map<Long, Queue<Runnable>> deferredTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> specialSwingTicks = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> pendingExecutions = ConcurrentHashMap.newKeySet();
    private MinecraftServer lastProcessedServer;
    private int lastProcessedServerTick = Integer.MIN_VALUE;

    private static final int NATURAL_DEATH_TIMEOUT = 40;
    private static final int GENERIC_DEATH_TIMEOUT = 25;
    private static final int DEDICATED_DEATH_TIMEOUT = 40;
    private static final int FINAL_SWEEP_DELAY = 10;

    public void enqueue(Player attacker, Entity target) {
        if (target instanceof Player || target.level().isClientSide) {
            return;
        }
        requests.add(new KillRequest(target.level().dimension(), target.getUUID(), target.getId(),
                attacker.getUUID(), new WeakReference<>(target)));
    }

    public void enqueue(Entity target) {
        enqueueNullable(null, target);
    }

    private void enqueueNullable(@Nullable Player attacker, Entity target) {
        if (target instanceof Player || target.level().isClientSide) {
            return;
        }
        requests.add(new KillRequest(target.level().dimension(), target.getUUID(), target.getId(),
                attacker == null ? null : attacker.getUUID(), new WeakReference<>(target)));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        onVanillaServerTick(event.getServer());
    }

    /**
     * Called from both Forge and a MinecraftServer Mixin. The latter keeps executions,
     * timeouts and tombstones alive even if another mod replaces Forge's global event bus.
     */
    public void onVanillaServerTick(MinecraftServer server) {
        int tick = server.getTickCount();
        if (lastProcessedServer == server && lastProcessedServerTick == tick) {
            return;
        }
        lastProcessedServer = server;
        lastProcessedServerTick = tick;

        tombstones.tick(tick);
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity reentered : tombstones.findReentered(level, tick)) {
                UltimatumMod.LOGGER.warn(
                        "Permanently erased entity {} re-entered the world; removing it again",
                        reentered);
                eraser.erase(level, reentered);
            }
        }
        Queue<Object> due = pig2Resets.remove((long) tick);
        if (due != null) {
            Object pig2Class;
            while ((pig2Class = due.poll()) != null) {
                try {
                    TrustedKernel.resetPig(pig2Class);
                } catch (Throwable error) {
                    UltimatumMod.LOGGER.error("Could not restore Pig2 shutdown flag", error);
                }
            }
        }
        long currentTick = tick;
        for (Map.Entry<Long, Queue<Runnable>> entry : deferredTasks.entrySet()) {
            if (entry.getKey() <= currentTick
                    && deferredTasks.remove(entry.getKey(), entry.getValue())) {
                Runnable task;
                while ((task = entry.getValue().poll()) != null) {
                    try {
                        task.run();
                    } catch (Throwable error) {
                        UltimatumMod.LOGGER.error("Deferred execution cleanup failed", error);
                    }
                }
            }
        }
        KillRequest request;
        while ((request = requests.poll()) != null) {
            execute(server, request);
        }
        IntegrationSelfTest.tick(server, this);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || !player.swinging || !isHoldingAbsoluteEnd(player)) {
            return;
        }
        onAbsoluteEndSwing(player);
    }

    public void onAbsoluteEndSwing(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !isHoldingAbsoluteEnd(player)) {
            return;
        }
        long now = serverPlayer.getServer().getTickCount();
        long previous = specialSwingTicks.getOrDefault(serverPlayer.getUUID(), Long.MIN_VALUE / 2);
        if (now - previous < 4) {
            return;
        }
        specialSwingTicks.put(serverPlayer.getUUID(), now);
        eraseSpecialLookTarget(serverPlayer,
                UltimatumMod.ARTIFACT_REACH_SERVICE.reach(serverPlayer));
    }

    public boolean eraseSpecialLookTarget(Player player, double reach) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer
                && logicalControllerEraser.eraseLookedAt(level, serverPlayer, reach, this)) {
            return true;
        }
        List<Entity> candidates = LookTargetResolver.candidates(level, player, reach);
        if (candidates.isEmpty()) {
            return false;
        }
        // This fallback is what reaches mobs that suppress vanilla picking/attack
        // packets. It still enters the exact same queued execution pipeline as a normal
        // onLeftClickEntity call.
        Entity selected = candidates.stream()
                .filter(LivingEntity.class::isInstance)
                .findFirst()
                .orElse(candidates.get(0));
        enqueue(player, selected);
        return true;
    }

    private static boolean isHoldingAbsoluteEnd(Player player) {
        return UltimatumMod.ABSOLUTE_END.isPresent()
                && (player.getMainHandItem().is(UltimatumMod.ABSOLUTE_END.get())
                || player.getOffhandItem().is(UltimatumMod.ABSOLUTE_END.get()));
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (tombstones.blocks(level, event.getEntity(), level.getServer().getTickCount())) {
            event.setCanceled(true);
            event.getEntity().discard();
        }
    }

    private void execute(MinecraftServer server, KillRequest request) {
        ServerLevel level = server.getLevel(request.dimension());
        if (level == null) {
            return;
        }
        Entity target = level.getEntity(request.targetUuid());
        if (target == null) {
            Entity referenced = request.targetReference().get();
            if (referenced != null && referenced.level() == level) {
                target = referenced;
            }
        }
        if (target == null || target instanceof Player || target.isRemoved()) {
            return;
        }
        if (!pendingExecutions.add(target.getUUID())) {
            return;
        }
        Entity observedTarget = target;
        ServerPlayer attacker = findPlayer(server, request.attackerUuid());
        KillAdapter adapter = findAdapter(target);
        boolean deferred = false;
        try {
            if (logicalControllerEraser.eraseIfPresent(level, target, this)) {
                return;
            }

            // Pig2 is the sole LivingEntity exception: merely attempting a normal death
            // is known to trip its deliberate JVM shutdown path. Non-living controller
            // proxies also have no death semantics for the generic kernel to establish.
            if (adapter != null && (!adapter.allowStandardDeath(target)
                    || !(target instanceof LivingEntity))) {
                UltimatumMod.LOGGER.info("Executing isolated {} adapter for {}",
                        adapter.name(), target);
                adapter.execute(level, target, attacker, this);
                return;
            }

            if (!(target instanceof LivingEntity living)) {
                UltimatumMod.LOGGER.info("Deeply erasing non-living target {}", target);
                markAndErase(level, target, 100);
                return;
            }

            if (requiresUniversalDeathKernel(target)) {
                UltimatumMod.LOGGER.info(
                        "Executing universal direct-death pipeline for modded entity {}", target);
                startUniversalAnimatedDeath(server, level, living, attacker);
                deferred = true;
                return;
            }

            if (adapter != null && adapter.requiresImmediateIsolation(target)) {
                UltimatumMod.LOGGER.info(
                        "Skipping hostile hurt probe; isolating {} through the generic death kernel and {} adapter",
                        target, adapter.name());

                // Establish real death and detach hostile health/respawn state in this
                // server tick. Afterwards remove only the server tick-list entry: clients
                // retain the model long enough to render the red falling death animation,
                // while the entity gets no further AI/attack tick.
                genericDeathKernel.execute(level, living, attacker);
                try {
                    adapter.execute(level, target, attacker, this);
                } catch (Throwable error) {
                    UltimatumMod.LOGGER.error("{} immediate isolation failed for {}",
                            adapter.name(), target, error);
                }
                DeterministicWorldIndexEraser.suspendServerTicking(level, target);
                deferred = true;
                UUID isolatedUuid = target.getUUID();
                scheduleTask(server, 20, () -> {
                    Entity remainder = findIndexedEntity(level, isolatedUuid);
                    if (remainder != null) {
                        markAndErase(level, remainder, 400);
                    }
                    pendingExecutions.remove(isolatedUuid);
                });
                return;
            }

            if (tryStandardDeath(level, living, attacker)) {
                UltimatumMod.LOGGER.info("Natural death started for {}; observing it before escalation",
                        target);
                if (adapter != null && adapter.concealConfirmedDeath(target)) {
                    // The server keeps the real death/drop lifecycle. Only remove the
                    // misleading client corpse that some bosses render as still alive.
                    eraser.concealConfirmedDeath(level, target);
                }
                deferred = true;
                scheduleTask(server, NATURAL_DEATH_TIMEOUT,
                        () -> verifyNaturalDeath(level, observedTarget.getUUID(), observedTarget,
                                request.attackerUuid(), adapter));
                return;
            }

            UltimatumMod.LOGGER.info(
                    "Normal damage was rejected; entering the generic Mixin death kernel for {}",
                    target);
            genericDeathKernel.execute(level, living, attacker);
            deferred = true;
            scheduleTask(server, GENERIC_DEATH_TIMEOUT,
                    () -> verifyGenericDeath(level, observedTarget.getUUID(), observedTarget,
                            request.attackerUuid(), adapter));
        } catch (Throwable error) {
            UltimatumMod.LOGGER.error("Execution pipeline failed for {}; applying deep erasure", target, error);
            genericEraseThenAdapt(level, target, attacker, adapter);
        } finally {
            if (!deferred) {
                pendingExecutions.remove(target.getUUID());
            }
        }
    }

    private void verifyNaturalDeath(ServerLevel level, UUID targetUuid, Entity original,
                                    UUID attackerUuid, @Nullable KillAdapter adapter) {
        Entity active = findIndexedEntity(level, targetUuid);
        ServerPlayer attacker = findPlayer(level.getServer(), attackerUuid);
        if (active == null) {
            finishSuccessfulDeath(level, original, attacker, adapter);
            pendingExecutions.remove(targetUuid);
            return;
        }
        if (!(active instanceof LivingEntity living)) {
            genericEraseThenAdapt(level, active, attacker, adapter);
            pendingExecutions.remove(targetUuid);
            return;
        }

        UltimatumMod.LOGGER.info(
                "Natural death for {} did not finish in {} ticks; escalating to generic death",
                active, NATURAL_DEATH_TIMEOUT);
        genericDeathKernel.execute(level, living, attacker);
        scheduleTask(level.getServer(), GENERIC_DEATH_TIMEOUT,
                () -> verifyGenericDeath(level, targetUuid, active, attackerUuid, adapter));
    }

    private void verifyGenericDeath(ServerLevel level, UUID targetUuid, Entity original,
                                    UUID attackerUuid, @Nullable KillAdapter adapter) {
        Entity active = findIndexedEntity(level, targetUuid);
        ServerPlayer attacker = findPlayer(level.getServer(), attackerUuid);
        if (active == null) {
            finishSuccessfulDeath(level, original, attacker, adapter);
            pendingExecutions.remove(targetUuid);
            return;
        }

        UltimatumMod.LOGGER.info(
                "Generic death for {} did not leave the entity indexes in {} ticks",
                active, GENERIC_DEATH_TIMEOUT);
        if (adapter != null) {
            try {
                // Only now, after the universal death kernel demonstrably stalled, may
                // known compatibility code sever external health/respawn/controller state.
                UltimatumMod.LOGGER.info(
                        "Generic death was insufficient; executing {} death rescue for {}",
                        adapter.name(), active);
                adapter.execute(level, active, attacker, this);
                scheduleTask(level.getServer(), DEDICATED_DEATH_TIMEOUT,
                        () -> verifyDedicatedDeath(level, targetUuid, active,
                                attackerUuid, adapter));
                return;
            } catch (Throwable error) {
                UltimatumMod.LOGGER.error("{} death rescue failed for {}",
                        adapter.name(), active, error);
            }
        }
        deeplyEraseAndSweep(level, active, targetUuid);
    }

    private void verifyDedicatedDeath(ServerLevel level, UUID targetUuid, Entity original,
                                      UUID attackerUuid, KillAdapter adapter) {
        Entity active = findIndexedEntity(level, targetUuid);
        ServerPlayer attacker = findPlayer(level.getServer(), attackerUuid);
        if (active == null) {
            finishSuccessfulDeath(level, original, attacker, adapter);
            pendingExecutions.remove(targetUuid);
            return;
        }
        UltimatumMod.LOGGER.info(
                "{} death rescue for {} timed out after {} ticks; using final deep erasure",
                adapter.name(), active, DEDICATED_DEATH_TIMEOUT);
        deeplyEraseAndSweep(level, active, targetUuid);
    }

    private void deeplyEraseAndSweep(ServerLevel level, Entity target, UUID targetUuid) {
        markAndErase(level, target, 400);
        scheduleTask(level.getServer(), FINAL_SWEEP_DELAY, () -> {
            Entity remainder = findIndexedEntity(level, targetUuid);
            if (remainder != null) {
                UltimatumMod.LOGGER.warn("Target {} re-entered after deep erasure; purging it again",
                        remainder);
                markAndErase(level, remainder, 400);
            }
            pendingExecutions.remove(targetUuid);
        });
    }

    private void genericEraseThenAdapt(ServerLevel level, Entity target,
                                       @Nullable ServerPlayer attacker,
                                       @Nullable KillAdapter adapter) {
        if (adapter != null) {
            try {
                adapter.execute(level, target, attacker, this);
            } catch (Throwable error) {
                UltimatumMod.LOGGER.error("{} emergency cleanup failed for {}",
                        adapter.name(), target, error);
            }
        }
        markAndErase(level, target, 400);
    }

    private void finishSuccessfulDeath(ServerLevel level, Entity target,
                                       @Nullable ServerPlayer attacker,
                                       @Nullable KillAdapter adapter) {
        if (adapter == null) {
            return;
        }
        try {
            adapter.onStandardDeath(level, target, attacker, this);
        } catch (Throwable error) {
            UltimatumMod.LOGGER.warn("{} post-death cleanup failed for {}",
                    adapter.name(), target, error);
        }
    }

    @Nullable
    private static Entity findIndexedEntity(ServerLevel level, UUID uuid) {
        Entity direct = level.getEntity(uuid);
        if (direct != null) {
            return direct;
        }
        // Some hostile implementations corrupt only the UUID lookup while leaving the
        // entity in ticking/section indexes. Treat either representation as still alive.
        for (Entity candidate : level.getAllEntities()) {
            if (candidate.getUUID().equals(uuid)) {
                return candidate;
            }
        }
        return null;
    }

    public boolean isEntityIndexed(ServerLevel level, UUID uuid) {
        return findIndexedEntity(level, uuid) != null;
    }

    private static ServerPlayer findPlayer(MinecraftServer server, UUID uuid) {
        return uuid == null ? null : server.getPlayerList().getPlayer(uuid);
    }

    private KillAdapter findAdapter(Entity target) {
        for (KillAdapter adapter : adapters) {
            if (adapter.supports(target)) {
                return adapter;
            }
        }
        return null;
    }

    private boolean tryStandardDeath(ServerLevel level, LivingEntity living, ServerPlayer attacker) {
        DamageSource source = deathSource(level, attacker);
        try {
            living.hurt(source, Float.MAX_VALUE);
        } catch (Throwable error) {
            UltimatumMod.LOGGER.debug("Normal damage was rejected by {}", living, error);
        }
        return deathStarted(living);
    }

    public boolean tryForcedDeath(ServerLevel level, Entity target, ServerPlayer attacker) {
        if (!(target instanceof LivingEntity living) || target.isRemoved()) {
            return target.isRemoved();
        }
        DamageSource source = deathSource(level, attacker);
        try {
            living.setHealth(0.0F);
            living.die(source);
        } catch (Throwable error) {
            UltimatumMod.LOGGER.debug("Direct death was rejected by {}", target, error);
        }
        return deathStarted(target);
    }

    private static DamageSource deathSource(ServerLevel level, ServerPlayer attacker) {
        return attacker == null
                ? level.damageSources().genericKill()
                : level.damageSources().playerAttack(attacker);
    }

    private static boolean deathStarted(Entity target) {
        if (target.isRemoved()) {
            return true;
        }
        return target instanceof LivingEntity living
                && (!living.isAlive() || living.isDeadOrDying());
    }

    private static boolean requiresUniversalDeathKernel(Entity target) {
        // This is intentionally based on whether the runtime class is vanilla, not on a
        // particular mod id. Unknown modded LivingEntities receive the same generic path
        // as ordinary Omni mobs, without triggering a hostile hurt() override first.
        return !target.getClass().getName().startsWith("net.minecraft.");
    }

    private void startUniversalAnimatedDeath(MinecraftServer server, ServerLevel level,
                                             LivingEntity target,
                                             @Nullable ServerPlayer attacker) {
        UUID targetUuid = target.getUUID();
        genericDeathKernel.execute(level, target, attacker);

        // Keep zero health visible during the normal red/falling animation. Vanilla emits
        // event 60 (the death POOF) only when deathTime reaches 20, immediately before
        // removal, so do not emit it in GenericDeathKernel at death start.
        new GenericBossEventPurger(level).zeroProgress(target);
        for (ServerPlayer player : level.players()) {
            try {
                dev.srryo.ultimatum.network.UltimatumNetwork.animateDeathFor(player, target);
            } catch (Throwable error) {
                UltimatumMod.LOGGER.debug("Could not start client death animation for {}", target,
                        error);
            }
        }
        DeterministicWorldIndexEraser.suspendServerTicking(level, target);
        scheduleTask(server, 21, () -> {
            Entity remainder = findIndexedEntity(level, targetUuid);
            if (remainder != null) {
                try {
                    level.broadcastEntityEvent(remainder, (byte) 60);
                } catch (Throwable error) {
                    UltimatumMod.LOGGER.debug("Could not broadcast delayed death particles for {}",
                            remainder, error);
                }
                markAndErase(level, remainder, 400);
            }
            pendingExecutions.remove(targetUuid);
        });
    }

    public void markAndErase(ServerLevel level, Entity target, int lifetimeTicks) {
        // The timeout parameter is retained for binary/source compatibility with adapters.
        // Final erasure is now permanent for this UUID and is persisted with the world.
        tombstones.addPermanent(level, target);
        eraser.erase(level, target);
    }

    /** Used by the vanilla entity-manager Mixin, independently of Forge events. */
    public boolean blocksReentry(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)
                || !tombstones.blocks(level, entity, level.getServer().getTickCount())) {
            return false;
        }
        // Write the backing removal reason because an adversarial override may make
        // discard()/remove() a no-op while trying to force the entity back into indexes.
        ReflectionAccess.put(entity, Entity.RemovalReason.KILLED,
                "removalReason", "f_146795_");
        return true;
    }

    public DeepEntityEraser eraser() {
        return eraser;
    }

    public void schedulePigReset(MinecraftServer server, int delayTicks, Object pig2Class) {
        long tick = (long) server.getTickCount() + Math.max(1, delayTicks);
        pig2Resets.computeIfAbsent(tick, ignored -> new ConcurrentLinkedQueue<>()).add(pig2Class);
    }

    public void scheduleTask(MinecraftServer server, int delayTicks, Runnable task) {
        long tick = (long) server.getTickCount() + Math.max(1, delayTicks);
        deferredTasks.computeIfAbsent(tick, ignored -> new ConcurrentLinkedQueue<>()).add(task);
    }
}
