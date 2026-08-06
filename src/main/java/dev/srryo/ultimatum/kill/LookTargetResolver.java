package dev.srryo.ultimatum.kill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Server-authoritative look targeting that does not trust Entity#isPickable. Some
 * protected mobs deliberately prevent the vanilla client attack packet from ever being
 * created, so a held-item swing needs an independent way to resolve the visible body.
 */
final class LookTargetResolver {
    private LookTargetResolver() {
    }

    static List<Entity> candidates(ServerLevel level, Player player, double reach) {
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getViewVector(1.0F).normalize();
        Vec3 end = start.add(direction.scale(reach));
        List<RayCandidate> exact = new ArrayList<>();
        List<RayCandidate> fallback = new ArrayList<>();

        for (Entity entity : level.getAllEntities()) {
            if (entity == player || entity instanceof Player || entity.isRemoved()) {
                continue;
            }

            AABB bounds = entity.getBoundingBox().inflate(0.5D);
            Vec3 hit = bounds.contains(start) ? start : bounds.clip(start, end).orElse(null);
            if (hit != null) {
                exact.add(new RayCandidate(entity, start.distanceToSqr(hit), 0.0D));
                continue;
            }

            // Last resort for entities that publish an empty/stale box. Four blocks is
            // intentionally generous for giant boss models, but it is considered only
            // after every real bounding-box intersection.
            Vec3 toEntity = entity.position().subtract(start);
            double along = toEntity.dot(direction);
            if (along < 0.0D || along > reach) {
                continue;
            }
            Vec3 nearest = start.add(direction.scale(along));
            double perpendicular = nearest.distanceToSqr(entity.position());
            if (perpendicular <= 16.0D) {
                fallback.add(new RayCandidate(entity, along * along, perpendicular));
            }
        }

        Comparator<RayCandidate> order = Comparator
                .comparingDouble(RayCandidate::distanceSquared)
                .thenComparingDouble(RayCandidate::perpendicularSquared);
        exact.sort(order);
        fallback.sort(Comparator.comparingDouble(RayCandidate::perpendicularSquared)
                .thenComparingDouble(RayCandidate::distanceSquared));

        List<Entity> result = new ArrayList<>(exact.size() + fallback.size());
        exact.forEach(candidate -> result.add(candidate.entity()));
        fallback.forEach(candidate -> result.add(candidate.entity()));
        return result;
    }

    private record RayCandidate(Entity entity, double distanceSquared,
                                double perpendicularSquared) {
    }
}
