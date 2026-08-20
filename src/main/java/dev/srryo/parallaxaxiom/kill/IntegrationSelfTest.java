package dev.srryo.parallaxaxiom.kill;

import dev.srryo.parallaxaxiom.ParallaxAxiomMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.UUID;

/** Production-launch integration harness. It is completely inert unless explicitly enabled. */
final class IntegrationSelfTest {
    private static final String MODE = System.getProperty("parallax_axiom.integrationTest", "");
    private static int startedAt = -1;
    private static UUID targetUuid;
    private static ServerLevel targetLevel;
    private static boolean finished;

    private IntegrationSelfTest() {
    }

    static void tick(MinecraftServer server, KillService service) {
        if (!"pig2".equals(MODE) || finished || server.getTickCount() < 20) {
            return;
        }
        if (startedAt < 0) {
            startPig2(server, service);
            return;
        }
        int elapsed = server.getTickCount() - startedAt;
        Entity indexed = targetLevel == null || targetUuid == null ? null : targetLevel.getEntity(targetUuid);
        if (elapsed >= 12 && indexed == null) {
            finish(server, true, "Pig2 is absent from the ServerLevel UUID index");
        } else if (elapsed > 100) {
            finish(server, false, "Pig2 survived for more than 100 ticks");
        }
    }

    private static void startPig2(MinecraftServer server, KillService service) {
        ResourceLocation key = new ResourceLocation("pig2mod", "pig2");
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
            finish(server, false, "pig2mod:pig2 is not registered");
            return;
        }
        ServerLevel level = server.overworld();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(key);
        Entity target = type.create(level);
        if (target == null) {
            finish(server, false, "Pig2 EntityType returned null");
            return;
        }
        BlockPos spawn = level.getSharedSpawnPos().above(2);
        target.moveTo(spawn, 0.0F, 0.0F);
        if (!level.addFreshEntity(target)) {
            finish(server, false, "ServerLevel rejected the Pig2 spawn");
            return;
        }
        startedAt = server.getTickCount();
        targetUuid = target.getUUID();
        targetLevel = level;
        ParallaxAxiomMod.LOGGER.info("[PARALLAX-AXIOM-IT] Spawned Pig2 {}; executing", targetUuid);
        service.enqueue(target);
    }

    private static void finish(MinecraftServer server, boolean success, String detail) {
        finished = true;
        if (success) {
            ParallaxAxiomMod.LOGGER.info("[PARALLAX-AXIOM-IT] PASS: {}", detail);
        } else {
            ParallaxAxiomMod.LOGGER.error("[PARALLAX-AXIOM-IT] FAIL: {}", detail);
        }
        server.halt(false);
    }
}
