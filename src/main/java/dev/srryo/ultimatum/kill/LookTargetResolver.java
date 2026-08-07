package dev.srryo.ultimatum.kill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
    private static final double BROKEN_BOUNDS_EPSILON = 1.0E-4D;
    private static final double STALE_BOUNDS_TOLERANCE = 0.25D;
    private static final double BROKEN_BOUNDS_FALLBACK_RADIUS_SQUARED = 16.0D;

    private LookTargetResolver() {
    }

    static List<Entity> candidates(ServerLevel level, Player player, double reach) {
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getViewVector(1.0F).normalize();
        double visibleReach = unobstructedReach(level, player, start, direction, reach);
        Vec3 end = start.add(direction.scale(visibleReach));
        List<RayCandidate> exact = new ArrayList<>();
        List<RayCandidate> fallback = new ArrayList<>();

        for (Entity entity : level.getAllEntities()) {
            if (entity == player || entity instanceof Player || entity.isRemoved()) {
                continue;
            }

            AABB bounds = entity.getBoundingBox();
            Vec3 hit = bounds.contains(start) ? start : bounds.clip(start, end).orElse(null);
            if (hit != null) {
                exact.add(new RayCandidate(entity, start.distanceToSqr(hit), 0.0D));
                continue;
            }

            // A broad positional fallback is unsafe for ordinary entities: every held-item
            // swing reaches this resolver, including a deliberate click on the ground. Keep
            // the fallback only for protected proxies that publish a degenerate or detached
            // box; normal entities must actually intersect the view ray.
            if (!hasBrokenBounds(entity, bounds)) {
                continue;
            }
            Vec3 toEntity = entity.position().subtract(start);
            double along = toEntity.dot(direction);
            if (along < 0.0D || along > visibleReach) {
                continue;
            }
            Vec3 nearest = start.add(direction.scale(along));
            double perpendicular = nearest.distanceToSqr(entity.position());
            if (perpendicular <= BROKEN_BOUNDS_FALLBACK_RADIUS_SQUARED) {
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

    private static double unobstructedReach(ServerLevel level, Player player, Vec3 start,
                                            Vec3 direction, double reach) {
        Vec3 requestedEnd = start.add(direction.scale(reach));
        BlockHitResult blockHit = level.clip(new ClipContext(start, requestedEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() == HitResult.Type.MISS) {
            return reach;
        }
        return Math.min(reach, start.distanceTo(blockHit.getLocation()));
    }

    private static boolean hasBrokenBounds(Entity entity, AABB bounds) {
        if (!Double.isFinite(bounds.minX) || !Double.isFinite(bounds.minY)
                || !Double.isFinite(bounds.minZ) || !Double.isFinite(bounds.maxX)
                || !Double.isFinite(bounds.maxY) || !Double.isFinite(bounds.maxZ)) {
            return true;
        }
        if (bounds.getXsize() <= BROKEN_BOUNDS_EPSILON
                || bounds.getYsize() <= BROKEN_BOUNDS_EPSILON
                || bounds.getZsize() <= BROKEN_BOUNDS_EPSILON) {
            return true;
        }
        return !bounds.inflate(STALE_BOUNDS_TOLERANCE).contains(entity.position());
    }

    private record RayCandidate(Entity entity, double distanceSquared,
                                double perpendicularSquared) {
    }
}
