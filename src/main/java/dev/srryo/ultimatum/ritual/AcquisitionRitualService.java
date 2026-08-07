package dev.srryo.ultimatum.ritual;

import dev.srryo.ultimatum.UltimatumMod;
import dev.srryo.ultimatum.invincibility.InvincibilityService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runs the data-driven acquisition ritual on the End's return-portal podium. */
public final class AcquisitionRitualService {
    private static final int SCAN_INTERVAL = 10;
    private static final double OFFERING_RADIUS = 3.5D;
    private static final double CATALYST_RADIUS = 8.0D;
    private static final String RESERVED_TAG = "UltimatumAcquisitionRitual";

    private final Map<ResourceKey<Level>, RitualState> activeRituals = new HashMap<>();
    private BlockPos cachedEndPodium;
    private MinecraftServer lastProcessedServer;
    private int lastProcessedTick = Integer.MIN_VALUE;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            onVanillaServerTick(event.getServer());
        }
    }

    /** Also called by a vanilla-server mixin because adversarial mods may replace Forge's bus. */
    public void onVanillaServerTick(MinecraftServer server) {
        int tick = server.getTickCount();
        if (lastProcessedServer == server && lastProcessedTick == tick) {
            return;
        }
        lastProcessedServer = server;
        lastProcessedTick = tick;

        var activeIterator = activeRituals.entrySet().iterator();
        while (activeIterator.hasNext()) {
            Map.Entry<ResourceKey<Level>, RitualState> entry = activeIterator.next();
            ServerLevel ritualLevel = server.getLevel(entry.getKey());
            if (ritualLevel == null || entry.getValue().tick(ritualLevel)) {
                activeIterator.remove();
            }
        }

        ServerLevel end = server.getLevel(Level.END);
        if (end == null || activeRituals.containsKey(Level.END)) {
            return;
        }
        if (tick % SCAN_INTERVAL != 0) {
            return;
        }

        BlockPos podium = findEndPodium(end);
        if (podium != null) {
            tryStartAt(end, podium);
        }
    }

    /** Public for GameTest; normal gameplay reaches this only through the End podium scan. */
    public boolean tryStartAt(ServerLevel level, BlockPos center) {
        if (activeRituals.containsKey(level.dimension())) {
            return false;
        }

        AABB offeringArea = offeringArea(center);
        // A hard shutdown can outlive the in-memory state while item NBT survives.
        // Restore those offerings before attempting the ritual again.
        level.getEntitiesOfClass(ItemEntity.class, offeringArea,
                        item -> item.getPersistentData().getBoolean(RESERVED_TAG))
                .forEach(RitualState::restore);
        List<ItemEntity> offerings = level.getEntitiesOfClass(ItemEntity.class,
                offeringArea, item -> item.isAlive() && !item.getItem().isEmpty()
                        && !item.getPersistentData().getBoolean(RESERVED_TAG));
        if (offerings.isEmpty()) {
            return false;
        }

        List<AcquisitionRitualRecipe> recipes = new ArrayList<>(
                level.getRecipeManager().getAllRecipesFor(
                        RitualRegistries.ACQUISITION_TYPE.get()));
        recipes.sort(Comparator.comparingInt(AcquisitionRitualRecipe::priority)
                .reversed());
        for (AcquisitionRitualRecipe recipe : recipes) {
            ServerPlayer catalyst = recipe.requiresWornObserver()
                    ? findObserverCatalyst(level, center) : null;
            if (recipe.requiresWornObserver() && catalyst == null) {
                continue;
            }
            Map<UUID, Integer> reservation = allocate(recipe, offerings);
            if (reservation == null) {
                continue;
            }

            RitualState state = new RitualState(center.immutable(), recipe,
                    reservation, catalyst == null ? null : catalyst.getUUID());
            state.reserve(level);
            activeRituals.put(level.dimension(), state);
            level.playSound(null, center, SoundEvents.BEACON_ACTIVATE,
                    SoundSource.BLOCKS, 1.25F, 0.72F);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    center.getX() + 0.5D, center.getY() + 2.0D,
                    center.getZ() + 0.5D, 90, 2.4D, 1.2D, 2.4D, 0.04D);
            UltimatumMod.LOGGER.info("Started acquisition ritual {} at {}",
                    recipe.getId(), center);
            return true;
        }
        return false;
    }

    private static Map<UUID, Integer> allocate(AcquisitionRitualRecipe recipe,
                                                List<ItemEntity> offerings) {
        Map<UUID, Integer> reserved = new LinkedHashMap<>();
        for (AcquisitionRitualRecipe.Requirement requirement : recipe.requirements()) {
            int needed = requirement.count();
            for (ItemEntity entity : offerings) {
                ItemStack stack = entity.getItem();
                if (!requirement.ingredient().test(stack)) {
                    continue;
                }
                int alreadyReserved = reserved.getOrDefault(entity.getUUID(), 0);
                int available = stack.getCount() - alreadyReserved;
                if (available <= 0) {
                    continue;
                }
                int taken = Math.min(needed, available);
                reserved.put(entity.getUUID(), alreadyReserved + taken);
                needed -= taken;
                if (needed == 0) {
                    break;
                }
            }
            if (needed > 0) {
                return null;
            }
        }
        return reserved;
    }

    private static ServerPlayer findObserverCatalyst(ServerLevel level,
                                                      BlockPos center) {
        Vec3 point = Vec3.atCenterOf(center).add(0.0D, 1.5D, 0.0D);
        AABB area = AABB.ofSize(point, CATALYST_RADIUS * 2.0D,
                CATALYST_RADIUS * 2.0D, CATALYST_RADIUS * 2.0D);
        return level.getEntitiesOfClass(ServerPlayer.class, area,
                        InvincibilityService::hasAbsoluteArtifactEquipped)
                .stream()
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(point)))
                .orElse(null);
    }

    private BlockPos findEndPodium(ServerLevel level) {
        if (cachedEndPodium != null && isPodium(level, cachedEndPodium)) {
            return cachedEndPodium;
        }
        cachedEndPodium = null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = level.getMaxBuildHeight() - 1;
             y >= level.getMinBuildHeight(); y--) {
            cursor.set(0, y, 0);
            if (isPodium(level, cursor)) {
                cachedEndPodium = cursor.immutable();
                return cachedEndPodium;
            }
        }
        return null;
    }

    private static boolean isPodium(ServerLevel level, BlockPos center) {
        if (!level.getBlockState(center).is(Blocks.BEDROCK)) {
            return false;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = -4; y <= 1; y++) {
                    cursor.set(center.getX() + x, center.getY() + y,
                            center.getZ() + z);
                    if (level.getBlockState(cursor).is(Blocks.END_PORTAL)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static AABB offeringArea(BlockPos center) {
        return AABB.ofSize(Vec3.atCenterOf(center).add(0.0D, 2.0D, 0.0D),
                OFFERING_RADIUS * 2.0D, 5.0D, OFFERING_RADIUS * 2.0D);
    }

    private static final class RitualState {
        private final BlockPos center;
        private final AcquisitionRitualRecipe recipe;
        private final Map<UUID, Integer> reservation;
        private final UUID catalyst;
        private int age;

        private RitualState(BlockPos center, AcquisitionRitualRecipe recipe,
                            Map<UUID, Integer> reservation, UUID catalyst) {
            this.center = center;
            this.recipe = recipe;
            this.reservation = reservation;
            this.catalyst = catalyst;
        }

        private void reserve(ServerLevel level) {
            int index = 0;
            for (UUID id : reservation.keySet()) {
                ItemEntity entity = findItem(level, id);
                if (entity != null) {
                    prepare(entity);
                    position(entity, index++, reservation.size(), 0.0F);
                }
            }
        }

        /** Returns true when the state has either completed or cancelled. */
        private boolean tick(ServerLevel level) {
            if (!offeringsRemain(level) || !catalystRemains(level)) {
                release(level);
                level.playSound(null, center, SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 0.8F, 0.62F);
                return true;
            }

            age++;
            int index = 0;
            float phase = age * 0.055F;
            for (UUID id : reservation.keySet()) {
                ItemEntity entity = findItem(level, id);
                if (entity != null) {
                    prepare(entity);
                    position(entity, index++, reservation.size(), phase);
                }
            }

            if (age % 4 == 0) {
                double progress = age / (double) recipe.duration();
                level.sendParticles(ParticleTypes.END_ROD,
                        center.getX() + 0.5D, center.getY() + 1.8D + progress,
                        center.getZ() + 0.5D, 5, 1.7D, 0.45D, 1.7D, 0.01D);
                level.sendParticles(ParticleTypes.PORTAL,
                        center.getX() + 0.5D, center.getY() + 1.5D,
                        center.getZ() + 0.5D, 9, 2.0D, 0.8D, 2.0D, 0.035D);
            }
            if (age < recipe.duration()) {
                return false;
            }

            complete(level);
            return true;
        }

        private boolean offeringsRemain(ServerLevel level) {
            for (Map.Entry<UUID, Integer> entry : reservation.entrySet()) {
                ItemEntity entity = findItem(level, entry.getKey());
                if (entity == null || !entity.isAlive()
                        || entity.getItem().getCount() < entry.getValue()) {
                    return false;
                }
            }
            return true;
        }

        private boolean catalystRemains(ServerLevel level) {
            if (catalyst == null) {
                return true;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(catalyst);
            return player != null && player.level() == level
                    && player.distanceToSqr(Vec3.atCenterOf(center))
                    <= CATALYST_RADIUS * CATALYST_RADIUS
                    && InvincibilityService.hasAbsoluteArtifactEquipped(player);
        }

        private void complete(ServerLevel level) {
            for (Map.Entry<UUID, Integer> entry : reservation.entrySet()) {
                ItemEntity entity = findItem(level, entry.getKey());
                if (entity == null) {
                    continue;
                }
                entity.getItem().shrink(entry.getValue());
                if (entity.getItem().isEmpty()) {
                    entity.discard();
                } else {
                    restore(entity);
                }
            }

            ItemStack result = recipe.getResultItem(level.registryAccess());
            ItemEntity output = new ItemEntity(level,
                    center.getX() + 0.5D, center.getY() + 2.3D,
                    center.getZ() + 0.5D, result);
            output.setDefaultPickUpDelay();
            output.setDeltaMovement(0.0D, 0.22D, 0.0D);
            level.addFreshEntity(output);
            level.sendParticles(ParticleTypes.FLASH,
                    center.getX() + 0.5D, center.getY() + 2.1D,
                    center.getZ() + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.END_ROD,
                    center.getX() + 0.5D, center.getY() + 2.1D,
                    center.getZ() + 0.5D, 150, 2.8D, 1.7D, 2.8D, 0.08D);
            level.playSound(null, center, SoundEvents.END_PORTAL_SPAWN,
                    SoundSource.BLOCKS, 1.6F, 1.15F);
            UltimatumMod.LOGGER.info("Completed acquisition ritual {} at {}",
                    recipe.getId(), center);
        }

        private void release(ServerLevel level) {
            for (UUID id : reservation.keySet()) {
                ItemEntity entity = findItem(level, id);
                if (entity != null) {
                    restore(entity);
                }
            }
        }

        private void position(ItemEntity entity, int index, int total,
                              float phase) {
            double angle = phase + Math.PI * 2.0D * index / Math.max(1, total);
            double radius = 1.35D + 0.18D * (index % 3);
            double y = center.getY() + 1.75D
                    + Math.sin(angle * 1.7D + index) * 0.24D;
            entity.setPos(center.getX() + 0.5D + Math.cos(angle) * radius,
                    y, center.getZ() + 0.5D + Math.sin(angle) * radius);
            entity.setDeltaMovement(Vec3.ZERO);
        }

        private static void prepare(ItemEntity entity) {
            entity.getPersistentData().putBoolean(RESERVED_TAG, true);
            entity.setNoGravity(true);
            entity.setInvulnerable(true);
            entity.setPickUpDelay(Integer.MAX_VALUE);
            entity.setDeltaMovement(Vec3.ZERO);
        }

        private static void restore(ItemEntity entity) {
            entity.getPersistentData().remove(RESERVED_TAG);
            entity.setNoGravity(false);
            entity.setInvulnerable(false);
            entity.setDefaultPickUpDelay();
        }

        private static ItemEntity findItem(ServerLevel level, UUID id) {
            return level.getEntity(id) instanceof ItemEntity item ? item : null;
        }
    }
}
